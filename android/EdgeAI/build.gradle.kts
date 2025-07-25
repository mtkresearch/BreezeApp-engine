@Suppress("DSL_SCOPE_VIOLATION")
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("kotlin-parcelize")
    id("maven-publish")
}

group = "com.github.mtkresearch" // 注意這行是 JitPack 專用，保持不變
version = "EdgeAI-v0.1.2" // 你想要的版本

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                groupId = "com.github.mtkresearch"
                artifactId = "EdgeAI"
                version = "EdgeAI-v0.1.2"
            }
        }
    }
}

android {
    namespace = "com.mtkresearch.breezeapp.edgeai"
    compileSdk = 35

    defaultConfig {
        minSdk = 34
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        aidl = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    
    // 配置測試選項以支援 Java 21 + Mockito + Byte Buddy
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
            
            // 為 JVM 測試添加系統屬性
            all {
                it.systemProperty("net.bytebuddy.experimental", "true")
                it.systemProperty("mockito.mock.serialization", "true")
                it.jvmArgs("-XX:+EnableDynamicAgentLoading")
                it.maxHeapSize = "2g"
            }
        }
    }
    
    // 配置 packaging 選項避免衝突
    packaging {
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt"
            )
        }
    }
}

dependencies {
    // Kotlin Coroutines for asynchronous programming
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    
    // === Unit Testing Dependencies (JVM) ===
    testImplementation("junit:junit:4.13.2")
    
    // Mockito - 支援 Java 17 的版本
    testImplementation("org.mockito:mockito-core:5.7.0")
    testImplementation("org.mockito:mockito-inline:5.2.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.1.0")
    
    // Byte Buddy - 明確指定支援 Java 21 的版本
    testImplementation("net.bytebuddy:byte-buddy:1.14.10")
    testImplementation("net.bytebuddy:byte-buddy-agent:1.14.10")
    
    // Coroutines 測試支援
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    
    // Android 測試核心庫（用於 Robolectric）
    testImplementation("androidx.test:core:1.5.0")
    testImplementation("androidx.test.ext:junit:1.1.5")
    testImplementation("androidx.test:runner:1.5.2")
    
    // === Instrumentation Testing Dependencies (Android) ===
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.test:rules:1.5.0")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("org.mockito:mockito-android:5.7.0")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    implementation(kotlin("reflect"))
} 