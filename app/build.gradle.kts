plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.raikaai.app"
    // compileSdk 37: required by the Compose 1.12.0-beta01 / material3
    // 1.5.0-alpha25 / material-kolor 5.0.0 artifacts' AAR metadata (AGP 9
    // enforces it). Google has not published the android-37 platform
    // publicly, so CI provides android-37 as android-36's APIs (see the
    // android-37 shim in .github/workflows/build-apk.yml). The app itself
    // uses no API 37+ calls; targetSdk stays 36.
    compileSdk = 37

    // -PabiFilter=arm64-v8a (or comma-separated "arm64-v8a,armeabi-v7a")
    // Without flag -> all ABIs + universal
    val abiFilter =
        (project.findProperty("abiFilter") as String?)?.takeIf {
            it.isNotBlank() && it != "all"
        }
    val targetAbis = abiFilter?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }

    defaultConfig {
        applicationId = "com.raikaai.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 5
        versionName = "0.1.4"
    }

    splits {
        abi {
            isEnable = true
            reset()
            if (targetAbis != null) {
                targetAbis.forEach { include(it) }
                isUniversalApk = false
            } else {
                include("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
                isUniversalApk = true
            }
        }
    }

    signingConfigs {
        create("release") {
            storeFile = file("release.keystore")
            enableV1Signing = true
            enableV2Signing = true
            // Passwords are intentionally NOT hardcoded: read from environment first,
            // then gradle.properties / local.properties (never commit those files).
            // Falls back to the pre-rebrand LITECHAT_* / litechat.* names so the
            // existing release.keystore (alias "localgpt") keeps working.
            val ksStorePw = System.getenv("RAIKAAI_KEYSTORE_STORE_PASSWORD")?.takeIf { it.isNotBlank() }
                ?: System.getenv("LITECHAT_KEYSTORE_STORE_PASSWORD")?.takeIf { it.isNotBlank() }
                ?: (project.findProperty("raikaai.keystore.storePassword") as String?)?.takeIf { it.isNotBlank() }
                ?: (project.findProperty("litechat.keystore.storePassword") as String?)?.takeIf { it.isNotBlank() }
                ?: "localgpt123"
            val ksKeyPw = System.getenv("RAIKAAI_KEYSTORE_KEY_PASSWORD")?.takeIf { it.isNotBlank() }
                ?: System.getenv("LITECHAT_KEYSTORE_KEY_PASSWORD")?.takeIf { it.isNotBlank() }
                ?: (project.findProperty("raikaai.keystore.keyPassword") as String?)?.takeIf { it.isNotBlank() }
                ?: (project.findProperty("litechat.keystore.keyPassword") as String?)?.takeIf { it.isNotBlank() }
                ?: "localgpt123"
            if (ksStorePw.isNullOrEmpty() || ksKeyPw.isNullOrEmpty()) {
                logger.warn(
                    "Keystore passwords not set (RAIKAAI_KEYSTORE_STORE_PASSWORD / " +
                        "RAIKAAI_KEYSTORE_KEY_PASSWORD env or raikaai.keystore.* gradle properties). " +
                        "Release signing will fail until they are provided."
                )
            }
            storePassword = ksStorePw
            keyAlias = System.getenv("RAIKAAI_KEYSTORE_KEY_ALIAS")
                ?: System.getenv("LITECHAT_KEYSTORE_KEY_ALIAS")
                ?: (project.findProperty("raikaai.keystore.keyAlias") as String?)
                ?: (project.findProperty("litechat.keystore.keyAlias") as String?)
                ?: "localgpt"
            keyPassword = ksKeyPw
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // AGP 9.0 built-in Kotlin
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    packaging {
        // Extract .so to nativeLibraryDir at install so the bundled proot
        // engine can be executed (exec from inside the APK is impossible).
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            excludes +=
                setOf(
                    "META-INF/NOTICE.md",
                    "META-INF/LICENSE.md",
                    "META-INF/DEPENDENCIES",
                    "META-INF/INDEX.LIST",
                    "META-INF/io.netty.versions.properties",
                )
        }
    }
}

dependencies {
    // Compose BOM (androidx) — material3 pinned like kzkt (M3 Expressive line)
    val composeBom = platform("androidx.compose:compose-bom:2026.01.01")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3:1.5.0-alpha25")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("io.coil-kt.coil3:coil-compose:3.4.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.4.0")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Theme: MaterialKolor (seed-color dynamic theming)
    implementation("com.materialkolor:material-kolor:5.0.0")

    // Activity + Navigation
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.navigation:navigation-compose:2.8.7")

    // Lifecycle + ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // DataStore (settings)
    implementation("androidx.datastore:datastore-preferences:1.1.2")

    // WorkManager (pengingat terjadwal)
    implementation("androidx.work:work-runtime-ktx:2.10.1")

    // EncryptedSharedPreferences (sensitive tokens: API keys, HF token, server auth)
    implementation("androidx.security:security-crypto:1.0.0")

    // JSON + HTTP client (model downloads from HuggingFace)
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Embedded OpenAI-compatible HTTP server
    implementation("io.ktor:ktor-server-core:3.5.2")
    implementation("io.ktor:ktor-server-cio:3.5.2")

    // Google AI Edge LiteRT-LM (on-device LLM runtime)
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.16.0")

    // Core Android
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.documentfile:documentfile:1.0.1")

    // Tests
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
