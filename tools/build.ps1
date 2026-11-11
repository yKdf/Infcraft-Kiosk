[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$sdkRoot = Join-Path $PSScriptRoot 'sdk'
$buildTools = Join-Path $sdkRoot 'android-15'
$platform = Join-Path $sdkRoot 'android-9\android.jar'
$buildRoot = Join-Path $projectRoot 'build'
$privateRoot = Join-Path $projectRoot '.private'

function Invoke-Checked {
    param(
        [Parameter(Mandatory = $true)][string]$FilePath,
        [Parameter(Mandatory = $true)][string[]]$ArgumentList
    )
    & $FilePath @ArgumentList
    if ($LASTEXITCODE -ne 0) { throw "$FilePath failed with exit code $LASTEXITCODE" }
}

function Find-JdkTool {
    param([Parameter(Mandatory = $true)][string]$Name)
    if ($env:JAVA_HOME) {
        $fromJavaHome = Join-Path $env:JAVA_HOME "bin\$Name.exe"
        if (Test-Path -LiteralPath $fromJavaHome) { return $fromJavaHome }
    }
    $command = Get-Command "$Name.exe" -ErrorAction SilentlyContinue
    if ($command) { return $command.Source }
    $androidStudioTool = "C:\Program Files\Android\Android Studio\jbr\bin\$Name.exe"
    if (Test-Path -LiteralPath $androidStudioTool) { return $androidStudioTool }
    throw "Java JDK tool '$Name.exe' was not found. Install JDK 17 or newer and set JAVA_HOME."
}

$requiredSdkFiles = @(
    $platform,
    (Join-Path $buildTools 'aapt.exe'),
    (Join-Path $buildTools 'd8.bat'),
    (Join-Path $buildTools 'zipalign.exe'),
    (Join-Path $buildTools 'apksigner.bat')
)
foreach ($requiredFile in $requiredSdkFiles) {
    if (-not (Test-Path -LiteralPath $requiredFile)) {
        throw "Android SDK is incomplete. Run .\tools\setup-sdk.ps1 first. Missing: $requiredFile"
    }
}

$javac = Find-JdkTool 'javac'
$jdkBin = Split-Path -Parent $javac
$keytool = Join-Path $jdkBin 'keytool.exe'
if (-not (Test-Path -LiteralPath $keytool)) {
    throw "The JDK containing javac.exe does not contain keytool.exe: $jdkBin"
}
# Android's .bat launchers resolve Java through JAVA_HOME/PATH.
$env:JAVA_HOME = Split-Path -Parent $jdkBin
$env:Path = "$jdkBin;$env:Path"
$classesRoot = Join-Path $buildRoot 'classes'
$dexRoot = Join-Path $buildRoot 'dex'
foreach ($directory in @($classesRoot, $dexRoot)) {
    if (Test-Path -LiteralPath $directory) { Remove-Item -LiteralPath $directory -Recurse -Force }
    New-Item -ItemType Directory -Path $directory | Out-Null
}
New-Item -ItemType Directory -Force -Path $privateRoot | Out-Null

Push-Location $projectRoot
try {
    Invoke-Checked (Join-Path $buildTools 'aapt.exe') @('package','-f','-m','-J','build','-M','android/AndroidManifest.xml','-S','android/res','-A','android/assets','-I',$platform,'-F','build/resources.apk')
    $javaSources = @(Get-ChildItem -LiteralPath 'android/src/com/biel/kiosk' -Filter '*.java' | ForEach-Object FullName)
    Invoke-Checked $javac (@('--release','8','-cp',$platform,'-d',$classesRoot) + $javaSources)
    $classFiles = @(Get-ChildItem -LiteralPath (Join-Path $classesRoot 'com/biel/kiosk') -Filter '*.class' | ForEach-Object FullName)
    Invoke-Checked (Join-Path $buildTools 'd8.bat') (@('--lib',$platform,'--min-api','28','--output',$dexRoot) + $classFiles)

    Copy-Item -LiteralPath 'build/resources.apk' -Destination 'build/unsigned.apk' -Force
    Add-Type -AssemblyName System.IO.Compression
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $unsignedApk = [System.IO.Compression.ZipFile]::Open((Join-Path $buildRoot 'unsigned.apk'), [System.IO.Compression.ZipArchiveMode]::Update)
    try {
        $oldDex = $unsignedApk.GetEntry('classes.dex')
        if ($oldDex) { $oldDex.Delete() }
        [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($unsignedApk, (Join-Path $dexRoot 'classes.dex'), 'classes.dex', [System.IO.Compression.CompressionLevel]::Optimal) | Out-Null
    }
    finally { $unsignedApk.Dispose() }

    Invoke-Checked (Join-Path $buildTools 'zipalign.exe') @('-f','4','build/unsigned.apk','build/aligned.apk')
    $signingKey = Join-Path $privateRoot 'signing.p12'
    $signingPassword = Join-Path $privateRoot 'signing-password'
    if (-not (Test-Path -LiteralPath $signingKey)) {
        $randomBytes = New-Object byte[] 32
        $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
        try { $rng.GetBytes($randomBytes) } finally { $rng.Dispose() }
        $password = [Convert]::ToBase64String($randomBytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
        [System.IO.File]::WriteAllText($signingPassword, $password)
        Invoke-Checked $keytool @('-genkeypair','-keystore',$signingKey,'-storepass:file',$signingPassword,'-alias','kiosk','-keyalg','RSA','-keysize','3072','-validity','10000','-dname','CN=Infcraft Kiosk')
    }
    elseif (-not (Test-Path -LiteralPath $signingPassword)) {
        throw "Signing password is missing: $signingPassword"
    }
    Invoke-Checked (Join-Path $buildTools 'apksigner.bat') @('sign','--ks',$signingKey,'--ks-pass',"file:$signingPassword",'--out','build/infcraft-kiosk.apk','build/aligned.apk')
    Invoke-Checked (Join-Path $buildTools 'apksigner.bat') @('verify','build/infcraft-kiosk.apk')
    Write-Host "APK: $(Join-Path $buildRoot 'infcraft-kiosk.apk')"
}
finally { Pop-Location }
