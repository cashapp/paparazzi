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

import app.cash.paparazzi.gradle.instrumentation.ResourcesCompatVisitorFactory
import app.cash.paparazzi.gradle.reporting.DiffImage
import app.cash.paparazzi.gradle.reporting.PaparazziTestReporter
import app.cash.paparazzi.gradle.utils.artifactViewFor
import app.cash.paparazzi.gradle.utils.capitalize
import app.cash.paparazzi.gradle.utils.relativize
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.instrumentation.FramesComputationMode
import com.android.build.api.instrumentation.InstrumentationScope
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.DynamicFeatureAndroidComponentsExtension
import com.android.build.api.variant.HasUnitTest
import com.android.build.api.variant.LibraryAndroidComponentsExtension
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.type.ArtifactTypeDefinition
import org.gradle.api.artifacts.type.ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE
import org.gradle.api.file.Directory
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.artifacts.transform.UnzipTransform
import org.gradle.api.internal.tasks.testing.report.TestReporter
import org.gradle.api.logging.LogLevel.LIFECYCLE
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.reporting.ReportingExtension
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.options.Option
import org.gradle.api.tasks.testing.AbstractTestTask
import org.gradle.api.tasks.testing.Test
import org.gradle.internal.operations.BuildOperationExecutor
import org.gradle.internal.operations.BuildOperationRunner
import org.gradle.internal.os.OperatingSystem
import org.gradle.language.base.plugins.LifecycleBasePlugin.VERIFICATION_GROUP
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinAndroidTarget
import java.util.Locale
import javax.inject.Inject
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@Suppress("unused")
public class PaparazziPlugin @Inject constructor(
  private val providerFactory: ProviderFactory,
  private val buildOperationRunner: BuildOperationRunner,
  private val buildOperationExecutor: BuildOperationExecutor
) : Plugin<Project> {
  override fun apply(project: Project) {
    val supportedPlugins = listOf("com.android.application", "com.android.library", "com.android.dynamic-feature")
    project.afterEvaluate {
      check(supportedPlugins.any { project.plugins.hasPlugin(it) }) {
        "One of ${supportedPlugins.joinToString(", ")} must be applied for Paparazzi to work properly."
      }
      project.plugins.withId("org.jetbrains.kotlin.multiplatform") {
        val kmpExtension = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
        check(kmpExtension.targets.any { target -> target is KotlinAndroidTarget }) {
          "There must be an Android target configured when using Paparazzi with the Kotlin Multiplatform Plugin"
        }
      }
    }

    supportedPlugins.forEach { plugin ->
      project.plugins.withId(plugin) {
        val androidComponents = project.extensions.getByType(AndroidComponentsExtension::class.java)
        when (androidComponents) {
          is LibraryAndroidComponentsExtension,
          is ApplicationAndroidComponentsExtension,
          is DynamicFeatureAndroidComponentsExtension -> Unit
          // exhaustive to avoid potential breaking changes in future AGP releases
          else -> error("${androidComponents.javaClass.name} from $plugin is not supported in Paparazzi")
        }
        setupPaparazzi(project, androidComponents)
      }
    }
  }

  private fun setupPaparazzi(project: Project, extension: AndroidComponentsExtension<*, *, *>) {
    project.addTestDependency()
    val layoutlibNativeRuntimeFileCollection = project.setupLayoutlibRuntimeDependency()
    val layoutlibResourcesFileCollection = project.setupLayoutlibResourcesDependency()
    val snapshotOutputDir = project.layout.projectDirectory.dir("src/test/snapshots")

    // Create anchor tasks for all variants.
    val verifyVariants = project.tasks.register("verifyPaparazzi") {
      it.group = VERIFICATION_GROUP
      it.description = "Run screenshot tests for all variants"
    }
    val recordVariants = project.tasks.register("recordPaparazzi") {
      it.group = VERIFICATION_GROUP
      it.description = "Record golden images for all variants"
    }
    val cleanRecordVariants = project.tasks.register("cleanRecordPaparazzi") {
      it.group = VERIFICATION_GROUP
      it.description = "Clean and record golden images for all variants"
    }
    val deleteSnapshots = project.tasks.register("deletePaparazziSnapshots", Delete::class.java) {
      it.group = VERIFICATION_GROUP
      it.description = "Delete all golden images"
      val files = project.fileTree(snapshotOutputDir) { tree ->
        tree.include("**/*.png")
        tree.include("**/*.mov")
      }
      it.delete(files)
    }

    // We need to pull target sdk as defined from DSL otherwise it gets set to some default value when resolving during [onVariants]
    var targetSdkVersion: String? = null
    extension.finalizeDsl { androidExtension ->
      targetSdkVersion = androidExtension?.targetSdkVersion() ?: DEFAULT_COMPILE_SDK_VERSION.toString()
    }

    extension.onVariants { variant ->
      val variantSlug = variant.name.capitalize()
      val testVariant = (variant as? HasUnitTest)?.unitTest ?: return@onVariants

      val projectDirectory = project.layout.projectDirectory
      val buildDirectory = project.layout.buildDirectory
      val gradleUserHomeDir = project.gradle.gradleUserHomeDir
      val reportOutputDir =
        project.extensions.getByType(ReportingExtension::class.java).baseDirectory.dir("paparazzi/${variant.name}")

      val testInstrumentation = testVariant.instrumentation
      testInstrumentation.transformClassesWith(
        ResourcesCompatVisitorFactory::class.java,
        InstrumentationScope.ALL
      ) { }
      testInstrumentation.setAsmFramesComputationMode(
        FramesComputationMode.COMPUTE_FRAMES_FOR_INSTRUMENTED_METHODS
      )

      val sources = AndroidVariantSources(variant)

      val writeResourcesTask = project.tasks.register(
        "preparePaparazzi${variantSlug}Resources",
        PrepareResourcesTask::class.java
      ) { task ->
        val nonTransitiveRClassEnabled =
          project.providers.gradleProperty("android.nonTransitiveRClass").orNull?.toBoolean() ?: true
        val gradleHomeDir = projectDirectory.dir(project.gradle.gradleUserHomeDir.path)

        task.packageName.set(variant.namespace)
        task.artifactFiles.from(sources.packageAwareArtifactFiles)
        task.nonTransitiveRClassEnabled.set(nonTransitiveRClassEnabled)
        task.targetSdkVersion.set(targetSdkVersion)
        task.projectResourceDirs.set(sources.localResourceDirs.relativize(projectDirectory))
        task.moduleResourceDirs.set(sources.moduleResourceDirs.relativize(projectDirectory))
        task.aarExplodedDirs.set(sources.aarExplodedDirs.relativize(gradleHomeDir))
        task.projectAssetDirs.set(
          sources.localAssetDirs.relativize(projectDirectory)
            .zip(sources.moduleAssetDirs.relativize(projectDirectory), List<String>::plus)
        )
        task.aarAssetDirs.set(sources.aarAssetDirs.relativize(gradleHomeDir))
        task.paparazziResources.set(buildDirectory.file("intermediates/paparazzi/${variant.name}/resources.json"))
      }

      val testVariantSlug = testVariant.name.capitalize()

      project.tasks.named { it == "test$testVariantSlug" }
        .configureEach { it.dependsOn(writeResourcesTask) }

      val recordTaskProvider = project.tasks.register("recordPaparazzi$variantSlug", PaparazziTask::class.java) {
        it.group = VERIFICATION_GROUP
        it.description = "Record golden images for variant '${variant.name}'"
        it.mustRunAfter(deleteSnapshots)
      }
      recordVariants.configure { it.dependsOn(recordTaskProvider) }
      val cleanRecordTaskProvider = project.tasks.register("cleanRecordPaparazzi$variantSlug") {
        it.group = VERIFICATION_GROUP
        it.description = "Clean and record golden images for variant '${variant.name}'"
        it.dependsOn(deleteSnapshots, recordTaskProvider)
      }
      cleanRecordVariants.configure { it.dependsOn(cleanRecordTaskProvider) }
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

      val overwriteOnMaxPercentDifferenceProvider = project.overwriteOnMaxPercentDifferenceProvider()
      val paparazziGradlePropertiesProvider =
        project.providers.gradlePropertiesPrefixedBy("app.cash.paparazzi")
      val failureDir = buildDirectory.dir("paparazzi/failures")
      val testTaskProvider =
        project.tasks.withType(Test::class.java).named { it == "test$testVariantSlug" }
      testTaskProvider.configureEach { test ->
        test.setTestReporter(
          PaparazziTestReporter(
            buildOperationRunner = buildOperationRunner,
            buildOperationExecutor = buildOperationExecutor,
            diffRegistryFactory = createDiffRegistryFactory(failureDir, isVerifyRun)
          )
        )

        test.systemProperties["paparazzi.test.resources"] =
          writeResourcesTask.flatMap { it.paparazziResources.asFile }.get().path
        test.systemProperties["paparazzi.project.dir"] = projectDirectory.toString()
        test.systemProperties["paparazzi.build.dir"] = buildDirectory.get().toString()
        test.systemProperties["paparazzi.report.dir"] = reportOutputDir.get().toString()
        test.systemProperties["paparazzi.snapshot.dir"] = snapshotOutputDir.toString()
        test.systemProperties["paparazzi.artifacts.cache.dir"] = gradleUserHomeDir.path

        test.inputs.property("paparazzi.test.record", isRecordRun)
        test.inputs.property("paparazzi.test.verify", isVerifyRun)
        test.inputs.property("paparazzi.gradleProperties", paparazziGradlePropertiesProvider)

        test.inputs.files(sources.localResourceDirs)
          .withPropertyName("paparazzi.localResourceDirs")
          .withPathSensitivity(PathSensitivity.RELATIVE)
        test.inputs.files(sources.moduleResourceDirs)
          .withPropertyName("paparazzi.moduleResourceDirs")
          .withPathSensitivity(PathSensitivity.RELATIVE)
        test.inputs.files(sources.localAssetDirs)
          .withPropertyName("paparazzi.localAssetDirs")
          .withPathSensitivity(PathSensitivity.RELATIVE)
        test.inputs.files(sources.moduleAssetDirs)
          .withPropertyName("paparazzi.moduleAssetDirs")
          .withPathSensitivity(PathSensitivity.RELATIVE)
        test.inputs.files(layoutlibNativeRuntimeFileCollection)
          .withPropertyName("paparazzi.nativeRuntime")
          .withPathSensitivity(PathSensitivity.NONE)
        test.inputs.files(layoutlibResourcesFileCollection)
          .withPropertyName("paparazzi.layoutlib.resources")
          .withPathSensitivity(PathSensitivity.NONE)
        test.inputs.file(writeResourcesTask.flatMap { it.paparazziResources })
          .withPropertyName("paparazzi.test.resources")
          .withPathSensitivity(PathSensitivity.NONE)
        test.inputs.property("paparazzi.test.overwriteOnMaxPercentDifference", overwriteOnMaxPercentDifferenceProvider)
          .optional(true)

        test.inputs.dir(
          isVerifyRun.flatMap {
            project.objects.directoryProperty().apply {
              set(if (it) snapshotOutputDir else null)
            }
          }
        ).withPropertyName("paparazzi.snapshot.input.dir")
          .withPathSensitivity(PathSensitivity.RELATIVE)
          .optional()

        test.outputs.dir(
          isRecordRun.flatMap {
            project.objects.directoryProperty().apply {
              set(if (it) snapshotOutputDir else null)
            }
          }
        ).withPropertyName("paparazzi.snapshots.output.dir")
          .optional()

        test.outputs.dir(reportOutputDir).withPropertyName("paparazzi.report.dir")
        test.outputs.dir(failureDir)
          .withPropertyName("paparazzi.failures.dir")
          .optional()

        test.doFirst {
          // Note: these are lazy properties that are not resolvable in the Gradle configuration phase.
          // They need special handling, so they're added as inputs.property above, and systemProperty here.
          test.systemProperties.putAll(paparazziGradlePropertiesProvider.get())
          test.systemProperties["paparazzi.layoutlib.runtime.root"] =
            layoutlibNativeRuntimeFileCollection.singleFile.absolutePath
          test.systemProperties["paparazzi.layoutlib.resources.root"] =
            layoutlibResourcesFileCollection.singleFile.absolutePath
          test.systemProperties["paparazzi.test.record"] = isRecordRun.get()
          test.systemProperties["paparazzi.test.record.overwriteOnMaxPercentDifference"] =
            overwriteOnMaxPercentDifferenceProvider.orNull == "true"
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

  private fun createDiffRegistryFactory(
    failureDirProperty: Provider<Directory>,
    isVerifyRun: Provider<Boolean>
  ): () -> Map<Pair<String, String>, DiffImage> =
    {
      val failureDir = failureDirProperty.get().asFile
      if (isVerifyRun.get() && failureDir.exists()) {
        failureDir.listFiles()
          ?.filter { it.name.startsWith("delta-") }
          ?.associate { diff ->
            // TODO: read from failure diff metadata file instead of brittle parsing
            val nameSegments = diff.name.split("_", limit = 3)
            val testClassPackage = nameSegments[0].replace("delta-", "")
            val testClass = "$testClassPackage.${nameSegments[1]}"
            val testMethodWithLabel = nameSegments[2].removeSuffix(".png")

            Pair(testClass, testMethodWithLabel) to DiffImage(
              path = diff.path,
              base64EncodedImage =
              @OptIn(ExperimentalEncodingApi::class)
              Base64.encode(diff.readBytes())
            )
          } ?: emptyMap()
      } else {
        emptyMap()
      }
    }

  public abstract class PaparazziTask : DefaultTask() {
    @Option(option = "tests", description = "Sets test class or method name to be included, '*' is supported.")
    public open fun setTestNameIncludePatterns(testNamePattern: List<String>): PaparazziTask {
      project.tasks.withType(Test::class.java).configureEach {
        it.setTestNameIncludePatterns(testNamePattern)
      }
      return this
    }
  }

  private fun <T : AbstractTestTask> T.setTestReporter(testReporter: TestReporter) {
    AbstractTestTask::class.java
      .getDeclaredMethod("setTestReporter", TestReporter::class.java).apply {
        isAccessible = true
        invoke(this@setTestReporter, testReporter)
      }
  }

  private fun Project.setupLayoutlibRuntimeDependency(): FileCollection {
    val operatingSystem = OperatingSystem.current()
    val nativeLibraryArtifactId = when {
      operatingSystem.isMacOsX -> {
        val osArch = System.getProperty("os.arch").lowercase(Locale.US)
        if (osArch.startsWith("x86")) "mac" else "mac-arm"
      }

      operatingSystem.isWindows -> "win"
      else -> "linux"
    }

    val nativeRuntimeConfiguration = configurations.create("layoutlibRuntime")
    nativeRuntimeConfiguration.dependencies.add(
      dependencies.create("com.android.tools.layoutlib:layoutlib-runtime:$NATIVE_LIB_VERSION:$nativeLibraryArtifactId")
    )
    dependencies.registerTransform(UnzipTransform::class.java) { transform ->
      transform.from.attribute(ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.JAR_TYPE)
      transform.to.attribute(ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.DIRECTORY_TYPE)
    }

    return nativeRuntimeConfiguration
      .artifactViewFor(ArtifactTypeDefinition.DIRECTORY_TYPE)
      .files
  }

  private fun Project.setupLayoutlibResourcesDependency(): FileCollection {
    val layoutlibResourcesConfiguration = configurations.create("layoutlibResources")
    layoutlibResourcesConfiguration.dependencies.add(
      dependencies.create("com.android.tools.layoutlib:layoutlib-resources:$NATIVE_LIB_VERSION")
    )
    dependencies.registerTransform(UnzipTransform::class.java) { transform ->
      transform.from.attribute(ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.JAR_TYPE)
      transform.to.attribute(ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.DIRECTORY_TYPE)
    }

    return layoutlibResourcesConfiguration
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

  private fun Project.isInternal(): Boolean = providers.gradleProperty("app.cash.paparazzi.internal").orNull == "true"

  private fun Project.overwriteOnMaxPercentDifferenceProvider(): Provider<String> =
    providers.gradleProperty("app.cash.paparazzi.overwriteOnMaxPercentDifference")

  private fun Any.targetSdkVersion(): String =
    ((this as? CommonExtension<*, *, *, *, *, *>)?.testOptions?.targetSdk ?: DEFAULT_COMPILE_SDK_VERSION).toString()

  private fun Provider<List<Directory>>?.relativize(directory: Directory): Provider<List<String>> =
    this?.map { dirs -> dirs.map { directory.relativize(it.asFile) } }
      ?: providerFactory.provider { emptyList() }
}

private const val DEFAULT_COMPILE_SDK_VERSION = 36
