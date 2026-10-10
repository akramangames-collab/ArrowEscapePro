plugins { id("com.android.application") }

val releaseStorePath = providers.environmentVariable("ARROW_RELEASE_STORE_FILE").orNull
val releaseStorePassword = providers.environmentVariable("ARROW_RELEASE_STORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("ARROW_RELEASE_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("ARROW_RELEASE_KEY_PASSWORD").orNull
val releaseSigningReady = listOf(
    releaseStorePath,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword
).all { !it.isNullOrBlank() }

android {
    namespace = "com.arrowescape.pro"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }
    defaultConfig {
        applicationId = "com.arrowescape.pro"
        minSdk = 24
        targetSdk = 36
        versionCode = 22
        versionName = "1.1.10"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    signingConfigs {
        if (releaseSigningReady) {
            create("release") {
                storeFile = file(releaseStorePath!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }
    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".debug"
            manifestPlaceholders["ADMOB_APP_ID"] = "ca-app-pub-3940256099942544~3347511713"
            buildConfigField("String", "ADMOB_APP_ID", "\"ca-app-pub-3940256099942544~3347511713\"")
            buildConfigField("String", "ADMOB_BANNER_ID", "\"ca-app-pub-3940256099942544/6300978111\"")
            buildConfigField("String", "ADMOB_INTERSTITIAL_ID", "\"ca-app-pub-3940256099942544/1033173712\"")
            buildConfigField("String", "ADMOB_REWARDED_ID", "\"ca-app-pub-3940256099942544/5224354917\"")
        }
        getByName("release") {
            isDebuggable = false
            // Play Console recommends R8 optimization. Current AndroidX/Google SDKs
            // include consumer keep rules; app-specific rules live in proguard-rules.pro.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
                "ads-r8-functional-rules.pro"
            )
            @Suppress("UnstableApiUsage")
            optimization.keepRules {
                // GMA 1.5.0 contains one broad rule that intentionally preserves the
                // already-obfuscated ads_mobile_sdk.* names only for vendor debugging.
                // Ignore the SDK consumer file and re-add every functional rule locally.
                ignoreFrom("com.google.android.libraries.ads.mobile.sdk:ads-mobile-sdk")
            }
            if (releaseSigningReady) signingConfig = signingConfigs.getByName("release")
            manifestPlaceholders["ADMOB_APP_ID"] = "ca-app-pub-2475015099415787~6197424406"
            buildConfigField("String", "ADMOB_APP_ID", "\"ca-app-pub-2475015099415787~6197424406\"")
            buildConfigField("String", "ADMOB_BANNER_ID", "\"ca-app-pub-2475015099415787/6590130477\"")
            buildConfigField("String", "ADMOB_INTERSTITIAL_ID", "\"ca-app-pub-2475015099415787/3782285105\"")
            buildConfigField("String", "ADMOB_REWARDED_ID", "\"ca-app-pub-2475015099415787/2469203439\"")
        }
    }
    buildFeatures { buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    packaging { resources.excludes += setOf("META-INF/DEPENDENCIES", "META-INF/LICENSE*") }
}

dependencies {
    // Keep AndroidX on current stable releases so Play SDK Index does not resolve
    // older transitive Activity/Fragment versions from advertising dependencies.
    implementation("androidx.activity:activity:1.13.0")
    implementation("androidx.core:core:1.18.0")
    // Do not force WorkManager/Room into the app. Keep only version constraints so
    // they are upgraded if a dependency actually needs them, but omitted otherwise.
    // This reduces unnecessary DEX/keep rules and improves R8 effectiveness.
    constraints {
        implementation("androidx.work:work-runtime:2.12.0") {
            because("Use current WorkManager only when transitively required")
        }
        implementation("androidx.room:room-runtime:2.8.5") {
            because("Use current Room only when transitively required")
        }
        implementation("androidx.fragment:fragment:1.9.1") {
            because("Use the current stable Fragment SDK when it is pulled transitively")
        }
    }

    implementation("com.google.android.libraries.ads.mobile.sdk:ads-mobile-sdk:1.5.0")
    // Override the SDK's older HSDP 2.0.1 transitive runtime with the current 2.1.0 release.
    implementation("com.google.android.play:hsdp:2.2.0")
    implementation("com.google.android.ump:user-messaging-platform:4.0.0")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}

// Prevent UMP or another transitive dependency from bringing the legacy GMA SDK back.
configurations.configureEach {
    exclude(group = "com.google.android.gms", module = "play-services-ads")
    exclude(group = "com.google.android.gms", module = "play-services-ads-lite")
}
