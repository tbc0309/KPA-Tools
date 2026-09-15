package cn.pegasus.setup;

import android.content.Context;
import java.lang.reflect.*;

/** 调用掌机系统提供的存储 Root 接口。 */
public final class RootBridge {
 private final Object manager;
 private final Method runner;
 public RootBridge(Context context) throws Exception {
  manager=context.getSystemService("custom_function");
  if(manager==null) throw new Exception("当前系统没有可直接调用的存储 Root 接口，请使用掌机设置的 Root 脚本入口");
  runner=manager.getClass().getMethod("runShellScriptWithRootPermissionForResult",String.class);
 }
 public String call(String command) throws Exception {
  try {Object value=runner.invoke(manager,command); return value==null?"":value.toString();}
  catch(InvocationTargetException e){throw new Exception("存储 Root 接口拒绝调用："+e.getCause(),e.getCause());}
 }
 public void check() throws Exception {
  String r=call("id -u").trim();
  if(!r.equals("0")) throw new Exception("系统接口没有返回 Root 身份（"+r+"）。请使用掌机设置中的 Root 脚本入口；不会跳过系统权限检查。");
 }
 public void checkSharedAndroidStorage(Context context)throws Exception {
  check();java.io.File probe=new java.io.File(SetupEngine.HOME+"/Android/data/"+context.getPackageName()+"/files/kpa-root-probe-"+java.util.UUID.randomUUID());probe.getParentFile().mkdirs();String path=q(probe.getAbsolutePath());String result=call("if (printf KPA > "+path+") 2>/dev/null && [ \"$(cat "+path+" 2>/dev/null)\" = KPA ]; then rm -f "+path+"; echo SHARED_ANDROID_OK; else rm -f "+path+" 2>/dev/null; echo SHARED_ANDROID_DENIED; fi").trim();
  if(!result.equals("SHARED_ANDROID_OK"))throw new Exception("存储 Root 无法写入 Android/data，已停止执行；请重新运行授权脚本。");
 }
 public static String q(String value){return "'"+value.replace("'","'\\''")+"'";}
}
