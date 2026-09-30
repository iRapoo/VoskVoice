import java.util.Properties

plugins {
    id("com.android.application")
}

/**
 * Release signing key, looked up in this order:
 * 1. Environment variables (used by the GitHub Actions release workflow):
 *    SIGNING_KEYSTORE_FILE, SIGNING_KEYSTORE_PASSWORD, SIGNING_KEY_ALIAS, SIGNING_KEY_PASSWORD.
 * 2. `keystore.properties` in the project root (local builds by the maintainer), with keys
 *    storeFile, storePassword, keyAlias, keyPassword. The file is git-ignored.
 * 3. Nothing found: the release APK is signed with the local debug key.
 */
data class ReleaseKey(val file: File, val storePassword: String, val alias: String, val keyPassword: String)

fun findReleaseKey(): ReleaseKey? {
    System.getenv("SIGNING_KEYSTORE_FILE")?.takeIf { it.isNotBlank() }?.let { path ->
        return ReleaseKey(
            file = file(path),
            storePassword = System.getenv("SIGNING_KEYSTORE_PASSWORD").orEmpty(),
            alias = System.getenv("SIGNING_KEY_ALIAS").orEmpty(),
            keyPassword = System.getenv("SIGNING_KEY_PASSWORD").orEmpty(),
        )
    }
    val propsFile = rootProject.file("keystore.properties")
    if (!propsFile.exists()) return null
    val props = Properties().apply { propsFile.inputStream().use(::load) }
    return ReleaseKey(
        file = rootProject.file(props.getProperty("storeFile")),
        storePassword = props.getProperty("storePassword"),
        alias = props.getProperty("keyAlias"),
        keyPassword = props.getProperty("keyPassword"),
    )
}

val releaseKey = findReleaseKey()

android {
    namespace = "xyz.quenix.voskvoice"
    compileSdk = 37

    defaultConfig {
        applicationId = "xyz.quenix.voskvoice"
        // Wear OS 3 is based on Android 11 (API 30).
        minSdk = 30
        targetSdk = 36
        // CI passes the version from the git tag (v1.2.3 -> 1.2.3); local builds use the defaults.
        versionCode = (findProperty("versionCode") as String?)?.toInt() ?: 1
        versionName = (findProperty("versionName") as String?) ?: "1.0.0"

        ndk {
            // Wear OS watches are ARM. Most of them (TicWatch Pro 3, Galaxy Watch 4/5) run a 32-bit
            // userspace; newer ones are 64-bit. Dropping x86 saves ~20 MB of libvosk.so.
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }
    }

    signingConfigs {
        if (releaseKey != null) {
            create("release") {
                storeFile = releaseKey.file
                storePassword = releaseKey.storePassword
                keyAlias = releaseKey.alias
                keyPassword = releaseKey.keyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
    }

    packaging {
        // libvosk.so is loaded by JNA from the APK's native library directory.
        jniLibs.useLegacyPackaging = true
    }
}

dependencies {
    // Offline speech recognition (Kaldi). JNA is declared explicitly as an AAR because the
    // vosk-android POM excludes its transitive dependencies.
    implementation("com.alphacephei:vosk-android:0.3.75")
    implementation("net.java.dev.jna:jna:5.18.1@aar")
    // Like WristGestures, the app itself uses only the Android framework (no AndroidX) to stay
    // small on watches with 1 GB of RAM.
    testImplementation("junit:junit:4.13.2")
}
