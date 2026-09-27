package cn.pegasus.setup;

import android.content.Context;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/** Installs the helper and invokes its explicit storage synchronization action. */
final class RootHelperManager {
    void prepareOta(RootShell shell)throws Exception{
        shell.run("sh /data/adb/modules/kpa_root_helper/service.sh --prepare-ota");
        if(!"OTA_PREPARED".equals(shell.prop("kpa.root_helper.state")))
            throw new java.io.IOException("OTA 准备未完成，请查看模块状态");
    }
    boolean removeWithApp(RootShell shell)throws Exception{
        return !"keep".equals(shell.run("if [ -f /data/adb/kpa_root_helper/keep_on_uninstall ]; then echo keep; else echo remove; fi"));
    }

    void setRemoveWithApp(RootShell shell,boolean enabled)throws Exception{
        shell.run("mkdir -p /data/adb/kpa_root_helper && "+(enabled?
                "rm -f /data/adb/kpa_root_helper/keep_on_uninstall":
                "touch /data/adb/kpa_root_helper/keep_on_uninstall"));
    }
    static final class SyncResult {
        final String state,path,time,reason;
        SyncResult(String state,String path,String time,String reason){this.state=state;this.path=path;this.time=time;this.reason=reason;}
    }

    void install(Context context,RootShell shell)throws Exception{
        File archive=new File(context.getExternalFilesDir(null),"KPA_Root_Helper.zip");
        try(InputStream input=context.getAssets().open("KPA_Root_Helper.zip");
            OutputStream output=new FileOutputStream(archive)){
            byte[] buffer=new byte[8192];
            for(int count;(count=input.read(buffer))>0;)output.write(buffer,0,count);
        }
        shell.run("magisk --install-module "+quote(archive.getAbsolutePath()));
    }

    SyncResult synchronize(RootShell shell)throws Exception{
        shell.run("/data/adb/modules/kpa_root_helper/action.sh");
        return new SyncResult(shell.prop("kpa.root_helper.tf"),shell.prop("kpa.root_helper.tf_path"),shell.prop("kpa.root_helper.tf_time"),shell.prop("kpa.root_helper.tf_error"));
    }

    private static String quote(String value){return "'"+value.replace("'","'\\''")+"'";}
}
