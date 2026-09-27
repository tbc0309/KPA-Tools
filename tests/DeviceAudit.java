package cn.pegasus.setup;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Regression fixtures confined to the supplied /data/local/tmp audit directory. */
public final class DeviceAudit {
 private static int failures;
 private static void check(String name,boolean result){System.out.println((result?"PASS ":"FAIL ")+name);if(!result)failures++;}
 private static File zip(File root,String name,String... entries)throws Exception{
  File file=new File(root,name);try(ZipOutputStream out=new ZipOutputStream(new FileOutputStream(file))){
   for(String entry:entries){out.putNextEntry(new ZipEntry(entry));out.write("fixture".getBytes("UTF-8"));out.closeEntry();}
  }return file;
 }
 private static int run(File script)throws Exception{
  Process p=new ProcessBuilder("/system/bin/sh",script.getAbsolutePath()).redirectErrorStream(true).start();
  try(BufferedReader in=new BufferedReader(new InputStreamReader(p.getInputStream()))){while(in.readLine()!=null){}}
  return p.waitFor();
 }
 public static void main(String[] args)throws Exception{
  if(args.length!=1||!args[0].matches("/data/local/tmp/kpa-audit-[A-Za-z0-9_-]+"))throw new Exception("Unsafe test root");
  File root=new File(args[0]);if(root.exists())throw new Exception("Use a fresh test directory");root.mkdirs();
  check("main archive ordering",SourceDiscovery.kind(zip(root,"mixed.zip","Roms/GBA/game.gba","pegasus-frontend/settings.txt","RetroArch/retroarch.cfg"))==1);
  check("android archive",SourceDiscovery.kind(zip(root,"android.zip","PG_Android/Android/data/org.pegasus_frontend.android/files/pegasus-frontend/settings.txt"))==3);
  check("gba archive",SourceDiscovery.kind(zip(root,"gba.zip","Roms/GBA/game.gba","Roms/GBA/metadata.pegasus.txt"))==2);
  check("unknown archive",SourceDiscovery.kind(zip(root,"unknown.zip","notes.txt"))==0);
  for(String path:new String[]{"../escape","/absolute","a/../b","a/./b","C:/boot","a\nb"}){
   boolean rejected=false;try{SetupEngine.safe(path);}catch(IOException expected){rejected=true;}check("reject path "+path.replace('\n','_'),rejected);
  }
  check("normal archive path",SetupEngine.safe("RetroArch/config/test.cfg").equals("RetroArch/config/test.cfg"));
  check("skip saves",SetupEngine.skip("Roms/GBA/saves/test.sav"));
  check("translate storage state",KpaLanguage.english("存储准备：已完成").equals("Storage setup: ready"));
  AppSpec[] apps=AppSpec.list();for(AppSpec app:apps)app.configure=true;
  check("TF only game target",SetupEngine.spaceTarget("Roms/GBA/game.gba",new File("/storage/ABCD-1234"),apps).startsWith("/storage/ABCD-1234/"));
  check("config remains internal",SetupEngine.spaceTarget("RetroArch/retroarch.cfg",new File("/storage/ABCD-1234"),apps).startsWith(SetupEngine.HOME+"/"));
  check("unknown package rejected",SetupEngine.spaceTarget("Android/data/com.unrelated/files/a",root,apps)==null);
  File source=new File(root,"source.txt"),target=new File(root,"output/target.txt"),victim=new File(root,"unrelated.txt");
  SetupEngine.write(source,"new");SetupEngine.write(victim,"keep");target.getParentFile().mkdirs();
  Files.createSymbolicLink(target.toPath(),victim.toPath());
  LinkedHashMap<String,SetupEngine.Item> items=new LinkedHashMap<>();items.put(target.getAbsolutePath(),new SetupEngine.Item(source,target.getAbsolutePath()));
  File layer=new File(root,"runs/ordered/main");layer.mkdirs();BulkLayerWriter.write(layer,items,0,1);
  check("reject destination symlink",run(new File(layer,"apply.sh"))!=0&&SetupEngine.read(victim).equals("keep"));
  Files.delete(target.toPath());
  check("normal copy",run(new File(layer,"apply.sh"))==0&&SetupEngine.read(target).equals("new"));
  IntegrityVerifier.prepare(new File(root,"verify"),items.values(),apps);
  check("existence verification",run(new File(root,"verify/"+SetupEngine.VERIFY_SCRIPT))==0);
  Files.delete(target.toPath());
  check("missing file verification",run(new File(root,"verify/"+SetupEngine.VERIFY_SCRIPT))!=0);
  check("missing file repair",run(new File(root,"verify/repair.sh"))==0&&target.isFile());
  Files.delete(target.toPath());
  File missingVictim=new File(root,"unrelated-missing.txt");
  Files.createSymbolicLink(target.toPath(),missingVictim.toPath());
  check("reject repair symlink",run(new File(root,"verify/repair.sh"))!=0&&!missingVictim.exists());
  for(AppSpec app:apps)app.configure=false;
  check("deselected frontend config",SetupEngine.spaceTarget("pegasus-frontend/settings.txt",root,apps)==null);
  check("deselected emulator config",SetupEngine.spaceTarget("RetroArch/retroarch.cfg",root,apps)==null);
  System.out.println("FAILURES="+failures);if(failures!=0)System.exit(1);
 }
}
