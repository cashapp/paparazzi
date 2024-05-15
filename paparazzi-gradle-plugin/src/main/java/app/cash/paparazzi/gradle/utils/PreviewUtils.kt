package app.cash.paparazzi.gradle.utils

import app.cash.paparazzi.gradle.PaparazziExtension
import com.android.build.gradle.TestedExtension
import com.android.build.gradle.api.BaseVariant
import com.android.build.gradle.internal.api.TestedVariant
import org.gradle.api.DomainObjectSet
import org.gradle.api.Project
import java.io.File
import java.util.Locale

private const val TEST_SOURCE_DIR = "build/generated/source/paparazzi"
private const val KSP_SOURCE_DIR = "build/generated/ksp"
private const val PREVIEW_DATA_FILE = "paparazziPreviews.kt"
private const val PREVIEW_TEST_FILE = "PreviewTests.kt"

internal fun <T> Project.registerGeneratePreviewTask(
  variants: DomainObjectSet<T>,
  config: PaparazziExtension
) where T : BaseVariant, T : TestedVariant {
  val extension = extensions.getByType(TestedExtension::class.java)
  val namespace = extension.namespace
  val namespaceDir = namespace?.replace(".", "/")

  variants.all { variant ->
    val typeName = variant.buildType.name
    val typeNameCap = typeName.capitalize()

    val testSourceDir = "$projectDir/$TEST_SOURCE_DIR/${typeName}UnitTest"
    val previewTestDir = "$testSourceDir/$namespaceDir"

    extension.sourceSets.getByName("test$typeNameCap").java {
      srcDir(testSourceDir)
    }

    if (config.generatePreviewTestClass.get()) {
      val taskName = "paparazziGeneratePreview${typeNameCap}UnitTestKotlin"
      tasks.register(taskName) { task ->
        task.description = "Generates the preview test class to the test source set for $typeName"

        task.dependsOn("ksp${typeNameCap}Kotlin")
        task.inputs.file(
          "$projectDir/$KSP_SOURCE_DIR/$typeName/kotlin/$namespaceDir/$PREVIEW_DATA_FILE"
        )
        task.outputs.dir(previewTestDir)
        task.outputs.file("$previewTestDir/$PREVIEW_TEST_FILE")
        task.outputs.cacheIf { true }

        task.doLast {
          File(previewTestDir).mkdirs()
          File(previewTestDir, PREVIEW_TEST_FILE).writeText(
            buildString {
              appendLine("package $namespace")
              append(PREVIEW_TEST_SOURCE)
            }
          )
        }
      }

      // test compilation depends on the task
      tasks.findByName("compile${typeNameCap}UnitTestKotlin")?.dependsOn(taskName)
      // run task before processing symbols
      tasks.findByName("ksp${typeNameCap}UnitTestKotlin")?.mustRunAfter(taskName)
    }
  }
}

private fun String.capitalize() =
  replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }

private const val PREVIEW_TEST_SOURCE = """
import app.cash.paparazzi.Paparazzi
import app.cash.paparazzi.annotations.PaparazziPreviewData
import app.cash.paparazzi.preview.PaparazziValuesProvider
import app.cash.paparazzi.preview.snapshot
import com.android.ide.common.rendering.api.SessionParams.RenderingMode.SHRINK
import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterInjector
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(TestParameterInjector::class)
class PreviewTests(
  @TestParameter(valuesProvider = PreviewConfigValuesProvider::class)
  private val preview: PaparazziPreviewData,
) {
  private class PreviewConfigValuesProvider : PaparazziValuesProvider(paparazziPreviews)

  @get:Rule
  val paparazzi = Paparazzi(
    renderingMode = SHRINK,
  )

  @Test
  fun preview() {
    assumeTrue(preview !is PaparazziPreviewData.Empty)
    paparazzi.snapshot(preview)
  }
}
"""
