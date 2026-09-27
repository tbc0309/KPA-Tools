package cn.pegasus.setup;

import android.os.Build;

/** Immutable snapshot of device, Magisk, helper and storage state. */
final class RootHelperStatus {
    static final String REQUIRED_VERSION = "1.0.0";
    final boolean rootGranted, helperReady;
    final String rootState, magisk, version, state, active, inactive;
    final String activeSha, inactiveSha, base, storage, tf, tfPath, tfTime;

    private RootHelperStatus(boolean rootGranted, boolean helperReady, String rootState,
            String magisk, String version, String state, String active, String inactive,
            String activeSha, String inactiveSha, String base, String storage,
            String tf, String tfPath, String tfTime) {
        this.rootGranted=rootGranted; this.helperReady=helperReady; this.rootState=rootState;
        this.magisk=magisk; this.version=version; this.state=state; this.active=active;
        this.inactive=inactive; this.activeSha=activeSha; this.inactiveSha=inactiveSha;
        this.base=base; this.storage=storage; this.tf=tf; this.tfPath=tfPath; this.tfTime=tfTime;
    }

    static RootHelperStatus capture(RootShell shell) {
        boolean granted=shell.isGranted();
        String rootState=granted?"已授权":"未授权", magisk="未检测到";
        if(granted) try { magisk=shell.run("magisk -v"); }
        catch(Exception error){rootState=value(error.getMessage(),"Root 请求失败");}
        String version=shell.prop("kpa.root_helper.version");
        boolean ready="V2".equals(shell.prop("kpa.root_helper.ready"))&&REQUIRED_VERSION.equals(version)&&"2".equals(shell.prop("kpa.root_helper.stock_builder"))&&"1".equals(shell.prop("kpa.root_helper.lifecycle"));
        String state=value(shell.prop("kpa.root_helper.state"),"未就绪");
        if(granted)try{
            String health=shell.run("m=/data/adb/modules/kpa_root_helper; if [ ! -f \"$m/module.prop\" ]; then echo MISSING; elif [ -f \"$m/disable\" ] || [ -f \"$m/remove\" ]; then echo DISABLED; else p=$(cat /data/adb/kpa_root_helper/monitor.pid 2>/dev/null); if [ -n \"$p\" ] && grep -q 'kpa_root_helper/service.sh' \"/proc/$p/cmdline\" 2>/dev/null; then echo RUNNING; else echo STOPPED; fi; fi");
            if("MISSING".equals(health))ready=false;
            else if("DISABLED".equals(health)){ready=true;state="ERROR_MODULE_DISABLED";}
            else if(ready&&"STOPPED".equals(health)&&"READY".equals(state))state="ERROR_MONITOR_STOPPED";
        }catch(Exception error){state="ERROR_STATUS_READ";}
        return new RootHelperStatus(granted,ready,rootState,magisk,version,
                state,
                value(shell.prop("kpa.root_helper.active"),shell.prop("ro.boot.slot_suffix")),
                value(shell.prop("kpa.root_helper.inactive"),"未知"),
                value(shell.prop("kpa.root_helper.active_sha"),"未知"),
                value(shell.prop("kpa.root_helper.inactive_sha"),"未知"),
                value(shell.prop("kpa.root_helper.base"),"未校验"),
                value(shell.prop("kpa.root_helper.storage_path"),"未就绪"),
                value(shell.prop("kpa.root_helper.tf"),"未检测"),
                shell.prop("kpa.root_helper.tf_path"),shell.prop("kpa.root_helper.tf_time"));
    }

    String[] deviceRows(){return new String[]{KpaDevice.read().displayName(),Build.DISPLAY,
            "Android "+Build.VERSION.RELEASE,active,rootState,magisk};}
    String[] helperRows(){return new String[]{helperReady?REQUIRED_VERSION:"未安装或待更新",state,inactive,
            activeSha+" / "+inactiveSha,base,storage,"SYNCED".equals(tf)?tfPath:tf};}
    String summary(){if("READY_TO_REBOOT".equals(state))return "新槽已修补并校验，可以重启";
        if("OTA_PREPARED".equals(state))return "请直接进行系统 OTA，勿提前重启；重启会失去 Root";
        if("PREPARING_OTA".equals(state))return "正在备份并恢复原版 boot，请勿重启";
        if("OTA_RUNNING".equals(state))return "系统 OTA 正在安装，请勿重启";
        if("BUILDING_STOCK".equals(state))return "正在从 OTA 合成原版 boot";
        if("PATCHING".equals(state))return "正在修补 OTA 新槽，请勿重启";
        if(state.startsWith("ERROR"))return "KPA Root Helper 发生错误，请查看日志";
        if(!rootGranted)return "获得完整 Root 授权后会自动安装 KPA Root Helper";
        if(!helperReady)return "模块未就绪";
        return "KPA Root Helper 正在监控 A/B OTA";}
    private static String shortHash(String hash){return hash.length()>16?hash.substring(0,8)+"…"+hash.substring(hash.length()-8):hash;}
    private static String value(String text,String fallback){return text==null||text.trim().isEmpty()?fallback:text.trim();}
}
