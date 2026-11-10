import com.google.protobuf.gradle.proto
import groovy.xml.MarkupBuilder
import groovy.xml.XmlSlurper
import groovy.xml.slurpersupport.NodeChild
import org.gradle.kotlin.dsl.support.uppercaseFirstChar
import java.util.Properties

plugins {
    id("nexfusion.bundle")
    alias(libs.plugins.protobuf)
}

android {
    namespace = "io.github.nexfusion.patches"

    lint {
        abortOnError = false
    }

    defaultConfig {
        applicationId = "io.github.nexfusion.patches"
        versionCode = 80
        val patchVersion = Properties().apply {
            rootProject.file("morphe-patches/gradle.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
        }["version"]
        versionName = "4.3.1"
        buildConfigField("String", "PATCH_VERSION", "\"$patchVersion\"")
    }
    packaging {
        resources {
            excludes += listOf(
                "META-INF/**",
                "kotlin/**",
                "**.bin",
                "**/*.proto",
                "**/*.textproto",
            )
        }
    }
    sourceSets {
        named("main") {
            val srcDirs = arrayOf(
                "../../morphe-patches/extensions/shared/library/src/main/java",
                "../../morphe-patches/extensions/shared-youtube/library/src/main/java",
                "../../morphe-patches/extensions/youtube/src/main/java",
                "../../morphe-patches/extensions/music/src/main/java",
                "../../morphe-patches/extensions/reddit/src/main/java",
                "../../morphe-patches-library/extension-library/src/main/java",
            )
            java.directories += srcDirs
            kotlin.directories += srcDirs

            proto {
                srcDirs(
                    "../../morphe-patches/extensions/youtube/src/main/proto",
                    "../../morphe-patches/extensions/shared-youtube/library/src/main/proto",
                )
            }
        }
    }
}

nexfusionBundle {
    entry = "io.github.nexfusion.nexfusion.NexFusionPatches"
    author = "a092devs"
    descriptionResource = "bundle_description"
    resourcePackageId = 0x4e
    compatibilitySeries = "3.6"
    minManagerVersion = ""
}

// Exclude Morphe-specific files that depend on protobuf/innertube/javascriptengine
// which are not available in the Xposed module build context.
tasks.withType<JavaCompile>().configureEach {
    exclude(
        "**/patches/HideRelatedVideosPatch.java",
        "**/patches/playback/quality/PrioritizeVideoQualityPatch.java",
        "**/OAuth2Preference.java",
        "**/SpoofVideoStreamsSignInPreference.java",
        "**/SpoofSignaturePatch.java",
        "**/preference/YouTubeGlassNavigationBarPreference.java",
        "**/preference/MusicGlassNavigationBarPreference.java",
        "**/preference/GlassNavigationBarSizePreference.java",
    )
}

dependencies {
    compileOnly(libs.nexfusion.patcher)
    compileOnly(project(":stub"))
    implementation(libs.annotation)
    implementation(libs.gson)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.javascriptengine)
    implementation(libs.protobuf.javalite)
    implementation(libs.collections4)
    implementation(libs.lang3)
}

protobuf {
    protoc {
        artifact = libs.protobuf.protoc.get().toString()
    }
    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                create("java") {
                    option("lite")
                }
            }
        }
    }
}

abstract class GenerateStringsTask @Inject constructor() : DefaultTask() {
    @get:InputDirectory
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val inputDirectory: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    private fun writeNode(builder: MarkupBuilder, node: Any?) {
        if (node !is NodeChild) return
        val attributes = node.attributes()
        builder.withGroovyBuilder {
            if (node.children().any()) {
                node.name()(attributes) {
                    node.children().forEach {
                        writeNode(builder, it)
                    }
                }
            } else {
                node.name()(attributes, node.text())
            }
        }
    }

    private fun mergeResources(inputFiles: List<File>, output: File) {
        output.parentFile.mkdirs()
        output.writer().use { writer ->
            val builder = MarkupBuilder(writer)
            builder.doubleQuotes = true
            builder.withGroovyBuilder {
                val keys = mutableSetOf<String>()
                "resources" {
                    for (inputFile in inputFiles) {
                        if (!inputFile.exists()) continue
                        val inputXml = XmlSlurper().parse(inputFile)
                        inputXml.children().forEach {
                            if (it !is NodeChild) return@forEach
                            val key = it.attributes()["name"] as? String ?: return@forEach
                            if (keys.contains(key)) return@forEach
                            writeNode(builder, it)
                            keys.add(key)
                        }
                    }
                }
            }
        }
    }

