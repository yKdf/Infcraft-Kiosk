[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$sdkRoot = Join-Path $PSScriptRoot 'sdk'
$repository = 'https://dl.google.com/android/repository'
$packages = @(
    @{ Name = 'platform-28_r06.zip'; Sha1 = '9a4e52b1d55bd2e24216b150aafae2503d3efba6'; Target = 'android-9\android.jar' },
    @{ Name = 'build-tools_r35_windows.zip'; Sha1 = 'af059bb67cf7786f45ee0db85e2d24985df1b4b6'; Target = 'android-15\aapt.exe' }
)

New-Item -ItemType Directory -Force -Path $sdkRoot | Out-Null
foreach ($package in $packages) {
    if (Test-Path -LiteralPath (Join-Path $sdkRoot $package.Target)) {
        Write-Host "$($package.Name) is already installed"
        continue
    }
    $archive = Join-Path ([System.IO.Path]::GetTempPath()) ([System.IO.Path]::GetRandomFileName() + '.zip')
    try {
        Write-Host "Downloading $($package.Name)"
        Invoke-WebRequest -UseBasicParsing -Uri "$repository/$($package.Name)" -OutFile $archive
        $actualSha1 = (Get-FileHash -Algorithm SHA1 -LiteralPath $archive).Hash.ToLowerInvariant()
        if ($actualSha1 -ne $package.Sha1) {
            throw "Official repository checksum mismatch for $($package.Name): $actualSha1"
        }
        Expand-Archive -LiteralPath $archive -DestinationPath $sdkRoot -Force
    }
    finally {
        Remove-Item -LiteralPath $archive -Force -ErrorAction SilentlyContinue
    }
}
Write-Host 'SDK ready.'
