plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.compiler)
}

dependencies {
    implementation(project(":page:core"))
    implementation(project(":page:runtime"))
    implementation(project(":page:ui"))
    implementation(project(":page:editor"))
    implementation(project(":page:lsp"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.runtime)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.lsp4j)
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
