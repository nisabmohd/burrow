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

        versionCode = 2
        versionName = "0.1.0"
        // About screen links. They open in the browser; the app itself never goes online.
        buildConfigField("String", "REPO_URL", "\"https://github.com/nisabmohd/burrow\"")
        buildConfigField("String", "PLAY_STORE_URL", "\"https://play.google.com/store/apps/details?id=app.burrow.money\"")
    }

    flavorDimensions += "distribution"
    productFlavors {
        // Google Play. SMS capture ships here only after the Play SMS declaration is approved;
        // until then SMS_CAPTURE is false and the app uses notification capture only.
        create("play") {
            dimension = "distribution"
            buildConfigField("boolean", "PLAY_STORE", "true")
            buildConfigField("boolean", "PLAY_LISTED", "false") // true once live: turns on "Rate on Play Store"
            buildConfigField("boolean", "SMS_CAPTURE", "false")
        }
        // F-Droid: no Google code at all.
        create("foss") {
            dimension = "distribution"
            versionNameSuffix = "-foss"
            buildConfigField("boolean", "PLAY_STORE", "false")
            buildConfigField("boolean", "PLAY_LISTED", "false")
            buildConfigField("boolean", "SMS_CAPTURE", "true")
        }
        // The APK on GitHub Releases. Same as foss; there is no update checker (no internet).
        create("github") {
            dimension = "distribution"
            versionNameSuffix = "-github"
            buildConfigField("boolean", "PLAY_STORE", "false")
            buildConfigField("boolean", "PLAY_LISTED", "false")
            buildConfigField("boolean", "SMS_CAPTURE", "true")
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
        // every language; split APKs would leave Hindi missing on an English phone. Two languages: tiny.
        language { enableSplit = false }
    }

    androidResources {
        // Only the languages the app ships (keep in step with values-* folders).
        localeFilters += listOf("en", "hi")
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
