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
    compileSdk = 36
    defaultConfig {
        applicationId = "com.arrowescape.pro"
        minSdk = 24
        targetSdk = 36
        versionCode = 3
        versionName = "1.0.2"
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
            buildConfigField("String", "ADMOB_BANNER_ID", "\"ca-app-pub-3940256099942544/6300978111\"")
            buildConfigField("String", "ADMOB_INTERSTITIAL_ID", "\"ca-app-pub-3940256099942544/1033173712\"")
            buildConfigField("String", "ADMOB_REWARDED_ID", "\"ca-app-pub-3940256099942544/5224354917\"")
        }
        getByName("release") {
            isDebuggable = false
            if (releaseSigningReady) signingConfig = signingConfigs.getByName("release")
            manifestPlaceholders["ADMOB_APP_ID"] = "ca-app-pub-2475015099415787~6197424406"
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
    implementation("com.google.android.gms:play-services-ads:25.4.0")
    implementation("com.google.android.ump:user-messaging-platform:3.2.0")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
