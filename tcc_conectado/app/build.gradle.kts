import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

val localSettings = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val apiBaseUrl = localSettings.getProperty("API_BASE_URL", "http://10.0.2.2:8088/php_appest/")
require(apiBaseUrl.matches(Regex("https?://[a-zA-Z0-9.:-]+/[a-zA-Z0-9_/-]*"))) { "API_BASE_URL inválida" }

android {
    namespace = "com.github.carloseduardofsousa.tcc"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.github.carloseduardofsousa.tcc"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"


    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.mpandroidchart)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

// Comando opcional para quem ainda quiser usar a API local. Ele não faz parte do
// build: compilar o Android nunca deve iniciar MySQL, executar migrações ou abrir
// processos em segundo plano.
tasks.register<Exec>("prepararAmbienteLocal") {
    group = "development"
    description = "Inicia a API e o MySQL locais para o emulador Android."
    onlyIf {
        System.getProperty("os.name").startsWith("Windows")
    }
    commandLine("powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File",
        rootProject.file("../iniciar-local.ps1").absolutePath)
}
