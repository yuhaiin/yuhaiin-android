plugins {
    id("com.android.application")
    id("androidx.baselineprofile")
    kotlin("plugin.serialization") version "2.4.21"
    id("org.jetbrains.kotlin.plugin.compose")
}

fun gitOutput(vararg arguments: String): String? = runCatching {
    val process =
        ProcessBuilder("git", *arguments).redirectError(ProcessBuilder.Redirect.DISCARD).start()
    val output = process.inputStream.bufferedReader().use { it.readText().trim() }
    if (process.waitFor() == 0) output.takeIf { it.isNotBlank() } else null
}
    .getOrNull()

fun getCommit(): String = gitOutput("rev-parse", "--short", "HEAD") ?: "unknown"

fun getVersionName(): String =
    System.getenv("RELEASE_VERSION")?.takeIf { it.isNotBlank() }
        ?: gitOutput("describe", "--tags", "--always", "--dirty")
        ?: getCommit()

tasks.withType<org.jetbrains.kotlin.gradle.internal.KaptWithoutKotlincTask>().configureEach {
    kaptProcessJvmArgs.add("-Xmx512m")
}

android {
    namespace = "io.github.asutorufa.yuhaiin"
    compileSdk = 37
    compileOptions {
        // Flag to enable support for the new language APIs
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    defaultConfig {
        applicationId = "io.github.asutorufa.yuhaiin"
        val documentsAuthorityValue = "$applicationId.documents"

        // Now we can use ${documentsAuthority} in our Manifest
        manifestPlaceholders["documentsAuthority"] = documentsAuthorityValue
        // Now we can use BuildConfig.DOCUMENTS_AUTHORITY in our code
        buildConfigField("String", "DOCUMENTS_AUTHORITY", "\"$documentsAuthorityValue\"")
        buildConfigField("String", "GIT_COMMIT", "\"${getCommit()}\"")
        minSdk = 24
        // uses-sdk:minSdkVersion 21 cannot be smaller than version 23 declared in library
        // [androidx.compose.material3:material3-android:1.5.0-alpha04]
        targetSdk = 37

        versionCode = 184
        versionName = getVersionName()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        if (System.getenv("KEYSTORE_PATH") != null)
            create("releaseConfig") {
                storeFile = file(System.getenv("KEYSTORE_PATH"))
                keyAlias = System.getenv("KEY_ALIAS")
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
    }

    buildTypes {
        release {
            // Enables code shrinking, obfuscation, and optimization for only
            // your project's release build type.
            isMinifyEnabled = true

            // Enables resource shrinking, which is performed by the
            // Android Gradle plugin.
            isShrinkResources = true

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )

            if (System.getenv("KEYSTORE_PATH") != null)
                signingConfig = signingConfigs.getByName("releaseConfig")
        }
    }

    splits {
        abi {
            isEnable = true

            // Resets the list of ABIs that Gradle should create APKs for to none.
            reset()

            include("x86_64", "arm64-v8a")
        }
    }

    sourceSets {
        named("main") {
            java { directories.add("src/main/kotlin") }
        }
    }

    testOptions {
        unitTests.apply {
            isIncludeAndroidResources = true
        }

        unitTests.all {
            it.useJUnit()
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
        aidl = true
    }
    buildToolsVersion = "36.1.0"
}

base {
    archivesName.set("yuhaiin")
}

dependencies {
    baselineProfile(project(":benchmark"))
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    testImplementation(kotlin("test"))
    testImplementation("junit:junit:4.13.2")
    implementation("androidx.appcompat:appcompat:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation("com.google.android.material:material:1.14.0")
    implementation("fastutil:fastutil:5.0.9")
    implementation("androidx.core:core-ktx:1.19.1")

    val nav3Version = "1.2.0"
    implementation("androidx.navigation3:navigation3-runtime:${nav3Version}")
    implementation("androidx.navigation3:navigation3-ui:${nav3Version}")

    implementation(project(":yuhaiin"))

    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-text")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-navigation3:2.11.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.4.0")
    implementation("androidx.fragment:fragment-compose:1.9.1")
    implementation("androidx.compose.material:material-navigation")
    implementation("androidx.compose.material:material-icons-core")
}

// Profiles are generated explicitly on a connected API 33+ device, then reviewed into source
// control.
baselineProfile {
    automaticGenerationDuringBuild = false
    saveInSrc = true
}
