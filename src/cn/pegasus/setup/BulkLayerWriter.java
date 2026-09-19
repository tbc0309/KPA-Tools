package cn.pegasus.setup;
import java.io.*;import java.util.*;
final class BulkLayerWriter {
 static String parent(String p){return p.substring(0,p.lastIndexOf('/'));}
 static String name(String p){return p.substring(p.lastIndexOf('/')+1);}
 static void args(StringBuilder s,String command,Collection<String> paths){ArrayList<String> a=new ArrayList<>(paths);for(int n=0;n<a.size();n+=48){s.append(command);for(int j=n;j<Math.min(n+48,a.size());j++)s.append(' ').append(RootBridge.q(a.get(j)));s.append('\n');}}
 static void write(File run,Map<String,SetupEngine.Item> items,Map<String,Long> versions,int offset,int total)throws Exception {
  File batchRoot=run.getParentFile().getParentFile(),overallProgress=new File(batchRoot,"item-progress.txt");
  StringBuilder sources=new StringBuilder(),targets=new StringBuilder(),s=new StringBuilder("#!/system/bin/sh\nset -e\n[ \"$(id -u)\" = 0 ] || exit 1\nassert_safe() { p=\"$1\"; while [ \"$p\" != / ] && [ \"$p\" != /data/user/0 ] && [ -n \"$p\" ]; do [ ! -L \"$p\" ] || { echo 'Unsafe symlink'; exit 1; }; p=${p%/*}; done; }\n");
  s.append("warnings=").append(RootBridge.q(new File(batchRoot,SetupEngine.WARNINGS).getAbsolutePath())).append("\ncopy_batch() { dest=\"$1\"; shift; cp -f \"$@\" \"$dest\" 2>/dev/null && return 0; for src in \"$@\"; do cp -f \"$src\" \"$dest\" 2>/dev/null || echo \"WARNING: 文件复制失败：$src\" | tee -a \"$warnings\"; done; return 0; }\n");
  for(Map.Entry<String,Long> v:versions.entrySet())s.append("ver=$(dumpsys package ").append(RootBridge.q(v.getKey())).append(" | sed -n 's/.*versionCode=\\([0-9]*\\).*/\\1/p' | head -n 1)\n[ \"$ver\" = ").append(RootBridge.q(v.getValue().toString())).append(" ] || { echo 'Clean profile version mismatch'; exit 1; }\n");
  Map<String,List<SetupEngine.Item>> groups=new LinkedHashMap<>();Map<String,Set<String>> privateFiles=new LinkedHashMap<>(),privateDirs=new LinkedHashMap<>();Set<String> parents=new LinkedHashSet<>(),labels=new LinkedHashSet<>();boolean raResources=false;int cores=0;
  for(SetupEngine.Item i:items.values()){
   sources.append(i.source.getAbsolutePath()).append('\n');targets.append(i.target).append('\n');String p=parent(i.target);parents.add(p);groups.computeIfAbsent(p,k->new ArrayList<>()).add(i);
   if(i.target.startsWith("/data/user/0/")){String[] parts=i.target.split("/");String base="/data/user/0/"+parts[4];labels.add(base+"/"+parts[5]);privateFiles.computeIfAbsent(base,k->new LinkedHashSet<>()).add(i.target);Set<String> ds=privateDirs.computeIfAbsent(base,k->new LinkedHashSet<>());while(!p.equals(base)){ds.add(p);p=parent(p);}}
   if(i.target.startsWith("/data/user/0/com.retroarch.aarch64/")&&i.source.getAbsolutePath().contains("/stage/ra/")){raResources=true;if(i.target.contains("/cores/")&&i.target.endsWith(".so"))cores++;}
  }
  File sourceList=new File(run,"apply-source.exists"),targetList=new File(run,"apply-target.exists");SetupEngine.write(sourceList,sources.toString());SetupEngine.write(targetList,targets.toString());
  s.append("missing=0\nwhile IFS= read -r path; do [ -f \"$path\" ] || missing=1; done < ").append(RootBridge.q(sourceList.getAbsolutePath())).append("\n[ $missing -eq 0 ]\n");for(String p:parents)s.append("assert_safe ").append(RootBridge.q(p)).append('\n');args(s,"mkdir -p",parents);
  int n=0;for(String base:privateFiles.keySet()){String owner="owner_"+(n++);s.append(owner).append("=$(stat -c '%u:%g' ").append(RootBridge.q(base)).append(")\n[ \"${").append(owner).append("%%:*}\" != 0 ]\n");args(s,"chown \"$"+owner+"\"",privateDirs.get(base));args(s,"chmod 700",privateDirs.get(base));}
  int copied=0;for(Map.Entry<String,List<SetupEngine.Item>> g:groups.entrySet()){
   List<SetupEngine.Item> batch=new ArrayList<>();for(SetupEngine.Item i:g.getValue()){if(name(i.target).equals(i.source.getName()))batch.add(i);else{s.append("cp -f ").append(RootBridge.q(i.source.getAbsolutePath())).append(' ').append(RootBridge.q(i.target)).append(" 2>/dev/null || echo ").append(RootBridge.q("WARNING: 文件复制失败："+i.target)).append(" | tee -a \"$warnings\"\n");copied++;}}
   for(int start=0;start<batch.size();start+=48){int end=Math.min(start+48,batch.size());s.append("copy_batch ").append(RootBridge.q(g.getKey()+"/"));for(int j=start;j<end;j++)s.append(' ').append(RootBridge.q(batch.get(j).source.getAbsolutePath()));s.append('\n');copied+=end-start;s.append("printf '处理文件\\t").append(offset+copied).append("\\t").append(total).append("\\n' > ").append(RootBridge.q(overallProgress.getAbsolutePath())).append("\n");}
   if(batch.isEmpty())s.append("printf '处理文件\\t").append(offset+copied).append("\\t").append(total).append("\\n' > ").append(RootBridge.q(overallProgress.getAbsolutePath())).append("\n");
  }
  n=0;for(String base:privateFiles.keySet()){String owner="owner_"+(n++);args(s,"chown \"$"+owner+"\"",privateFiles.get(base));args(s,"chmod 600",privateFiles.get(base));}
  for(String d:labels)s.append(SetupEngine.restoreLabel(d));s.append("missing=0\nwhile IFS= read -r path; do [ -f \"$path\" ] || missing=1; done < ").append(RootBridge.q(targetList.getAbsolutePath())).append("\n[ $missing -eq 0 ]\n");
  if(raResources){if(cores==0)throw new IOException("RetroArch resource layer has no cores");s.append("echo RA_RESOURCES_OK > ").append(RootBridge.q(new File(run,"ra-resources-ready.txt").getAbsolutePath())).append('\n');}
  s.append("echo APPLY_FILES_OK\n");SetupEngine.write(new File(run,"apply.sh"),s.toString());
 }
}
