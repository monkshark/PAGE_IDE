import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.compiler)
}

dependencies {
    implementation(project(":page:core"))
    implementation(project(":page:ui"))
    implementation(project(":page:editor"))
    implementation(libs.kotlinx.coroutines.swing)
}

compose.desktop {
    application {
        mainClass = "page.app.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Msi)
            packageName = "PAGE"
            packageVersion = "0.1.0"
            description = "PAGE — Pair · Atlas · Glass · Echo"
            vendor = "Monkshark"
        }
    }
}
