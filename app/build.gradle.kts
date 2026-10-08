import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.sentry.android)
}

/** Machine-specific settings, never committed: SDK path, release signing, Sentry upload token. */
val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
}

/**
 * Release signing: `local.properties` (never committed) may point at a keystore.properties file kept
 * outside the repository, with storeFile, storePassword, keyAlias and keyPassword. Without it,
 * release builds are produced unsigned.
 */
val releaseSigning: Properties? = run {
    val path = localProperties.getProperty("taverntales.signing") ?: return@run null
    val file = file(path).takeIf { it.exists() } ?: return@run null
    Properties().apply { file.inputStream().use(::load) }
}

/**
 * Where release builds send crash reports (Sentry, EU region). Not a secret: it ships inside the app
 * and only allows sending events. Debug builds get none, so they never report.
 */
val sentryDsn = "https://3ac3c56d09f9c4e9b1be5c3e10bc8627@o4512220490366976.ingest.de.sentry.io/4512220511600725"
val sentryAuthToken: String? = localProperties.getProperty("sentry.auth.token")
    ?.trim()?.removeSurrounding("\"")?.takeIf { it.isNotBlank() }

android {
    namespace = "dev.tevv.taverntales"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.tevv.taverntales"
        minSdk = 26
        targetSdk = 36
        versionCode = 13
        versionName = "0.10.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["sentryDsn"] = ""
    }

    signingConfigs {
        if (releaseSigning != null) {
            create("release") {
                storeFile = file(releaseSigning.getProperty("storeFile"))
                storePassword = releaseSigning.getProperty("storePassword")
                keyAlias = releaseSigning.getProperty("keyAlias")
                keyPassword = releaseSigning.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
            manifestPlaceholders["sentryDsn"] = sentryDsn
        }
        // The release build (shrunk, signed) as dev.tevv.taverntales.releasetest: checks a release on a
        // phone next to the real app without touching the real app's data. Own name and icon badge.
        create("releaseTest") {
            initWith(getByName("release"))
            applicationIdSuffix = ".releasetest"
            versionNameSuffix = "-test"
            matchingFallbacks += "release"
        }
        debug {
            // Development builds install next to the real app instead of replacing it.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
}

sentry {
    org.set("vetle-olavesen")
    projectName.set("tavern-tales")
    // Uploads the R8 mapping for release builds so crash reports show real class and method names;
    // needs `sentry.auth.token` in local.properties, and is skipped without it.
    sentryAuthToken?.let { authToken.set(it) }
    includeProguardMapping.set(true)
    autoUploadProguardMapping.set(sentryAuthToken != null)
    ignoredBuildTypes.set(setOf("debug"))
    // Only crash reporting: the SDK is added by hand (core, no NDK), and no bytecode instrumentation,
    // source uploads or plugin telemetry.
    autoInstallation { enabled.set(false) }
    tracingInstrumentation { enabled.set(false) }
    includeSourceContext.set(false)
    telemetry.set(false)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coil.compose)
    implementation(libs.okhttp)
    implementation(libs.sentry.android.core)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest) // the empty activity Compose UI tests run in

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
