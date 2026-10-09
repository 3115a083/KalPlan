import java.io.File
import java.util.zip.ZipFile

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "cc.stkmn.kalplan"
    compileSdk = 37

    defaultConfig {
        applicationId = "cc.stkmn.kalplan"
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        targetSdk = 37
        manifestPlaceholders["appAuthRedirectScheme"] = "cc.stkmn.kalplan"
        versionCode = 4
        versionName = "0.4.0-dev"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            pickFirsts += setOf(
                "META-INF/LICENSE.md",
                "META-INF/NOTICE.md"
            )
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/NOTICE"
            )
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.work:work-runtime-ktx:2.11.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")

    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("org.eclipse.angus:jakarta.mail:2.0.5")
    implementation("org.eclipse.angus:angus-activation:2.0.3")
    implementation("jakarta.activation:jakarta.activation-api:2.1.4")

    implementation("org.jsoup:jsoup:1.23.2")
    implementation("com.google.re2j:re2j:1.8")

    implementation("net.openid:appauth:0.11.1")

    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")

    testImplementation("junit:junit:4.13.2")
}


// Export exact runtime component versions for an online advisory check in CI.
tasks.register("dependencyInventory") {
    val runtime = configurations.named("debugRuntimeClasspath")
    doLast {
        val identifiers = runtime.get().incoming.resolutionResult.allComponents.mapNotNull {
            it.id as? org.gradle.api.artifacts.component.ModuleComponentIdentifier
        }.distinctBy { "${it.group}:${it.module}:${it.version}" }.sortedBy { "${it.group}:${it.module}" }
        val target = layout.buildDirectory.file("reports/dependencies.tsv").get().asFile
        target.parentFile.mkdirs()
        target.writeText(identifiers.joinToString("\n") { "${it.group}:${it.module}\t${it.version}" })
    }
}


// Preserve notices from all exact runtime archives, including nested AAR jars.
abstract class DependencyNoticeTask : DefaultTask() {
    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty
}

val bundleDependencyNotices = tasks.register<DependencyNoticeTask>("bundleDependencyNotices") {
    val runtime = configurations.named("debugRuntimeClasspath")
    outputDirectory.set(layout.buildDirectory.dir("generated/dependency-notices"))
    val output = outputDirectory
    inputs.files(runtime)
    outputs.dir(output)
    doLast {
        val destination = output.get().asFile
        destination.deleteRecursively()
        val licenses = destination.resolve("licenses").apply { mkdirs() }
        val temporary = layout.buildDirectory.dir("notice-tmp").get().asFile.apply { mkdirs() }
        val inventory = mutableListOf<String>()
        var counter = 0
        fun copyNotices(archive: File, component: String, nested: Boolean = false) {
            ZipFile(archive).use { zip ->
                val entries = zip.entries().asSequence().toList()
                for (entry in entries.filter { !it.isDirectory }) {
                    val name = entry.name.substringAfterLast('/').uppercase()
                    if (name.contains("LICENSE") || name.contains("NOTICE") || name.startsWith("COPYING")) {
                        require(entry.size <= 1_000_000) { "Dependency notice exceeds size limit" }
                        val bytes = zip.getInputStream(entry).use { it.readNBytes(1_000_001) }
                        require(bytes.size <= 1_000_000)
                        val prefix = component.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(140)
                        val basename = entry.name.substringAfterLast('/').replace(Regex("[^a-zA-Z0-9._-]"), "_").take(60)
                        licenses.resolve("dependency-" + prefix + "-" + counter++ + "-" + basename + ".txt")
                            .writeBytes(bytes)
                    }
                    if (!nested && archive.extension == "aar" && (entry.name == "classes.jar" || entry.name.startsWith("libs/") && entry.name.endsWith(".jar"))) {
                        require(entry.size <= 100_000_000) { "Nested dependency archive exceeds limit" }
                        val jar = File.createTempFile("notice-", ".jar", temporary)
                        try {
                            zip.getInputStream(entry).use { input -> jar.outputStream().use { input.copyTo(it) } }
                            copyNotices(jar, component, true)
                        } finally { jar.delete() }
                    }
                }
            }
        }
        for (artifact in runtime.get().incoming.artifacts.artifacts.sortedBy { it.id.componentIdentifier.displayName }) {
            val component = artifact.id.componentIdentifier.displayName
            inventory += component
            if (artifact.file.extension in setOf("jar", "aar")) copyNotices(artifact.file, component)
        }
        licenses.resolve("01-RESOLVED-COMPONENTS.txt").writeText(inventory.distinct().joinToString("\n"))
        logger.lifecycle("Bundled {} dependency notice/license entries.", counter)
    }
}
androidComponents.onVariants { variant ->
    variant.sources.assets?.addGeneratedSourceDirectory(bundleDependencyNotices, DependencyNoticeTask::outputDirectory)
}
