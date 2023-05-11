/*
 * Copyright (C) 2019 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package app.cash.paparazzi.gradle

import app.cash.paparazzi.NATIVE_LIB_VERSION
import app.cash.paparazzi.VERSION
import app.cash.paparazzi.gradle.utils.artifactViewFor
import app.cash.paparazzi.gradle.utils.artifactsFor
import com.android.build.gradle.BaseExtension
import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType
import com.android.build.gradle.tasks.MergeSourceSetFolders
import com.android.ide.common.symbols.getPackageNameFromManifest
import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.type.ArtifactTypeDefinition
import org.gradle.api.artifacts.type.ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.artifacts.transform.UnzipTransform
import org.gradle.api.logging.LogLevel.LIFECYCLE
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.options.Option
import org.gradle.api.tasks.testing.Test
import org.gradle.internal.os.OperatingSystem
import org.gradle.language.base.plugins.LifecycleBasePlugin.VERIFICATION_GROUP
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinAndroidPluginWrapper
import org.jetbrains.kotlin.gradle.plugin.KotlinMultiplatformPluginWrapper
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinAndroidTarget
import java.util.Locale
import kotlin.io.path.invariantSeparatorsPathString

@Suppress("unused")
class PaparazziPlugin : Plugin<Project> {
  override fun apply(project: Project) {
    project.afterEvaluate {
      check(!project.plugins.hasPlugin("com.android.application")) {
        error(
          "Currently, Paparazzi only works in Android library -- not application -- modules. " +
            "See https://github.com/cashapp/paparazzi/issues/107"
        )
      }
      check(project.plugins.hasPlugin("com.android.library")) {
        "The Android Gradle library plugin must be applied for Paparazzi to work properly."
      }
    }

    project.plugins.withId("com.android.library") { setupPaparazzi(project) }
  }

  private fun setupPaparazzi(project: Project) {
    project.addTestDependency()
    val nativePlatformFileCollection = project.setupNativePlatformDependency()

    // Create anchor tasks for all variants.
    val verifyVariants = project.tasks.register("verifyPaparazzi")
    val recordVariants = project.tasks.register("recordPaparazzi")

    val variants = project.extensions.getByType(LibraryExtension::class.java)
      .libraryVariants
    variants.all { variant ->
      val variantSlug = variant.name.capitalize(Locale.US)
      val testVariant = variant.unitTestVariant ?: return@all

      val mergeResourcesOutputDir = variant.mergeResourcesProvider.flatMap { it.outputDir }
      val mergeAssetsProvider =
        project.tasks.named("merge${variantSlug}Assets") as TaskProvider<MergeSourceSetFolders>
      val mergeAssetsOutputDir = mergeAssetsProvider.flatMap { it.outputDir }
      val buildDirectory = project.layout.buildDirectory
      val reportOutputDir = buildDirectory.dir("reports/paparazzi")
      val snapshotOutputDir = project.layout.projectDirectory.dir("src/test/snapshots")

      // local resources
      val localResourceFiles = project
        .files(variant.sourceSets.flatMap { it.resDirectories })

      // library resources
      // https://android.googlesource.com/platform/tools/base/+/96015063acd3455a76cdf1cc71b23b0828c0907f/build-system/gradle-core/src/main/java/com/android/build/gradle/tasks/MergeResources.kt#875
      val libraryResourceFiles = variant.runtimeConfiguration
        .artifactsFor(ArtifactType.ANDROID_RES.type)
        .artifactFiles

      val packageAwareArtifactFiles = variant.runtimeConfiguration
        .artifactsFor(ArtifactType.SYMBOL_LIST_WITH_PACKAGE_NAME.type)
        .artifactFiles

      val writeResourcesTask = project.tasks.register(
        "preparePaparazzi${variantSlug}Resources",
        PrepareResourcesTask::class.java
      ) { task ->
        val android = project.extensions.getByType(BaseExtension::class.java)
        val nonTransitiveRClassEnabled =
          (project.findProperty("android.nonTransitiveRClass") as? String).toBoolean()

        task.packageName.set(android.packageName())
        task.artifactFiles.from(packageAwareArtifactFiles)
        task.nonTransitiveRClassEnabled.set(nonTransitiveRClassEnabled)
        task.mergeResourcesOutputDir.set(buildDirectory.asRelativePathString(mergeResourcesOutputDir))
        task.targetSdkVersion.set(android.targetSdkVersion())
        task.compileSdkVersion.set(android.compileSdkVersion())
        task.mergeAssetsOutputDir.set(buildDirectory.asRelativePathString(mergeAssetsOutputDir))
        task.localResourceFiles.from(localResourceFiles)
        task.libraryResourceFiles.from(libraryResourceFiles)
        task.paparazziResources.set(buildDirectory.file("intermediates/paparazzi/${variant.name}/resources.txt"))
      }

      val testVariantSlug = testVariant.name.capitalize(Locale.US)

      project.plugins.withType(JavaBasePlugin::class.java) {
        project.tasks.named("compile${testVariantSlug}JavaWithJavac")
          .configure { it.dependsOn(writeResourcesTask) }
      }

      project.plugins.withType(KotlinMultiplatformPluginWrapper::class.java) {
        val multiplatformExtension =
          project.extensions.getByType(KotlinMultiplatformExtension::class.java)
        check(multiplatformExtension.targets.any { target -> target is KotlinAndroidTarget }) {
          "There must be an Android target configured when using Paparazzi with the Kotlin Multiplatform Plugin"
        }
        project.tasks.named("compile${testVariantSlug}KotlinAndroid")
          .configure { it.dependsOn(writeResourcesTask) }
      }

      project.plugins.withType(KotlinAndroidPluginWrapper::class.java) {
        project.tasks.named("compile${testVariantSlug}Kotlin")
          .configure { it.dependsOn(writeResourcesTask) }
      }

      val recordTaskProvider = project.tasks.register("recordPaparazzi$variantSlug", PaparazziTask::class.java) {
        it.group = VERIFICATION_GROUP
      }
      recordVariants.configure { it.dependsOn(recordTaskProvider) }
      val verifyTaskProvider = project.tasks.register("verifyPaparazzi$variantSlug", PaparazziTask::class.java) {
        it.group = VERIFICATION_GROUP
      }
      verifyVariants.configure { it.dependsOn(verifyTaskProvider) }

      val isRecordRun = project.objects.property(Boolean::class.java)
      val isVerifyRun = project.objects.property(Boolean::class.java)

      project.gradle.taskGraph.whenReady { graph ->
        isRecordRun.set(recordTaskProvider.map { graph.hasTask(it) })
        isVerifyRun.set(verifyTaskProvider.map { graph.hasTask(it) })
      }

      val testTaskProvider = project.tasks.named("test$testVariantSlug", Test::class.java) { test ->
        test.systemProperties["paparazzi.test.resources"] =
          writeResourcesTask.flatMap { it.paparazziResources.asFile }.get().path
        test.systemProperties["paparazzi.build.dir"] =
          buildDirectory.get().toString()
        test.systemProperties["kotlinx.coroutines.main.delay"] = true
        test.systemProperties.putAll(project.properties.filterKeys { it.startsWith("app.cash.paparazzi") })

        test.inputs.property("paparazzi.test.record", isRecordRun)
        test.inputs.property("paparazzi.test.verify", isVerifyRun)

        test.inputs.dir(mergeResourcesOutputDir)
        test.inputs.dir(mergeAssetsOutputDir)
        test.inputs.files(nativePlatformFileCollection)
          .withPropertyName("paparazzi.nativePlatform")
          .withPathSensitivity(PathSensitivity.NONE)

        test.outputs.dir(reportOutputDir)
        test.outputs.dir(snapshotOutputDir)

        @Suppress("ObjectLiteralToLambda")
        // why not a lambda?  See: https://docs.gradle.org/7.2/userguide/validation_problems.html#implementation_unknown
        test.doFirst(object : Action<Task> {
          override fun execute(t: Task) {
            // Note: these are lazy properties that are not resolvable in the Gradle configuration phase.
            // They need special handling, so they're added as inputs.property above, and systemProperty here.
            test.systemProperties["paparazzi.platform.data.root"] =
              nativePlatformFileCollection.singleFile.absolutePath
            test.systemProperties["paparazzi.test.record"] = isRecordRun.get()
            test.systemProperties["paparazzi.test.verify"] = isVerifyRun.get()
          }
        })
      }

      recordTaskProvider.configure { it.dependsOn(testTaskProvider) }
      verifyTaskProvider.configure { it.dependsOn(testTaskProvider) }

      testTaskProvider.configure { test ->
        @Suppress("ObjectLiteralToLambda")
        // why not a lambda?  See: https://docs.gradle.org/7.2/userguide/validation_problems.html#implementation_unknown
        test.doLast(object : Action<Task> {
          override fun execute(t: Task) {
            val uri = reportOutputDir.get().asFile.toPath().resolve("index.html").toUri()
            test.logger.log(LIFECYCLE, "See the Paparazzi report at: $uri")
          }
        })
      }
    }
  }

  open class PaparazziTask : DefaultTask() {
    @Option(option = "tests", description = "Sets test class or method name to be included, '*' is supported.")
    open fun setTestNameIncludePatterns(testNamePattern: List<String>): PaparazziTask {
      project.tasks.withType(Test::class.java).configureEach {
        it.setTestNameIncludePatterns(testNamePattern)
      }
      return this
    }
  }

  private fun Project.setupNativePlatformDependency(): FileCollection {
    val nativePlatformConfiguration = configurations.create("nativePlatform")
    configurations.add(nativePlatformConfiguration)

    val operatingSystem = OperatingSystem.current()
    val nativeLibraryArtifactId = when {
      operatingSystem.isMacOsX -> {
        val osArch = System.getProperty("os.arch").lowercase(Locale.US)
        if (osArch.startsWith("x86")) "macosx" else "macarm"
      }
      operatingSystem.isWindows -> "win"
      else -> "linux"
    }
    nativePlatformConfiguration.dependencies.add(
      dependencies.create("app.cash.paparazzi:layoutlib-native-$nativeLibraryArtifactId:$NATIVE_LIB_VERSION")
    )
    dependencies.registerTransform(UnzipTransform::class.java) { transform ->
      transform.from.attribute(ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.JAR_TYPE)
      transform.to.attribute(ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.DIRECTORY_TYPE)
    }

    return nativePlatformConfiguration
      .artifactViewFor(ArtifactTypeDefinition.DIRECTORY_TYPE)
      .files
  }

  private fun Project.addTestDependency() {
    configurations.getByName("testImplementation").dependencies.add(
      dependencies.create("app.cash.paparazzi:paparazzi:$VERSION")
    )
  }

  private fun BaseExtension.packageName(): String {
    namespace?.let { return it }

    // TODO: explore whether AGP 7.x APIs can handle source set filtering
    sourceSets
      .filterNot { it.name.startsWith("androidTest") }
      .map { it.manifest.srcFile }
      .filter { it.exists() }
      .forEach {
        return getPackageNameFromManifest(it)
      }
    throw IllegalStateException("No source sets available")
  }

  private fun BaseExtension.compileSdkVersion(): String {
    return compileSdkVersion!!.substringAfter("android-", DEFAULT_COMPILE_SDK_VERSION.toString())
  }

  private fun BaseExtension.targetSdkVersion(): String {
    return defaultConfig.targetSdkVersion?.apiLevel?.toString()
      ?: DEFAULT_COMPILE_SDK_VERSION.toString()
  }
}

private fun Directory.relativize(child: Directory): String {
  return asFile.toPath().relativize(child.asFile.toPath()).invariantSeparatorsPathString
}

private fun DirectoryProperty.asRelativePathString(child: Provider<Directory>): Provider<String> =
  flatMap { root -> child.map { root.relativize(it) } }

private const val DEFAULT_COMPILE_SDK_VERSION = 33
