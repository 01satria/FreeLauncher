plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.minimallauncher"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.minimallauncher"
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    // Split APK per ABI untuk ukuran minimal
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = true
        }
    }

    signingConfigs {
        create("release") {
            // Diisi dari environment variable di GitHub Actions
            storeFile = System.getenv("KEYSTORE_PATH")?.let { file(it) }
            storePassword = System.getenv("KEYSTORE_PASSWORD") ?: ""
            keyAlias = System.getenv("KEY_ALIAS") ?: ""
            keyPassword = System.getenv("KEY_PASSWORD") ?: ""
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
        freeCompilerArgs += listOf("-opt-in=kotlin.RequiresOptIn")
    }

    buildFeatures {
        viewBinding = true
        // Matikan fitur yang tidak dipakai
        buildConfig = false
        aidl = false
        renderScript = false
        resValues = false
        shaders = false
    }

    // Packaging minimal
    packaging {
        resources {
            excludes += setOf(
                "META-INF/**.kotlin_module",
                "**.kotlin_builtins",
                "kotlin/**",
                "META-INF/NOTICE*",
                "META-INF/LICENSE*"
            )
        }
    }
}

dependencies {
    // Hanya RecyclerView, tidak ada Compose, tidak ada lifecycle overhead
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.core:core-ktx:1.12.0")
}
