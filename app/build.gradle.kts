plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.ebike.dash"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ebike.dash"
        minSdk = 24
        targetSdk = 35
        versionCode = 15
        versionName = "0.9.0"
        // 只打包手机常用的 ARM 架构，安装包更小
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // 正式发布前请换成自己的签名证书
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { viewBinding = true }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            // 可用 -ProbolectricRepo=<镜像地址> 指定 Robolectric 运行时的下载源
            providers.gradleProperty("robolectricRepo").orNull?.let { url ->
                it.systemProperty("robolectric.dependency.repo.url", url)
            }
        }
    }
}

dependencies {
    implementation("org.maplibre.gl:android-sdk:11.13.5")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core:1.6.1")
}