    private val subDirs = listOf("shared", "shared-youtube", "youtube", "music", "reddit", "sponsorblock")

    @TaskAction
    fun action() {
        if (!inputDirectory.isPresent) return
        val inputDir = inputDirectory.get().asFile
        if (!inputDir.exists()) return
        val outputDir = outputDirectory.get().asFile

        runCatching {
            inputDir.listFiles()?.filter { it.isDirectory }?.forEach { variant ->
                val genResDir = File(outputDir, variant.name).apply { mkdirs() }

                val stringFiles = subDirs.map { File(variant, "$it/strings.xml") }
                mergeResources(stringFiles, File(genResDir, "strings.xml"))

                val arrayFiles = subDirs.map { File(variant, "$it/arrays.xml") }
                if (arrayFiles.any { it.exists() }) {
                    mergeResources(arrayFiles, File(genResDir, "arrays.xml"))
                }
            }
        }.onFailure {
            System.err.println(it)
            throw it
        }
    }
}

abstract class CopyResourcesTask @Inject constructor() : DefaultTask() {
    @get:InputDirectory
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val inputDirectory: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun action() {
        if (!inputDirectory.isPresent) return
        val baseDir = inputDirectory.get().asFile
        if (!baseDir.exists()) return
        val outputDir = outputDirectory.get().asFile
        outputDir.deleteRecursively()

        val resourcePaths = mapOf(
            "qualitybutton/drawable" to null,
            "settings/drawable" to null,
            "settings/menu" to null,
            "settings/layout" to listOf("morphe_settings_with_toolbar.xml"),
            "sponsorblock/drawable" to null,
            "sponsorblock/layout" to listOf("morphe_sb_skip_sponsor_button.xml"),
            "swipecontrols/drawable" to null,
            "copyvideolinkbutton/drawable" to null,
            "downloads/drawable" to null,
            "speedbutton/drawable" to null,
            "navigationbuttons/drawable" to null,
            "speed/drawable" to null,
            "searchcomments/drawable" to null,
            "channelsearch/drawable" to null,
        )

        for ((resourcePath, excludes) in resourcePaths) {
            val dir = resourcePath.substringAfter('/')
            val sourceDir = File(baseDir, resourcePath)
            val targetDir = File(outputDir, dir)
            sourceDir.listFiles()?.forEach { file ->
                if (excludes == null || !excludes.contains(file.name)) {
                    file.copyTo(File(targetDir, file.name), overwrite = true)
                }
            }
        }
    }
}

androidComponents {
    onVariants { variant ->
        val variantName = variant.name.uppercaseFirstChar()
        val strFile = project.file("../../morphe-patches/patches/src/main/resources/addresources")
        val strTask = project.tasks.register<GenerateStringsTask>("generateStrings$variantName") {
            if (strFile.exists()) {
                inputDirectory.set(strFile)
            }
        }
        variant.sources.res?.addGeneratedSourceDirectory(
            strTask, GenerateStringsTask::outputDirectory
        )

        val resFile = project.file("../../morphe-patches/patches/src/main/resources")
        val resTask = project.tasks.register<CopyResourcesTask>("copyResources$variantName") {
            if (resFile.exists()) {
                inputDirectory.set(resFile)
            }
        }
        variant.sources.res?.addGeneratedSourceDirectory(
            resTask, CopyResourcesTask::outputDirectory
        )
    }
}

// Fingerprint tests run against the APKs in `binaries/`, named `<package name>…apk`.
android.sourceSets.named("test") {
    java.directories += "../testing/java"
    kotlin.directories += "../testing/java"
}
tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    systemProperty("java.library.path", file("../testing/jniLibs").path)
}

dependencies {
    testImplementation(libs.nexfusion.patcher)
    testImplementation(kotlin("test-junit5"))
    testImplementation("org.jetbrains.kotlin:kotlin-reflect:1.9.20")
    testImplementation(libs.junit.jupiter.params)
    testImplementation(libs.jadx.core)
    testImplementation(libs.slf4j.simple)
}
