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

import app.cash.paparazzi.gradle.utils.artifactViewFor
import app.cash.paparazzi.gradle.utils.artifactsFor
import com.android.build.gradle.BaseExtension
import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.TestedExtension
import com.android.build.gradle.api.BaseVariant
import com.android.build.gradle.internal.api.TestedVariant
import com.android.build.gradle.internal.dsl.BaseAppModuleExtension
import com.android.build.gradle.internal.dsl.DynamicFeatureExtension
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType
import com.android.build.gradle.tasks.MergeSourceSetFolders
import org.gradle.api.DefaultTask
import org.gradle.api.DomainObjectSet
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
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
    val legacyResourceLoadingEnabled = (project.findProperty("app.cash.paparazzi.legacy.resource.loading") as? String)?.toBoolean() ?: false

    if (legacyResourceLoadingEnabled) {
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

      project.plugins.withId("com.android.library") {
        setupPaparazzi(project, project.extensions.getByType(LibraryExtension::class.java).libraryVariants)
      }
    } else {
      val supportedPlugins = listOf("com.android.application", "com.android.library", "com.android.dynamic-feature")
      project.afterEvaluate {
        check(supportedPlugins.any { project.plugins.hasPlugin(it) }) {
          "One of ${supportedPlugins.joinToString(", ")} must be applied for Paparazzi to work properly."
        }
      }

      supportedPlugins.forEach { plugin ->
        project.plugins.withId(plugin) {
          val variants = when (val extension = project.extensions.getByType(TestedExtension::class.java)) {
            is LibraryExtension -> extension.libraryVariants
            is BaseAppModuleExtension -> extension.applicationVariants
            is DynamicFeatureExtension -> extension.applicationVariants
            // exhaustive to avoid potential breaking changes in future AGP releases
            else -> throw IllegalStateException("${extension.javaClass.name} from $plugin is not supported in Paparazzi")
          }
          setupPaparazzi(project, variants)
        }
      }
    }
  }

  private fun <T> setupPaparazzi(project: Project, variants: DomainObjectSet<T>) where T : BaseVariant, T : TestedVariant {
    project.addTestDependency()
    val nativePlatformFileCollection = project.setupNativePlatformDependency()

    // Create anchor tasks for all variants.
    val verifyVariants = project.tasks.register("verifyPaparazzi") {
      it.group = VERIFICATION_GROUP
      it.description = "Run screenshot tests for all variants"
    }
    val recordVariants = project.tasks.register("recordPaparazzi") {
      it.group = VERIFICATION_GROUP
      it.description = "Record golden images for all variants"
    }

    variants.all { variant ->
      val variantSlug = variant.name.capitalize(Locale.US)
      val testVariant = variant.unitTestVariant ?: return@all

      val mergeResourcesOutputDir = variant.mergeResourcesProvider.flatMap { it.outputDir }
      val mergeAssetsProvider =
        project.tasks.named("merge${variantSlug}Assets") as TaskProvider<MergeSourceSetFolders>
      val mergeAssetsOutputDir = mergeAssetsProvider.flatMap { it.outputDir }
      val projectDirectory = project.layout.projectDirectory
      val buildDirectory = project.layout.buildDirectory
      val gradleUserHomeDir = project.gradle.gradleUserHomeDir
      val reportOutputDir = buildDirectory.dir("reports/paparazzi")
      val snapshotOutputDir = project.layout.projectDirectory.dir("src/test/snapshots")

      val localResourceDirs = project
        .files(variant.sourceSets.flatMap { it.resDirectories })

      // https://android.googlesource.com/platform/tools/base/+/96015063acd3455a76cdf1cc71b23b0828c0907f/build-system/gradle-core/src/main/java/com/android/build/gradle/tasks/MergeResources.kt#875

      val moduleResourceDirs = variant.runtimeConfiguration
        .artifactsFor(ArtifactType.ANDROID_RES.type) { it is ProjectComponentIdentifier }
        .artifactFiles

      val aarExplodedDirs = variant.runtimeConfiguration
        .artifactsFor(ArtifactType.ANDROID_RES.type) { it !is ProjectComponentIdentifier }
        .artifactFiles

      val localAssetDirs = project
        .files(variant.sourceSets.flatMap { it.assetsDirectories })

      // https://android.googlesource.com/platform/tools/base/+/96015063acd3455a76cdf1cc71b23b0828c0907f/build-system/gradle-core/src/main/java/com/android/build/gradle/tasks/MergeResources.kt#875

      val moduleAssetDirs = variant.runtimeConfiguration
        .artifactsFor(ArtifactType.ASSETS.type) { it is ProjectComponentIdentifier }
        .artifactFiles

      val aarAssetDirs = variant.runtimeConfiguration
        .artifactsFor(ArtifactType.ASSETS.type) { it !is ProjectComponentIdentifier }
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
          (project.findProperty("android.nonTransitiveRClass") as? String)?.toBoolean() ?: true

        task.packageName.set(android.packageName())
        task.artifactFiles.from(packageAwareArtifactFiles)
        task.nonTransitiveRClassEnabled.set(nonTransitiveRClassEnabled)
        task.mergeResourcesOutputDir.set(buildDirectory.asRelativePathString(mergeResourcesOutputDir))
        task.targetSdkVersion.set(android.targetSdkVersion())
        task.compileSdkVersion.set(android.compileSdkVersion())
        task.mergeAssetsOutputDir.set(buildDirectory.asRelativePathString(mergeAssetsOutputDir))
        task.projectResourceDirs.from(localResourceDirs)
        task.moduleResourceDirs.from(moduleResourceDirs)
        task.aarExplodedDirs.from(aarExplodedDirs)
        task.projectAssetDirs.from(localAssetDirs.plus(moduleAssetDirs))
        task.aarAssetDirs.from(aarAssetDirs)
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
        it.description = "Record golden images for variant '${variant.name}'"
      }
      recordVariants.configure { it.dependsOn(recordTaskProvider) }
      val verifyTaskProvider = project.tasks.register("verifyPaparazzi$variantSlug", PaparazziTask::class.java) {
        it.group = VERIFICATION_GROUP
        it.description = "Run screenshot tests for variant '${variant.name}'"
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
        test.systemProperties["paparazzi.project.dir"] = projectDirectory.toString()
        test.systemProperties["paparazzi.build.dir"] = buildDirectory.get().toString()
        test.systemProperties["paparazzi.artifacts.cache.dir"] = gradleUserHomeDir.path
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

        test.doFirst {
          // Note: these are lazy properties that are not resolvable in the Gradle configuration phase.
          // They need special handling, so they're added as inputs.property above, and systemProperty here.
          test.systemProperties["paparazzi.platform.data.root"] =
            nativePlatformFileCollection.singleFile.absolutePath
          test.systemProperties["paparazzi.test.record"] = isRecordRun.get()
          test.systemProperties["paparazzi.test.verify"] = isVerifyRun.get()
        }

        test.doLast {
          val uri = reportOutputDir.get().asFile.toPath().resolve("index.html").toUri()
          test.logger.log(LIFECYCLE, "See the Paparazzi report at: $uri")
        }
      }

      recordTaskProvider.configure { it.dependsOn(testTaskProvider) }
      verifyTaskProvider.configure { it.dependsOn(testTaskProvider) }
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
    val operatingSystem = OperatingSystem.current()
    val nativeLibraryArtifactId = when {
      operatingSystem.isMacOsX -> {
        val osArch = System.getProperty("os.arch").lowercase(Locale.US)
        if (osArch.startsWith("x86")) "macosx" else "macarm"
      }
      operatingSystem.isWindows -> "win"
      else -> "linux"
    }

    val nativePlatformConfiguration = configurations.create("nativePlatform")
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
    val dependency = if (isInternal()) {
      dependencies.project(mapOf("path" to ":paparazzi"))
    } else {
      dependencies.create("app.cash.paparazzi:paparazzi:$VERSION")
    }
    configurations.getByName("testImplementation").dependencies.add(dependency)
  }

  private fun Project.isInternal(): Boolean {
    return properties["app.cash.paparazzi.internal"].toString() == "true"
  }

  private fun BaseExtension.packageName(): String = namespace ?: ""

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
