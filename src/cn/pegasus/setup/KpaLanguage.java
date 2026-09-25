package cn.pegasus.setup;

import android.content.Context;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

final class KpaLanguage {
 private static final String MANUAL="language.manual", ENGLISH="language.english";
 private static final LinkedHashMap<String,String> TEXT=new LinkedHashMap<>();
 static {
  put("KPA助手","KPA Assistant"); put("授权","Authorize"); put("资料","Sources"); put("执行","Run"); put("关于","About");
  put("设备授权","Device access"); put("设备型号","Device model"); put("未知机型，谨慎使用！","Unknown device — use with caution!");
  put("文件权限","File access"); put("存储 Root","Storage Root"); put("已授权","Granted"); put("待授权","Required"); put("已通过","Passed"); put("待执行","Pending");
  put("启动检查","Startup check"); put("掌机管理","Device Manager"); put("下一步","Next"); put("返回","Back"); put("关闭","Close"); put("取消","Cancel"); put("确认","Confirm"); put("完成","Done");
  put("资料检测","Source check"); put("资料目录","Source folder"); put("GBA整包","GBA bundle"); put("未选择 · 可选","Not selected · Optional");
  put("安装配置","Install setup"); put("Roms存储","ROM storage"); put("自动检测","Auto detection"); put("正在搜索资料","Searching for sources"); put("正在核对内容","Checking contents");
  put("检测结果","Check results"); put("等待自动检测","Waiting for auto detection"); put("资料完整","Sources ready"); put("请补全标红项目","Complete highlighted items");
  put("调整选择","Adjust"); put("检测中","Checking"); put("补全资料","Complete"); put("应用选择","Apps"); put("覆盖资料","Overlays");
  put("准备执行","Ready to run"); put("执行结果","Result"); put("执行中","Running"); put("执行成功","Completed"); put("执行失败","Failed"); put("部分完成","Completed with warnings");
  put("执行日志","Run log"); put("返回选择","Back to sources"); put("确认执行","Start"); put("查看结果","View result"); put("最终确认执行","Confirm initialization"); put("确认开始","Start now");
  put("设置教程","Setup guides"); put("手柄配置教程","Controller guide"); put("模拟器设置教程","Emulator guide"); put("构建 ","Build ");
  put("为 KONKR Pocket Advance 安装应用、导入配置与整理游戏资料。","Install apps, import settings, and organize game files for KONKR Pocket Advance.");
  put("本助手用于安装与配置整理，第三方应用、配置、教程及游戏内容的版权归各自作者与权利人所有。","This assistant installs and organizes configuration. Third-party apps, settings, guides, and game content remain the property of their respective owners.");
  put("步骤切换","Switch step"); put("按钮选择","Select"); put("确定","Confirm"); put("语言切换","Language"); put("提示详情","Notice");
  put("选择 ZIP","Select ZIP"); put("选择文件夹","Select folder"); put("浏览存储","Browse storage"); put("不导入","Skip"); put("选择","Select"); put("补选","Choose");
  put("定制配置","Custom settings"); put("安卓覆盖","Android overlay"); put("游戏列表","Game lists"); put("未选择","Not selected"); put("未找到","Not found"); put("缺少","Missing"); put("已修改","Changed");
  put("已安装","Installed"); put("未安装","Not installed"); put("安装更新","Install/update"); put("补选安装","Choose APK"); put("安装包 · ","APK · ");
  put("天马G 前端","Pegasus G Frontend"); put("RetroArch G（GBA 必需）","RetroArch G (required for GBA)"); put("MT 文件管理器（可选）","MT File Manager (optional)");
  put("最终结果","Final result"); put("初始化失败，请查看执行日志","Initialization failed. Open the run log."); put("GBA整包请自行覆盖","Copy the GBA bundle manually");
  put("正在初始化","Initializing"); put("正在准备","Preparing"); put("正在准备，请勿关机","Preparing — do not power off"); put("解压中，请耐心等待","Extracting — please wait"); put("总进度","Overall");
  put("校验资料","Checking sources"); put("清理旧配置","Cleaning old settings"); put("导入定制配置","Importing custom settings"); put("导入 GBA整包","Importing GBA bundle"); put("覆盖游戏列表","Applying game lists"); put("初始化 RA","Initializing RA"); put("覆盖安卓目录","Applying Android overlay"); put("核验文件","Verifying files"); put("初始化完成","Initialization complete");
  put("安装天马G","Installing Pegasus G"); put("安装 RA","Installing RA"); put("安装其他应用","Installing other apps"); put("操作记录","Run log");
  put("错误：","Error: "); put("提醒：","Notice: "); put("文件复制失败：","File copy failed: "); put("已跳过","Skipped"); put("文件缺失：","Missing file: "); put("二次覆盖后核验通过","Verification passed after retry"); put("首次核验未通过，正在重试异常文件","Initial verification failed; retrying affected files");
  put("应用存储权限已设置：","App storage access configured: "); put("内存不足","Out of memory"); put("开始初始化","Initialization started"); put("应用安装完成","App installation complete"); put("文件覆盖完成","File import complete"); put("文件存在检查通过","File existence check passed"); put("覆盖文件检查通过","Overlay file check passed");
  put("访问被拒绝","Access denied"); put("存储空间不足","Not enough storage"); put("文件不存在","File not found"); put("路径不安全","Unsafe path"); put("应用目录权限异常","Invalid app folder permissions"); put("RA 资源释放完成","RA resources initialized"); put("RA 资源释放超时","RA resource initialization timed out"); put("无法读取 RA 版本","Unable to read RA version");
  put("设备→Root 脚本：运行 KPA_Authorize.sh","Device → Root Script: run KPA_Authorize.sh");
  put("请先授予文件权限","Grant file access first"); put("授权后会自动生成 KPA_Authorize.sh","KPA_Authorize.sh will be created automatically"); put("掌机管理 → Root 脚本 → 运行 KPA_Authorize.sh","Device Manager → Root Script → run KPA_Authorize.sh"); put("执行结果页面下滑到最后，出现 SUCCESS 后返回 KPA助手","Scroll to SUCCESS, then return to KPA Assistant"); put("授权通过，可以继续","Authorization passed. You can continue.");
  put("选择教程查看设置说明","Open a guide for setup instructions"); put("资料不完整，请补选标红项目","Sources incomplete. Complete highlighted items."); put("资料完整，可以继续","Sources ready. You can continue."); put("正在检测资料","Checking sources"); put("等待自动识别资料","Waiting for automatic source detection");
  put("正在初始化 · 请保持本页","Initializing · Keep this page open"); put("检查无误后确认执行","Review and start initialization"); put("点击执行日志查看","Open the run log for details");
  put("款安装"," apps selected"); put("游戏与列表","Games and lists"); put("内部存储","Internal storage"); put("TF卡","SD card"); put("存储选择","Storage");
  put("资料与应用检测通过","Sources and apps passed"); put("资料检测通过后才能继续","Complete the source check before continuing"); put("资料检测失败：","Source check failed: "); put("资料自动识别完成","Sources detected automatically");
  put("未找到资料目录，请手动选择","Source folder not found. Select it manually."); put("发现多个资料目录，请手动确认","Multiple source folders found. Select one manually."); put("发现多个 GBA整包，请手动确认","Multiple GBA bundles found. Select one manually.");
  put("请选择完整资料文件夹","Select a complete source folder"); put("请选择 ZIP 文件","Select a ZIP file"); put("缺少有效安卓覆盖配置，请补选","Valid Android overlay is missing"); put("未找到定制主配置 ZIP，请在导入选项选择","Custom settings ZIP is missing"); put("主配置缺少天马G或 RA 配置，请补选","Custom settings must contain Pegasus G or RA settings");
  put("所选 ZIP 内未识别到 GBA 游戏和列表结构","The selected ZIP does not contain a recognized GBA games/list structure"); put("缺少安装包：","Missing APK: "); put("安装包不属于所选软件","The APK does not match this app");
  put("空间检查：","Storage check: "); put("可用 ","Available "); put("预计解压 ","Estimated extracted size "); put("空间不足：继续安装应用与覆盖配置，GBA 请自行覆盖","Not enough space for GBA. Apps and settings will continue; copy GBA manually.");
  put("用户已确认，开始检查执行条件","Confirmed. Checking requirements."); put("准备资料：开始整理配置与游戏","Preparing sources and games"); put("执行清单完成，正在生成 Root 脚本","Plan ready. Creating the Root script."); put("资料准备完成：","Sources prepared: "); put("资料准备完成，等待运行 Root 脚本","Sources ready. Waiting for the Root script.");
  put("准备配置","Preparing settings"); put("准备 GBA","Preparing GBA"); put("准备安卓覆盖：只读取配置文件","Preparing Android overlay"); put("处理文件","Processing files"); put("个文件"," files"); put("个目标文件"," target files");
  put("执行完成，发现 ","Completed with "); put(" 项提示"," notices"); put("已处理 ","Processed "); put("核验 ","Verified "); put(" 项"," items"); put(" 个文件核验通过"," files verified");
  put("执行成功 · 核验通过 ","Completed · Verified "); put("部分完成 · 点击执行日志查看","Completed with warnings · Open the run log"); put("执行失败 · 点击执行日志查看","Failed · Open the run log");
  put("Root 脚本运行本批次脚本，完成后查看结果","Run this batch script from Root Script, then view the result"); put("正在执行，请稍候","Initialization is running"); put("先执行存储 Root 授权脚本","Run the Storage Root authorization script first");
  put("存储 Root 授权有效","Storage Root authorization valid"); put("系统权限通过","System permission passed"); put("权限待确认","Permission pending"); put("目录已确认","Folder confirmed"); put("路径已复制","Path copied");
  put("✓ 必备资料与应用已确认","✓ Required sources and apps confirmed"); put("✓ 资料自动识别完成","✓ Sources detected automatically"); put("✓ 本次文件存在复核通过：","✓ File existence check passed: "); put("✓ 初次安装与覆盖已核验，请在最后一页查看手动设置教程。","✓ Installation and overlays verified. Review the setup guides on the About page.");
  put("安装 / 更新 ","Install/update "); put(" 清理旧配置 · 覆盖资料 · 不备份"," · Clean old settings · Apply sources · No backup"); put(" · GBA 自行覆盖"," · Copy GBA manually");
  put("系统存储权限：","System storage access: "); put("当前阶段：","Current stage: "); put("当前文件夹","Current folder"); put("复制路径","Copy path"); put("返回上级","Up one level"); put("打开应用","Open app"); put("知道了","OK");
  put("初始化计划","Initialization plan"); put("最后授权与配置","Final access and setup"); put("存储 Root 执行说明","Storage Root instructions"); put("执行存储 Root 脚本","Run Storage Root script"); put("返回助手","Return to assistant");
  put("读取文件完整性与存储授权状态，不修改配置。","Checks files and storage access without modifying settings."); put("检查所有目标文件是否存在。不会启动应用或修改配置。","Checks that target files exist. Apps and settings are not modified.");
  put("当前系统没有可直接调用的存储 Root 接口，请使用掌机设置的 Root 脚本入口","Storage Root cannot be called directly. Use Device Manager → Root Script."); put("普通应用无法访问系统 Root 服务","A regular app cannot access the system Root service");
  put("存储 Root 接口不可直调：","Storage Root cannot be called directly: "); put("存储 Root 接口拒绝调用：","Storage Root call rejected: "); put("存储 Root 无法写入 Android/data，已停止执行；请重新运行授权脚本。","Storage Root cannot write Android/data. Run the authorization script again.");
  put("请先完成存储授权","Complete storage authorization first"); put("请先完成本批次初始化脚本","Run this batch initialization script first"); put("请先一键检测，生成本批次脚本","Run the check first to create this batch script"); put("请先生成脚本","Create the script first");
  put("正在读取所选 ZIP 文件，请稍候…","Reading the selected ZIP…"); put("准备过程已中断，请重新确认执行","Preparation was interrupted. Confirm again."); put("准备文件已缺失，请重新准备：","Prepared file is missing: "); put("文件导入完成，请查看最终结果","File import complete. View the result.");
  put("应用未安装或没有启动入口","App is not installed or has no launch activity"); put("所选 GBA整包不可用","Selected GBA bundle is unavailable"); put("所选 ZIP 是空文件","Selected ZIP is empty"); put("目录无权限或不可读取","Folder is inaccessible"); put("无法读取所选文件","Unable to read selected file");
  put("无法创建本地准备目录","Unable to create local staging folder"); put("无法创建分步脚本目录：","Unable to create step folder: "); put("无法创建目录：","Unable to create folder: "); put("无法读取配置目录：","Unable to read settings folder: ");
  put("压缩包路径无效","Invalid ZIP path"); put("压缩包包含不安全路径：","ZIP contains an unsafe path: "); put("压缩包体积无效","Invalid ZIP size"); put("压缩包条目体积无效","Invalid ZIP entry size"); put("压缩包校验失败：","ZIP integrity check failed: ");
  put("配置中不允许符号链接：","Symlinks are not allowed in settings: "); put("配置文件编码无法安全识别：","Unable to identify settings file encoding: "); put("目标名称包含不支持的控制字符","Target name contains unsupported control characters");
  put("游戏目标目录不可写","Game destination is not writable"); put("整合包中未找到 metadata.pegasus.txt 游戏列表","metadata.pegasus.txt was not found in the bundle"); put("GBA 包中没有识别到游戏与 metadata.pegasus.txt 列表","No games or metadata.pegasus.txt list found in the GBA bundle");
  put("已跳过 PSP 中文件名无法识别的金手指文件","Skipped an optional PSP cheat with an unreadable filename"); put("已跳过安卓覆盖中文件名无法识别的可选文件","Skipped an optional Android overlay file with an unreadable filename"); put("已跳过文件名无法识别的可选文件","Skipped an optional file with an unreadable filename");
  put("重试失败：","Retry failed: "); put("已重试覆盖：","Retried: "); put("二次覆盖文件：","Files retried: "); put("二次覆盖后仍有文件核验未通过","Some files are still missing after retry");
  put("现有配置：应用未安装","Existing settings: app not installed"); put("现有配置：暂无检测路径，尚未核对","Existing settings: no check path"); put("现有配置：无法直接读取，正式执行时处理","Existing settings: checked during initialization"); put("现有配置：已有文件，执行时按所选资料覆盖","Existing settings: will be replaced by selected sources");
  put("提醒数量：","Notices: "); put("核验文件：","Verified files: "); put("执行状态：","Status: "); put("成功","Success"); put("完成","Complete");
  put("教程来源：","Guide source: "); put("教程读取失败：","Unable to read guide: "); put("关闭教程","Close guide");
  put("失败","failed"); put("跳过","Skipped"); put("正在","In progress: "); put("准备","Preparing"); put("清理","Cleaning"); put("安装应用：","Installing app: "); put("覆盖","Applying"); put("检查","Checking"); put("核验中","Verifying");
 }
 private static void put(String zh,String en){TEXT.put(zh,en);}
 static boolean isEnglish(Context context){android.content.SharedPreferences prefs=context.getSharedPreferences("kpa-language",0);if(prefs.getBoolean(MANUAL,false))return prefs.getBoolean(ENGLISH,false);return !"zh".equalsIgnoreCase(Locale.getDefault().getLanguage());}
 static void toggle(Context context){boolean next=!isEnglish(context);context.getSharedPreferences("kpa-language",0).edit().putBoolean(MANUAL,true).putBoolean(ENGLISH,next).apply();}
 static String text(Context context,String value){return isEnglish(context)?english(value):value;}
 static String english(String value){
  if(value==null||value.isEmpty())return value;
  if(TEXT.containsKey(value))return TEXT.get(value);
  java.util.ArrayList<Map.Entry<String,String>> entries=new java.util.ArrayList<>(TEXT.entrySet());
  java.util.Collections.sort(entries,(a,b)->Integer.compare(b.getKey().length(),a.getKey().length()));
  String out=value;for(Map.Entry<String,String> entry:entries)out=out.replace(entry.getKey(),entry.getValue());return out;
 }
}
