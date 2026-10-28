plugins { id("com.android.application"); id("org.jetbrains.kotlin.android"); id("org.jetbrains.kotlin.plugin.compose") }
android {
    namespace = "app.hop.singapore"
    compileSdk = 36
    defaultConfig {
        applicationId = "app.hop.singapore"
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "0.2.0"
    }
    signingConfigs {
        create("release") {
            storeFile = providers.environmentVariable("HOP_SIGNING_STORE_FILE").orNull?.let { file(it) }
            storePassword = providers.environmentVariable("HOP_SIGNING_STORE_PASSWORD").orNull
            keyAlias = providers.environmentVariable("HOP_SIGNING_KEY_ALIAS").orNull
            keyPassword = providers.environmentVariable("HOP_SIGNING_KEY_PASSWORD").orNull
        }
    }
    buildTypes { release { isDebuggable = false; signingConfig = signingConfigs.getByName("release") } }
    buildFeatures { compose = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.11.01"))
    implementation("androidx.activity:activity-compose:1.12.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    testImplementation("junit:junit:4.13.2")
}
