import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// URL del backend de Planify para cada instalación, sin dejarla en el código. Se busca, por este orden, en
// android/planify.properties (ver planify.properties.example), en -Pplanify.baseUrl=… y en la variable de entorno
// PLANIFY_BASE_URL. La release no se compila sin ella; la debug apunta por defecto al backend local del emulador.
val planify = Properties().apply {
    rootProject.file("planify.properties").takeIf { it.isFile }?.reader()?.use { load(it) }
}
fun planifySetting(name: String, env: String): String? =
    (planify.getProperty(name) ?: providers.gradleProperty(name).orNull ?: providers.environmentVariable(env).orNull)
        ?.trim()?.takeIf { it.isNotEmpty() }?.let { if (it.endsWith("/")) it else "$it/" }

val releaseBaseUrl = planifySetting("planify.baseUrl", "PLANIFY_BASE_URL")
val debugBaseUrl = planifySetting("planify.debugBaseUrl", "PLANIFY_DEBUG_BASE_URL") ?: "http://10.0.2.2:3000/"
fun cleartext(url: String?) = (url?.startsWith("http://") == true).toString()

android {
    namespace = "com.lito.planify.dashboard"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.lito.planify.dashboard"
        minSdk = 29 // XORO CPF 10PF: Android 10
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
        // El RTC del marco no conserva la hora: cualquier fecha anterior a la compilación es falsa.
        buildConfigField("long", "BUILD_TIME", "${System.currentTimeMillis()}L")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            buildConfigField("String", "BASE_URL", "\"${releaseBaseUrl.orEmpty()}\"")
            manifestPlaceholders["cleartext"] = cleartext(releaseBaseUrl)
            signingConfig = signingConfigs.getByName("debug") // Instalación local por ADB, como la app
        }
        debug {
            buildConfigField("String", "BASE_URL", "\"$debugBaseUrl\"")
            manifestPlaceholders["cleartext"] = cleartext(debugBaseUrl)
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
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// Sin URL, la release se para antes de compilar nada (la debug y los tests no la necesitan)
val checkPlanifyBaseUrl = tasks.register("checkPlanifyBaseUrl") {
    val url = releaseBaseUrl
    doLast {
        if (url == null) throw GradleException(
            "Falta la URL del backend: copia android/planify.properties.example a android/planify.properties " +
                "y rellena planify.baseUrl (o usa -Pplanify.baseUrl=… o PLANIFY_BASE_URL)."
        )
    }
}
tasks.matching { it.name == "preReleaseBuild" }.configureEach { dependsOn(checkPlanifyBaseUrl) }

dependencies {
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("com.airbnb.android:lottie:6.7.1")

    implementation("com.squareup.retrofit2:retrofit:3.0.0")
    implementation("com.squareup.retrofit2:converter-gson:3.0.0")

    testImplementation("junit:junit:4.13.2")
}
