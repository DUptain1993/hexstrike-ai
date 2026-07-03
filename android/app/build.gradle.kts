plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// Stages proot + its two runtime deps as prebuilt Android native libs, fetched straight from
// Termux's official package repository and checksum-verified (see scripts/fetch-proot.sh for the
// full rationale: it's a drop-in replacement for cross-compiling proot with the NDK, which needs
// extra native deps — talloc, libandroid-shmem — that aren't part of the NDK sysroot).
val prootLibsDir = layout.buildDirectory.dir("generated/prootLibs")

val fetchProotBinaries by tasks.registering(Exec::class) {
    description = "Downloads prebuilt proot binaries from Termux's package repo into build/generated/prootLibs"
    val outputDir = prootLibsDir.get().asFile
    doFirst { outputDir.mkdirs() }
    commandLine("bash", "${rootDir}/scripts/fetch-proot.sh", outputDir.absolutePath)
}

tasks.named("preBuild") {
    dependsOn(fetchProotBinaries)
}

android {
    namespace = "com.hexstrike.ai"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.hexstrike.ai"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    signingConfigs {
        create("release") {
            val keystorePath = System.getenv("HEXSTRIKE_KEYSTORE_PATH")
            if (keystorePath != null) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("HEXSTRIKE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("HEXSTRIKE_KEY_ALIAS")
                keyPassword = System.getenv("HEXSTRIKE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (System.getenv("HEXSTRIKE_KEYSTORE_PATH") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        // proot (+ libtalloc/libandroid-shmem) are shipped as lib*.so under jniLibs and run as
        // real subprocesses via ProcessBuilder, not loaded with System.loadLibrary. That needs
        // an actual extracted file on disk with the execute bit set — useLegacyPackaging = true
        // is what makes PackageManager extract native libs to nativeLibraryDir at install time.
        // The default (false) stores them uncompressed/page-aligned *inside* the APK and loads
        // them via mmap without ever extracting them, which leaves nativeLibraryDir empty and
        // makes every proot path check fail on real devices despite the APK containing the libs.
        jniLibs {
            useLegacyPackaging = true
        }
    }

    sourceSets {
        getByName("main") {
            jniLibs.srcDir(prootLibsDir)
        }
    }

    lint {
        abortOnError = false
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.retrofit.core)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp.core)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.okhttp.sse)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.commons.compress)
    implementation(libs.markwon.core)

    testImplementation("junit:junit:4.13.2")
    testImplementation(libs.kotlinx.coroutines.android)
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
