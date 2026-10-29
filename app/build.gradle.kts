plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

import java.util.Properties

android {
    namespace = "com.v4ex.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.v4ex.android"
        minSdk = 26
        targetSdk = 35
        versionCode = 13
        versionName = "0.5.5"
    }
    // 签名配置按优先级读取，前一个没有就找下一个：
    //   1. 项目根目录的 keystore.properties（本地临时用，已在 .gitignore）
    //   2. ~/.gradle/gradle.properties 里的 V4EX_STORE_FILE 等（推荐，密钥放仓库外）
    //   3. 同名环境变量（CI 用）
    // 三者都没有时 release 不签名，但 debug 构建不受影响。
    val keystoreProps = Properties().apply {
        val f = rootProject.file("keystore.properties")
        if (f.exists()) f.inputStream().use { load(it) }
    }

    // 项目内文件沿用原始键名（storeFile / storePassword / …），
    // gradle.properties 和环境变量用带前缀的 V4EX_* ，避免和别的项目撞名。
    fun signingValue(canonical: String): String? {
        val legacy = keystoreProps.getProperty(canonical)
        if (!legacy.isNullOrBlank()) return legacy
        val prefixed = "V4EX_$canonical"
        val fromGradleProp = providers.gradleProperty(prefixed).orNull
        if (!fromGradleProp.isNullOrBlank()) return fromGradleProp
        return providers.environmentVariable(prefixed).orNull?.takeIf { it.isNotBlank() }
    }

    // storeFile 支持绝对路径；相对路径按「项目根目录」解析，找不到再按「本机 home」解析
    fun resolveStoreFile(raw: String): File {
        val f = File(raw)
        if (f.isAbsolute) return f
        val inProject = rootProject.file(raw)
        return if (inProject.exists()) inProject else File(System.getProperty("user.home"), raw)
    }

    val storeFileValue = signingValue("storeFile")
    val storePasswordValue = signingValue("storePassword")
    val keyAliasValue = signingValue("keyAlias")
    val keyPasswordValue = signingValue("keyPassword")
    val hasSigning = listOf(storeFileValue, storePasswordValue, keyAliasValue, keyPasswordValue)
        .all { !it.isNullOrBlank() }

    signingConfigs {
        if (hasSigning) {
            create("release") {
                storeFile = resolveStoreFile(storeFileValue!!)
                storePassword = storePasswordValue
                keyAlias = keyAliasValue
                keyPassword = keyPasswordValue
            }
        }
    }

    buildTypes {
        release {
            if (hasSigning) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

dependencies {
    implementation("com.mikepenz:multiplatform-markdown-renderer-m3:0.30.0")
    implementation("com.mikepenz:multiplatform-markdown-renderer-coil2:0.30.0")
    implementation("com.google.zxing:core:3.5.3")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
