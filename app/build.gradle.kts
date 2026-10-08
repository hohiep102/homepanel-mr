import com.android.build.api.variant.BuildConfigField
import com.android.build.api.variant.ResValue

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.meta.spatial.plugin")
}
android {
    namespace = "vn.homepanel"
    compileSdk = 36
    defaultConfig {
        applicationId = "vn.homepanel.mr"
        minSdk = 34
        targetSdk = 34
        versionCode = 15
        versionName = "1.0.2"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk { abiFilters += "arm64-v8a" }
    }
    bundle { language { enableSplit = false } }
    for ((edition, prefix) in mapOf("store" to "HOMEPANEL_RELEASE", "community" to "HOMEPANEL_COMMUNITY")) {
        val keyFile = providers.environmentVariable("${prefix}_STORE").orNull
        if (keyFile != null) {
            signingConfigs.create(edition) {
                storeFile = file(keyFile)
                storePassword = providers.environmentVariable("${prefix}_PASSWORD").get()
                keyAlias = providers.environmentVariable("${prefix}_ALIAS").get()
                keyPassword = providers.environmentVariable("${prefix}_KEY_PASSWORD").get()
            }
        }
    }
    flavorDimensions += "distribution"
    productFlavors {
        create("community") {
            dimension = "distribution"
            applicationIdSuffix = ".community"
            signingConfig = signingConfigs.findByName("community")
        }
        create("store") {
            dimension = "distribution"
            applicationIdSuffix = ".store"
            buildConfigField("String", "META_APP_ID", "\"2307890349946049\"")
            signingConfig = signingConfigs.findByName("store")
        }
    }
    buildTypes {
        getByName("debug") { applicationIdSuffix = ".debug" }
        getByName("release") {
            isDebuggable = false
        }
    }
    testBuildType = providers.gradleProperty("testBuildType").orElse("debug").get()
    buildFeatures { compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    packaging { resources.excludes += setOf("META-INF/LICENSE*", "META-INF/NOTICE*") }
}
androidComponents.onVariants { variant ->
    val edition = variant.productFlavors.single { it.first == "distribution" }.second
    val debug = variant.buildType == "debug"
    val scheme = "homepanelmr-$edition" + if (debug) "-debug" else ""
    val label = "HomePanel MR" + if (edition == "community") " Community" else ""
    requireNotNull(variant.buildConfigFields).put("AUTH_RETURN_SCHEME", BuildConfigField("String", "\"$scheme\"", "Unique callback per installed edition."))
    variant.manifestPlaceholders.put("homepanelReturnScheme", scheme)
    variant.resValues.put(variant.makeResValueKey("string", "app_name"), ResValue(label + if (debug) " Debug" else "", "Distribution label"))
}
spatial { allowUsageDataCollection.set(false) }
for (edition in listOf("community", "store")) {
    tasks.register("${edition}ReleaseLicenseInventory") {
        doLast {
            val output = layout.buildDirectory.file("reports/$edition-release-license-inventory.tsv").get().asFile
            output.parentFile.mkdirs()
            output.writeText(configurations.getByName("${edition}ReleaseRuntimeClasspath").resolvedConfiguration.resolvedArtifacts
                .sortedBy { it.moduleVersion.id.toString() }
                .joinToString("\n") { "${it.moduleVersion.id}\t${it.file.absolutePath}" } + "\n")
        }
    }
}
tasks.register("releaseLicenseInventory") { dependsOn("communityReleaseLicenseInventory", "storeReleaseLicenseInventory") }
dependencies {
    "storeImplementation"("com.meta.horizon.platform.ovr:android-platform-sdk:77.0.1")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // Cameras HA can only stream (RTSP) play as HLS; OkHttp keeps the user's own TLS trust for the server.
    for (module in listOf("exoplayer", "exoplayer-hls", "datasource-okhttp")) implementation("androidx.media3:media3-$module:1.5.1")
    for (module in listOf("", "-vr", "-toolkit", "-compose", "-mruk", "-isdk", "-physics")) {
        implementation("com.meta.spatial:meta-spatial-sdk$module:0.14.0")
    }
    androidTestImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-intents:3.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.09.03"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}
