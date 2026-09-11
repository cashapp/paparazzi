package app.cash.paparazzi.gradle

import app.cash.paparazzi.gradle.utils.capitalize
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.Directory
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.kotlin.gradle.dsl.ExplicitApiMode
import org.jetbrains.kotlin.gradle.plugin.KotlinBaseApiPlugin
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

/** Outputs of a kapt round, to be folded back into the compilation that triggered it. */
internal class PaparazziKapt(
  val generatedKotlinSources: Provider<Directory>,
  val generatedJavaSources: Provider<Directory>,
  val generatedClasses: Provider<Directory>,
  val tasks: List<TaskProvider<*>>
)

/**
 * Wires kapt for a Paparazzi source set.
 *
 * Annotation processors are declared through a `kapt<SourceSet>` configuration, matching the
 * convention KGP uses for its own source sets. Both kapt tasks are registered through
 * [KotlinBaseApiPlugin], the same entry point used for the source set's Kotlin compilation, so no
 * `KotlinCompilation` is required.
 *
 * Returns `null` when no processors are declared, so projects that do not use kapt pay nothing.
 */
internal fun Project.registerKaptIfNeeded(
  kotlinApiPlugin: KotlinBaseApiPlugin,
  componentName: String,
  sourceSetName: String,
  kotlinCompileTask: TaskProvider<out KotlinJvmCompile>,
  sources: FileCollection,
  compileClasspath: FileCollection
): PaparazziKapt? {
  val processors = kaptConfiguration(sourceSetName)
  if (processors.dependencies.isEmpty() && processors.allDependencies.isEmpty()) return null

  val componentSlug = componentName.capitalize()
  val kaptExtension = kotlinApiPlugin.kaptExtension
  val kaptWorker = kaptWorkerConfiguration(kotlinApiPlugin.pluginVersion)

  val stubsDir = layout.buildDirectory.dir("tmp/kapt3/stubs/$componentName")
  val generatedClasses = layout.buildDirectory.dir("tmp/kapt3/classes/$componentName")
  val generatedJavaSources = layout.buildDirectory.dir("generated/source/kapt/$componentName")
  val generatedKotlinSources =
    layout.buildDirectory.dir("generated/source/kaptKotlin/$componentName")

  val generateStubs = kotlinApiPlugin.registerKaptGenerateStubsTask(
    "kaptGenerateStubs${componentSlug}Kotlin",
    kotlinCompileTask,
    kaptExtension,
    provider { ExplicitApiMode.Disabled }
  )
  generateStubs.configure { task ->
    task.source(sources)
    task.libraries.from(compileClasspath)
    task.stubsDir.set(stubsDir)
    task.destinationDirectory.set(generatedClasses)
    task.kaptClasspath.from(processors)
    task.compilerOptions.moduleName.set(componentName)
    task.multiPlatformEnabled.set(false)
    task.compilerOptions.jvmTarget.set(
      kotlinCompileTask.flatMap { it.compilerOptions.jvmTarget }
    )
    // Stub generation runs kapt as a compiler plugin, which is what lets it tolerate references
    // to code the processors have not generated yet.
    task.pluginClasspath.from(kaptWorker)
    task.compilerOptions.freeCompilerArgs.set(emptyList())
  }

  val kapt = kotlinApiPlugin.registerKaptTask("kapt${componentSlug}Kotlin", kaptExtension)
  kapt.configure { task ->
    task.dependsOn(generateStubs)
    task.kaptJars.from(kaptWorker)
    task.stubsDir.set(stubsDir)
    task.kaptClasspath.from(processors)
    task.classpath.from(compileClasspath)
    task.compiledSources.from(generatedClasses)
    task.classesDir.set(generatedClasses)
    task.destinationDir.set(generatedJavaSources)
    task.kotlinSourcesDestinationDir.set(generatedKotlinSources)
    task.includeCompileClasspath.set(false)
    task.incAptCache.set(layout.buildDirectory.dir("tmp/kapt3/incApCache/$componentName"))
    task.sourceSetName.set(sourceSetName)
    task.source.from(sources)
  }

  return PaparazziKapt(
    generatedKotlinSources = generatedKotlinSources,
    generatedJavaSources = generatedJavaSources,
    generatedClasses = generatedClasses,
    tasks = listOf(generateStubs, kapt)
  )
}

/**
 * The worker classpath kapt tasks run on.
 *
 * Normally created by the standalone `kotlin-kapt` plugin, which cannot be applied alongside AGP's
 * built-in Kotlin support, so it is created here instead.
 */
private fun Project.kaptWorkerConfiguration(kotlinVersion: String): Configuration =
  configurations.maybeCreate(KAPT_WORKER_CONFIGURATION).apply {
    isCanBeConsumed = false
    isCanBeResolved = true
    if (dependencies.isEmpty()) {
      // The worker runs out-of-process, so it needs the compiler and stdlib alongside kapt itself.
      listOf(
        "org.jetbrains.kotlin:kotlin-annotation-processing-embeddable:$kotlinVersion",
        "org.jetbrains.kotlin:kotlin-compiler-embeddable:$kotlinVersion",
        "org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion"
      ).forEach { dependencies.add(project.dependencies.create(it)) }
    }
  }

private const val KAPT_WORKER_CONFIGURATION = "kotlinKaptWorkerDependencies"

/** `kapt<SourceSet>`, e.g. `kaptScreenshotTest`, mirroring KGP's naming for its own source sets. */
internal fun Project.kaptConfiguration(sourceSetName: String): Configuration =
  configurations.maybeCreate("kapt${sourceSetName.capitalize()}").apply {
    isCanBeConsumed = false
    isCanBeResolved = true
    description = "Annotation processors for the '$sourceSetName' Paparazzi source set."
  }

/** `ksp<SourceSet>`, e.g. `kspScreenshotTest`, mirroring KSP's naming for its own source sets. */
internal fun Project.kspConfiguration(sourceSetName: String): Configuration =
  configurations.maybeCreate("ksp${sourceSetName.capitalize()}").apply {
    isCanBeConsumed = false
    isCanBeResolved = true
    description = "Symbol processors for the '$sourceSetName' Paparazzi source set."
  }
