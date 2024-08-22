import java.io.ByteArrayOutputStream
import java.util.Date

plugins {
    id("com.android.application")
    kotlin("android")
    id("com.google.devtools.ksp")
    id("androidx.navigation.safeargs.kotlin")
    kotlin("plugin.serialization") version "2.0.20"
}

fun getVersionCode(): Int {
    return try {
        val stdout = ByteArrayOutputStream()
        exec {
            commandLine = listOf("git", "rev-list", "--first-parent", "--count", "main")
            standardOutput = stdout
        }
        Integer.parseInt(stdout.toString().trim())
    } catch (e: Exception) {
        5
    }
}

fun getVersionName(): String {
    return try {
        val stdout = ByteArrayOutputStream()
        exec {
            commandLine = listOf("git", "describe", "--tags", "--dirty")
            standardOutput = stdout
        }
        stdout.toString().trim()
    } catch (e: Exception) {
        (((Date().time / 1000) - 1451606400) / 10).toString()
    }
}

fun getCommit(): String {
    return try {
        val stdout = ByteArrayOutputStream()
        exec {
            commandLine = listOf("git", "rev-parse", "--short", "HEAD")
            standardOutput = stdout
        }
        "-" + stdout.toString().trim()
    } catch (e: Exception) {
        ""
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.internal.KaptWithoutKotlincTask>()
    .configureEach {
        kaptProcessJvmArgs.add("-Xmx512m")
    }

android {
    compileSdk = 34

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

        minSdk = 21
        targetSdk = 34

        versionCode = 184
        versionName = getVersionName() + getCommit()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" // 追加

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

    this.buildOutputs.all {
        val variantOutputImpl = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
        val variantName: String = variantOutputImpl.name
        variantOutputImpl.outputFileName = "yuhaiin-${variantName}.apk"
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
                "proguard-rules.pro"
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
            java.srcDir("src/main/kotlin")
        }
    }

    testOptions {
        execution = "ANDROIDX_TEST_ORCHESTRATOR"

        unitTests.apply {
            isIncludeAndroidResources = true
            all {
                it.systemProperty(
                    "robolectric.dependency.repo.url",
                    "https://maven.aliyun.com/repository/public"
                )
                it.systemProperty("robolectric.dependency.repo.id", "aliyunmaven")
            }
        }

        namespace = "io.github.asutorufa.yuhaiin"
    }

    buildFeatures {
        dataBinding = false
        viewBinding = true
        compose = false
        buildConfig = true
        aidl = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.3.1"
    }
}

dependencies {
    implementation(fileTree(mapOf("include" to listOf("*.aar", "*.jar"), "dir" to "libs")))
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.browser:browser:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")

    // room
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // nav
    val navVersion = "2.7.7"
    implementation("androidx.navigation:navigation-fragment-ktx:$navVersion")
    implementation("androidx.navigation:navigation-ui-ktx:$navVersion")

    implementation(project(":yuhaiin"))
    implementation(project(":logcatviewer"))

    /*
    //compose
    val composeVersion = "1.2.1"
    implementation("androidx.activity:activity-compose:1.6.0")
    debugImplementation("androidx.compose.ui:ui-tooling:$composeVersion")
    implementation("androidx.compose.ui:ui-tooling-preview:$composeVersion")
    // Animations
    implementation("androidx.compose.animation:animation:$composeVersion")
    // Integration with ViewModels
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.5.1")
    // Compose Material Design
    implementation("androidx.compose.material3:material3:1.0.0-alpha16")
    implementation("androidx.compose.material3:material3-window-size-class:1.0.0-alpha16")
    */

    testImplementation("androidx.test:core:1.6.1")
    testImplementation("androidx.test:runner:1.6.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.test.espresso:espresso-core:3.6.1")
    testImplementation("androidx.test.ext:junit-ktx:1.2.1")
    androidTestUtil("androidx.test:orchestrator:1.5.0")
    testImplementation("org.robolectric:robolectric:4.13")
}
