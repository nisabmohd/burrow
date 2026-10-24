import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.baselineprofile)

}

android {
    namespace = "app.burrow.money"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.burrow.money"
        minSdk = 26
        targetSdk = 36

        versionCode = 5
        versionName = "0.2.0"
        // About screen links. They open in the browser.
        buildConfigField("String", "REPO_URL", "\"https://github.com/nisabmohd/burrow\"")
        buildConfigField("String", "PLAY_STORE_URL", "\"https://play.google.com/store/apps/details?id=app.burrow.money\"")
    }

    flavorDimensions += "distribution"
    productFlavors {
        // Google Play. Reads bank SMS like the other builds; needs the Play Console SMS permissions
        // declaration (release/sms-declaration.md) before a release with it can go out.
        create("play") {
            dimension = "distribution"
            buildConfigField("boolean", "PLAY_STORE", "true")
            buildConfigField("boolean", "PLAY_LISTED", "false") // true once live: turns on "Rate on Play Store"
            buildConfigField("boolean", "SMS_CAPTURE", "true")
            buildConfigField("boolean", "GITHUB_UPDATES", "false") // Play updates it
        }
        // F-Droid: no Google code at all.
        create("foss") {
            dimension = "distribution"
            versionNameSuffix = "-foss"
            buildConfigField("boolean", "PLAY_STORE", "false")
            buildConfigField("boolean", "PLAY_LISTED", "false")
            buildConfigField("boolean", "SMS_CAPTURE", "true")
            buildConfigField("boolean", "GITHUB_UPDATES", "false") // F-Droid updates it
        }
        // The APK on GitHub Releases. Same as foss, plus the update check (as Sprout): the only build
        // with INTERNET, used for nothing but asking GitHub for the latest release.
        create("github") {
            dimension = "distribution"
            versionNameSuffix = "-github"
            buildConfigField("boolean", "PLAY_STORE", "false")
            buildConfigField("boolean", "PLAY_LISTED", "false")
            buildConfigField("boolean", "SMS_CAPTURE", "true")
            buildConfigField("boolean", "GITHUB_UPDATES", "true")
        }
    }

    signingConfigs {
        // CI sets the env vars; locally the gitignored keystore/keystore.properties. Otherwise debug key.
        val keystore = System.getenv("BURROW_KEYSTORE_PATH")
        val local = rootProject.file("keystore/keystore.properties")
        if (keystore != null) {
            create("release") {
                storeFile = file(keystore)
                storePassword = System.getenv("BURROW_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("BURROW_KEY_ALIAS")
                keyPassword = System.getenv("BURROW_KEY_PASSWORD")
            }
        } else if (local.exists()) {
            val props = Properties().apply { local.inputStream().use { load(it) } }
            create("release") {
                storeFile = rootProject.file("keystore/" + props.getProperty("storeFile"))
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    bundle {
        // The language can be changed inside the app (More › Language), so every Play install needs
        // every language; split APKs would leave a language missing on a phone set to another. Seven languages: still small.
        language { enableSplit = false }
    }

    androidResources {
        // Only the languages the app ships (keep in step with values-* folders).
        localeFilters += listOf("en", "de", "es", "fr", "hi", "ja", "pt-rBR")
        generateLocaleConfig = true
    }

    packaging {
        resources {
            excludes += listOf(
                "META-INF/**/LICENSE.txt",
                "META-INF/*.version",
                "META-INF/{AL2.0,LGPL2.1}",
                "kotlin/**.kotlin_builtins",
                "kotlin-tooling-metadata.json",
                "DebugProbesKt.bin",
            )
        }
    }

    dependenciesInfo {
        includeInApk = false // F-Droid rejects the signed dependency block
        includeInBundle = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

baselineProfile {
    automaticGenerationDuringBuild = false // ./gradlew :app:generateBaselineProfile, then commit
    mergeIntoMain = true
    dexLayoutOptimization = true
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.profileinstaller)
    baselineProfile(project(":baselineprofile"))
    // Home-screen widgets come later (ROADMAP week 5): add glance-appwidget + glance-material3 then.

    testImplementation(libs.junit)
}
