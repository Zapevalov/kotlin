import java.io.File
import proguard.gradle.ProGuardTask
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.artifacts.maven.Conf2ScopeMappingContainer.COMPILE
import org.gradle.api.file.DuplicatesStrategy

description = "Kotlin Compiler"

plugins {
    maven
}

// You can run Gradle with "-Pkotlin.build.proguard=true" to enable ProGuard run on kotlin-compiler.jar (on TeamCity, ProGuard always runs)
val shrink =
    findProperty("kotlin.build.proguard")?.toString()?.toBoolean()
    ?: hasProperty("teamcity")

val compilerManifestClassPath = "kotlin-stdlib.jar kotlin-reflect.jar kotlin-script-runtime.jar"

val fatJarContents by configurations.creating

val fatJarContentsStripMetadata by configurations.creating
val fatJarContentsStripServices by configurations.creating
val fatSourcesJarContents by configurations.creating
val fatJar by configurations.creating
val compilerJar by configurations.creating
val runtimeJar by configurations.creating

val compile by configurations.creating {
    the<MavenPluginConvention>()
        .conf2ScopeMappings
        .addMapping(0, this, COMPILE)
}

val default by configurations
default.extendsFrom(runtimeJar)

val compilerBaseName = name

val outputJar = fileFrom(buildDir, "libs", "$compilerBaseName.jar")

val compilerModules: Array<String> by rootProject.extra

compilerModules.forEach { evaluationDependsOn(it) }

val compiledModulesSources = compilerModules.map {
    project(it).mainSourceSet.allSource
}

dependencies {
    // Maven plugin generates pom compile dependencies from compile configuration
    compile(project(":kotlin-stdlib"))
    compile(project(":kotlin-script-runtime"))
    compile(project(":kotlin-reflect"))
    compile(project(":kotlin-annotations-jvm"))

    compilerModules.forEach {
        fatJarContents(project(it)) { isTransitive = false }
    }

    compiledModulesSources.forEach {
        fatSourcesJarContents(it)
    }

    fatJarContents(project(":core:builtins", configuration = "builtins"))
    fatJarContents(commonDep("javax.inject"))
    fatJarContents(commonDep("org.jline", "jline"))
    fatJarContents(commonDep("org.fusesource.jansi", "jansi"))
    fatJarContents(protobufFull())
    fatJarContents(commonDep("com.google.code.findbugs", "jsr305"))
    fatJarContents(commonDep("io.javaslang", "javaslang"))
    fatJarContents(commonDep("org.jetbrains.kotlinx", "kotlinx-coroutines-core")) { isTransitive = false }

    fatJarContents(intellijCoreDep()) { includeJars("intellij-core") }
    fatJarContents(intellijDep()) { includeIntellijCoreJarDependencies(project, { !(it.startsWith("jdom") || it.startsWith("log4j")) }) }
    fatJarContents(intellijDep()) { includeJars("jna-platform", "lz4-1.3.0") }
    fatJarContentsStripServices(intellijDep("jps-standalone")) { includeJars("jps-model") }
    fatJarContentsStripMetadata(intellijDep()) { includeJars("oro-2.0.8", "jdom", "log4j") }
}


val packCompiler by task<ShadowJar> {
    configurations = listOf(fatJar)
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    destinationDir = File(buildDir, "libs")

    setupPublicJar(compilerBaseName, "before-proguard")

    from(fatJarContents)
    afterEvaluate {
        fatJarContentsStripServices.files.forEach { from(zipTree(it)) { exclude("META-INF/services/**") } }
        fatJarContentsStripMetadata.files.forEach { from(zipTree(it)) { exclude("META-INF/jb/** META-INF/LICENSE") } }
    }

    manifest.attributes["Class-Path"] = compilerManifestClassPath
    manifest.attributes["Main-Class"] = "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler"
}

val proguard by task<ProGuardTask> {
    dependsOn(packCompiler)
    configuration("$rootDir/compiler/compiler.pro")

    val outputJar = fileFrom(buildDir, "libs", "$compilerBaseName-after-proguard.jar")

    inputs.files(packCompiler.outputs.files.singleFile)
    outputs.file(outputJar)

    // TODO: remove after dropping compatibility with ant build
    doFirst {
        System.setProperty("kotlin-compiler-jar-before-shrink", packCompiler.outputs.files.singleFile.canonicalPath)
        System.setProperty("kotlin-compiler-jar", outputJar.canonicalPath)
    }

    libraryjars(mapOf("filter" to "!META-INF/versions/**"), compile)
    libraryjars(
        mapOf("filter" to "!META-INF/versions/**"),
        files(
            firstFromJavaHomeThatExists("jre/lib/rt.jar", "../Classes/classes.jar"),
            firstFromJavaHomeThatExists("jre/lib/jsse.jar", "../Classes/jsse.jar"),
            toolsJar()
        )
    )

    printconfiguration("$buildDir/compiler.pro.dump")
}

val pack = if (shrink) proguard else packCompiler

dist(
    targetName = "$compilerBaseName.jar",
    fromTask = pack
)

runtimeJarArtifactBy(pack, pack.outputs.files.singleFile) {
    name = compilerBaseName
    classifier = ""
}

sourcesJar {
    from(fatSourcesJarContents)
}

javadocJar()

publish()
