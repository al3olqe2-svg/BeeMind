plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

val signingNames = listOf("BEEMIND_KEYSTORE_PATH", "BEEMIND_STORE_PASSWORD", "BEEMIND_KEY_ALIAS", "BEEMIND_KEY_PASSWORD")
val signingValues = signingNames.associateWith { providers.environmentVariable(it).orNull }
val hasSigning = signingValues.values.all { !it.isNullOrBlank() }
require(signingValues.values.none { !it.isNullOrBlank() } || hasSigning) {
    "Provide all four BeeMind signing variables or none."
}
val buildNumber = providers.environmentVariable("BEEMIND_VERSION_CODE").orElse("1").get().toInt()
require(buildNumber in 1..2100000000) { "Invalid Android versionCode" }

android {
    namespace = "com.beemind.personal"
    compileSdk = 35
    buildToolsVersion = "35.0.0"
    defaultConfig {
        applicationId = "com.beemind.personal"
        minSdk = 26
        targetSdk = 35
        versionCode = buildNumber
        versionName = "0.1.0"
    }
    signingConfigs {
        if (hasSigning) create("persistentTest") {
            storeFile = file(signingValues.getValue("BEEMIND_KEYSTORE_PATH")!!)
            storePassword = signingValues.getValue("BEEMIND_STORE_PASSWORD")
            keyAlias = signingValues.getValue("BEEMIND_KEY_ALIAS")
            keyPassword = signingValues.getValue("BEEMIND_KEY_PASSWORD")
        }
    }
    buildTypes {
        debug {
            if (hasSigning) signingConfig = signingConfigs.getByName("persistentTest")
        }
        release {
            isMinifyEnabled = false
            if (hasSigning) signingConfig = signingConfigs.getByName("persistentTest")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
    lint { abortOnError = true; warningsAsErrors = true }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
ksp { arg("room.schemaLocation", "$projectDir/schemas") }

dependencies {
    implementation(libs.androidx.core)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.lifecycle)
    implementation(libs.androidx.navigation)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.preview)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.tooling)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    testImplementation(libs.junit)
}
