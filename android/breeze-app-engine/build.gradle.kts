import com.android.build.gradle.BaseExtension
import com.android.build.gradle.AppExtension
import com.android.build.gradle.LibraryExtension
import java.util.Properties
import java.io.FileInputStream

plugins {
    id("com.android.application") apply false
    id("com.android.library") apply false
    id("org.jetbrains.kotlin.android") apply false
    id("kotlin-parcelize") apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.23" apply false
    id("org.jetbrains.dokka") version "1.9.10" apply false
}

// =========================================================================================
// Dynamic Module Configuration
// =========================================================================================
val isLibraryMode = project.hasProperty("breeze.engine.isLibrary") && 
                    project.property("breeze.engine.isLibrary").toString().toBoolean()

if (isLibraryMode) {
    apply(plugin = "com.android.library")
    println(">>> BreezeApp-engine: Configuring as LIBRARY Module")
} else {
    apply(plugin = "com.android.application")
    println(">>> BreezeApp-engine: Configuring as APPLICATION Module")
}

apply(plugin = "org.jetbrains.kotlin.android")
apply(plugin = "kotlin-parcelize")
apply(plugin = "org.jetbrains.kotlin.plugin.serialization")
apply(plugin = "org.jetbrains.dokka")

// Configure Android settings using BaseExtension to avoid accessor issues
configure<BaseExtension> {
    namespace = "com.mtkresearch.breezeapp.engine"
    compileSdkVersion(35)

    signingConfigs {
        create("release") {
            val keystorePropertiesFile = rootProject.file("keystore.properties")
            if (keystorePropertiesFile.exists()) {
                val keystoreProperties = Properties()
                keystoreProperties.load(FileInputStream(keystorePropertiesFile))
                storeFile = file(keystoreProperties.getProperty("storeFile") ?: "${System.getProperty("user.home")}/Resource/android_key_mr")
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            } else {
                storeFile = file(System.getProperty("KEYSTORE_FILE") ?: "${System.getProperty("user.home")}/Resource/android_key_mr")
                storePassword = System.getenv("KEYSTORE_PASSWORD") ?: System.getProperty("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS") ?: System.getProperty("KEY_ALIAS") ?: "key0"
                keyPassword = System.getenv("KEY_PASSWORD") ?: System.getProperty("KEY_PASSWORD")
            }
        }
    }

    defaultConfig {
        // Handle applicationId only for AppExtension
        if (!isLibraryMode) {
            (this@configure as? AppExtension)?.defaultConfig?.applicationId = "com.mtkresearch.breezeapp.engine"
        }
        
        minSdkVersion(23)
        targetSdkVersion(35)
        
        versionCode = 22
        versionName = "1.10.0"
        
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        getByName("debug") {
            isMinifyEnabled = false
        }
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures.aidl = true
    buildFeatures.buildConfig = true

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    packagingOptions {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/LICENSE.md"
            excludes += "META-INF/LICENSE-notice.md"
            excludes += "META-INF/DEPENDENCIES"
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all { test ->
                System.getProperties().forEach { (k, v) ->
                    if (k.toString().startsWith("test.")) {
                        test.systemProperty(k.toString(), v)
                    }
                }
                test.testLogging {
                    events("passed", "skipped", "failed", "standardOut", "standardError")
                    showExceptions = true
                    showCauses = true
                    showStackTraces = true
                    showStandardStreams = true
                    exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
                }
            }
        }
    }

    sourceSets {
        getByName("main") {
            java.srcDirs("src/main/java")
            jniLibs.srcDirs("libs")
            
            if (isLibraryMode) {
                manifest.srcFile("src/main/AndroidManifest.xml")
                // Exclude standalone resources in library mode
                res.srcDirs("src/main/res")
            } else {
                manifest.srcFile("src/main/AndroidManifest.xml")
                manifest.srcFile("src/standalone/AndroidManifest.xml")
                // Include standalone resources in app mode
                res.srcDirs("src/main/res", "src/standalone/res")
            }
        }
    }
}

// Kotlin Options need to be configured separately or via extension
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

    dependencies {
        add("implementation", project(":EdgeAI"))
        
        // Check for local libs
        val sherpaAar = file("libs/sherpa-onnx-1.12.6.aar")
        
        // In Library Mode, we cannot use direct local .aar file dependencies.
        // We use compileOnly here to satisfy build, and the Host App (Signal) MUST include the AAR in its implementation.
        if (isLibraryMode) {
             add("compileOnly", files("libs/sherpa-onnx-1.12.6.aar"))
        } else {
            if (sherpaAar.exists()) {
                add("implementation", files("libs/sherpa-onnx-1.12.6.aar"))
            } else {
                add("compileOnly", "com.k2fsa.sherpa:onnx:1.12.6") 
            }
        }
    
        add("implementation", "org.pytorch:executorch-android:0.7.0")
    add("implementation", "com.llama.llamastack:llama-stack-client-kotlin:0.2.14")
    add("implementation", "com.squareup.okhttp3:okhttp:4.12.0")
    add("implementation", "com.squareup.okhttp3:logging-interceptor:4.12.0")
    add("implementation", "com.squareup.okio:okio:3.6.0")

    // In Library Mode, rely on Host App for UI components to avoid resource conflicts (e.g. attr/radius)
    val uiConfiguration = if (isLibraryMode) "compileOnly" else "implementation"

    add(uiConfiguration, "androidx.core:core-ktx:1.12.0")
    add(uiConfiguration, "com.google.android.material:material:1.11.0")
    add("implementation", "org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    
    add("implementation", "io.github.classgraph:classgraph:4.8.165")
    add("implementation", "org.jetbrains.kotlin:kotlin-reflect:1.9.20")
    
    add("testImplementation", "junit:junit:4.13.2")
    add("testImplementation", "io.mockk:mockk:1.13.10")
    add("testImplementation", "org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
    add("testImplementation", "androidx.test:core:1.6.1")
    add("testImplementation", "androidx.test:core-ktx:1.6.1")
    add("testImplementation", "androidx.test.ext:junit:1.2.1")
    add("testImplementation", "io.mockk:mockk:1.13.10")
    add("testImplementation", "org.mockito:mockito-core:5.2.0")
    add("testImplementation", "org.mockito:mockito-inline:4.11.0")
    add("testImplementation", "org.mockito.kotlin:mockito-kotlin:4.1.0")
    add("testImplementation", "org.robolectric:robolectric:4.14.1")
    add("testImplementation", "org.json:json:20231013")

    add("androidTestImplementation", "androidx.test:core:1.6.1")
    add("androidTestImplementation", "androidx.test:core-ktx:1.6.1")
    add("androidTestImplementation", "androidx.test.espresso:espresso-core:3.6.1")
    add("androidTestImplementation", "androidx.test.ext:junit:1.2.1")
    add("androidTestImplementation", "androidx.test:rules:1.6.1")
    add("androidTestImplementation", "io.mockk:mockk-android:1.13.10")
}

// Use string-based task access or safe configuration to avoid Dokka type resolution issues at script compilation
// Since we applied dokka with 'apply false', the task classes might be visible but let's be safe.
if (tasks.names.contains("dokkaHtml")) {
    tasks.named("dokkaHtml") {
        val dokkaTask = this as org.jetbrains.dokka.gradle.DokkaTask
        dokkaTask.outputDirectory.set(file("$projectDir/build/dokka"))
        dokkaTask.dokkaSourceSets.named("main") {
            moduleName.set("BreezeApp Engine")
        }
    }
}