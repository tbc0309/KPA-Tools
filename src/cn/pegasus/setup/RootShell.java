package cn.pegasus.setup;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/** Executes a bounded command through Magisk su. */
final class RootShell {
    String run(String command) throws Exception {
        long timeout = command.contains("kpa_root_helper/action.sh") || command.contains("--prepare-ota") ? 600000L : 30000L;
        Process process = new ProcessBuilder("su", "-c", command).redirectErrorStream(true).start();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        InputStream input = process.getInputStream();
        byte[] buffer = new byte[2048];
        long deadline = System.currentTimeMillis() + timeout;
        try {
        while (System.currentTimeMillis() < deadline) {
            while (input.available() > 0) {
                int count = input.read(buffer);
                if (count > 0) output.write(buffer, 0, count);
            }
            try {
                int exitCode = process.exitValue();
                int count;
                while ((count=input.read(buffer))!=-1) output.write(buffer,0,count);
                String value = output.toString("UTF-8").trim();
                if (exitCode != 0) throw new IOException(value.isEmpty() ? "Root 请求被拒绝" : value);
                return value;
            } catch (IllegalThreadStateException running) {
                Thread.sleep(100);
            }
        }
        process.destroy();
        throw new IOException("Root 操作超时");
        } finally { input.close(); process.destroy(); }
    }

    String prop(String key) {
        try {
            Process process=new ProcessBuilder("getprop",key).start();
            try(java.io.BufferedReader reader=new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream(),"UTF-8"))){
                String value=reader.readLine();return value==null?"":value.trim();
            } finally {process.destroy();}
        }
        catch (Exception ignored) { return ""; }
    }

    boolean isGranted() {
        try { return run("id").contains("uid=0"); }
        catch (Exception ignored) { return false; }
    }
}
