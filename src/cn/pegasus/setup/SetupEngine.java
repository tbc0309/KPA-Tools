package cn.pegasus.setup;

import android.content.Context;
import android.os.StatFs;
import org.json.*;
import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;

public final class SetupEngine {
 public interface Log { void line(String s); }
 public static final String HOME="/storage/emulated/0";
 final Context context; final File config,gba,destination,run,stage; final AppSpec[] apps; final Log log;
 final LinkedHashMap<String,Item> items=new LinkedHashMap<>();
 final LinkedHashMap<String,String> apkHashes=new LinkedHashMap<>();
 final LinkedHashMap<String,Item> mainItems=new LinkedHashMap<>(),gbaItems=new LinkedHashMap<>(),androidItems=new LinkedHashMap<>(),listItems=new LinkedHashMap<>();
 String activeLayer="";
 final RootBridge root;
 boolean includeGba=true;
 int progressStart=8,progressEnd=20; long lastItemReport=0;
 void itemProgress(String label,int done,int total,boolean force){long now=System.currentTimeMillis();if(force||done==total||now-lastItemReport>=2000){lastItemReport=now;log.line("@ITEM\t"+label+"\t"+done+"\t"+Math.max(1,total));}}
 ImportSources sources=new ImportSources();
 File exportedScript;
 static final class Item { final File source; final String target; final long size; final String hash;
  Item(File s,String t) throws Exception{source=s;target=t;size=s.length();hash=IntegrityVerifier.criticalTarget(t,size)?md5(s):"size:"+size;}}
 public SetupEngine(Context c,File cfg,File g,File dst,AppSpec[] a,Log l) throws Exception {
  this(c,cfg,g,dst,a,l,null);
 }
 public SetupEngine(Context c,File cfg,File g,File dst,AppSpec[] a,Log l,File resumed)throws Exception {
  context=c;config=cfg;gba=g;destination=dst;apps=a;log=l;
  RootBridge candidate=null;try{candidate=new RootBridge(c);}catch(Exception ignored){}root=candidate;
  run=resumed!=null&&resumed.isDirectory()&&resumed.getParentFile().getAbsolutePath().equals(HOME+"/PegasusG-Setup/runs")&&!java.nio.file.Files.isSymbolicLink(resumed.toPath())?resumed:new File(HOME+"/PegasusG-Setup/runs/"+new java.text.SimpleDateFormat("yyyyMMdd-HHmmss-SSS",Locale.US).format(new Date()));
  // 临时文件放在应用目录，避免公共存储扫描大量小文件。
  File privateExternal=context.getExternalFilesDir("staging");
  stage=privateExternal==null?new File(run,"stage"):new File(privateExternal,run.getName());
  if(!stage.isDirectory()&&!stage.mkdirs())throw new IOException("无法创建本地准备目录");
 }
 public File prepare() throws Exception {
  // 准备阶段只解包并生成清单，不改动目标目录。
  File cfgZip=sources.mainZip!=null?sources.mainZip:findConfigZip(config);
  if(gba!=null&&(!gba.isFile()||!gba.getName().toLowerCase(Locale.ROOT).endsWith(".zip")))throw new IOException("所选 GBA整包不可用");
  if(!destination.isDirectory()||!destination.canWrite())throw new IOException("游戏目标目录不可写");
  if(gba!=null&&SourceDiscovery.kind(gba)!=2)throw new IOException("GBA 包中没有识别到游戏与 metadata.pegasus.txt 列表");
  if(gba!=null&&(!enabled("org.pegasus_frontend.android")||!enabled("com.retroarch.aarch64")))throw new IOException("导入 GBA整包需要启用天马G和 RA 配置");
  includeGba=gba!=null&&spaceAllowsGba(cfgZip,gba,sources,apps,destination,new StatFs(destination.getAbsolutePath()).getAvailableBytes());
  write(new File(run,"gba-status.txt"),gba==null?"SKIPPED":includeGba?"INCLUDED":"MANUAL");
  if(gba==null)log.line("GBA整包：未选择，本次跳过");else if(!includeGba)log.line("GBA整包：空间不足，本次仅安装应用与覆盖配置");
  log.line("准备资料：开始整理配置与游戏");
  log.line("@PROGRESS:8");activeLayer="main";extract(cfgZip,false);progressStart=20;progressEnd=27;if(includeGba){activeLayer="gba";extract(gba,true);}log.line("@PROGRESS:27");
  activeLayer="android";if(sources.androidZip!=null)extractAndroid(sources.androidZip);else overlay(new File(config,"【2】覆盖安卓文件夹/Android"),"Android");
  // 只覆盖整合包实际包含的游戏列表。
  File lists=sources.lists!=null?sources.lists:new File(config,"【3】覆盖游戏列表（装好游戏后覆盖）");
  if(new File(lists,"Roms").isDirectory())lists=new File(lists,"Roms");
  activeLayer="lists";if(lists.isDirectory()){File[] ds=lists.listFiles();if(ds!=null)for(File d:ds)if(d.isDirectory()&&hasCollection(d.getName()))overlay(d,"Roms/"+d.getName());}
  File settings=new File(stage,"device-template.txt");
  try(InputStream in=context.getAssets().open("templates/pegasus-frontend/settings.txt");OutputStream out=new FileOutputStream(settings)){copy(in,out);}
  String text=read(settings);write(settings,text);
  activeLayer="main";put(settings,HOME+"/Android/data/org.pegasus_frontend.android/files/pegasus-frontend/settings.txt");
  put(settings,HOME+"/pegasus-frontend/settings.txt");
  File dirs=new File(stage,"game_dirs.txt");TreeSet<String> collections=new TreeSet<>();
  for(Item item:items.values())if(item.target.endsWith("/metadata.pegasus.txt")&&item.target.startsWith(destination.getAbsolutePath()+"/Roms/"))collections.add(new File(item.target).getParent());
  if(includeGba&&collections.isEmpty())throw new IOException("整合包中未找到 metadata.pegasus.txt 游戏列表");
  File previous=new File(HOME,"pegasus-frontend/game_dirs.txt");
  if(previous.isFile())for(String line:read(previous).split("\n")){String path=line.trim();if(path.startsWith("/storage/")&&new File(path).isDirectory())collections.add(path);}
  if(!includeGba)collections.add(destination.getAbsolutePath()+"/Roms/GBA");
  write(dirs,String.join("\n",collections)+"\n");
  put(dirs,HOME+"/pegasus-frontend/game_dirs.txt");
  put(dirs,HOME+"/Android/data/org.pegasus_frontend.android/files/pegasus-frontend/game_dirs.txt");
  log.line("RA 初始化：安装后自动启动并等待资源释放");
  long bytes=0;for(Item item:items.values())bytes+=item.size;
  try(android.util.JsonWriter w=new android.util.JsonWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(new File(run,"plan.json")),StandardCharsets.UTF_8)))){
   w.beginObject().name("files").beginArray();for(Item item:items.values())w.beginObject().name("source").value(item.source.getAbsolutePath()).name("target").value(item.target).name("bytes").value(item.size).name("md5").value(item.hash).endObject();w.endArray().name("apps").beginArray();
   for(AppSpec a:apps)if(a.install||a.configure){if(a.install){if(a.apk==null)throw new IOException("缺少安装包："+a.label);apkHashes.put(a.pkg,md5(a.apk));}w.beginObject().name("package").value(a.pkg).name("label").value(a.label).name("install").value(a.install).name("configure").value(a.configure).name("apk").value(a.apk==null?null:a.apk.getAbsolutePath()).name("apkMd5").value(apkHashes.get(a.pkg)).endObject();}
   w.endArray().name("config").value(config.getAbsolutePath()).name("gba").value(gba==null?null:gba.getAbsolutePath()).name("gbaImported").value(includeGba).name("destination").value(destination.getAbsolutePath()).name("sources").beginObject().name("mainZip").value(cfgZip.getAbsolutePath()).name("androidZip").value(sources.androidZip==null?null:sources.androidZip.getAbsolutePath()).name("lists").value(lists.getAbsolutePath()).name("grantStorage").value(sources.grantStorage).endObject().endObject();
  }
  log.line("执行清单完成，正在生成 Root 脚本");
  createScripts();log.line("资料准备完成："+items.size()+" 个文件 · "+gb(bytes)+" GB");return run;
 }
 public boolean execute() throws Exception {
  try{if(root==null)throw new IOException("普通应用无法访问系统 Root 服务");root.check();}
  catch(Exception e){log.line("存储 Root 接口不可直调："+e.getMessage()+"\n已生成 "+getSetupScript()+"。请在掌机设置的 Root 脚本入口运行一次，然后返回读取结果。");return false;}
  log.line("正在通过存储 Root 执行初始化");
  String result=root.call("sh "+RootBridge.q(getSetupScript().getAbsolutePath()));log.line(result);
  if(!new File(run,"root-status.txt").isFile()||!read(new File(run,"root-status.txt")).trim().equals("DONE"))throw new IOException("初始化未完成，请查看 root.log");
  log.line("文件导入完成，请查看最终结果");return true;
 }
 void checkSources() throws Exception {for(Item i:items.values())if(IntegrityVerifier.critical(i)&&!md5(i.source).equals(i.hash))throw new IOException("准备文件被修改，请重新准备："+i.source);}
 void createScripts() throws Exception {
  writeOrderedLayers();
  IntegrityVerifier.prepare(run,items.values(),apps);createBootstrap();
 }
 void writeOrderedLayers()throws Exception{
  writeLayer("main",mainItems);writeLayer("gba",gbaItems);writeLayer("lists",listItems);writeLayer("android",androidItems);
 }
 void writeLayer(String name,Map<String,Item> items)throws Exception{File dir=new File(run,"ordered/"+name);if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("无法创建分步脚本目录："+name);BulkLayerWriter.write(dir,items,Collections.emptyMap());}
 static String restoreLabel(String dir){String base=dir.substring(0,dir.lastIndexOf('/'));return "restorecon -RF "+RootBridge.q(dir)+"\nlabel=$(ls -Zd "+RootBridge.q(base)+" | awk '{print $1}')\ncase \"$label\" in *:app_data_file:*|*:privapp_data_file:*) ;; *) echo 'Unexpected application security label'; exit 1;; esac\nchcon -R \"$label\" "+RootBridge.q(dir)+"\n";}
 void createBootstrap() throws Exception {
  String status=RootBridge.q(new File(run,"root-status.txt").getAbsolutePath());
  File finalScript=new File(HOME,"KPA_Setup_"+run.getName().replace('-','_')+".sh");
  StringBuilder s=new StringBuilder("#!/system/bin/sh\nset -e\n[ \"$(id -u)\" = 0 ] || { echo 'Run from the handheld Root script entry'; exit 1; }\n");
  s.append("if [ -f ").append(status).append(" ] && [ \"$(cat ").append(status).append(")\" = DONE ]; then echo 'Completed. Return to KPA Assistant.'; exit 0; fi\n");
  s.append("statusfile=").append(status).append("\ntrap 'code=$?; if [ $code -ne 0 ]; then echo ERROR > \"$statusfile\"; echo \"Failed, exit code $code\"; fi' EXIT\n");
  s.append("warnings=").append(RootBridge.q(new File(run,"warnings.txt").getAbsolutePath())).append("\n: > \"$warnings\"\nwarn() { echo \"WARNING: $*\" | tee -a \"$warnings\"; }\n");
  s.append("echo VALIDATING > ").append(status).append("\n");
  LinkedHashMap<String,String> checked=new LinkedHashMap<>();for(Item i:items.values())if(IntegrityVerifier.critical(i))checked.put(i.source.getAbsolutePath(),i.hash);for(AppSpec a:apps)if(a.install)checked.put(a.apk.getAbsolutePath(),apkHashes.get(a.pkg));
  StringBuilder checks=new StringBuilder();for(Map.Entry<String,String> e:checked.entrySet())checks.append(e.getValue()).append("  ").append(e.getKey()).append('\n');File checkList=new File(run,"prepared-source.md5");write(checkList,checks.toString());
  s.append("md5sum -c -s ").append(RootBridge.q(checkList.getAbsolutePath())).append("\n");
  // 只清理已选应用的配置目录。
  s.append("# Clear only the selected applications' old configuration directories.\necho CLEAN_APPS > ").append(status).append("\n");
  for(AppSpec a:apps)if(a.install||a.configure){
   s.append("am force-stop ").append(RootBridge.q(a.pkg)).append(" >/dev/null 2>&1 || true\necho ").append(RootBridge.q("Cleaning old data: "+a.pkg)).append('\n');
   // RA 重装前清除私有首次启动状态；随后定制层会重新写入公开配置。
   if(a.install&&a.pkg.equals("com.retroarch.aarch64"))s.append("if pm path com.retroarch.aarch64 >/dev/null 2>&1; then n=0; cleared=0; while [ $n -lt 10 ]; do pm clear com.retroarch.aarch64 >/dev/null 2>&1 && { cleared=1; break; }; am force-stop com.google.android.packageinstaller >/dev/null 2>&1 || true; am force-stop com.android.vending >/dev/null 2>&1 || true; n=$((n+1)); sleep 2; done; [ $cleared -eq 1 ] || { echo 'Unable to clear RetroArch data'; exit 1; }; fi\n");
   for(String path:resetPaths(a.pkg))s.append("rm -rf -- ").append(RootBridge.q(path)).append('\n');
  }
  AppSpec pegasus=findApp("org.pegasus_frontend.android"),ra=findApp("com.retroarch.aarch64");
  // 安装天马G后导入 KPA 定制配置。
  s.append("# Install Pegasus G, then import the KONKR Pocket Advance customization package.\n");
  appendInstall(s,status,pegasus,true);
  appendLayer(s,status,"CUSTOM","Applying custom configuration","main",false);
  // 导入 GBA 整合包，再覆盖【3】中的游戏列表。
  s.append("# Import the selected game bundle, then overwrite it with the guide's final Roms lists.\n");
  if(includeGba)appendLayer(s,status,"GBA","Applying GBA package","gba",false);
  appendLayer(s,status,"LISTS","Applying game lists","lists",false);
  if(ra!=null&&(ra.install||ra.configure)){appendInstall(s,status,ra,true);if(sources.grantStorage)s.append(StorageGrant.commands(ra.pkg));
  // 启动 RA，并等待当前版本完成 base.apk 初始化。
   s.append("# Launch RetroArch and wait for this version's base.apk extraction to complete.\n");
   String cfg=HOME+"/Android/data/com.retroarch.aarch64/files/retroarch.cfg";
   s.append("echo RA_FIRST_LAUNCH > ").append(status).append("\nmkdir -p ").append(RootBridge.q(new File(cfg).getParent())).append("\nrm -f ").append(RootBridge.q(cfg)).append("\nfor perm in android.permission.READ_EXTERNAL_STORAGE android.permission.WRITE_EXTERNAL_STORAGE; do n=0; while [ $n -lt 10 ]; do pm grant com.retroarch.aarch64 \"$perm\" >/dev/null 2>&1 || true; dumpsys package com.retroarch.aarch64 | grep -F \"$perm: granted=true\" >/dev/null && break; n=$((n+1)); sleep 1; done; [ $n -lt 10 ] || { echo \"RetroArch permission failed: $perm\"; exit 1; }; done\nmonkey -p com.retroarch.aarch64 1 >/dev/null 2>&1 || true\nn=0\nwhile [ $n -lt 30 ]; do\n  last=$(sed -n 's/^bundle_assets_extract_last_version[[:space:]]*=[[:space:]]*\"\\([^\"]*\\)\".*/\\1/p' ").append(RootBridge.q(cfg)).append(" 2>/dev/null | tail -n 1)\n  current=$(sed -n 's/^bundle_assets_extract_version_current[[:space:]]*=[[:space:]]*\"\\([^\"]*\\)\".*/\\1/p' ").append(RootBridge.q(cfg)).append(" 2>/dev/null | tail -n 1)\n  [ -n \"$current\" ] && [ \"$last\" = \"$current\" ] && break\n  n=$((n+1))\n  sleep 2\ndone\n[ $n -lt 30 ] || { echo 'RetroArch base.apk extraction timed out'; exit 1; }\nam force-stop com.retroarch.aarch64 >/dev/null 2>&1 || true\nam start --user 0 -n com.imnks.kpatools/cn.pegasus.setup.MainActivity >/dev/null 2>&1 || true\necho 'RetroArch base.apk extraction completed'\n");
  }
  // RA 完成后安装其余模拟器和 MT。
  s.append("# Install the remaining selected emulators and MT Manager.\n");
  for(AppSpec a:apps)if(!a.pkg.equals("org.pegasus_frontend.android")&&!a.pkg.equals("com.retroarch.aarch64")){appendInstall(s,status,a,false);if((a.install||a.configure)&&sources.grantStorage)s.append(StorageGrant.commands(a.pkg));}
  if(enabled("com.retroarch.aarch64"))log.line("RA 初始化：自动启动并等待 base.apk 完成");
  // 最后执行 KPA 安卓目录覆盖并核验结果。
  appendLayer(s,status,"APPLY","Applying Android overlay","android",false);
  s.append("echo VERIFY > ").append(status).append("\nif ! sh ").append(RootBridge.q(new File(run,"verify.sh").getAbsolutePath())).append("; then warn '部分文件核验未通过，请查看核验明细'; fi\ncp ").append(RootBridge.q(new File(run,"verification-state.txt").getAbsolutePath())).append(' ').append(RootBridge.q(new File(run,"initial-verification.txt").getAbsolutePath())).append("\nwarning_count=$(grep -c '^WARNING:' \"$warnings\" 2>/dev/null || true)\nprintf 'WARNINGS=%s\\n' \"$warning_count\" >> ").append(RootBridge.q(new File(run,"initial-verification.txt").getAbsolutePath())).append("\necho 'All files imported and verified; confirm runtime permissions and application folders' > ").append(RootBridge.q(new File(run,"root-filecheck.txt").getAbsolutePath())).append("\n");
  // 成功核验后隐藏原始 ZIP，防止天马G首次启动再次识别并重复解压。
  if(includeGba){String source=RootBridge.q(gba.getAbsolutePath()),backup=RootBridge.q(gba.getAbsolutePath()+".bak");s.append("if [ -f ").append(source).append(" ]; then gba_backup=").append(backup).append("; [ ! -e \"$gba_backup\" ] || gba_backup=\"$gba_backup.$(date +%Y%m%d%H%M%S)\"; mv ").append(source).append(" \"$gba_backup\"; echo \"GBA package renamed: $gba_backup\"; fi\n");}
  s.append("echo DONE > ").append(status).append("\n");
  s.append("rm -rf -- ").append(RootBridge.q(stage.getAbsolutePath())).append(' ').append(RootBridge.q(new File(run,"ordered").getAbsolutePath())).append("\nrm -f -- ").append(RootBridge.q(new File(run,"apply.sh").getAbsolutePath())).append(' ').append(RootBridge.q(new File(run,"check-entry.sh").getAbsolutePath())).append(' ').append(RootBridge.q(new File(run,"plan.json").getAbsolutePath())).append(' ').append(RootBridge.q(new File(run,"prepared-source.md5").getAbsolutePath())).append(' ').append(RootBridge.q(new File(run,"apply-source.md5").getAbsolutePath())).append(' ').append(RootBridge.q(new File(run,"apply-target.md5").getAbsolutePath())).append(' ').append(RootBridge.q(new File(run,"apply-target.sizes").getAbsolutePath())).append(' ').append(RootBridge.q(new File(run,"final-files-template.md5").getAbsolutePath())).append(' ').append(RootBridge.q(new File(run,"final-files.sizes").getAbsolutePath())).append(' ').append(RootBridge.q(new File(run,"verification-state.txt").getAbsolutePath())).append("\nrm -f -- ").append(RootBridge.q(finalScript.getAbsolutePath())).append("\n");
  exportedScript=finalScript;
  String commands=s.toString();int firstLine=commands.indexOf('\n');if(firstLine>=0)commands=commands.substring(firstLine+1);
  String single="#!/system/bin/sh\necho 'KPA initialization started.'\n(\n"+commands+") > "+RootBridge.q(new File(run,"root.log").getAbsolutePath())+" 2>&1\ncode=$?\nif [ $code -eq 0 ] && [ \"$(cat "+status+" 2>/dev/null)\" = DONE ]; then\n  echo 'SUCCESS: KPA initialization completed. Return to KPA Assistant.'\nelse\n  echo 'ERROR: KPA initialization failed. Return to KPA Assistant for details.'\n  [ $code -ne 0 ] || code=1\n  exit $code\nfi\n";
  write(exportedScript,single);write(new File(run,"script-path.txt"),exportedScript.getAbsolutePath());
 }
 AppSpec findApp(String pkg){for(AppSpec a:apps)if(a.pkg.equals(pkg))return a;return null;}
 void appendInstall(StringBuilder s,String status,AppSpec a,boolean critical){if(a==null||!a.install)return;String temp="/data/local/tmp/pegasus-setup-"+a.pkg+".apk";s.append("echo INSTALL > ").append(status).append("\necho ").append(RootBridge.q("Installing: "+a.pkg)).append("\ncp ").append(RootBridge.q(a.apk.getAbsolutePath())).append(' ').append(RootBridge.q(temp)).append("\nchmod 644 ").append(RootBridge.q(temp)).append("\ninstall_code=1\nn=0\nwhile [ $n -lt 10 ]; do\n  install_code=0\n  install_out=$(pm install -r ").append(RootBridge.q(temp)).append(" 2>&1) || install_code=$?\n  [ $install_code -eq 0 ] && break\n  am force-stop com.google.android.packageinstaller >/dev/null 2>&1 || true\n  am force-stop com.android.vending >/dev/null 2>&1 || true\n  n=$((n+1))\n  sleep 2\ndone\necho \"$install_out\"\n");if(critical)s.append("[ $install_code -eq 0 ] || exit $install_code\n");else s.append("[ $install_code -eq 0 ] || warn ").append(RootBridge.q(a.label+" 安装失败，已继续执行")).append("\n");s.append("rm -f ").append(RootBridge.q(temp)).append("\n");}
 void appendLayer(StringBuilder s,String status,String state,String message,String layer,boolean critical){s.append("echo ").append(state).append(" > ").append(status).append("\necho ").append(RootBridge.q(message)).append("\n");String command="sh "+RootBridge.q(new File(run,"ordered/"+layer+"/apply.sh").getAbsolutePath());if(critical)s.append(command).append("\n");else s.append("if ! ").append(command).append("; then warn ").append(RootBridge.q(message+"存在文件错误，已继续执行")).append("; fi\n");}

 public File getSetupScript(){return exportedScript!=null?exportedScript:new File(HOME,"KPA_Setup_"+run.getName().replace('-','_')+".sh");}
 static String[] resetPaths(String pkg){
  ArrayList<String> paths=new ArrayList<>();paths.add(HOME+"/Android/data/"+pkg);String extra=pkg.equals("org.pegasus_frontend.android")?"pegasus-frontend":pkg.equals("com.retroarch.aarch64")?"RetroArch":pkg.equals("com.dsemu.drastic")?"DraStic":pkg.equals("org.ppsspp.ppsspp")?"PSP":pkg.equals("xyz.aethersx2.android")?"aethersx2DATA":pkg.equals("org.citra.emu")?"citra-emu":pkg.equals("org.mupen64plusae.v3.fzurita.pro")?"M64PlusFZ":pkg.equals("org.dolphinemu.mmjr")?"mmjr2-vbi":"";if(!extra.isEmpty())paths.add(HOME+"/"+extra);return paths.toArray(new String[0]);
 }
 void extractAndroid(File zip)throws Exception {
  log.line("准备安卓覆盖：只读取配置文件");int count=0;
  try(ZipFile z=openZip(zip)){Enumeration<? extends ZipEntry> es=z.entries();while(es.hasMoreElements()){
   ZipEntry e=es.nextElement();String rel=safe(e.getName());if(e.isDirectory()||rel.endsWith(".sh"))continue;
   if(rel.startsWith("PG_Android/"))rel=rel.substring(11);
   if(!rel.startsWith("Android/")||skip(rel))continue;String t=target(rel,false);if(t==null)continue;
   File f=new File(stage,"android/"+rel);unzip(z,e,f);sanitize(f,rel);put(f,t);count++;
  }}if(count==0)throw new IOException("所选 Android 覆盖包没有找到已勾选应用的 Android 配置，请选择 KPA 专用覆盖包");
 }
 static boolean configFile(String rel){String s=rel.toLowerCase(Locale.ROOT);if(s.startsWith("roms/"))return s.endsWith("/metadata.pegasus.txt");return s.endsWith(".cfg")||s.endsWith(".ini")||s.endsWith("settings.txt")||s.endsWith("game_dirs.txt");}
 void extract(File zip,boolean games) throws Exception {
  try(ZipFile z=openZip(zip)){Enumeration<? extends ZipEntry> es=z.entries();int count=0,visited=0;while(es.hasMoreElements()){
   ZipEntry e=es.nextElement();++visited;itemProgress(games?"GBA整包":"配置资料",visited,z.size(),visited==z.size());if(visited%1000==0)log.line("@PROGRESS:"+(progressStart+(progressEnd-progressStart)*visited/z.size()));String rel=safe(e.getName());if(e.isDirectory()||skip(rel))continue;
   if(rel.indexOf('\uFFFD')>=0){if(rel.startsWith("PSP/Cheats/")&&e.getSize()<=4){log.line("已忽略 PSP 空白占位文件；有效金手指文件保持不变");continue;}else throw new IOException("资料包文件名已损坏，请重新获取："+rel);}
   String target=target(rel,games);if(target==null)continue;
   File f=new File(stage,(games?"games/":"config/")+rel);unzip(z,e,f);sanitize(f,rel);put(f,target);
   ++count;
  }}
 }
 void overlay(File folder,String prefix) throws Exception {
  if(!folder.isDirectory())return;File[] fs=folder.listFiles();if(fs==null)throw new IOException("无法读取配置目录："+folder);
  for(File f:fs){if(!f.getCanonicalFile().equals(f.getAbsoluteFile()))throw new IOException("配置中不允许符号链接："+f);
   String rel=safe(prefix+"/"+f.getName());if(skip(rel))continue;
   if(f.isDirectory()){overlay(f,rel);continue;}
   String target=target(rel,false);if(target==null)continue;
   File out=new File(stage,"overlays/"+rel);mkdir(out.getParentFile());try(InputStream in=new FileInputStream(f);OutputStream os=new FileOutputStream(out)){copy(in,os);}sanitize(out,rel);put(out,target);
  }
 }
 String target(String rel,boolean games){if(!includeGba&&(rel.equals("Roms/GBA")||rel.startsWith("Roms/GBA/")))return null;return spaceTarget(rel,destination,apps);}
 static boolean configured(AppSpec[] apps,String pkg){for(AppSpec app:apps)if(app.pkg.equals(pkg))return app.configure;return false;}
 static String spaceTarget(String rel,File destination,AppSpec[] apps){
  String top=rel.split("/")[0];
  if(top.equals("Roms"))return destination.getAbsolutePath()+"/"+rel;
  if(top.equals("pegasus-frontend")&&configured(apps,"org.pegasus_frontend.android"))return HOME+"/"+rel;
  if(top.equals("RetroArch")&&configured(apps,"com.retroarch.aarch64"))return HOME+"/"+rel;
  if(top.equals("Android")){
   if(rel.startsWith("Android/data/com.retroarch.aarch64/files/")&&configured(apps,"com.retroarch.aarch64"))return HOME+"/"+rel;
   if(rel.startsWith("Android/data/org.pegasus_frontend.android/files/")&&configured(apps,"org.pegasus_frontend.android"))return HOME+"/"+rel;
   return null;
  }
  String pkg=null;
  if(top.equals("DraStic"))pkg="com.dsemu.drastic";if(top.equals("PSP"))pkg="org.ppsspp.ppsspp";
  if(top.equals("aethersx2DATA"))pkg="xyz.aethersx2.android";if(top.equals("citra-emu"))pkg="org.citra.emu";
  if(top.equals("M64PlusFZ"))pkg="org.mupen64plusae.v3.fzurita.pro";if(top.equals("mmjr2-vbi"))pkg="org.dolphinemu.mmjr";
  return pkg!=null&&configured(apps,pkg)?HOME+"/"+rel:null;
 }
 boolean hasCollection(String name){String prefix=destination.getAbsolutePath()+"/Roms/"+name+"/";for(String t:items.keySet())if(t.startsWith(prefix))return true;return false;}
 boolean enabled(String pkg){for(AppSpec a:apps)if(a.pkg.equals(pkg))return a.configure;return false;}

 void sanitize(File f,String rel) throws Exception {
  String lower=rel.toLowerCase(Locale.ROOT);if(!(lower.endsWith(".cfg")||lower.endsWith(".ini")||lower.endsWith("settings.txt")||lower.endsWith("game_dirs.txt")||lower.endsWith("metadata.pegasus.txt")))return;
  byte[] raw=java.nio.file.Files.readAllBytes(f.toPath());Charset encoding=StandardCharsets.UTF_8;String s=new String(raw,encoding);if(s.indexOf('\0')>=0)return;
  if(!Arrays.equals(raw,s.getBytes(encoding))){encoding=Charset.forName("GBK");s=new String(raw,encoding);if(!Arrays.equals(raw,s.getBytes(encoding))){if(!(lower.endsWith(".ini")||lower.endsWith(".cfg")))throw new IOException("配置文件编码无法安全识别："+rel);encoding=StandardCharsets.ISO_8859_1;s=new String(raw,encoding);}}ArrayList<String> out=new ArrayList<>();
  for(String line:s.split("\n",-1)){
   int eq=line.indexOf('='),colon=line.indexOf(':');int separator=eq<0?colon:colon<0?eq:Math.min(eq,colon);if(separator>=0){String key=line.substring(0,separator).trim().toLowerCase(Locale.ROOT);if(key.indexOf('#')<0&&(key.contains("password")||key.contains("passwd")||key.contains("token")||key.contains("username")||key.contains("credential")||key.contains("cheevos_user")||key.contains("cheevos_pass")))continue;}
   if(lower.endsWith("metadata.pegasus.txt")||lower.endsWith("game_dirs.txt")||lower.endsWith("settings.txt"))line=line.replace("/storage/emulated/0/Roms",destination.getAbsolutePath()+"/Roms");
   out.add(line);
  }byte[] cleaned=String.join("\n",out).getBytes(encoding);if(Arrays.equals(raw,cleaned))return;try(OutputStream os=new FileOutputStream(f)){os.write(cleaned);}
 }
 void put(File f,String t) throws Exception {if(t.indexOf('\n')>=0||t.indexOf('\r')>=0||t.indexOf('\t')>=0)throw new IOException("目标名称包含不支持的控制字符");Item item=new Item(f,t);items.put(t,item);if(activeLayer.equals("main"))mainItems.put(t,item);else if(activeLayer.equals("gba"))gbaItems.put(t,item);else if(activeLayer.equals("android"))androidItems.put(t,item);else if(activeLayer.equals("lists"))listItems.put(t,item);}
 static String safe(String rel) throws IOException {
  rel=rel.replace('\\','/');if(rel.startsWith("/")||rel.indexOf('\n')>=0||rel.indexOf('\r')>=0||rel.indexOf('\0')>=0)throw new IOException("压缩包路径无效");
  for(String p:rel.split("/"))if(p.equals("..")||p.equals(".")||p.indexOf(':')>=0)throw new IOException("压缩包包含不安全路径："+rel);return rel;
 }
 static boolean skip(String rel){String s=rel.toLowerCase(Locale.ROOT);return s.matches(".*(^|/)(saves?|savestates?|states|screenshots|logs?|cache|backup|input_record|users)(/|$).*")||s.contains("retroachievements")||s.contains("credentials")||s.contains("token")||s.endsWith("stats.db")||s.endsWith("favorites.txt")||s.endsWith("datacache.json");}
 static File findConfigZip(File config) throws IOException {return SourceDiscovery.unique(config,1);}

 static ZipFile openZip(File f) throws IOException{return new ZipFile(f,Charset.forName("GBK"));}
 static final class SpaceEstimate {long minimum,full,existingConfig,existingGba;boolean includes(long available)throws IOException{if(available<minimum)throw new IOException("配置文件空间不足，还缺 "+gb(minimum-available)+" GiB");return available>=full;}}
 static boolean spaceAllowsGba(File cfg,File gba,ImportSources sources,AppSpec[] apps,long available)throws Exception{return estimateSpace(cfg,gba,sources,apps,new File(HOME)).includes(available);}
 static boolean spaceAllowsGba(File cfg,File gba,ImportSources sources,AppSpec[] apps,File destination,long available)throws Exception{return estimateSpace(cfg,gba,sources,apps,destination).includes(available);}
 static SpaceEstimate estimateSpace(File cfg,File gba,ImportSources sources,AppSpec[] apps,File destination)throws Exception{
  long base=zipSize(cfg);
  for(File extra:new File[]{sources.androidZip})if(extra!=null)base=Math.addExact(base,zipSize(extra));
  SpaceEstimate quote=new SpaceEstimate();quote.minimum=base;quote.full=gba==null?base:Math.addExact(base,zipSize(gba));return quote;
 }
 static long spaceEntries(File zip,File destination,AppSpec[] apps,Map<String,Long> targets)throws Exception{long largest=0;try(ZipFile z=openZip(zip)){Enumeration<? extends ZipEntry> entries=z.entries();while(entries.hasMoreElements()){ZipEntry entry=entries.nextElement();if(entry.isDirectory())continue;String rel=safe(entry.getName());if(skip(rel))continue;long size=entry.getSize();if(size<0)throw new IOException("压缩包体积无效");largest=Math.max(largest,size);String target=spaceTarget(rel,destination,apps);if(target!=null&&(target.startsWith(HOME+"/")||target.equals(HOME)))targets.put(target,Math.max(size,targets.containsKey(target)?targets.get(target):0));}}return largest;}
 static long existingCredit(Map<String,Long> targets){long credit=0;for(Map.Entry<String,Long> target:targets.entrySet()){File file=new File(target.getKey());try{if(file.isFile()&&file.canRead()&&!java.nio.file.Files.isSymbolicLink(file.toPath()))credit=Math.addExact(credit,Math.min(file.length(),target.getValue()));}catch(Exception ignored){}}return credit;}
 static long maxZipEntry(File file)throws IOException{long largest=0;try(ZipFile z=openZip(file)){Enumeration<? extends ZipEntry> entries=z.entries();while(entries.hasMoreElements())largest=Math.max(largest,entries.nextElement().getSize());}return largest;}
 static final Map<String,Long> ZIP_SIZE_CACHE=Collections.synchronizedMap(new LinkedHashMap<String,Long>(){protected boolean removeEldestEntry(Map.Entry<String,Long> e){return size()>32;}});
 static long zipSize(File f) throws IOException {String key=f.getAbsolutePath()+"|"+f.length()+"|"+f.lastModified();Long cached=ZIP_SIZE_CACHE.get(key);if(cached!=null)return cached;long total=0;try(ZipFile z=openZip(f)){Enumeration<? extends ZipEntry> e=z.entries();while(e.hasMoreElements()){long n=e.nextElement().getSize();if(n<0||n>16L*1024*1024*1024)throw new IOException("压缩包条目体积无效");total=Math.addExact(total,n);}}ZIP_SIZE_CACHE.put(key,total);return total;}
 static void unzip(ZipFile z,ZipEntry e,File f) throws Exception {if(f.isFile()&&!java.nio.file.Files.isSymbolicLink(f.toPath())&&f.length()==e.getSize()){CRC32 cached=new CRC32();try(InputStream in=new FileInputStream(f)){byte[] b=new byte[262144];int n;while((n=in.read(b))!=-1)cached.update(b,0,n);}if(cached.getValue()==e.getCrc())return;}mkdir(f.getParentFile());CRC32 crc=new CRC32();long size=0;try(InputStream in=z.getInputStream(e);OutputStream out=new FileOutputStream(f)){byte[] b=new byte[262144];int n;while((n=in.read(b))!=-1){out.write(b,0,n);crc.update(b,0,n);size+=n;}}if(size!=e.getSize()||crc.getValue()!=e.getCrc())throw new IOException("压缩包校验失败："+e.getName());}
 static void mkdir(File f) throws IOException {if(!f.isDirectory()&&!f.mkdirs())throw new IOException("无法创建目录："+f);}
 static void copy(InputStream in,OutputStream out) throws IOException {byte[] b=new byte[262144];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}
 static String md5(File f) throws Exception {MessageDigest md=MessageDigest.getInstance("MD5");try(InputStream in=new FileInputStream(f)){byte[] b=new byte[262144];int n;while((n=in.read(b))!=-1)md.update(b,0,n);}StringBuilder s=new StringBuilder();for(byte b:md.digest())s.append(String.format(Locale.US,"%02x",b&255));return s.toString();}
 static String read(File f) throws IOException {try(InputStream in=new FileInputStream(f);ByteArrayOutputStream out=new ByteArrayOutputStream()){copy(in,out);return out.toString("UTF-8");}}
 static void write(File f,String s) throws IOException {mkdir(f.getParentFile());try(OutputStream out=new FileOutputStream(f)){out.write(s.getBytes(StandardCharsets.UTF_8));}}
 static String set(String cfg,String key,String value){String line=key+" = \""+value.replace("\\","\\\\").replace("\"","\\\"")+"\"";String pattern="(?m)^"+java.util.regex.Pattern.quote(key)+"\\s*=.*$";return java.util.regex.Pattern.compile(pattern).matcher(cfg).find()?cfg.replaceAll(pattern,java.util.regex.Matcher.quoteReplacement(line)):cfg+"\n"+line+"\n";}
 static String gb(long n){return String.format(Locale.US,"%.2f",n/1073741824.0);}
}
