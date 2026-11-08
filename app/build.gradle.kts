import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
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

        versionCode = 10
        versionName = "0.4.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // About screen links. They open in the browser.
        buildConfigField("String", "REPO_URL", "\"https://github.com/nisabmohd/burrow\"")
        buildConfigField("String", "CONTACT_EMAIL", "\"hello.midapps@gmail.com\"")
        buildConfigField("String", "PLAY_STORE_URL", "\"https://play.google.com/store/apps/details?id=app.burrow.money\"")
    }

    flavorDimensions += "distribution"
    productFlavors {
        // Google Play. Reads bank SMS like the other builds; needs the Play Console SMS permissions
        // declaration (release/sms-declaration.md) before a release with it can go out.
        create("play") {
            dimension = "distribution"
            // Android Studio opens playDebug by default (as Weft). With fossDebug, src/drive and src/play
            // aren't part of the project and DriveBackup / PlayReview show as unresolved in the editor.
            isDefault = true
            buildConfigField("boolean", "PLAY_STORE", "true")
            buildConfigField("boolean", "PLAY_LISTED", "true") // live on Play (2026-10-28): "Rate on Play Store" in About
            buildConfigField("boolean", "SMS_CAPTURE", "true")
            buildConfigField("boolean", "GITHUB_UPDATES", "false") // Play updates it
            buildConfigField("boolean", "DRIVE_BACKUP", "true")
        }
        // F-Droid: no Google code at all.
        create("foss") {
            dimension = "distribution"
            versionNameSuffix = "-foss"
            buildConfigField("boolean", "PLAY_STORE", "false")
            buildConfigField("boolean", "PLAY_LISTED", "false")
            buildConfigField("boolean", "SMS_CAPTURE", "true")
            buildConfigField("boolean", "GITHUB_UPDATES", "false") // F-Droid updates it
            buildConfigField("boolean", "DRIVE_BACKUP", "false")
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
            buildConfigField("boolean", "DRIVE_BACKUP", "true")
        }
    }

    sourceSets {
        // src/play: Play's In-App Review; src/drive: Google Drive backup (play and github, as Weft).
        // foss has neither and gets the stand-ins, so it never contains Google code.
        // AGP 9 compiles Kotlin itself and reads Kotlin files from the `kotlin` source set (as Weft).
        getByName("play").kotlin.srcDir("src/drive/java")
        getByName("github").kotlin.srcDirs("src/drive/java", "src/noPlay/java")
        getByName("foss").kotlin.srcDirs("src/noDrive/java", "src/noPlay/java")
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
        debug {
            // ar-XB (right-to-left English) and en-XA for checks: tools/phone_checks.py rtl.
            isPseudoLocalesEnabled = true
        }
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
    "playImplementation"(libs.play.review)
    "playImplementation"(libs.play.services.auth)
    "githubImplementation"(libs.play.services.auth)
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

    // Component behaviour tests on the emulator (app/src/androidTest): user, 2026-10-24.
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
