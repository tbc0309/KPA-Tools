package cn.pegasus.setup;
import java.io.*;import java.util.*;import org.json.*;
/** 在启动模拟器前核验准备文件。 */
public final class IntegrityVerifier {
 static boolean criticalTarget(String target,long size){
  String n=target.toLowerCase(Locale.ROOT);if(size>2L*1024*1024)return false;
  return n.endsWith(".cfg")||n.endsWith(".ini")||n.endsWith(".xml")||n.endsWith(".json")||n.endsWith(".txt")||n.endsWith(".opt")||n.endsWith(".conf");
 }
 static boolean critical(SetupEngine.Item f){return criticalTarget(f.target,f.size);}
 public static void prepare(File run,Collection<SetupEngine.Item> files,AppSpec[] apps)throws Exception {
  StringBuilder list=new StringBuilder(),sizes=new StringBuilder();boolean ra=false;Map<String,String> samples=new LinkedHashMap<>();
  for(SetupEngine.Item f:files){String t=f.target;if(t.startsWith(SetupEngine.HOME+"/Android/data/com.retroarch.aarch64/"))ra=true;if(critical(f))list.append(f.hash).append("  ").append(t).append('\n');sizes.append(f.size).append('\t').append(t).append('\n');
   if(t.startsWith("/data/user/0/")){String[] p=t.split("/");String dir="/data/user/0/"+p[4]+"/"+p[5];if(!samples.containsKey(dir))samples.put(dir,t);}}
  File template=new File(run,"final-files-template.md5"),sizeList=new File(run,"final-files.sizes"),manifest=new File(run,"final-files.md5"),details=new File(run,"verification-details.txt"),state=new File(run,"verification-state.txt");SetupEngine.write(template,list.toString());SetupEngine.write(sizeList,sizes.toString());
  StringBuilder s=new StringBuilder("#!/system/bin/sh\nset -e\n[ \"$(id -u)\" = 0 ] || exit 1\n");
  s.append("echo 'STATUS=CHECKING' > ").append(RootBridge.q(state.getAbsolutePath())).append("\ntrap 'printf \"STATUS=PARTIAL\\nFILES=").append(files.size()).append("\\nRA=").append(ra?"OK":"NOT_SELECTED").append("\\n\" > ").append(RootBridge.q(state.getAbsolutePath())).append("' EXIT\ncp ").append(RootBridge.q(template.getAbsolutePath())).append(' ').append(RootBridge.q(manifest.getAbsolutePath())).append("\n");
  File actualSizes=new File(run,"final-files-actual.sizes"),expectedSizes=new File(run,"final-files-expected.sizes");
  s.append("[ ! -s ").append(RootBridge.q(manifest.getAbsolutePath())).append(" ] || md5sum -c ").append(RootBridge.q(manifest.getAbsolutePath())).append(" > ").append(RootBridge.q(details.getAbsolutePath())).append(" 2>&1\n");
  // 用 NUL 分隔并批量 stat，兼容带空格的文件名，避免逐文件创建进程。
  s.append("awk '{sub(/^[^\\t]*\\t/,\"\"); printf \"%s%c\",$0,0}' ").append(RootBridge.q(sizeList.getAbsolutePath())).append(" | xargs -0 -n 128 stat -c '%s %n' > ").append(RootBridge.q(actualSizes.getAbsolutePath())).append("\ntr '\\t' ' ' < ").append(RootBridge.q(sizeList.getAbsolutePath())).append(" > ").append(RootBridge.q(expectedSizes.getAbsolutePath())).append("\ncmp -s ").append(RootBridge.q(expectedSizes.getAbsolutePath())).append(' ').append(RootBridge.q(actualSizes.getAbsolutePath())).append("\nrm -f ").append(RootBridge.q(actualSizes.getAbsolutePath())).append(' ').append(RootBridge.q(expectedSizes.getAbsolutePath())).append("\n");
  for(Map.Entry<String,String> e:samples.entrySet()){String base=e.getKey().substring(0,e.getKey().lastIndexOf('/'));s.append("[ \"$(stat -c %u ").append(RootBridge.q(base)).append(")\" = \"$(stat -c %u ").append(RootBridge.q(e.getValue())).append(")\" ]\n[ \"$(ls -Zd ").append(RootBridge.q(base)).append(" | awk '{print $1}')\" = \"$(ls -Z ").append(RootBridge.q(e.getValue())).append(" | awk '{print $1}')\" ]\n");}
  s.append("printf 'STATUS=OK\\nFILES=").append(files.size()).append("\\nRA=").append(ra?"OK":"NOT_SELECTED").append("\\n' > ").append(RootBridge.q(state.getAbsolutePath())).append("\ntrap - EXIT\necho FILES_OK\n");SetupEngine.write(new File(run,"verify.sh"),s.toString());
  File entry=new File(SetupEngine.HOME,"KPA_Check_"+run.getName().replace('-','_')+".sh");SetupEngine.write(new File(run,"check-entry.sh"),"#!/system/bin/sh\nsh "+RootBridge.q(new File(run,"verify.sh").getAbsolutePath())+"\n");
 }
 public static boolean passed(File run){try{return SetupEngine.read(new File(run,"verification-state.txt")).contains("STATUS=OK");}catch(Exception e){return false;}}
 public static int count(File run){try{String r=SetupEngine.read(new File(run,"verification-state.txt"));for(String l:r.split("\n"))if(l.startsWith("FILES="))return Integer.parseInt(l.substring(6));}catch(Exception ignored){}return 0;}
 public static File exportEntry(File run)throws Exception {File out=new File(SetupEngine.HOME,"KPA_Check_"+run.getName().replace('-','_')+".sh");SetupEngine.write(out,SetupEngine.read(new File(run,"check-entry.sh")));return out;}
}
