package cn.pegasus.setup;
import java.io.File;
public final class AppSpec {
 public final String pkg,label;
 public File apk;
 public boolean installed,install,configure;
 public boolean supportsConfig=true;
 public AppSpec(String p,String l){pkg=p;label=l;}
 public static AppSpec[] list(){AppSpec[] specs=new AppSpec[]{
  new AppSpec("org.pegasus_frontend.android","天马G 前端"),new AppSpec("com.retroarch.aarch64","RetroArch G（GBA 必需）"),
  new AppSpec("com.dsemu.drastic","DraStic / NDS"),new AppSpec("org.ppsspp.ppsspp","PPSSPP / PSP"),
  new AppSpec("xyz.aethersx2.android","AetherSX2 / PS2"),new AppSpec("org.citra.emu","Citra / 3DS"),
  new AppSpec("org.mupen64plusae.v3.fzurita.pro","M64PlusFZ / N64"),new AppSpec("org.dolphinemu.mmjr","Dolphin / NGC · Wii"),
  new AppSpec("bin.mt.plus","MT 文件管理器（可选）")};specs[specs.length-1].supportsConfig=false;return specs;}
}
