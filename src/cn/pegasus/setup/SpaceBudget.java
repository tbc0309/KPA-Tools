package cn.pegasus.setup;
import java.io.IOException;
final class SpaceBudget {
 static final long RESERVE=256L*1024*1024;
 static long required(long config,long apks){return Math.addExact(Math.addExact(Math.multiplyExact(config,2),Math.multiplyExact(apks,3)),RESERVE);}
 static boolean includeGba(long config,long gba,long apks,long available)throws IOException{long minimum=required(config,apks);if(available<minimum)throw new IOException("APP 与配置空间不足，还缺 "+SetupEngine.gb(minimum-available)+" GB");return available>=Math.addExact(minimum,Math.multiplyExact(gba,2));}
}
