package cn.pegasus.setup;

import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.regex.*;

/** Standalone app_process entry point: reconstructs stock images, never writes partitions. */
public final class StockBootBuilder {
    private static final String BASE="BW03_20260730_20260730-1638";
    private static final String API="https://fota5p.adups.com/otainter-5.0/fota5/detectSchedule.do";
    private static final Map<String,String> KNOWN=new HashMap<>();
    static {
        KNOWN.put("20260730","735e1d3855dc0165762006cdcb1136d3047b8e999a559fbd259b2adb58a32487");
        KNOWN.put("20260813","66919aa93f4d1ce9055f2f08b20034e031e63444c2b77e0d2fa3eb186817a71a");
        KNOWN.put("20260828","f25e0d5115e4e382983f9acb47b3a2b8aefb8c8c866d5a07bb888e48553d4039");
    }
    private final File root,base,dumper;
    private final String serial;
    private StockBootBuilder(String[] a){root=new File(a[0]);base=new File(a[1]);dumper=new File(a[2]);serial=a[4];}
    public static void main(String[] args){
        try {
            if(args.length!=5)throw new IOException("Expected repository, base, dumper, firmware, serial");
            StockBootBuilder builder=new StockBootBuilder(args);
            File result=builder.ensure(args[3]);
            System.out.println("STOCK_READY="+result.getAbsolutePath());
        } catch(Exception e){System.err.println("STOCK_ERROR="+e.getMessage());System.exit(1);}
    }
    private File ensure(String firmware)throws Exception{
        String target=date(firmware);
        File existing=verified(target);
        if(existing!=null)return existing;
        if(!serial.matches("BW03[A-Z0-9]+"))throw new IOException("Invalid device serial");
        requireImage(base,KNOWN.get("20260730"));
        String source=BASE;File sourceBoot=base;
        // Cached, verified intermediate images avoid repeating completed work.
        for(int step=0;step<12;step++){
            if(date(source).equals(target))return save(target,source,sourceBoot);
            JSONObject offer=offer(source,target);
            String next=offer.getString("versionName"),nextDate=date(next);
            if(nextDate.compareTo(date(source))<=0||nextDate.compareTo(target)>0)throw new IOException("OTA chain does not reach installed firmware: "+next);
            File built=verified(nextDate);
            if(built==null){
                File archive=archive(source,offer);
                File work=new File(root,"work/"+date(source)+"_"+nextDate);
                File old=new File(work,"source"),out=new File(work,"output");mkdir(old);mkdir(out);
                copy(sourceBoot,new File(old,"boot.img"));
                System.out.println("REBUILD="+source+" -> "+next);
                Process p=new ProcessBuilder(dumper.getAbsolutePath(),archive.getAbsolutePath(),"--source-dir",old.getAbsolutePath(),"--out",out.getAbsolutePath(),"--images","boot","--no-parallel").redirectErrorStream(true).start();
                try(BufferedReader reader=new BufferedReader(new InputStreamReader(p.getInputStream(),StandardCharsets.UTF_8))){String line;while((line=reader.readLine())!=null)System.out.println(line);}
                if(p.waitFor()!=0)throw new IOException("Payload reconstruction failed: "+next);
                File image=new File(out,"boot.img");
                requireImage(image,KNOWN.get(nextDate));
                // The payload tool verifies source/operation/output hashes, including unknown future builds.
                built=save(nextDate,next,image);
                Files.deleteIfExists(new File(old,"boot.img").toPath());Files.deleteIfExists(image.toPath());
                old.delete();out.delete();work.delete();
            }else System.out.println("REUSE="+next);
            source=next;sourceBoot=built;
        }
        throw new IOException("OTA chain limit reached");
    }
    private File verified(String version)throws Exception{
        File dir=new File(root,"boot/original/current/BW03_"+version),image=new File(dir,"boot_stock.img"),hash=new File(dir,"boot_stock.sha256");
        if(!image.isFile()||!hash.isFile())return null;
        String expected=read(hash).trim();
        if(!expected.matches("[a-fA-F0-9]{64}"))return null;
        if(KNOWN.containsKey(version)&&!KNOWN.get(version).equalsIgnoreCase(expected))return null;
        if(image.length()!=33554432L||!digest(image,"SHA-256").equalsIgnoreCase(expected))return null;
        return image;
    }
    private File save(String version,String server,File image)throws Exception{
        File dir=new File(root,"boot/original/current/BW03_"+version);mkdir(dir);
        File dest=new File(dir,"boot_stock.img");if(!image.equals(dest))copy(image,dest);
        write(new File(dir,"boot_stock.sha256"),digest(dest,"SHA-256")+"\n");
        write(new File(dir,"firmware.txt"),"BW03_"+version+"\n");write(new File(dir,"server-version.txt"),server+"\n");
        return dest;
    }
    private JSONObject offer(String source,String target)throws Exception{
        File cache=new File(root,"ota-cache");mkdir(cache);
        File[] routes=cache.listFiles();
        if(routes!=null)for(File route:routes){
            File saved=new File(route,"offer.json");
            if(route.getName().startsWith(safe(source)+"__to__")&&saved.isFile()){
                JSONObject value=new JSONObject(read(saved));
                if(date(value.getString("versionName")).compareTo(target)<=0)return value;
            }
        }
        System.out.println("QUERY="+source);
        LinkedHashMap<String,String> p=new LinkedHashMap<>();
        p.put("device_type","pad");p.put("connect_type","-2");p.put("platform","MTK_7400_12.0");
        p.put("project","ayaneo$7400$12.0_KONKR Pocket Advance_en-US_other");p.put("version",source);
        p.put("devicesinfoExt","GT78-VN_ARBOR_GT78-VN_GT78-VN_k85v1$64_ARBOR__");
        p.put("swFingerprint","ARBOR/GT78-VN/GT78-VN:11/RP1A.200720.011/mp1V95182:user/release-keys");
        p.put("sdk_level","31");p.put("sdk_release","12");p.put("resolution","960#640");
        p.put("mid",Long.toString(System.currentTimeMillis())+"Kp");p.put("isNewMid","1");
        p.put("appVersion","5.30.1.237083.006_2025-07-25 10:56");p.put("appCode","216");p.put("local","en-US");
        p.put("operator","");p.put("spn1","");p.put("spn2","");p.put("sendId","1075259712158");
        p.put("fotaSign","50f0c23cbdc67a512562752e48b33828");p.put("androidId","");p.put("fcmId","");
        p.put("agreeType","false");p.put("upgradeAgreement","false");p.put("isActive","false");
        p.put("imei1",serial);p.put("imei2","");p.put("mac","ff:ff:ff:ff:ff:ff");p.put("esn",serial);
        StringBuilder plain=new StringBuilder();for(Map.Entry<String,String> e:p.entrySet())plain.append('&').append(e.getKey()).append('=').append(e.getValue());
        byte[] key=new byte[8];new SecureRandom().nextBytes(key);byte[] input=plain.toString().getBytes(StandardCharsets.UTF_8),encrypted=new byte[input.length+9];encrypted[0]=8;
        for(int i=0;i<8;i++){int v=key[i]&255;encrypted[i+1]=(byte)((v<<3)|(v>>>5));}
        for(int i=0;i<input.length;i++)encrypted[i+9]=(byte)(input[i]^key[i%8]);
        String encoded=hex(encrypted).toUpperCase(Locale.ROOT);
        byte[] body=("key="+encoded+"&shaKey="+hex(MessageDigest.getInstance("SHA-256").digest(encoded.getBytes(StandardCharsets.UTF_8)))).getBytes(StandardCharsets.UTF_8);
        HttpURLConnection connection=(HttpURLConnection)new URL(API).openConnection();connection.setConnectTimeout(30000);connection.setReadTimeout(45000);connection.setRequestMethod("POST");connection.setDoOutput(true);connection.setRequestProperty("Content-Type","application/x-www-form-urlencoded");
        JSONObject response;
        try{try(OutputStream stream=connection.getOutputStream()){stream.write(body);}if(connection.getResponseCode()!=200)throw new IOException("OTA query HTTP "+connection.getResponseCode());response=new JSONObject(read(connection.getInputStream(),1048576));}finally{connection.disconnect();}
        if(response.optInt("status")!=1000)throw new IOException("OTA server status "+response.optInt("status"));
        JSONObject value=response.getJSONObject("version");File route=route(source,value);mkdir(route);write(new File(route,"offer.json"),value.toString());return value;
    }
    private File route(String source,JSONObject offer)throws Exception{return new File(root,"ota-cache/"+safe(source)+"__to__"+safe(offer.getString("versionName")));}
    private File archive(String source,JSONObject offer)throws Exception{
        // The HTTPS API currently returns HTTP CDN URLs; verify its SHA256 before extraction.
        URL url=new URL(offer.getString("deltaurl").replace(" ","%20"));if(!"https".equals(url.getProtocol())&&!"http".equals(url.getProtocol()))throw new IOException("Invalid OTA URL protocol");
        String name=URLDecoder.decode(url.getPath().substring(url.getPath().lastIndexOf('/')+1),"UTF-8");
        if(name.isEmpty()||name.contains("/")||name.contains("\\")||name.equals(".")||name.equals(".."))throw new IOException("Invalid OTA filename");
        File dir=route(source,offer);mkdir(dir);File dest=new File(dir,name);
        if(validArchive(dest,offer)){System.out.println("CACHE="+name);return dest;}
        File temp=new File(dir,name+".download");System.out.println("DOWNLOAD="+name);
        HttpURLConnection c=(HttpURLConnection)url.openConnection();c.setConnectTimeout(30000);c.setReadTimeout(60000);
        try{if(c.getResponseCode()!=200)throw new IOException("OTA download HTTP "+c.getResponseCode());try(InputStream in=c.getInputStream();OutputStream out=new FileOutputStream(temp)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}}finally{c.disconnect();}
        if(!validArchive(temp,offer))throw new IOException("OTA archive checksum mismatch");move(temp,dest);write(new File(dir,"SHA256SUMS.txt"),digest(dest,"SHA-256")+"  "+name+"\n");return dest;
    }
    private boolean validArchive(File file,JSONObject offer)throws Exception{
        if(!file.isFile()||file.length()==0||file.length()!=offer.getLong("filesize"))return false;
        String md5=offer.optString("md5sum"),sha=offer.optString("sha");
        if(md5.isEmpty()&&sha.isEmpty())throw new IOException("OTA offer has no checksum");
        return (md5.isEmpty()||digest(file,"MD5").equalsIgnoreCase(md5))&&(sha.isEmpty()||digest(file,"SHA-256").equalsIgnoreCase(sha));
    }
    private static void requireImage(File f,String hash)throws Exception{if(f.length()!=33554432L||hash!=null&&!digest(f,"SHA-256").equalsIgnoreCase(hash))throw new IOException("Stock boot verification failed: "+f.getName());}
    private static String date(String version)throws Exception{Matcher m=Pattern.compile("^BW03_(\\d{8})(?:_|$)").matcher(version);if(!m.find())throw new IOException("Unsupported firmware: "+version);return m.group(1);}
    private static String safe(String value){return value.replaceAll("[^A-Za-z0-9._-]","_");}
    private static void mkdir(File dir)throws IOException{if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("Cannot create "+dir);}
    private static String digest(File file,String type)throws Exception{MessageDigest d=MessageDigest.getInstance(type);try(InputStream in=new FileInputStream(file)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)d.update(b,0,n);}return hex(d.digest());}
    private static String hex(byte[] bytes){StringBuilder s=new StringBuilder();for(byte b:bytes)s.append(String.format(Locale.ROOT,"%02x",b&255));return s.toString();}
    private static void copy(File src,File dest)throws Exception{mkdir(dest.getParentFile());File temp=new File(dest.getPath()+".tmp");Files.copy(src.toPath(),temp.toPath(),StandardCopyOption.REPLACE_EXISTING);if(!digest(src,"SHA-256").equals(digest(temp,"SHA-256")))throw new IOException("Copy verification failed");move(temp,dest);}
    private static void move(File src,File dest)throws IOException{Files.move(src.toPath(),dest.toPath(),StandardCopyOption.REPLACE_EXISTING);dest.setReadable(true,false);dest.setWritable(true,false);}
    private static void write(File file,String value)throws Exception{mkdir(file.getParentFile());File tmp=new File(file.getPath()+".tmp");try(FileOutputStream out=new FileOutputStream(tmp)){out.write(value.getBytes(StandardCharsets.UTF_8));out.getFD().sync();}move(tmp,file);}
    private static String read(File file)throws Exception{return read(new FileInputStream(file),1048576);}
    private static String read(InputStream stream,int limit)throws Exception{try(InputStream in=stream;ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1){if(out.size()+n>limit)throw new IOException("Response too large");out.write(b,0,n);}return out.toString("UTF-8");}}
}
