plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.rackexcel.mobile"
    compileSdk = 35
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "com.rackexcel.mobile"
        minSdk = 26
        targetSdk = 35
        versionCode = 42
        versionName = "2.13.4"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // 本仓库不内置任何真实模型端点、模型名与密钥。
        // 可通过环境变量（或 gradle 属性）注入自有配置；未注入时留空，
        // App 首次启动后会引导用户在“设置 → 配置中心”自行填写。
        val defaultUrl = providers.environmentVariable("MODEL_BASE_URL")
            .orElse(providers.gradleProperty("modelBaseUrl")).orElse("").get()
        val defaultModel = providers.environmentVariable("MODEL_NAME")
            .orElse(providers.gradleProperty("modelName")).orElse("").get()
        val defaultKey = providers.environmentVariable("MODEL_API_KEY")
            .orElse(providers.gradleProperty("modelApiKey")).orElse("").get()
        buildConfigField("String", "DEFAULT_MODEL_URL", "\"${defaultUrl.replace("\\", "\\\\").replace("\"", "\\\"")}\"")
        buildConfigField("String", "DEFAULT_MODEL_NAME", "\"${defaultModel.replace("\\", "\\\\").replace("\"", "\\\"")}\"")
        buildConfigField("String", "DEFAULT_API_KEY", "\"${defaultKey.replace("\\", "\\\\").replace("\"", "\\\"")}\"")

        // 备用配置同样不内置真实值。
        val fallbackUrl = providers.environmentVariable("FALLBACK_MODEL_BASE_URL")
            .orElse(providers.gradleProperty("fallbackModelBaseUrl")).orElse("").get()
        val fallbackModel = providers.environmentVariable("FALLBACK_MODEL_NAME")
            .orElse(providers.gradleProperty("fallbackModelName")).orElse("").get()
        val fallbackKey = providers.environmentVariable("FALLBACK_MODEL_API_KEY")
            .orElse(providers.gradleProperty("fallbackModelApiKey")).orElse("").get()
        buildConfigField("String", "FALLBACK_MODEL_URL", "\"${fallbackUrl.replace("\\", "\\\\").replace("\"", "\\\"")}\"")
        buildConfigField("String", "FALLBACK_MODEL_NAME", "\"${fallbackModel.replace("\\", "\\\\").replace("\"", "\\\"")}\"")
        buildConfigField("String", "FALLBACK_API_KEY", "\"${fallbackKey.replace("\\", "\\\\").replace("\"", "\\\"")}\"")
    }

    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".preview"
            versionNameSuffix = "-preview"
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.json:json:20240303")
    implementation("androidx.camera:camera-core:1.4.1")
    implementation("androidx.camera:camera-camera2:1.4.1")
    implementation("androidx.camera:camera-lifecycle:1.4.1")
    implementation("androidx.camera:camera-view:1.4.1")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test:1.9.24")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
