package app.cash.paparazzi.gradle

import app.cash.paparazzi.gradle.utils.capitalize
import com.google.devtools.ksp.gradle.IsolatedClassLoaderCacheBuildService
import com.google.devtools.ksp.gradle.KspAATask
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.file.FileCollection
import org.gradle.api.logging.LogLevel
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import java.io.File

/** Outputs of a KSP round, to be folded back into the compilation that triggered it. */
internal class PaparazziKsp(
  val generatedKotlinSources: Provider<Directory>,
  val generatedJavaSources: Provider<Directory>,
  val generatedClasses: Provider<Directory>,
  val task: TaskProvider<KspAATask>
)

/**
 * Wires KSP for a Paparazzi source set.
 *
 * KSP's own `registerKspAATask` needs a `KotlinCompilation`, which this source set deliberately
 * does not have. [KspAATask] is a plain Gradle task whose inputs are all public, though, so it is
 * registered and configured directly instead.
 *
 * Returns `null` when no processors are declared, so projects that do not use KSP pay nothing.
 */
internal fun Project.registerKspIfNeeded(
  componentName: String,
  sourceSetName: String,
  kspVersion: String,
  sources: FileCollection,
  javaSources: FileCollection,
  compileClasspath: FileCollection,
  jvmTarget: Provider<String>,
  languageVersion: Provider<String>
): PaparazziKsp? {
  val processors = kspConfiguration(sourceSetName)
  if (processors.dependencies.isEmpty() && processors.allDependencies.isEmpty()) return null

  val componentSlug = componentName.capitalize()
  val outputBase = layout.buildDirectory.dir("generated/ksp/$componentName")
  val kotlinOut = outputBase.map { it.dir("kotlin") }
  val javaOut = outputBase.map { it.dir("java") }
  val classesOut = outputBase.map { it.dir("classes") }
  val resourcesOut = outputBase.map { it.dir("resources") }

  val kspToolClasspath = kspToolConfiguration(kspVersion)

  // KSP 2.3.11+ requires this build service, which its own `registerKspAATask` would normally
  // supply. Registered under KSP's own key so the classloader cache is shared with any tasks the
  // KSP plugin registered itself.
  val isolatedClassLoaderCache = gradle.sharedServices.registerIfAbsent(
    IsolatedClassLoaderCacheBuildService.KEY,
    IsolatedClassLoaderCacheBuildService::class.java
  ) {}

  val task = tasks.register("ksp${componentSlug}Kotlin", KspAATask::class.java) { task ->
    task.kspClasspath.from(kspToolClasspath)
    task.isolatedClassLoaderCacheBuildService.set(isolatedClassLoaderCache)
    task.usesService(isolatedClassLoaderCache)
    task.kspConfig.let { config ->
      config.processorClasspath.from(processors)
      config.moduleName.set(componentName)
      config.sourceRoots.from(sources)
      config.javaSourceRoots.from(javaSources)
      config.libraries.from(compileClasspath)
      config.projectBaseDir.set(layout.projectDirectory)
      config.outputBaseDir.set(outputBase)
      config.cachesDir.set(layout.buildDirectory.dir("tmp/ksp/$componentName/caches"))
      config.kotlinOutputDir.set(kotlinOut)
      config.javaOutputDir.set(javaOut)
      config.classOutputDir.set(classesOut)
      config.resourceOutputDir.set(resourcesOut)
      config.platformType.set(KotlinPlatformType.jvm)
      // KSP runs out-of-process and reads these eagerly, so every one needs a concrete value.
      config.jvmTarget.set(jvmTarget.orElse(DEFAULT_JVM_TARGET))
      config.jvmDefaultMode.set("disable")
      config.jdkHome.set(layout.dir(provider { File(System.getProperty("java.home")) }))
      config.jdkVersion.set(Runtime.version().feature())
      config.profilingMode.set(false)
      config.konanHome.set("")
      config.konanTargetName.set("")
      config.logLevel.set(LogLevel.INFO)
      config.incremental.set(false)
      config.incrementalLog.set(false)
      config.allWarningsAsErrors.set(false)
      config.experimentalPsiResolution.set(false)
      config.languageVersion.set(languageVersion)
      config.apiVersion.set(languageVersion)
    }
  }

  return PaparazziKsp(
    generatedKotlinSources = kotlinOut,
    generatedJavaSources = javaOut,
    generatedClasses = classesOut,
    task = task
  )
}

/**
 * The classpath KSP itself runs on.
 *
 * Normally created by KSP's Gradle plugin, which only wires source sets it knows about, so it is
 * created here instead.
 */
private fun Project.kspToolConfiguration(kspVersion: String) =
  configurations.maybeCreate(KSP_TOOL_CONFIGURATION).apply {
    isCanBeConsumed = false
    isCanBeResolved = true
    if (dependencies.isEmpty()) {
      dependencies.add(
        project.dependencies.create("com.google.devtools.ksp:symbol-processing-aa-embeddable:$kspVersion")
      )
    }
  }

private const val KSP_TOOL_CONFIGURATION = "paparazziKspTooling"
private const val DEFAULT_JVM_TARGET = "17"
