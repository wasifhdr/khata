import java.util.Properties

// Drive authorises by package name plus signing certificate, so both build types
// must be signed by the key registered in the Cloud Console. Absent on a machine
// without the keystore, in which case the build still works and only Drive does
// not -- better than a build that cannot run at all.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.ksp)
  alias(libs.plugins.hilt)
  alias(libs.plugins.room)
}

android {
    namespace = "com.wasif.khata"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.wasif.khata"
        minSdk = 33
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    // MigrationTestHelper reads the exported schema from the variant's merged assets.
    // Robolectric runs against debug, so putting them here reaches the migration
    // tests while keeping release APKs free of schema JSON.
    sourceSets {
        getByName("debug").assets.srcDir("$projectDir/schemas")
    }

    signingConfigs {
        create("khata") {
            if (keystoreProperties.isNotEmpty()) {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        getByName("debug") {
            if (keystoreProperties.isNotEmpty()) signingConfig = signingConfigs.getByName("khata")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (keystoreProperties.isNotEmpty()) signingConfig = signingConfigs.getByName("khata")
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

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)

  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.haze)
  implementation(libs.androidx.datastore.preferences)
  // The account and an hour-long Drive token. Deliberately NOT
  // google-api-services-drive: Drive itself is four HttpURLConnection calls,
  // and the client library would be the largest thing in this app.
  implementation(libs.play.services.auth)
  implementation(libs.androidx.core.splashscreen)
  debugImplementation(libs.androidx.compose.ui.tooling)
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)

  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  // Not referenced directly; ui-test-junit4 and the runner resolve through it.
  androidTestImplementation(libs.androidx.test.espresso.core)

  implementation(libs.androidx.navigation.compose)

  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.paging)
  ksp(libs.androidx.room.compiler)

  implementation(libs.hilt.android)
  ksp(libs.hilt.compiler)
  implementation(libs.androidx.hilt.navigation.compose)

  implementation(libs.androidx.work.runtime)
  implementation(libs.androidx.hilt.work)
  // A separate processor from Dagger's hilt-android-compiler above; @HiltWorker
  // needs this one and neither replaces the other.
  ksp(libs.androidx.hilt.compiler)
  testImplementation(libs.androidx.work.testing)

  implementation(libs.androidx.paging.runtime)
  implementation(libs.androidx.paging.compose)

  testImplementation(libs.robolectric)
  testImplementation(libs.androidx.test.core)
  testImplementation(libs.androidx.room.testing)
  testImplementation(libs.androidx.paging.testing)
  testImplementation(libs.turbine)
}
