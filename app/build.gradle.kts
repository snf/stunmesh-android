import java.security.MessageDigest

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// There is exactly one core input for every variant. No upstream/stub fallback.
val corePath = providers.gradleProperty("coreAar").orNull
    ?: error("Pass -PcoreAar=/absolute/path/to/the/locally-built.aar")
val coreHash = providers.gradleProperty("coreAarSha256").orNull
    ?: error("Pass the reviewed local AAR SHA-256 as -PcoreAarSha256")
require(coreHash.matches(Regex("[0-9a-f]{64}"))) { "Invalid core SHA-256" }
val core = file(corePath)
require(core.isAbsolute && core.isFile) { "Local core AAR is missing" }
val actual = core.inputStream().use { stream ->
    val hash = MessageDigest.getInstance("SHA-256")
    val bytes = ByteArray(8192)
    while (true) { val n=stream.read(bytes); if(n<0) break; hash.update(bytes,0,n) }
    hash.digest().joinToString("") { "%02x".format(it) }
}
require(actual == coreHash) { "Local core AAR hash mismatch" }

base { archivesName = "stunmesh" }
android {
    namespace = "dev.stunmesh.android"
    compileSdk { version = release(36) { minorApiLevel = 1 } }
    buildToolsVersion = "36.1.0"
    defaultConfig {
        applicationId = "dev.stunmesh.local"
        minSdk = 28
        targetSdk = 36
        versionCode = 4
        versionName = "0.3.0-local.4"
        buildConfigField("String", "CORE_SHA256", "\"$coreHash\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
    }
    buildTypes {
        debug { applicationIdSuffix = ".debug"; versionNameSuffix = "-debug" }
        release { isDebuggable = false; optimization { enable = false } }
    }
    // Signing is a separate apksigner step. Gradle never receives signing keys.
    compileOptions { sourceCompatibility = JavaVersion.VERSION_11; targetCompatibility = JavaVersion.VERSION_11 }
    buildFeatures { compose = true; buildConfig = true }
    sourceSets { getByName("main").kotlin.srcDir("src/gobackend/kotlin") }
}
configurations.configureEach {
    resolutionStrategy { activateDependencyLocking() }
}
dependencyLocking { lockMode.set(LockMode.STRICT) }
dependencies {
    implementation(files(core))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.snakeyaml)
    testImplementation(libs.junit)
    testImplementation(libs.org.json)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}

// Exercise the same admission/storage code against the release variant too.
androidComponents { beforeVariants { it.hostTests[com.android.build.api.variant.HostTestBuilder.UNIT_TEST_TYPE]?.enable = true } }
