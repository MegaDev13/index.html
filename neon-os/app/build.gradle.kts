plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    buildFeatures { buildConfig = true }
    namespace = "dev.neonos"
    compileSdk = 35
    defaultConfig {
        applicationId = "dev.neonos.poco"
        minSdk = 33
        targetSdk = 35
        versionCode = 10200
        versionName = "0.2.0"
        testInstrumentationRunner = "dev.neonos.SmokeInstrumentation"
    }
    buildTypes {
        release { isMinifyEnabled = false; isDebuggable = false }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    lint { abortOnError = true; disable += "OldTargetApi" }
}
dependencies { testImplementation("junit:junit:4.13.2") }
