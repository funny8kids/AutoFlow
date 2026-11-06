plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.autoflow.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.autoflow.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 51
        versionName = "2.27.0"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        create("autoflow") {
            storeFile = rootProject.file("keystore/autoflow.jks")
            storePassword = "autoflow123"
            keyAlias = "autoflow"
            keyPassword = "autoflow123"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("autoflow")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("autoflow")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
            all {
                if (System.getenv("CI") == null) {
                    it.systemProperty("robolectric.dependency.repo.id", "aliyun")
                    it.systemProperty(
                        "robolectric.dependency.repo.url",
                        "https://maven.aliyun.com/repository/public",
                    )
                }
                // 单测环境关闭无限动画 / 无限轮询（见 ui/AnimationPolicy.kt），
                // 否则 Compose 的 waitForIdle() 永不返回，CI 会挂十几到几十分钟。
                it.systemProperty("autoflow.disableInfiniteAnimations", "true")
                // 打印测试开始 / 通过 / 跳过 / 失败与标准流，便于定位挂住的测试
                it.testLogging.apply {
                    events("started", "passed", "skipped", "failed")
                    showStandardStreams = true
                }
                it.maxHeapSize = "2048m"
            }
        }
    }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("androidx.test.ext:junit:1.2.1")
    testImplementation(composeBom)
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}