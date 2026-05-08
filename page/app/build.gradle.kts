import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.net.URI

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.compiler)
}

dependencies {
    implementation(project(":page:core"))
    implementation(project(":page:ui"))
    implementation(project(":page:editor"))
    implementation(project(":page:lsp"))
    implementation(libs.kotlinx.coroutines.swing)
}

val klsVersion = "1.3.13"
val klsDownloadUrl = "https://github.com/fwcd/kotlin-language-server/releases/download/$klsVersion/server.zip"
val klsResourcesDir: Provider<Directory> = layout.buildDirectory.dir("composeResources")
val klsServerDir: Provider<Directory> = klsResourcesDir.map { it.dir("common/lsp/server") }

val downloadKls by tasks.registering {
    group = "page"
    description = "Downloads kotlin-language-server"
    val url = klsDownloadUrl
    val target = layout.buildDirectory.file("kls/server-$klsVersion.zip")
    outputs.file(target)
    outputs.cacheIf { true }
    doLast {
        val out = target.get().asFile
        out.parentFile.mkdirs()
        if (out.exists() && out.length() > 0) return@doLast
        URI(url).toURL().openStream().use { input ->
            out.outputStream().use { output -> input.copyTo(output) }
        }
    }
}

val extractKls by tasks.registering(Copy::class) {
    group = "page"
    description = "Extracts kotlin-language-server into compose resources"
    dependsOn(downloadKls)
    from(zipTree(downloadKls.map { it.outputs.files.singleFile }))
    into(klsServerDir)
    eachFile {
        relativePath = org.gradle.api.file.RelativePath(
            !file.isDirectory,
            *relativePath.segments.drop(1).toTypedArray(),
        )
    }
    includeEmptyDirs = false
    val binDir = klsServerDir.map { it.dir("bin").asFile }
    doLast {
        binDir.get().listFiles()?.forEach { it.setExecutable(true, false) }
    }
}

val klsVersionMarker by tasks.registering {
    dependsOn(extractKls)
    val versionStr = klsVersion
    val target = klsResourcesDir.map { it.file("common/lsp/VERSION") }
    outputs.file(target)
    doLast {
        val f = target.get().asFile
        f.parentFile.mkdirs()
        f.writeText(versionStr)
    }
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
            appResourcesRootDir.set(klsResourcesDir)
        }
    }
}

tasks.matching {
    it.name == "run" ||
        it.name == "runDistributable" ||
        it.name == "createDistributable" ||
        it.name == "prepareAppResources" ||
        it.name.startsWith("package")
}.configureEach { dependsOn(klsVersionMarker) }

tasks.register<JavaExec>("runCodeEditorDemo") {
    group = "application"
    description = "Launches the standalone CodeEditor demo window"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("page.app.CodeEditorDemoKt")
}

tasks.withType<JavaExec>().configureEach {
    System.getenv("PAGE_EDITOR_TREESITTER")?.takeIf { it.isNotBlank() }?.let {
        systemProperty("page.editor.treesitter", it)
    }
}
