import java.util.Properties

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(localPropertiesFile.inputStream())
}

fun getSecret(key: String): String? {
    return localProperties.getProperty(key) ?: System.getenv(key)
}

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    signingConfigs {
        create("release") {
            storeFile = file("release.jks")
            keyAlias = getSecret("keyAlias")
            storePassword = getSecret("storePassword")
            keyPassword = getSecret("keyPassword")
        }
    }
    namespace = "io.github.auag0.pokegopip"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.auag0.pokegopip"
        minSdk = 26
        targetSdk = 36
        versionCode = 9
        versionName = "2.0.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Use the original key only if its passwords are available; otherwise a local key
            signingConfig = if (getSecret("storePassword") != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    packaging {
        resources {
            excludes.add("**/kotlin/**")
            excludes.add("kotlin-tooling-metadata.json")
            // Archivos que declaran el módulo moderno (java_init.list, module.prop, scope.list)
            merges.add("META-INF/xposed/*")
        }
    }
    androidResources {
        additionalParameters.addAll(listOf("--package-id", "0x81", "--allow-reserved-package-id"))
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8)
    }
}

dependencies {
    compileOnly("io.github.libxposed:api:101.0.0")
}
