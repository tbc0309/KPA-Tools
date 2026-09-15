param([Parameter(Mandatory=$true)][string]$Jdk,[Parameter(Mandatory=$true)][string]$Sdk,[string]$WorkDirectory,[string]$OutputApk)
$ErrorActionPreference='Stop'
if(!$WorkDirectory){$WorkDirectory=Join-Path $PSScriptRoot 'build'}
if(!$OutputApk){$OutputApk=Join-Path (Split-Path $PSScriptRoot) 'KPA助手.apk'}
$env:JAVA_HOME=$Jdk
$platform=Join-Path $Sdk 'android-35\android.jar'
if(!(Test-Path -LiteralPath $platform)){$platform=Join-Path $Sdk 'platforms\android-35\android.jar'}
$tools=Join-Path $Sdk 'android-15'
if(!(Test-Path -LiteralPath (Join-Path $tools 'aapt.exe'))){$tools=Join-Path $Sdk 'build-tools\35.0.0'}
if(!(Test-Path -LiteralPath $platform)){throw '缺少 Android 35 platform'}
if(!(Test-Path -LiteralPath (Join-Path $tools 'aapt.exe'))){throw '缺少 Android Build Tools 35.0.0'}
$stamp=Get-Date -Format 'yyyyMMddHHmmssfff'
$classes=Join-Path $WorkDirectory ('classes-'+$stamp)
$dex=Join-Path $WorkDirectory ('dex-'+$stamp)
New-Item -ItemType Directory -Force -Path $WorkDirectory,$classes,$dex | Out-Null
function CheckExit {if($LASTEXITCODE -ne 0){throw "构建失败，退出码 $LASTEXITCODE"}}
$sources=@(Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'src') -Recurse -Filter '*.java' | ForEach-Object {$_.FullName})
$buildTime=(Get-Date).ToUniversalTime().AddHours(8).ToString('yyyy-MM-dd HH:mm:ss')
$generated=Join-Path $WorkDirectory 'BuildInfo.java'
@"
package cn.pegasus.setup;
final class BuildInfo { static final String TIME="$buildTime"; private BuildInfo(){} }
"@ | Set-Content -LiteralPath $generated -Encoding UTF8
$sources += $generated
& (Join-Path $Jdk 'bin\javac.exe') -encoding UTF-8 -source 8 -target 8 -cp $platform -d $classes @sources
CheckExit
& (Join-Path $Jdk 'bin\jar.exe') cf (Join-Path $WorkDirectory 'classes.jar') -C $classes .
CheckExit
& (Join-Path $Jdk 'bin\java.exe') -cp (Join-Path $tools 'lib\d8.jar') com.android.tools.r8.D8 --release --min-api 30 --lib $platform --output $dex (Join-Path $WorkDirectory 'classes.jar')
CheckExit
$buildManifest=Join-Path $PSScriptRoot 'AndroidManifest.xml'
& (Join-Path $tools 'aapt.exe') package -f -M $buildManifest -S (Join-Path $PSScriptRoot 'res') -I $platform -A (Join-Path $PSScriptRoot 'assets') -F (Join-Path $WorkDirectory 'unsigned.apk')
CheckExit
Push-Location $dex
try {& (Join-Path $tools 'aapt.exe') add (Join-Path $WorkDirectory 'unsigned.apk') classes.dex;CheckExit} finally {Pop-Location}
& (Join-Path $tools 'zipalign.exe') -f 4 (Join-Path $WorkDirectory 'unsigned.apk') (Join-Path $WorkDirectory 'aligned.apk')
CheckExit
$key=Join-Path $WorkDirectory 'local-signing.p12'
if(!(Test-Path -LiteralPath $key)){
 & (Join-Path $Jdk 'bin\keytool.exe') -genkeypair -keystore $key -storepass pegasus-local -keypass pegasus-local -alias pegasus -keyalg RSA -keysize 2048 -validity 10000 -dname 'CN=PegasusG Setup Local Build'
 CheckExit
}
& (Join-Path $Jdk 'bin\java.exe') -jar (Join-Path $tools 'lib\apksigner.jar') sign --ks $key --ks-pass pass:pegasus-local --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true --out $OutputApk (Join-Path $WorkDirectory 'aligned.apk')
CheckExit
& (Join-Path $Jdk 'bin\java.exe') -jar (Join-Path $tools 'lib\apksigner.jar') verify --verbose $OutputApk
CheckExit
Write-Output "APK：$OutputApk"
