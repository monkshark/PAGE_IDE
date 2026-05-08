plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    api(project(":page:core"))
    implementation("io.github.bonede:tree-sitter:0.26.3")
    implementation("io.github.bonede:tree-sitter-java:0.23.5")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
