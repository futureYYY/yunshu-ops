$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$project = Join-Path $root "src\YunshuReceiver\YunshuReceiver.csproj"
$output = Join-Path $root "release\win-x64"

if (-not (Get-Command dotnet -ErrorAction SilentlyContinue)) {
    throw ".NET 8 SDK 未安装，请先安装 dotnet SDK 8.x。"
}

if (Test-Path $output) {
    Remove-Item $output -Recurse -Force
}

dotnet restore $project
dotnet publish $project `
    -c Release `
    -r win-x64 `
    --self-contained true `
    -p:PublishSingleFile=true `
    -p:IncludeNativeLibrariesForSelfExtract=true `
    -p:DebugType=None `
    -o $output

$exe = Join-Path $output "云枢智维桌面接收器.exe"
if (-not (Test-Path $exe)) {
    throw "发布完成但没有找到 $exe"
}

Write-Host "发布完成：$exe" -ForegroundColor Green
