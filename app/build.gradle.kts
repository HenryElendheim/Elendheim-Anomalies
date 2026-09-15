plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// The single place the app version lives. Everything else reads it from
// BuildConfig, which means the settings screen can never drift out of sync.
val appVersionName = "0.004"
val appVersionCode = 4

android {
    namespace = "com.elendheim.anomalies"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.elendheim.anomalies"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName
        vectorDrawables.useSupportLibrary = true

        ndk {
            // The map renderer ships native code for four processor families, and three
            // quarters of that is weight no phone will ever run. Keeping the two ARM
            // builds covers every real device and takes roughly thirty megabytes off
            // the download.
            abiFilters += setOf("arm64-v8a", "armeabi-v7a")
        }
    }

    signingConfigs {
        create("release") {
            // The checked-in keystore keeps every build signed with the same key,
            // which means a new APK installs as an update over the old one instead
            // of being rejected. Setting the four ELENDHEIM_KEYSTORE_* environment
            // variables overrides it with a private key when one is available.
            val envStore = System.getenv("ELENDHEIM_KEYSTORE_FILE")
            storeFile = if (envStore.isNullOrBlank()) {
                rootProject.file("keystore/elendheim.p12")
            } else {
                file(envStore)
            }
            storePassword = System.getenv("ELENDHEIM_KEYSTORE_PASSWORD") ?: "elendheim"
            keyAlias = System.getenv("ELENDHEIM_KEY_ALIAS") ?: "elendheim"
            keyPassword = System.getenv("ELENDHEIM_KEY_PASSWORD") ?: "elendheim"
            storeType = "PKCS12"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
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

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
    }

    // The published file is always named after the app with no version behind it,
    // which means each release overwrites the previous download and installs as an update.
    applicationVariants.all {
        outputs.all {
            (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl)
                .outputFileName = "Elendheim-Anomalies.apk"
        }
    }
}

ksp {
    // Room writes its schema out on every build so migrations can be diffed in review.
    arg("room.schemaLocation", "$projectDir/schemas")
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
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.documentfile)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.play.services.location)
    implementation(libs.maplibre.android)

    testImplementation(libs.junit)
}
