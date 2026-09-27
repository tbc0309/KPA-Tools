package cn.pegasus.setup;

import android.os.Build;
import java.io.BufferedReader;
import java.io.InputStreamReader;

/** Reads hardware identity from boot-image properties unaffected by app-facing model spoofing. */
final class KpaDevice {
    final String model, device, board;

    private KpaDevice(String model, String device, String board) {
        this.model=model; this.device=device; this.board=board;
    }

    static KpaDevice read() {
        String board=prop("ro.product.board");
        if("unknown".equals(board)&&Build.BOARD!=null&&!Build.BOARD.trim().isEmpty())board=Build.BOARD.trim();
        return new KpaDevice(prop("ro.product.bootimage.model"),
                prop("ro.product.bootimage.device"), board);
    }

    boolean supported() {
        boolean exact="GT78-VN".equalsIgnoreCase(model)
                && "GT78-VN".equalsIgnoreCase(device)
                && "k85v1_64".equalsIgnoreCase(board);
        boolean platform="k85v1_64".equalsIgnoreCase(board)
                && Build.DISPLAY!=null&&Build.DISPLAY.startsWith("BW03_");
        return exact||platform;
    }

    String displayName() {
        return supported()?"KONKR Pocket Advance":Build.MANUFACTURER+" "+Build.MODEL;
    }

    String identity() {
        return model+" · "+device+" · "+board;
    }

    private static String prop(String key) {
        Process process=null;
        try {
            process=new ProcessBuilder("/system/bin/getprop",key).redirectErrorStream(true).start();
            try(BufferedReader reader=new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String value=reader.readLine();
                if(process.waitFor()==0&&value!=null&&!value.trim().isEmpty())return value.trim();
            }
        } catch(Exception ignored) {
        } finally {
            if(process!=null)process.destroy();
        }
        return "unknown";
    }
}
