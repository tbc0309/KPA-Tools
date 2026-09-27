package cn.pegasus.setup;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.IBinder;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.TextView;

/** Event-driven OTA warning. No polling, input capture or partition access. */
public final class OtaNoticeService extends Service {
    private static final String CHANNEL="kpa_ota_safety";
    private static final int NOTICE=104;
    private WindowManager windows;
    private TextView banner;
    private WindowManager.LayoutParams bannerParams;
    private final android.os.Handler pulseHandler=new android.os.Handler(android.os.Looper.getMainLooper());
    private boolean dim;
    private final Runnable pulse=new Runnable(){public void run(){
        if(banner==null)return;
        dim=!dim;bannerParams.alpha=dim?.4f:.75f;windows.updateViewLayout(banner,bannerParams);
        pulseHandler.postDelayed(this,1000);
    }};
    private final android.content.BroadcastReceiver screenReceiver=new android.content.BroadcastReceiver(){
        @Override public void onReceive(android.content.Context context,Intent intent){
            pulseHandler.removeCallbacks(pulse);
            if(Intent.ACTION_SCREEN_ON.equals(intent.getAction()))startPulse();
        }
    };

    @Override public void onCreate(){
        super.onCreate();
        NotificationManager manager=getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(CHANNEL,"KPA Root Helper",NotificationManager.IMPORTANCE_LOW));
        windows=getSystemService(WindowManager.class);
        android.content.IntentFilter filter=new android.content.IntentFilter(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(screenReceiver,filter);
    }

    @Override public int onStartCommand(Intent intent,int flags,int id){
        // Never trust an intent-supplied success state; only the root helper publishes it.
        String state=new RootShell().prop("kpa.root_helper.state");
        String message=message(state);
        String text=KpaLanguage.text(this,message==null?"正在检测 OTA 状态，请勿重启":message);
        Intent page=new Intent(this,RootManagerActivity.class);
        PendingIntent open=PendingIntent.getActivity(this,0,page,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification notification=new Notification.Builder(this,CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_warning).setContentTitle("KPA Root Helper")
                .setContentText(text).setStyle(new Notification.BigTextStyle().bigText(text))
                .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).build();
        startForeground(NOTICE,notification);
        if(message==null){stopSelf();return START_NOT_STICKY;}
        if(Settings.canDrawOverlays(this)){
            try{
                if(banner==null){
                    banner=new TextView(this);banner.setTextSize(14);banner.setGravity(Gravity.CENTER);
                    banner.setSingleLine(true);
                    banner.setAutoSizeTextTypeUniformWithConfiguration(10,14,1,TypedValue.COMPLEX_UNIT_SP);
                    banner.setPadding(dp(14),dp(8),dp(14),dp(8));
                    WindowManager.LayoutParams params=new WindowManager.LayoutParams(getResources().getDisplayMetrics().widthPixels-dp(48),-2,
                            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                            PixelFormat.TRANSLUCENT);
                    params.gravity=Gravity.CENTER;
                    // Stay below Android's obscuring-opacity limit for touch-through windows.
                    params.alpha=.75f;
                    bannerParams=params;
                    windows.addView(banner,params);
                }
                boolean ready="READY_TO_REBOOT".equals(state);
                GradientDrawable surface=new GradientDrawable();
                surface.setColor(ready?0xff174a35:state.startsWith("ERROR")?0xff712b2b:0xff5a4115);
                surface.setCornerRadius(dp(8));banner.setBackground(surface);
                banner.setTextColor(Color.WHITE);banner.setText("KPA Root Helper · "+text);
                startPulse();
            }catch(RuntimeException unavailable){removeBanner();}
        }else removeBanner();
        return START_NOT_STICKY;
    }

    static String message(String state){
        if("READY_TO_REBOOT".equals(state))return "Root 修补及校验完成，可以点击系统重启";
        if("PATCHING".equals(state))return "正在修补 Root，请勿点击重启";
        if("OTA_RUNNING".equals(state))return "系统更新中，请勿重启；请等待 Root 校验完成";
        if("PREPARING_OTA".equals(state))return "正在准备 OTA，请勿重启";
        if("OTA_PREPARED".equals(state))return "请进行系统 OTA，勿提前重启";
        if(state.startsWith("ERROR"))return "Root 处理异常，请勿重启；打开 Root 管理检查";
        return null;
    }
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private void startPulse(){
        pulseHandler.removeCallbacks(pulse);dim=false;
        if(banner!=null){bannerParams.alpha=.75f;windows.updateViewLayout(banner,bannerParams);if(getSystemService(android.os.PowerManager.class).isInteractive())pulseHandler.postDelayed(pulse,1000);}
    }
    private void removeBanner(){pulseHandler.removeCallbacks(pulse);if(banner!=null){try{windows.removeView(banner);}catch(RuntimeException ignored){}banner=null;}}
    @Override public void onDestroy(){unregisterReceiver(screenReceiver);removeBanner();stopForeground(true);super.onDestroy();}
    @Override public IBinder onBind(Intent intent){return null;}
}
