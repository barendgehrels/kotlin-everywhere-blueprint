import java.util.Properties
import org.apache.tools.ant.filters.ReplaceTokens
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    js {
        browser()
        binaries.executable()
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":app:shared"))

            implementation(libs.compose.ui)
        }
        webMain.dependencies {
            implementation(libs.wrappers.browser)
        }
    }
}

// The Turnstile site key goes into index.html. It is public. -PturnstileTest uses Cloudflare's
// test key that always passes (for localhost); else -Pturnstile_site_key, else secret.properties.
// Without one the page has no human check: requestHumanCheck() answers "" at once.
fun secretProperty(name: String): String? = runCatching {
    Properties().apply { rootProject.file("secret.properties").inputStream().use { load(it) } }.getProperty(name)
}.getOrNull()

val turnstileSiteKey: String =
    if (providers.gradleProperty("turnstileTest").isPresent) "1x00000000000000000000AA"
    else providers.gradleProperty("turnstile_site_key").orNull
        ?: secretProperty("turnstile_site_key")
        ?: "".also { logger.info("No Turnstile site key: the web app has no human check") }

tasks.withType<ProcessResources>().configureEach {
    val tokens = mapOf("TITLE" to "Slippy", "TURNSTILE_SITE_KEY" to turnstileSiteKey)
    inputs.property("tokens", tokens)
    filesMatching("index.html") {
        filter<ReplaceTokens>("tokens" to tokens)
    }
}
