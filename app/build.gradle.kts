import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
}

// Release signing reads keystore.properties at the project root (git-ignored):
//   storeFile=release.jks
//   storePassword=...
//   keyAlias=...
//   keyPassword=...
// Without it, release builds are produced unsigned.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) keystorePropertiesFile.inputStream().use { load(it) }
}

android {
    // Kotlin package / R class namespace. Independent of applicationId, so the code keeps its package.
    namespace = "com.example.btcwidget"
    compileSdk = 36
    defaultConfig {
        // Store identity: unique on Google Play and permanent once published.
        applicationId = "io.github.phirapath21.btcwidget"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
      compose = true
      aidl = false
      buildConfig = false
      shaders = false
    }

    packaging {
      resources {
        excludes += "/META-INF/{AL2.0,LGPL2.1}"
      }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Background refresh
  implementation(libs.androidx.work.runtime.ktx)

  // Drag-and-drop reordering in lazy grids (dashboard metrics)
  implementation(libs.reorderable)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  implementation("androidx.compose.material:material-icons-core")
  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)
  // Instrumented tests
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Local tests: jUnit, coroutines, Android runner
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  // Real org.json for parser tests (android.jar only has stubs)
  testImplementation("org.json:json:20240303")
  // Real SQLite on the JVM, to test the local event database's SQL
  testImplementation("org.xerial:sqlite-jdbc:3.46.1.3")

  // Instrumented tests: jUnit rules and runners
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)

  // Nostr: relays over WebSocket, BIP-340 signatures, images (avatars, note pictures)
  implementation(libs.okhttp)
  implementation(libs.secp256k1.kmp)
  implementation(libs.secp256k1.kmp.jni.android)
  testImplementation(libs.secp256k1.kmp.jni.jvm)
  implementation(libs.coil.compose)
  implementation(libs.coil.network.okhttp)
  // Animated GIFs in notes, and video playback
  implementation(libs.coil.gif)
  implementation(libs.media3.exoplayer)
  implementation(libs.media3.ui)
  // Translating notes on the device (models download once from Google)
  implementation(libs.mlkit.translate)
  implementation(libs.mlkit.language.id)

  // Navigation
  implementation(libs.androidx.navigation3.ui)
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.androidx.lifecycle.viewmodel.navigation3)
}
