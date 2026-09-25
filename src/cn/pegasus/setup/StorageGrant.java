package cn.pegasus.setup;
import android.content.Context;
import android.content.pm.*;
import java.io.*;
import java.util.*;
public final class StorageGrant {
 public static String commands(String pkg){
  String q=RootBridge.q(pkg);
  return "echo "+RootBridge.q("Storage permissions: "+pkg)+"\n"+
   "for perm in android.permission.READ_EXTERNAL_STORAGE android.permission.WRITE_EXTERNAL_STORAGE; do\n"+
   " if dumpsys package "+q+" | grep -F \"$perm\" >/dev/null; then\n"+
   "  granted=0; n=0; while [ $n -lt 8 ]; do pm grant "+q+" \"$perm\" >/dev/null 2>&1 || true; dumpsys package "+q+" | grep -F \"$perm: granted=true\" >/dev/null && { granted=1; break; }; n=$((n+1)); sleep 1; done\n"+
   "  [ $granted -eq 1 ] && echo \"Granted $perm\" || echo \"Manual permission required: $perm\"\n"+
   " fi\ndone\n"+
   "if dumpsys package "+q+" | grep -F android.permission.MANAGE_EXTERNAL_STORAGE >/dev/null; then\n"+
   " n=0; allowed=0; while [ $n -lt 8 ]; do cmd appops set --uid "+q+" MANAGE_EXTERNAL_STORAGE allow >/dev/null 2>&1 || true; cmd appops get "+q+" MANAGE_EXTERNAL_STORAGE 2>/dev/null | grep -F allow >/dev/null && { allowed=1; break; }; n=$((n+1)); sleep 1; done\n"+
   " [ $allowed -eq 1 ] && echo 'All-files permission granted' || echo 'All-files permission requires manual confirmation'\nfi\n";
 }
}
