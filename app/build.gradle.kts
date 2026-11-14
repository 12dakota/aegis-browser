plugins {
    id("com.android.application")
    // AGP 9.x has built-in Kotlin — do not apply org.jetbrains.kotlin.android
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.aegis.browser"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.aegis.browser"
        minSdk = 31
        targetSdk = 37
        versionCode = 3
        versionName = "1.2.0-dual-engine"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("boolean", "ADULT_BUILD", "false")
        buildConfigField("boolean", "REQUIRE_LOGIN", "false")
        buildConfigField("boolean", "DEFAULT_ALLOW_ANY_EXTENSION", "false")
        buildConfigField("boolean", "OS_BUILD", "false")
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
            resValue("string", "app_name", "Provonlong Web Browser")
            buildConfigField("boolean", "ADULT_BUILD", "false")
            buildConfigField("boolean", "REQUIRE_LOGIN", "false")
            buildConfigField("boolean", "DEFAULT_ALLOW_ANY_EXTENSION", "false")
            buildConfigField("boolean", "OS_BUILD", "false")
            versionNameSuffix = "-family"
        }
        create("adult") {
            dimension = "audience"
            applicationId = "com.aegis.browser.adult"
            resValue("string", "app_name", "Provonlong Web Browser")
            buildConfigField("boolean", "ADULT_BUILD", "true")
            buildConfigField("boolean", "REQUIRE_LOGIN", "false")
            buildConfigField("boolean", "DEFAULT_ALLOW_ANY_EXTENSION", "true")
            buildConfigField("boolean", "OS_BUILD", "false")
            versionNameSuffix = "-adult"
        }
        create("os") {
            dimension = "audience"
            applicationId = "com.aegis.browser.os"
            resValue("string", "app_name", "Provonlong OS")
            buildConfigField("boolean", "ADULT_BUILD", "true")
            buildConfigField("boolean", "OS_BUILD", "true")
            buildConfigField("boolean", "REQUIRE_LOGIN", "false")
            buildConfigField("boolean", "DEFAULT_ALLOW_ANY_EXTENSION", "true")
            // Base for OS channel; CI stamps -PosVersionCode=<run_number> for auto-update.
            versionCode = (project.findProperty("osVersionCode") as String?)?.toIntOrNull() ?: 100
            versionNameSuffix = "-os"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
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
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
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
    implementation("com.google.android.material:material:1.14.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.activity:activity-ktx:1.9.3")
    // Chromium-based WebView helpers (updated by engine-update workflow)

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Firefox Gecko engine (updated by engine-update workflow)
    implementation("org.mozilla.geckoview:geckoview:153.0.20260810162159")
    // Compose for Servo composable
    implementation("androidx.compose.ui:ui:1.7.0")
    implementation("androidx.compose.material3:material3:1.3.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    // Servo: place servo-aarch64-android.aar in app/libs/ and uncomment:
    implementation(files("libs/servo-aarch64-android.aar"))
    implementation("org.wpewebkit.wpeview:wpeview:0.3.3")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
