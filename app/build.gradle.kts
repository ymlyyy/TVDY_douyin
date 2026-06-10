plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "mulin.tvdy"
    compileSdk = 35

    defaultConfig {
        applicationId = "mulin.tvdy"
        minSdk = 19
        targetSdk = 35
        versionCode = 60
        versionName = "1.0"

    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-Xlint:deprecation")
}
dependencies {
}
