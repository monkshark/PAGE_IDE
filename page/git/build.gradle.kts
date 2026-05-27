plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    implementation(project(":page:core"))
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
