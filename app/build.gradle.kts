import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Paparazzi (screenshots) and Robolectric (overlay behaviour) cannot share a test run:
//   ./gradlew testDebugUnitTest                  -> unit + Robolectric overlay tests
//   ./gradlew recordPaparazziDebug -Pscreenshots -> screenshot images
val screenshots = hasProperty("screenshots")
if (screenshots) apply(plugin = "app.cash.paparazzi")

// Version comes from the git tag in CI: ./gradlew assembleRelease -PappVersion=1.2.3
val appVersion: String = (findProperty("appVersion") as String?)?.removePrefix("v") ?: "0.0.1"
val appVersionCode: Int = appVersion.split(".", "-").take(3)
    .map { it.toIntOrNull() ?: 0 }
    .let { (it + listOf(0, 0, 0)).take(3) }
    .let { (maj, min, patch) -> maj * 10_000 + min * 100 + patch }
    .coerceAtLeast(1)

// GitHub repository that hosts releases (APK auto-update) and the data branch.
val githubRepo = (findProperty("githubRepo") as String?) ?: "Amnyle/Super_TFT"

// Signing: env vars (CI) or keystore.properties (local). Same key = updates install over each other.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun signingValue(env: String, prop: String): String? =
    System.getenv(env)?.takeIf { it.isNotBlank() } ?: keystoreProps.getProperty(prop)

android {
    namespace = "com.supertft"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.supertft"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersion
        buildConfigField("String", "GITHUB_REPO", "\"$githubRepo\"")
        buildConfigField(
            "String", "DATA_URL",
            "\"https://raw.githubusercontent.com/$githubRepo/data/tft.json\""
        )
    }

    signingConfigs {
        create("release") {
            val storePath = signingValue("SIGNING_STORE_FILE", "storeFile")
            if (storePath != null) {
                storeFile = rootProject.file(storePath)
                storePassword = signingValue("SIGNING_STORE_PASSWORD", "storePassword")
                keyAlias = signingValue("SIGNING_KEY_ALIAS", "keyAlias")
                keyPassword = signingValue("SIGNING_KEY_PASSWORD", "keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            val release = signingConfigs.getByName("release")
            signingConfig = if (release.storeFile != null) release else signingConfigs.getByName("debug")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    sourceSets["test"].java.srcDir(if (screenshots) "src/testScreenshots/java" else "src/testRobolectric/java")
    testOptions.unitTests.isIncludeAndroidResources = true
    testOptions.unitTests.all {
        // Optional local folder holding Robolectric's android-all jars (offline runs).
        System.getenv("ROBO_DIR")?.let { d ->
            it.systemProperty("robolectric.offline", "true"); it.systemProperty("robolectric.dependency.dir", d)
        }
        // Optional: folder of downloaded icons so screenshot tests show real images.
        System.getenv("SUPERTFT_ICONS")?.let { dir -> it.systemProperty("supertft.icons", dir) }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.lifecycle.compose)
    implementation(libs.androidx.savedstate)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coil.compose)
    testImplementation(libs.junit)
    if (!screenshots) {
        testImplementation(libs.robolectric)
        testImplementation(libs.androidx.test.core)
        testImplementation(platform(libs.compose.bom))
        testImplementation(libs.compose.ui.test)
    }
}
