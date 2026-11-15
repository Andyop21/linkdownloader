import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Release signing reads keystore.properties at the repository root (ignored by git). The keystore
// itself lives outside the repository. Without a usable file, release builds stop with this message
// instead of producing an unsigned APK; debug builds are not affected.
val keystorePropertiesFile: File = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.isFile) keystorePropertiesFile.inputStream().use { load(it) }
}
val signingKeys = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
val releaseStoreFile: File? = keystoreProperties.getProperty("storeFile")
    ?.takeIf { it.isNotBlank() }
    ?.let { rootProject.file(it) }
val releaseSigningProblem: String? = when {
    !keystorePropertiesFile.isFile ->
        "keystore.properties is missing from the repository root. Create it with storeFile, " +
            "storePassword, keyAlias and keyPassword (docs/development.md § Release builds)."
    signingKeys.any { keystoreProperties.getProperty(it).isNullOrBlank() } ->
        "keystore.properties lacks " +
            signingKeys.filter { keystoreProperties.getProperty(it).isNullOrBlank() }.joinToString() +
            " (docs/development.md § Release builds)."
    releaseStoreFile?.isFile != true ->
        "The keystore named by storeFile in keystore.properties does not exist " +
            "(docs/development.md § Release builds)."
    else -> null
}

android {
    namespace = "io.github.andyop21.linkdownloader"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "io.github.andyop21.linkdownloader"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        // versionCode = major * 10000 + minor * 100 + patch (docs/development.md § Versions).
        versionCode = 100
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseSigningProblem == null) {
            create("release") {
                storeFile = releaseStoreFile
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            ndk {
                abiFilters += listOf("arm64-v8a", "x86_64")
            }
        }
        release {
            if (releaseSigningProblem == null) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            ndk {
                // Release targets phones only (docs/architecture.md § Android targets), not ChromeOS.
                //noinspection ChromeOsAbiSupport
                abiFilters += listOf("arm64-v8a")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        jniLibs {
            // youtubedl-android runs Python and QuickJS as executables from nativeLibraryDir,
            // so native libraries must be extracted at install time.
            useLegacyPackaging = true
        }
    }

    lint {
        abortOnError = true
        warningsAsErrors = true
        // Dependency-freshness checks only; every other warning fails the build.
        disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
    }
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        allWarningsAsErrors = true
    }
}

val checkReleaseSigning = tasks.register("checkReleaseSigning") {
    description = "Stops release builds when keystore.properties is missing or incomplete."
    val problem = releaseSigningProblem
    doFirst {
        if (problem != null) throw GradleException("Release signing: $problem")
    }
}

tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    dependsOn(checkReleaseSigning)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.work.runtime)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.youtubedl.android)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
