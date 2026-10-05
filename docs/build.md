# 构建说明 / Build

## Android App

环境要求：JDK 17+（推荐 21）、Android SDK 35、Gradle 8.10.2（使用仓库内 wrapper 即可）。

```bash
cd android
export ANDROID_SDK_ROOT=/path/to/android-sdk   # 或写入 android/local.properties 的 sdk.dir

# 可选：构建期注入自有模型配置（不注入则留空，由用户在 App 内填写）
export MODEL_BASE_URL="https://your-endpoint.example.com/v1"
export MODEL_NAME="your-model-id"
export MODEL_API_KEY="your-key"

./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug      # 产物：android/app/build/outputs/apk/debug/
./gradlew :app:assembleRelease    # Release 需自备签名配置
```

> `local.properties`、`*.jks`、`*.pass` 均已在 `.gitignore` 中，请勿提交。

## Windows 桌面接收器

环境要求：.NET 8 SDK（Windows）。

```powershell
cd desktop-receiver
dotnet restore .\src\YunshuReceiver\YunshuReceiver.csproj
dotnet build   .\src\YunshuReceiver\YunshuReceiver.csproj -c Release
dotnet test    .\tests\YunshuReceiver.Tests\YunshuReceiver.Tests.csproj
```

自包含单文件发布（win-x64）：

```powershell
dotnet publish .\src\YunshuReceiver\YunshuReceiver.csproj `
  -c Release -r win-x64 --self-contained true `
  -p:PublishSingleFile=true -o .\release
```

> WPF 窗口与 Windows 防火墙行为必须在真实 Windows 上验证；WSL 只能做编译检查。

## 发布前检查 / Pre-release gate

```bash
bash tools/scan-secrets.sh
```

命中任何密钥/端点规则即退出非零，**不得发布**。
