@Suppress("DSL_SCOPE_VIOLATION")
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("kotlin-parcelize")
    id("maven-publish")
    id("jacoco")
    id("org.jetbrains.dokka") version "1.9.10"
}

group = "com.github.mtkresearch" // 注意這行是 JitPack 專用，保持不變
version = "EdgeAI-v0.2.0" // 你想要的版本

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                groupId = "com.github.mtkresearch"
                artifactId = "EdgeAI"
                version = "EdgeAI-v0.2.0"
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
    
    // MockK - Modern Kotlin-first mocking library (preferred over Mockito for Kotlin)
    testImplementation("io.mockk:mockk:1.13.8")
    testImplementation("io.mockk:mockk-android:1.13.8")
    
    // Mockito - 支援 Java 17 的版本 (kept for compatibility)
    testImplementation("org.mockito:mockito-core:5.7.0")
    testImplementation("org.mockito:mockito-inline:5.2.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.1.0")
    
    // Byte Buddy - 明確指定支援 Java 21 的版本
    testImplementation("net.bytebuddy:byte-buddy:1.14.10")
    testImplementation("net.bytebuddy:byte-buddy-agent:1.14.10")
    
    // Coroutines 測試支援
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    
    // Turbine - Flow testing library for easier Flow assertions
    testImplementation("app.cash.turbine:turbine:1.0.0")
    
    // Android 測試核心庫（用於 Robolectric）
    testImplementation("androidx.test:core:1.5.0")
    testImplementation("androidx.test.ext:junit:1.1.5")
    testImplementation("androidx.test:runner:1.5.2")
    
    // Robolectric - Android framework testing without emulator
    testImplementation("org.robolectric:robolectric:4.11.1")
    
    // === Instrumentation Testing Dependencies (Android) ===
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.test:rules:1.5.0")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("io.mockk:mockk-android:1.13.8")
    androidTestImplementation("org.mockito:mockito-android:5.7.0")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    androidTestImplementation("app.cash.turbine:turbine:1.0.0")
    implementation(kotlin("reflect"))
}

// Apply JaCoCo coverage configuration
apply(from = "jacoco.gradle")

// Dokka configuration for API documentation
tasks.named<org.jetbrains.dokka.gradle.DokkaTask>("dokkaHtml") {
    outputDirectory.set(file("$buildDir/dokka"))
    
    dokkaSourceSets {
        named("main") {
            moduleName.set("EdgeAI SDK")
            includes.from("README.md")
            
            sourceLink {
                localDirectory.set(file("src/main/java"))
                remoteUrl.set(uri("https://github.com/mtkresearch/BreezeApp-engine/tree/main/android/EdgeAI/src/main/java").toURL())
                remoteLineSuffix.set("#L")
            }
        }
    }
} 