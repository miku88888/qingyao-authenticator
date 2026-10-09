param(
    [string]$Sdk = $env:ANDROID_SDK_ROOT,
    [string]$Jdk = $env:JAVA_HOME,
    [switch]$Debug
)
$ErrorActionPreference = 'Stop'
$workspace = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$displayWorkspace = $workspace
# Some Windows Android tools cannot open non-ASCII paths. A junction keeps all
# sources, signing keys, and outputs in the user's original workspace.
if ($workspace -match '[^\x00-\x7F]') {
    $hash = [Security.Cryptography.SHA256]::Create()
    $suffix = ([BitConverter]::ToString($hash.ComputeHash([Text.Encoding]::UTF8.GetBytes($workspace)))).Replace('-','').Substring(0,12)
    $hash.Dispose()
    $junction = Join-Path ([IO.Path]::GetTempPath()) "qingyao-workspace-$suffix"
    if (-not (Test-Path -LiteralPath $junction)) { New-Item -ItemType Junction -Path $junction -Target $workspace | Out-Null }
    $link = Get-Item -LiteralPath $junction -Force
    $resolvedTarget = [IO.Path]::GetFullPath([string](@($link.Target)[0])).TrimEnd('\')
    if ($link.LinkType -ne 'Junction' -or $resolvedTarget -ne $workspace.TrimEnd('\')) { throw 'Build path connection does not point to this workspace.' }
    $workspace = $junction
}
if (-not $Sdk) { $Sdk = 'D:\AndroidDev\sdk' }
if (-not $Jdk) { $Jdk = 'D:\AndroidDev\jdk' }
$env:JAVA_HOME = $Jdk
$env:PATH = "$Jdk\bin;$env:PATH"
$platform = Join-Path $Sdk 'platforms\android-34\android.jar'
$buildTools = Join-Path $Sdk 'build-tools\34.0.0'
if (-not (Test-Path -LiteralPath $platform)) { throw 'Android SDK platform 34 is required.' }
function Run([string]$executable, [string[]]$arguments) {
    & $executable @arguments
    if ($LASTEXITCODE -ne 0) { throw "Build step failed: $executable" }
}
$build = Join-Path $workspace 'build\native'
$classes = Join-Path $build 'classes'
$generated = Join-Path $build 'generated'
$dex = Join-Path $build 'dex'
$dist = Join-Path $workspace 'dist'
New-Item -ItemType Directory -Path $build,$classes,$generated,$dex,$dist -Force | Out-Null
$zxing = Join-Path $workspace 'app\libs\zxing-core-3.5.4.jar'
if (-not (Test-Path -LiteralPath $zxing)) {
    New-Item -ItemType Directory -Path (Split-Path $zxing) -Force | Out-Null
    Invoke-WebRequest -Uri 'https://repo.maven.apache.org/maven2/com/google/zxing/core/3.5.4/core-3.5.4.jar' -OutFile $zxing -UseBasicParsing
}
$sourceManifest = Get-Content -LiteralPath (Join-Path $workspace 'app\src\main\AndroidManifest.xml') -Raw
$sourceManifest = $sourceManifest.Replace('<manifest xmlns:android=', '<manifest package="app.qingyao.auth" xmlns:android=')
if ($Debug) { $sourceManifest = $sourceManifest.Replace('<application ', '<application android:debuggable="true" ') }
$manifest = Join-Path $build 'AndroidManifest.xml'
[IO.File]::WriteAllText($manifest, $sourceManifest, [Text.UTF8Encoding]::new($false))
$resources = Join-Path $build 'res.zip'
Run (Join-Path $buildTools 'aapt2.exe') @('compile','--dir',(Join-Path $workspace 'app\src\main\res'),'-o',$resources)
$apk = Join-Path $build 'resources.apk'
Run (Join-Path $buildTools 'aapt2.exe') @('link','-o',$apk,'-I',$platform,'--manifest',$manifest,'--java',$generated,'-A',(Join-Path $workspace 'app\src\main\assets'),'--version-code','1','--version-name','1.0.0',$resources)
$sources = @((Get-ChildItem -LiteralPath (Join-Path $workspace 'app\src\main\java'),$generated -Filter '*.java' -Recurse).FullName)
$sourceList = Join-Path $build 'sources.txt'
[IO.File]::WriteAllLines($sourceList, @($sources | ForEach-Object { '"' + $_.Replace('\','/') + '"' }), [Text.UTF8Encoding]::new($false))
Run (Join-Path $Jdk 'bin\javac.exe') @('-encoding','UTF-8','--release','8','-classpath',"$platform;$zxing",'-d',$classes,"@$sourceList")
$classesJar = Join-Path $build 'classes.jar'
Run (Join-Path $Jdk 'bin\jar.exe') @('cf',$classesJar,'-C',$classes,'.')
$r8 = Join-Path $workspace '.tools\r8-9.5.23.jar'
if (-not (Test-Path -LiteralPath $r8)) {
    New-Item -ItemType Directory -Path (Split-Path $r8) -Force | Out-Null
    Invoke-WebRequest -Uri 'https://dl.google.com/dl/android/maven2/com/android/tools/r8/9.5.23/r8-9.5.23.jar' -OutFile $r8 -UseBasicParsing
}
Run (Join-Path $Jdk 'bin\java.exe') @('-cp',$r8,'com.android.tools.r8.D8','--release','--min-api','26','--lib',$platform,'--output',$dex,$classesJar,$zxing)
Run (Join-Path $Jdk 'bin\jar.exe') @('uf',$apk,'-C',$dex,'classes.dex')
$aligned = Join-Path $build 'aligned.apk'
Run (Join-Path $buildTools 'zipalign.exe') @('-f','-p','4',$apk,$aligned)
$signing = Join-Path $workspace '.signing'
New-Item -ItemType Directory -Path $signing -Force | Out-Null
$keystore = Join-Path $signing 'qingyao-release.jks'
$passFile = Join-Path $signing 'password.txt'
if (-not (Test-Path -LiteralPath $keystore)) {
    if (-not (Test-Path -LiteralPath $passFile)) {
        $random = New-Object byte[] 32
        $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
        $rng.GetBytes($random)
        $rng.Dispose()
        [IO.File]::WriteAllText($passFile,[Convert]::ToBase64String($random),[Text.UTF8Encoding]::new($false))
    }
    Run (Join-Path $Jdk 'bin\keytool.exe') @('-genkeypair','-keystore',$keystore,'-alias','qingyao','-storepass:file',$passFile,'-keypass:file',$passFile,'-keyalg','RSA','-keysize','3072','-validity','10000','-dname','CN=Qingyao, O=Local, C=CN')
}
$name = if ($Debug) {'qingyao-debug.apk'} else {'qingyao-1.0.0.apk'}
$output = Join-Path $dist $name
Run (Join-Path $buildTools 'apksigner.bat') @('sign','--ks',$keystore,'--ks-key-alias','qingyao','--ks-pass',"file:$passFile",'--out',$output,$aligned)
Run (Join-Path $buildTools 'apksigner.bat') @('verify','--verbose',$output)
Get-FileHash -LiteralPath $output -Algorithm SHA256 | Select-Object Algorithm,Hash
Write-Output "APK: $(Join-Path $displayWorkspace ('dist\' + $name))"
