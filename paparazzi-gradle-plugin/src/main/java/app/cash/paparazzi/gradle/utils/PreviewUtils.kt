package app.cash.paparazzi.gradle.utils

import app.cash.paparazzi.gradle.GeneratePreviewTestFileTask
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.HasUnitTest
import org.gradle.api.Project
import org.gradle.language.base.plugins.LifecycleBasePlugin.VERIFICATION_GROUP
import java.io.File
import java.util.Locale

private const val TEST_SOURCE_DIR = "build/generated/source/paparazzi"
private const val KSP_SOURCE_DIR = "build/generated/ksp"
private const val PREVIEW_DATA_FILE = "PaparazziPreviews.kt"
private const val PREVIEW_TEST_FILE = "PreviewTests.kt"

internal fun Project.registerGeneratePreviewTask(extension: AndroidComponentsExtension<*, *, *>) {
  extension.onVariants { variant ->
    val testVariant = (variant as? HasUnitTest)?.unitTest ?: return@onVariants
    val testVariantSlug = testVariant.name.capitalize()

    val buildType = testVariant.buildType
    val buildTypeCap = testVariant.buildType?.capitalize()

    val taskName = "paparazziGeneratePreview${testVariantSlug}Kotlin"
    val taskProvider = tasks.register(taskName, GeneratePreviewTestFileTask::class.java) { task ->
      task.group = VERIFICATION_GROUP
      task.description =
        "Generates the preview test class to the test source set for $testVariantSlug"

      task.dependsOn("ksp${buildTypeCap}Kotlin")
    }

    val testSourceDir =
      "$projectDir${File.separator}$TEST_SOURCE_DIR${File.separator}${buildType}UnitTest"
    testVariant.sources.java?.addStaticSourceDirectory(testSourceDir)

    // test compilation depends on the task
    project.tasks.named {
      it == "compile${testVariantSlug}Kotlin" ||
        it == "generate${testVariantSlug}LintModel" ||
        it == "lintAnalyze$testVariantSlug"
    }.configureEach { it.dependsOn(taskProvider) }
    // run task before processing symbols
    project.tasks.named { it == "ksp${testVariantSlug}Kotlin" }
      .configureEach { it.mustRunAfter(taskProvider) }

    gradle.taskGraph.whenReady {
      taskProvider.configure { task ->
        // Test variant appends .test to the namespace
        val namespace = testVariant.namespace.get().replace(".test$".toRegex(), "")
        val namespaceDir = namespace.replace(".", File.separator)
        val previewTestDir = "$testSourceDir${File.separator}$namespaceDir"

        // Defaulted to true unless specified in properties
        task.enabled = project.providers.gradleProperty(
          "app.cash.paparazzi.annotation.generateTestClass"
        ).orNull?.toBoolean() != false

        // Optional input if KSP doesn't output preview annotation file
        task.paparazziPreviewsFile.set(
          File(
            "$projectDir${File.separator}$KSP_SOURCE_DIR${File.separator}${buildType}${File.separator}kotlin${File.separator}$namespaceDir${File.separator}$PREVIEW_DATA_FILE"
          )
        )
        task.namespace.set(namespace)

        // Outputs
        task.previewTestOutputDir.set(File(previewTestDir))
        task.previewTestOutputFile.set(File("$previewTestDir${File.separator}$PREVIEW_TEST_FILE"))
      }
    }
  }
}

private fun String.capitalize() =
  replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
