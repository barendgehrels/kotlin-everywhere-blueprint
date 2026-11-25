import java.util.Properties
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    applyDefaultHierarchyTemplate()   // explicitly, because we add a source set of our own

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }
    jvm()
    // binaries.executable() also in this library: Compose's web tests need the webpack bundle (CMP-4906)
    js {
        browser()
        binaries.executable()
    }
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        binaries.executable()
    }
    android {
        namespace = "org.example.slippy.app.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
        androidResources {
            enable = true
        }
        withHostTest {
            isIncludeAndroidResources = true
        }
    }
    sourceSets {
        commonMain.dependencies {
            api(project(":core"))
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.contentNegotiation)
            implementation(libs.ktor.serialization.json)
            implementation(libs.ktor.client.logging)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.coroutines)
        }
        // DataStore once, for Android, iOS and the desktop; the web has localStorage
        // create(), because 'val x by creating' is deprecated since Gradle 9.6
        val dataStoreMain = create("dataStoreMain") {
            dependsOn(commonMain.get())
            dependencies {
                implementation(libs.androidx.datastore.preferences)
            }
        }
        androidMain {
            dependsOn(dataStoreMain)
            dependencies {
                implementation(libs.ktor.client.okhttp)
                implementation(libs.compose.uiToolingPreview)
                implementation(libs.compose.uiTooling)
            }
        }
        iosMain {
            dependsOn(dataStoreMain)
            dependencies {
                implementation(libs.ktor.client.darwin)
            }
        }
        jvmMain {
            dependsOn(dataStoreMain)
            dependencies {
                implementation(libs.ktor.client.java)
            }
        }
        webMain.dependencies {
            implementation(libs.ktor.client.js)
            implementation(libs.wrappers.browser)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

compose.resources {
    packageOfResClass = "org.example.slippy.resources"
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
}

// The generated config: the service URL and the version, known at compile time.
// -Pcloud_service_url wins, and is checked before secret.properties is needed at all, so a build
// with the property works without that file (and a build of another module never reads it).
val versionName: String = providers.gradleProperty("slippy.version").get()

// secret.properties, in the root, in .gitignore: cloud_service_url=https://...
// Read only when the property is not given; null when it is missing or cannot be read.
fun secretProperty(name: String): String? = runCatching {
    Properties().apply { rootProject.file("secret.properties").inputStream().use { load(it) } }.getProperty(name)
}.getOrNull()

val cloudServiceUrl: String? = providers.gradleProperty("cloud_service_url").orNull   // -P wins
    ?: secretProperty("cloud_service_url")

val generatedFolder = "generated/kotlin/config"

val generateConfig = tasks.register("generateConfig") {
    val url = cloudServiceUrl
    val content = """
        |package generated
        |
        |internal object AppConfig {
        |    const val CLOUD_SERVICE_URL: String = "$url"
        |    const val VERSION_NAME: String = "$versionName"
        |}
        |""".trimMargin()
    val file = layout.buildDirectory.file("$generatedFolder/AppConfig.kt")
    inputs.property("content", content)
    outputs.file(file)
    doLast {
        // Only fails a build that compiles the app, not one of the service
        if (url == null) throw GradleException("cloud_service_url: not in secret.properties, and no -Pcloud_service_url")
        file.get().asFile.apply { parentFile.mkdirs(); writeText(content) }
    }
}

kotlin.sourceSets.commonMain { kotlin.srcDir(layout.buildDirectory.dir(generatedFolder)) }
tasks.withType<KotlinCompilationTask<*>>().configureEach { dependsOn(generateConfig) }

// iOS reads its version from Info.plist (MARKETING_VERSION), so Xcode gets it in an .xcconfig,
// included by Configuration/Config.xcconfig
val generateVersionXcconfig = tasks.register("generateVersionXcconfig") {
    val content = "// Generated by :app:shared:generateVersionXcconfig from slippy.version\n" +
        "MARKETING_VERSION=$versionName\n"
    val file = rootProject.layout.projectDirectory.file("app/iosApp/Configuration/Version.xcconfig")
    inputs.property("content", content)
    outputs.file(file)
    doLast { file.asFile.writeText(content) }
}
tasks.matching { it.name == "embedAndSignAppleFrameworkForXcode" || it.name.startsWith("link") }
    .configureEach { dependsOn(generateVersionXcconfig) }
