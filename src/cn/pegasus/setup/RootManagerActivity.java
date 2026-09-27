package cn.pegasus.setup;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.CheckBox;
import android.content.res.ColorStateList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Root status UI; command, state, install and storage work live in focused classes. */
public final class RootManagerActivity extends Activity {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final RootShell shell=new RootShell();
    private final RootHelperManager helper=new RootHelperManager();
    private LinearLayout statusPanel,helperPanel,actionsPanel;
    private TextView notice;
    private boolean syncStarted;
    private boolean installStarted;
    private boolean transientNotice;
    private String persistentError;
    private RootHelperStatus latest;
    private boolean removeWithApp=true,removalLoaded,removalSaving;
    private boolean preparingOta;
    private int prepareTaps;
    private long prepareTapTime;
    private TextView prepareButton;
    private final Runnable clearPrepare=()->{prepareTaps=0;if(prepareButton!=null)prepareButton.setText(tr("准备 OTA"));};
    private volatile boolean disposed;
    private final Handler noticeHandler=new Handler(Looper.getMainLooper());
    private final Runnable refresh=()->probe();
    private final Runnable clearNotice=()->{transientNotice=false;showMonitoring();};

    @Override public void onCreate(Bundle state){super.onCreate(state);getWindow().setStatusBarColor(0xff0d141c);getWindow().setNavigationBarColor(0xff0d141c);draw();probe();}

