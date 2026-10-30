plugins { id("com.android.application") }
android {
    namespace = "tw.expirybook"
    compileSdk = 37
    defaultConfig {
        applicationId = "tw.expirybook"
        minSdk = 24
        targetSdk = 37
        versionCode = 12
        versionName = "5.2.0"
        testInstrumentationRunner = "tw.expirybook.StoreInstrumentation"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
dependencies {
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    testImplementation("junit:junit:4.13.2")
}
