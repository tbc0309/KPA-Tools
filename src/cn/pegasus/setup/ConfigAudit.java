package cn.pegasus.setup;
import android.content.Context;
import java.io.*;
import java.util.*;
public final class ConfigAudit {
 static String relative(String pkg){
  if(pkg.equals("org.pegasus_frontend.android"))return "Android/data/"+pkg+"/files/pegasus-frontend/settings.txt";
  if(pkg.equals("com.retroarch.aarch64"))return "Android/data/"+pkg+"/files/retroarch.cfg";
  if(pkg.equals("org.ppsspp.ppsspp"))return "PSP/SYSTEM/ppsspp.ini";
  if(pkg.equals("org.citra.emu"))return "citra-emu/config/config.ini";
  if(pkg.equals("org.dolphinemu.mmjr"))return "mmjr2-vbi/Config/Dolphin.ini";
  return null;
 }
 public static Map<String,String> inspect(Context c,AppSpec[] apps)throws Exception {
  LinkedHashMap<String,String> result=new LinkedHashMap<>();
  for(AppSpec app:apps){
   if(!app.supportsConfig)continue;try{c.getPackageManager().getPackageInfo(app.pkg,0);}catch(Exception e){result.put(app.pkg,"现有配置：应用未安装");continue;}
   String rel=relative(app.pkg);
   if(rel==null){result.put(app.pkg,"现有配置：暂无检测路径，尚未核对");continue;}
   File f=new File(SetupEngine.HOME,rel);
   if(!f.isFile()||!f.canRead()){result.put(app.pkg,"现有配置：无法直接读取，正式执行时处理");continue;}
   result.put(app.pkg,"现有配置：已有文件，执行时按所选资料覆盖");
  }return result;
 }
}
