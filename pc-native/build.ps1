<#
    build.ps1 - Menyusun aplikasi PC native My Singghara dan installer .exe-nya
    memakai kompiler C# bawaan Windows (.NET Framework), tanpa Visual Studio.

    Hasil:
      pc-native/build/MySinggharaPc.exe          -> aplikasi jendela native (WebView2)
      pc-native/build/MySingghara-Pc-Installer.exe -> installer satu berkas (payload tertanam)
      app/src/main/assets/web/pc/                -> salinan yang ikut dalam APK
#>

$ErrorActionPreference = 'Stop'

$root      = Split-Path -Parent $MyInvocation.MyCommand.Path
$libs      = Join-Path $root 'libs'
$build     = Join-Path $root 'build'
$stage     = Join-Path $build 'payload'
$repo      = Split-Path -Parent $root
$assetsDir = Join-Path $repo 'app\src\main\assets\web\pc'
$icon      = Join-Path $repo 'app\src\main\assets\web\favicon.ico'
$manifest  = Join-Path $root 'app.manifest'
$csc       = Join-Path $env:WINDIR 'Microsoft.NET\Framework64\v4.0.30319\csc.exe'
$sdkUrl    = 'https://api.nuget.org/v3-flatcontainer/microsoft.web.webview2/1.0.2903.40/microsoft.web.webview2.1.0.2903.40.nupkg'

if (-not (Test-Path $csc)) { throw "csc.exe tidak ditemukan di $csc" }
if (-not (Test-Path $icon)) { throw "Ikon aplikasi tidak ditemukan di $icon" }
if (-not (Test-Path $manifest)) { throw "Manifest DPI tidak ditemukan di $manifest" }

New-Item -ItemType Directory -Force -Path $build | Out-Null
New-Item -ItemType Directory -Force -Path $stage | Out-Null

# 1. Pustaka WebView2 (diunduh sekali, lalu ikut disimpan di repo)
if (-not (Test-Path (Join-Path $libs 'Microsoft.Web.WebView2.Core.dll'))) {
    Write-Host 'Mengunduh paket WebView2 SDK...'
    New-Item -ItemType Directory -Force -Path $libs | Out-Null
    $nupkg = Join-Path $build 'webview2.zip'
    Invoke-WebRequest -Uri $sdkUrl -OutFile $nupkg -UseBasicParsing
    $unzip = Join-Path $build 'webview2-sdk'
    if (Test-Path $unzip) { Remove-Item $unzip -Recurse -Force }
    Expand-Archive -Path $nupkg -DestinationPath $unzip -Force
    Copy-Item (Join-Path $unzip 'lib\net462\Microsoft.Web.WebView2.Core.dll') $libs -Force
    Copy-Item (Join-Path $unzip 'lib\net462\Microsoft.Web.WebView2.WinForms.dll') $libs -Force
    Copy-Item (Join-Path $unzip 'build\native\x64\WebView2Loader.dll') (Join-Path $libs 'WebView2Loader.x64.dll') -Force
    Copy-Item (Join-Path $unzip 'build\native\x86\WebView2Loader.dll') (Join-Path $libs 'WebView2Loader.x86.dll') -Force
}

$coreDll     = Join-Path $libs 'Microsoft.Web.WebView2.Core.dll'
$winFormsDll = Join-Path $libs 'Microsoft.Web.WebView2.WinForms.dll'
$loaderDll   = Join-Path $libs 'WebView2Loader.x64.dll'
foreach ($required in @($coreDll, $winFormsDll, $loaderDll)) {
    if (-not (Test-Path $required)) { throw "Pustaka hilang: $required" }
}

$fwRefs = @(
    '/r:System.dll',
    '/r:System.Core.dll',
    '/r:System.Drawing.dll',
    '/r:System.Windows.Forms.dll',
    '/r:System.IO.Compression.dll',
    '/r:System.IO.Compression.FileSystem.dll'
)

# 2. Aplikasi native
Write-Host 'Menyusun MySinggharaPc.exe...'
$appExe = Join-Path $build 'MySinggharaPc.exe'
$appArgs = @(
    '/nologo', '/target:winexe', '/platform:x64', '/optimize+', '/codepage:65001',
    "/out:$appExe", "/win32icon:$icon", "/win32manifest:$manifest",
    "/r:$coreDll", "/r:$winFormsDll"
) + $fwRefs + @("/resource:$icon,app.ico", (Join-Path $root 'MySinggharaPc.cs'))
& $csc $appArgs
if ($LASTEXITCODE -ne 0) { throw 'Kompilasi aplikasi gagal' }

# 3. Isi paket (payload) yang ditanam ke dalam installer
Copy-Item $appExe $stage -Force
Copy-Item $coreDll (Join-Path $stage 'Microsoft.Web.WebView2.Core.dll') -Force
Copy-Item $winFormsDll (Join-Path $stage 'Microsoft.Web.WebView2.WinForms.dll') -Force
Copy-Item $loaderDll (Join-Path $stage 'WebView2Loader.dll') -Force
Copy-Item $icon (Join-Path $stage 'MySingghara.ico') -Force

$payloadZip = Join-Path $build 'payload.zip'
if (Test-Path $payloadZip) { Remove-Item $payloadZip -Force }
Compress-Archive -Path (Join-Path $stage '*') -DestinationPath $payloadZip -CompressionLevel Optimal

# 4. Installer satu berkas
Write-Host 'Menyusun MySingghara-Pc-Installer.exe...'
$installerExe = Join-Path $build 'MySingghara-Pc-Installer.exe'
$installerArgs = @(
    '/nologo', '/target:winexe', '/platform:anycpu', '/optimize+', '/codepage:65001',
    "/out:$installerExe", "/win32icon:$icon"
) + $fwRefs + @(
    "/resource:$payloadZip,pcinst.payload.zip",
    "/resource:$icon,app.ico",
    (Join-Path $root 'PcInstaller.cs')
)
& $csc $installerArgs
if ($LASTEXITCODE -ne 0) { throw 'Kompilasi installer gagal' }

# 5. Salinan yang dibekukan dalam APK
New-Item -ItemType Directory -Force -Path $assetsDir | Out-Null
Copy-Item $installerExe (Join-Path $assetsDir 'MySingghara-Pc-Installer.exe') -Force

Write-Host ''
Write-Host 'Selesai:'
Write-Host ("  aplikasi  : {0} ({1:N0} byte)" -f $appExe, (Get-Item $appExe).Length)
Write-Host ("  payload   : {0} ({1:N0} byte)" -f $payloadZip, (Get-Item $payloadZip).Length)
Write-Host ("  installer : {0} ({1:N0} byte)" -f $installerExe, (Get-Item $installerExe).Length)
Write-Host ("  aset APK  : {0}" -f (Join-Path $assetsDir 'MySingghara-Pc-Installer.exe'))
