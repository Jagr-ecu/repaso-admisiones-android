import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
}

/*
 * Firma del APK de release. La clave (*.jks) y keystore.properties NUNCA van a git (ver .gitignore).
 * keystore.properties, en la raíz del proyecto:
 *   storeFile=C:/ruta/segura/preparate.jks
 *   storePassword=...
 *   keyAlias=preparate
 *   keyPassword=...
 * Sin ese archivo, `assembleRelease` genera un APK sin firmar, que no se puede instalar.
 */
val firma = rootProject.file("keystore.properties").takeIf { it.isFile }?.let { archivo ->
    Properties().apply { archivo.inputStream().use(::load) }
}

android {
    namespace = "com.preparate.app"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.preparate.app"
        minSdk = 29
        targetSdk = 36
        // Cada APK que se instala encima de otro necesita un versionCode mayor.
        versionCode = 1
        versionName = "0.1.0"
    }
    signingConfigs {
        if (firma != null) {
            create("release") {
                storeFile = file(firma.getProperty("storeFile"))
                storePassword = firma.getProperty("storePassword")
                keyAlias = firma.getProperty("keyAlias")
                keyPassword = firma.getProperty("keyPassword")
            }
        }
    }
    buildTypes {
        release {
            // Sin ofuscar: Firebase y kotlinx.serialization funcionan sin reglas extra, y el APK es pequeño.
            isMinifyEnabled = false
            if (firma != null) signingConfig = signingConfigs.getByName("release")
        }
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

dependencies {
    implementation(project(":dominio"))
    implementation(project(":datos"))
    implementation(project(":tutor"))
    implementation(project(":sincro"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material3.window)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
}
