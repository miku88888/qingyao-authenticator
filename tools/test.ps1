param([string]$Jdk = 'D:\AndroidDev\jdk')
$ErrorActionPreference='Stop'
$workspace=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$dependency=Join-Path $workspace '.tools\json.jar'
if(-not(Test-Path -LiteralPath $dependency)){
    New-Item -ItemType Directory -Path (Split-Path $dependency) -Force | Out-Null
    Invoke-WebRequest -Uri 'https://repo.maven.apache.org/maven2/org/json/json/20240303/json-20240303.jar' -OutFile $dependency -UseBasicParsing
}
$classes=Join-Path $workspace '.tools\tests'
New-Item -ItemType Directory -Path $classes -Force | Out-Null
$java=Join-Path $workspace 'app\src\main\java\app\qingyao\auth'
& (Join-Path $Jdk 'bin\javac.exe') -encoding UTF-8 --release 8 -classpath $dependency -d $classes (Join-Path $java 'Totp.java') (Join-Path $java 'Account.java') (Join-Path $java 'BackupCrypto.java') (Join-Path $workspace 'tools\CoreTest.java')
if($LASTEXITCODE -ne 0){throw 'Test compilation failed'}
& (Join-Path $Jdk 'bin\java.exe') -classpath "$classes;$dependency" app.qingyao.auth.CoreTest
if($LASTEXITCODE -ne 0){throw 'Core tests failed'}
