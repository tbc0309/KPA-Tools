package cn.pegasus.setup;
import java.io.*;import java.util.*;import org.json.*;
public final class IntegrityVerifier {
 public static void prepare(File run,Collection<SetupEngine.Item> files,AppSpec[] apps)throws Exception {
  StringBuilder exists=new StringBuilder();boolean ra=false;Map<String,String> samples=new LinkedHashMap<>();
  for(SetupEngine.Item f:files){String t=f.target;if(t.startsWith(SetupEngine.HOME+"/Android/data/com.retroarch.aarch64/"))ra=true;exists.append(t).append('\n');
   if(t.startsWith("/data/user/0/")){String[] p=t.split("/");String dir="/data/user/0/"+p[4]+"/"+p[5];if(!samples.containsKey(dir))samples.put(dir,t);}}
  File existenceList=new File(run,"文件存在清单.txt"),details=new File(run,SetupEngine.DETAILS),state=new File(run,SetupEngine.VERIFY_STATE);SetupEngine.write(existenceList,exists.toString());
  StringBuilder s=new StringBuilder("#!/system/bin/sh\nset -e\n[ \"$(id -u)\" = 0 ] || exit 1\n");
  s.append("echo 'STATUS=CHECKING' > ").append(RootBridge.q(state.getAbsolutePath())).append("\ntrap 'printf \"STATUS=PARTIAL\\nFILES=").append(files.size()).append("\\nRA=").append(ra?"OK":"NOT_SELECTED").append("\\n\" > ").append(RootBridge.q(state.getAbsolutePath())).append("' EXIT\n: > ").append(RootBridge.q(details.getAbsolutePath())).append("\n");
  s.append("missing=0\nwhile IFS= read -r path; do [ -f \"$path\" ] || { echo \"文件缺失：$path\" >> ").append(RootBridge.q(details.getAbsolutePath())).append("; missing=1; }; done < ").append(RootBridge.q(existenceList.getAbsolutePath())).append("\n[ $missing -eq 0 ]\n");
  for(Map.Entry<String,String> e:samples.entrySet()){String base=e.getKey().substring(0,e.getKey().lastIndexOf('/'));s.append("[ \"$(stat -c %u ").append(RootBridge.q(base)).append(")\" = \"$(stat -c %u ").append(RootBridge.q(e.getValue())).append(")\" ]\n[ \"$(ls -Zd ").append(RootBridge.q(base)).append(" | awk '{print $1}')\" = \"$(ls -Z ").append(RootBridge.q(e.getValue())).append(" | awk '{print $1}')\" ]\n");}
  s.append("printf 'STATUS=OK\\nFILES=").append(files.size()).append("\\nRA=").append(ra?"OK":"NOT_SELECTED").append("\\n' > ").append(RootBridge.q(state.getAbsolutePath())).append("\ntrap - EXIT\necho FILES_OK\n");SetupEngine.write(new File(run,SetupEngine.VERIFY_SCRIPT),s.toString());
  StringBuilder repair=new StringBuilder("#!/system/bin/sh\nrepaired=0\n");
  for(SetupEngine.Item f:files){repair.append("if [ ! -f ").append(RootBridge.q(f.target)).append(" ]; then mkdir -p ").append(RootBridge.q(new File(f.target).getParent())).append("; if cp -f ").append(RootBridge.q(f.source.getAbsolutePath())).append(' ').append(RootBridge.q(f.target)).append("; then ");if(f.target.startsWith("/data/user/0/")){String[] p=f.target.split("/");String base="/data/user/0/"+p[4];repair.append("owner=$(stat -c '%u:%g' ").append(RootBridge.q(base)).append("); chown \"$owner\" ").append(RootBridge.q(f.target)).append("; chmod 600 ").append(RootBridge.q(f.target)).append("; restorecon -F ").append(RootBridge.q(f.target)).append(" >/dev/null 2>&1 || true; ");}repair.append("echo ").append(RootBridge.q("已重试覆盖："+f.target)).append("; repaired=$((repaired+1)); else echo ").append(RootBridge.q("重试失败："+f.target)).append("; fi; fi\n");}
  repair.append("echo \"二次覆盖文件：$repaired 个\"\n");SetupEngine.write(new File(run,"repair.sh"),repair.toString());
 }
 public static boolean passed(File run){try{return SetupEngine.read(new File(run,SetupEngine.VERIFY_STATE)).contains("STATUS=OK");}catch(Exception e){return false;}}
 public static int count(File run){try{String r=SetupEngine.read(new File(run,SetupEngine.VERIFY_STATE));for(String l:r.split("\n"))if(l.startsWith("FILES="))return Integer.parseInt(l.substring(6));}catch(Exception ignored){}return 0;}
 public static File exportEntry(File run)throws Exception {File out=new File(SetupEngine.HOME,"KPA_Check_"+run.getName().replace('-','_')+".sh");SetupEngine.write(out,"#!/system/bin/sh\nsh "+RootBridge.q(new File(run,SetupEngine.VERIFY_SCRIPT).getAbsolutePath())+"\n");return out;}
}
