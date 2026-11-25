plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    js {
        nodejs()                                // close to the Workers runtime
        outputModuleName.set("slippy")          // a stable file name for index.js
        binaries.executable()                   // a .js file to run, not a library
    }
    sourceSets {
        jsMain.dependencies {
            implementation(project(":core"))
            implementation(libs.kotlinx.coroutines)
            implementation(libs.kotlinx.serialization)
            implementation(libs.wrappers.browser)
        }
        jsTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)   // runTest for the suspend helpers
        }
    }
}