    private void draw(){
        LinearLayout page=verticalPanel(0xff0d141c);page.setPadding(dp(16),dp(10),dp(16),dp(10));
        LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);bar.setPadding(dp(8),0,dp(8),0);bar.setBackground(background(0xff173b43));
        android.widget.Button back=new android.widget.Button(this);back.setText(tr("返回"));back.setTextSize(14);back.setTextColor(Color.WHITE);back.setAllCaps(false);back.setPadding(0,0,0,0);back.setMinWidth(0);back.setMinHeight(0);back.setBackground(background(0xff2b5963));KpaTouchFeedback.apply(back);back.setOnClickListener(view->finish());bar.addView(back,new LinearLayout.LayoutParams(dp(72),dp(36)));
        TextView title=text("Root 管理",20,Color.WHITE);title.setTypeface(null,1);title.setGravity(Gravity.CENTER_VERTICAL);bar.addView(title,new LinearLayout.LayoutParams(dp(170),dp(48)));
        LinearLayout messages=verticalPanel(Color.TRANSPARENT);messages.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        notice=text("正在检测完整 Root 与 KPA Root Helper 状态……",11,0xffe5b567);notice.setGravity(Gravity.RIGHT);notice.setMaxLines(2);messages.addView(notice,new LinearLayout.LayoutParams(-1,-2));
        bar.addView(messages,new LinearLayout.LayoutParams(0,dp(48),1));page.addView(bar);
        LinearLayout columns=new LinearLayout(this);page.addView(columns,new LinearLayout.LayoutParams(-1,0,1));
        statusPanel=card("设备状态");helperPanel=card("KPA Root Helper");addColumn(columns,statusPanel,false);addColumn(columns,helperPanel,true);
        actionsPanel=new LinearLayout(this);actionsPanel.setPadding(0,dp(6),0,0);page.addView(actionsPanel,new LinearLayout.LayoutParams(-1,-2));setContentView(page);
    }

    private void probe(){if(disposed)return;worker.execute(()->{RootHelperStatus snapshot=RootHelperStatus.capture(shell);if(snapshot.rootGranted&&!removalLoaded){try{removeWithApp=helper.removeWithApp(shell);removalLoaded=true;}catch(Exception error){showError(tr("设置读取失败"));}}runIfAlive(()->show(snapshot));});}

    private void show(RootHelperStatus snapshot){
        latest=snapshot;
        fill(statusPanel,"设备状态",new String[]{"机型","固件","系统","活动槽","完整 Root","Magisk"},snapshot.deviceRows());
        addRemovalOption(snapshot.rootGranted&&removalLoaded);
        fill(helperPanel,"KPA Root Helper",new String[]{"版本","监控状态","OTA 目标槽","boot 哈希","0730 基准","内部存储","TF 镜像"},snapshot.helperRows());
        if(!transientNotice){if(snapshot.helperReady)showMonitoring();else{notice.setText(tr(snapshot.rootGranted?"正在安装或更新 KPA Root Helper……":"获得完整 Root 授权后会自动安装 KPA Root Helper"));notice.setTextColor(0xffe5b567);}}
        if(snapshot.rootGranted&&!snapshot.helperReady&&!installStarted){installStarted=true;installHelper();}else if(snapshot.rootGranted&&snapshot.helperReady&&!syncStarted&&("READY".equals(snapshot.state)||"READY_TO_REBOOT".equals(snapshot.state)||"ERROR_STOCK_BUILD".equals(snapshot.state)||"ERROR_STORAGE".equals(snapshot.state)))synchronize();
        noticeHandler.removeCallbacks(refresh);
        if(snapshot.rootGranted&&snapshot.helperReady)noticeHandler.postDelayed(refresh,15000);
    }

    private void addRemovalOption(boolean allowed){
        actionsPanel.removeAllViews();
        LinearLayout removal=verticalPanel(0xff0d141c),ota=verticalPanel(0xff0d141c);
        actionsPanel.addView(removal,new LinearLayout.LayoutParams(0,-2,1));
        actionsPanel.addView(ota,new LinearLayout.LayoutParams(0,-2,1));
        prepareButton=text(preparingOta?"准备中":"准备 OTA",14,0xffe5edf5);
        prepareButton.setGravity(Gravity.CENTER);prepareButton.setBackground(background(0xff254657));
        KpaTouchFeedback.apply(prepareButton);
        boolean canPrepare=allowed&&latest!=null&&latest.helperReady&&"READY".equals(latest.state)&&!preparingOta;
        prepareButton.setEnabled(canPrepare);prepareButton.setAlpha(canPrepare?1f:.5f);
        ota.addView(prepareButton,new LinearLayout.LayoutParams(-1,dp(36)));
        prepareButton.setOnClickListener(view->confirmPrepare());
        TextView warning=text("准备后勿重启，直接进行系统 OTA",11,0xffe5b567);warning.setGravity(Gravity.CENTER);
        ota.addView(warning);
        CheckBox option=new CheckBox(this);option.setText(tr("卸载应用时移除模块"));option.setTextSize(13);option.setTextColor(0xffe5edf5);
        option.setButtonTintList(ColorStateList.valueOf(0xff55d6be));option.setPadding(dp(5),0,0,0);option.setMinHeight(0);
        option.setChecked(removeWithApp);option.setEnabled(allowed&&!removalSaving);option.setFocusableInTouchMode(false);
        removal.addView(option,new LinearLayout.LayoutParams(-1,dp(36)));
        TextView detail=text("仅移除 KPA Root Helper，保留备份",10,0xff9aa8b7);detail.setPadding(dp(10),0,0,0);removal.addView(detail);
        option.setOnCheckedChangeListener((button,checked)->{removalSaving=true;option.setEnabled(false);worker.execute(()->{
            try{helper.setRemoveWithApp(shell,checked);removeWithApp=checked;runIfAlive(()->showTransient(tr("设置已保存"),0xff55d6be));}
            catch(Exception error){showError(tr("设置保存失败"));}
            finally{removalSaving=false;runIfAlive(()->{option.setOnCheckedChangeListener(null);option.setChecked(removeWithApp);probe();});}
        });});
    }

    private void confirmPrepare(){
        long now=android.os.SystemClock.elapsedRealtime();
        if(now-prepareTapTime>3000)prepareTaps=0;
        prepareTapTime=now;prepareTaps++;
        noticeHandler.removeCallbacks(clearPrepare);
        if(prepareTaps<3){prepareButton.setText(tr(prepareTaps==1?"再按2次准备":"再按1次准备"));noticeHandler.postDelayed(clearPrepare,3000);return;}
        prepareTaps=0;preparingOta=true;persistentError=null;transientNotice=false;
        noticeHandler.removeCallbacks(clearNotice);noticeHandler.removeCallbacks(refresh);
        prepareButton.setEnabled(false);prepareButton.setText(tr("准备中"));
        notice.setText(tr("正在备份并恢复原版 boot，请勿重启"));notice.setTextColor(0xffe5b567);
        worker.execute(()->{try{helper.prepareOta(shell);}
            catch(Exception error){showError(tr("OTA 准备失败，请查看模块状态和日志"));}
            finally{runIfAlive(()->{preparingOta=false;probe();});}});
    }

    private void installHelper(){worker.execute(()->{try{helper.install(this,shell);runIfAlive(()->new AlertDialog.Builder(this).setTitle(tr("KPA Root Helper 已更新")).setMessage(tr("需要重启一次启动 OTA 监控服务。重启后重新进入 Root 管理查看状态。")).setCancelable(false).setNegativeButton(tr("稍后"),null).setPositiveButton(tr("立即重启"),(dialog,which)->worker.execute(()->{try{shell.run("svc power reboot");}catch(Exception ignored){}})).show());}catch(Exception error){showError(tr("KPA Root Helper 安装失败：")+error.getMessage());}});}

    private void synchronize(){syncStarted=true;notice.setText(tr("正在检查内部仓库并同步 TF 卡……"));worker.execute(()->{try{RootHelperManager.SyncResult result=helper.synchronize(shell);runIfAlive(()->{if("ERROR".equals(result.state))showError(tr("TF 卡同步失败：")+(result.reason.isEmpty()?tr("文件复制失败"):result.reason)+tr("；内部存储不受影响"));else showTransient(tr("SYNCED".equals(result.state)?"TF 卡同步成功":"未检测到 TF 卡，内部存储已就绪"),0xff55d6be);probe();});}catch(Exception error){showError(tr("TF 卡同步失败：")+error.getMessage());}});}

    private void showError(String message){runIfAlive(()->{persistentError=message;noticeHandler.removeCallbacks(clearNotice);transientNotice=false;notice.setText(message);notice.setTextColor(0xffff7777);});}
    private void showMonitoring(){if(notice!=null){String message=persistentError!=null?persistentError:latest!=null?tr(latest.summary()):tr("正在读取……");notice.setText(message);notice.setTextColor(persistentError!=null||latest!=null&&latest.state.startsWith("ERROR")?0xffff7777:0xff55d6be);}}
    private void showTransient(String message,int color){transientNotice=true;noticeHandler.removeCallbacks(clearNotice);notice.setText(message);notice.setTextColor(color);noticeHandler.postDelayed(clearNotice,5000);}
    private void copyValue(String key,String value){ClipboardManager clipboard=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);clipboard.setPrimaryClip(ClipData.newPlainText(tr(key),value));showTransient(tr("已复制：")+tr(key),0xff55d6be);}
    private void fill(LinearLayout panel,String title,String[] keys,String[] values){panel.removeAllViews();heading(panel,title);for(int i=0;i<keys.length;i++){String value=values[i];int color=value.contains("未")||value.contains("拒绝")||value.contains("ERROR")?0xffff7777:0xff55d6be;row(panel,keys[i],value,color);}}
    private LinearLayout card(String title){LinearLayout panel=verticalPanel(0xff17222d);panel.setPadding(dp(12),dp(8),dp(12),dp(8));panel.setBackground(background(0xff17222d));heading(panel,title);panel.addView(text("正在读取……",13,0xffe5b567));return panel;}
    private void addColumn(LinearLayout parent,View child,boolean right){LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,-1,1);params.setMargins(right?dp(5):0,dp(6),right?0:dp(5),dp(6));android.widget.ScrollView scroll=new android.widget.ScrollView(this);scroll.setFillViewport(true);scroll.addView(child);parent.addView(scroll,params);}
    private LinearLayout verticalPanel(int color){LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setBackgroundColor(color);return panel;}
    private void heading(LinearLayout panel,String value){TextView view=text(value,18,Color.WHITE);view.setTypeface(null,1);panel.addView(view);}
    private void row(LinearLayout panel,String key,String value,int color){LinearLayout line=new LinearLayout(this);line.addView(text(key,13,0xff9aa8b7),new LinearLayout.LayoutParams(dp(118),-2));TextView field=text(value,13,color);field.setSingleLine(true);field.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);field.setClickable(true);field.setFocusable(true);KpaTouchFeedback.apply(field);field.setOnClickListener(view->copyValue(key,value));line.addView(field,new LinearLayout.LayoutParams(0,-2,1));panel.addView(line);}
    private TextView text(String value,int size,int color){TextView view=new TextView(this);view.setText(tr(value));view.setTextSize(size);view.setTextColor(color);view.setPadding(dp(10),dp(5),dp(10),dp(5));return view;}
    private String tr(String value){return KpaLanguage.text(this,value);}
    private void toggleLanguage(){KpaLanguage.toggle(this);recreate();}
    private void runIfAlive(Runnable action){runOnUiThread(()->{if(!disposed&&!isFinishing()&&!isDestroyed())action.run();});}
    private GradientDrawable background(int color){GradientDrawable drawable=new GradientDrawable();drawable.setColor(color);drawable.setCornerRadius(dp(8));return drawable;}
    private int dp(float value){return(int)(value*getResources().getDisplayMetrics().density+.5f);}
    @Override public boolean onKeyDown(int code,KeyEvent event){if(code==KeyEvent.KEYCODE_BUTTON_Y){if(event.getRepeatCount()==0)toggleLanguage();return true;}return super.onKeyDown(code,event);}
    @Override public boolean onKeyUp(int code,KeyEvent event){if(code==KeyEvent.KEYCODE_BUTTON_Y)return true;return super.onKeyUp(code,event);}
    @Override protected void onDestroy(){disposed=true;noticeHandler.removeCallbacksAndMessages(null);worker.shutdown();super.onDestroy();}
}
