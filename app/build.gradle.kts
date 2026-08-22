plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.aegis.browser"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.aegis.browser"
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "1.2.0-dual-engine"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("boolean", "ADULT_BUILD", "false")
        buildConfigField("boolean", "REQUIRE_LOGIN", "true")
        buildConfigField("boolean", "DEFAULT_ALLOW_ANY_EXTENSION", "false")
        // Set -Paegis.bundledChromium=true after packaging Content Shell natives
        buildConfigField(
            "boolean",
            "USE_BUNDLED_CHROMIUM",
            (project.findProperty("aegis.bundledChromium") == "true").toString()
        )
    }

    flavorDimensions += "audience"
    productFlavors {
        create("family") {
            dimension = "audience"
            applicationId = "com.aegis.browser"
            resValue("string", "app_name", "Aegis Browser")
            buildConfigField("boolean", "ADULT_BUILD", "false")
            buildConfigField("boolean", "REQUIRE_LOGIN", "true")
            buildConfigField("boolean", "DEFAULT_ALLOW_ANY_EXTENSION", "false")
            versionNameSuffix = "-family"
        }
        create("adult") {
            dimension = "audience"
            applicationId = "com.aegis.browser.adult"
            resValue("string", "app_name", "Aegis Open")
            buildConfigField("boolean", "ADULT_BUILD", "true")
            buildConfigField("boolean", "REQUIRE_LOGIN", "false")
            buildConfigField("boolean", "DEFAULT_ALLOW_ANY_EXTENSION", "true")
            versionNameSuffix = "-adult"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
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
        viewBinding = true
        buildConfig = true
    }

    // Chromium pak/dat must stay uncompressed when bundled
    androidResources {
        noCompress += listOf("pak", "dat", "bin")
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            excludes += setOf("META-INF/DEPENDENCIES", "META-INF/LICENSE", "META-INF/LICENSE.txt")
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.activity:activity-ktx:1.9.3")
    // Chromium-based WebView helpers (updated by engine-update workflow)
    implementation("androidx.webkit:webkit:1.12.1")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Firefox Gecko engine (updated by engine-update workflow)
    implementation("org.mozilla.geckoview:geckoview:153.0.20260810162159")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
