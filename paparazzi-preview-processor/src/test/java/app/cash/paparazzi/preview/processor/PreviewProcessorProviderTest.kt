package app.cash.paparazzi.preview.processor

import app.cash.paparazzi.preview.processor.utils.DefaultComposeSource
import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.kspAllWarningsAsErrors
import com.tschuchort.compiletesting.kspArgs
import com.tschuchort.compiletesting.kspIncremental
import com.tschuchort.compiletesting.kspSourcesDir
import com.tschuchort.compiletesting.symbolProcessorProviders
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PreviewProcessorProviderTest {
  @get:Rule val temporaryFolder = TemporaryFolder()

  private val previewProcessor = PreviewProcessorProvider()

  @Test
  fun empty() {
    val kspCompileResult = compile(
      SourceFile.kotlin(
        "SamplePreview.kt",
        """
                package test

                import androidx.compose.runtime.Composable
                import androidx.compose.ui.tooling.preview.Preview
                import app.cash.paparazzi.annotations.Paparazzi

                @Preview
                @Composable
                fun SamplePreview() = Unit
                """
      )
    )

    assertThat(kspCompileResult.result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)

    val generatedFiles = kspCompileResult.generatedFiles.sorted()
    assertThat(generatedFiles.size).isEqualTo(2)
    val file = generatedFiles[1]
    assertThat(file.name).isEqualTo("paparazziVariant.txt")
    file.withText {
      // Assertion for variant has "sources" as we can't specify the variant name in testing KSP
      assertThat(it).isEqualTo(
        """
          sources
        """.trimIndent()
      )
    }

    val file2 = generatedFiles[0]
    assertThat(file2.name).isEqualTo("PaparazziPreviews.kt")
    file2.withText {
      assertThat(it).isEqualTo(
        """
          package test

          internal val paparazziPreviews = listOf<app.cash.paparazzi.annotations.PaparazziPreviewData>(
            app.cash.paparazzi.annotations.PaparazziPreviewData.Empty,
          )
        """.trimIndent()
      )
    }
  }

  @Test
  fun default() {
    val kspCompileResult = compile(
      SourceFile.kotlin(
        "SamplePreview.kt",
        """
                package test

                import androidx.compose.runtime.Composable
                import androidx.compose.ui.tooling.preview.Preview
                import app.cash.paparazzi.annotations.Paparazzi


                @Paparazzi
                @Preview
                @Composable
                fun SamplePreview() = Unit
                """
      )
    )

    assertThat(kspCompileResult.result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)

    val generatedFiles = kspCompileResult.generatedFiles.sorted()
    assertThat(generatedFiles.size).isEqualTo(2)
    val file = generatedFiles[1]
    assertThat(file.name).isEqualTo("paparazziVariant.txt")
    file.withText {
      // Assertion for variant has "sources" as we can't specify the variant name in testing KSP
      assertThat(it).isEqualTo(
        """
          sources
        """.trimIndent()
      )
    }

    val file2 = generatedFiles.sorted()[0]
    assertThat(file2.name).isEqualTo("PaparazziPreviews.kt")
    file2.withText {
      assertThat(it).isEqualTo(
        """
          package test

          internal val paparazziPreviews = listOf<app.cash.paparazzi.annotations.PaparazziPreviewData>(
            app.cash.paparazzi.annotations.PaparazziPreviewData.Default(
              snapshotName = "SamplePreview_SamplePreview",
              composable = { test.SamplePreview() },
            ),
          )
        """.trimIndent()
      )
    }
  }

  @Test
  fun privatePreview() {
    val kspCompileResult = compile(
      SourceFile.kotlin(
        "SamplePreview.kt",
        """
                package test

                import androidx.compose.runtime.Composable
                import androidx.compose.ui.tooling.preview.Preview
                import app.cash.paparazzi.annotations.Paparazzi


                @Paparazzi
                @Preview
                @Composable
                private fun SamplePreview() = Unit
                """
      )
    )

    assertThat(kspCompileResult.result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)

    val file = kspCompileResult.generatedFiles.sorted()[0]
    assertThat(file.name).isEqualTo("PaparazziPreviews.kt")
    file.withText {
      assertThat(it).isEqualTo(
        """
          package test

          internal val paparazziPreviews = listOf<app.cash.paparazzi.annotations.PaparazziPreviewData>(
            app.cash.paparazzi.annotations.PaparazziPreviewData.Error(
              snapshotName = "SamplePreview_SamplePreview",
              message = "test.SamplePreview is private. Make it internal or public to generate a snapshot.",
            ),
          )
        """.trimIndent()
      )
    }
  }

  @Test
  fun previewParameters() {
    val kspCompileResult = compile(
      SourceFile.kotlin(
        "SamplePreview.kt",
        """
                package test

                import androidx.compose.runtime.Composable
                import androidx.compose.ui.tooling.preview.Preview
                import androidx.compose.ui.tooling.preview.PreviewParameter
                import androidx.compose.ui.tooling.preview.PreviewParameterProvider
                import app.cash.paparazzi.annotations.Paparazzi


                @Paparazzi
                @Preview
                @Composable
                fun SamplePreview(
                  @PreviewParameter(SamplePreviewParameter::class) text: String,
                ) = Unit


                object SamplePreviewParameter: PreviewParameterProvider<String> {
                  override val values: Sequence<String> =
                    sequenceOf("test")
                }
                """
      )
    )

    assertThat(kspCompileResult.result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)

    val file = kspCompileResult.generatedFiles.sorted()[0]
    assertThat(file.name).isEqualTo("PaparazziPreviews.kt")
    file.withText {
      assertThat(it).isEqualTo(
        """
          package test

          internal val paparazziPreviews = listOf<app.cash.paparazzi.annotations.PaparazziPreviewData>(
            app.cash.paparazzi.annotations.PaparazziPreviewData.Error(
              snapshotName = "SamplePreview_SamplePreview",
              message = "test.SamplePreview preview uses PreviewParameters which aren't currently supported.",
            ),
          )
        """.trimIndent()
      )
    }
  }

  @Test
  fun multiplePreviews() {
    val kspCompileResult = compile(
      SourceFile.kotlin(
        "SamplePreview.kt",
        """
                package test

                import androidx.compose.runtime.Composable
                import androidx.compose.ui.tooling.preview.Preview
                import app.cash.paparazzi.annotations.Paparazzi


                @Paparazzi
                @Preview
                @Preview(name = "Night Pixel 4", uiMode = 0x20, device = "id:pixel_4") // uiMode maps to android.content.res.Configuration.UI_MODE_NIGHT_YES
                @Composable
                fun SamplePreview() = Unit
                """
      )
    )

    assertThat(kspCompileResult.result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)

    val generatedFiles = kspCompileResult.generatedFiles.sorted()
    val file2 = generatedFiles[0]
    assertThat(file2.name).isEqualTo("PaparazziPreviews.kt")
    file2.withText {
      assertThat(it).isEqualTo(
        """
          package test

          internal val paparazziPreviews = listOf<app.cash.paparazzi.annotations.PaparazziPreviewData>(
            app.cash.paparazzi.annotations.PaparazziPreviewData.Default(
              snapshotName = "SamplePreview_SamplePreview",
              composable = { test.SamplePreview() },
            ),
            app.cash.paparazzi.annotations.PaparazziPreviewData.Default(
              snapshotName = "SamplePreview_SamplePreview",
              composable = { test.SamplePreview() },
            ),
          )
        """.trimIndent()
      )
    }
  }

  private fun File.withText(doWithText: (String) -> Unit) {
    inputStream().use {
      doWithText(String(it.readBytes()).trimIndent())
    }
  }

  private fun prepareCompilation(vararg sourceFiles: SourceFile): KotlinCompilation =
    KotlinCompilation()
      .apply {
        workingDir = File(temporaryFolder.root, "debug")
        inheritClassPath = true
        symbolProcessorProviders = listOf(previewProcessor)
        sources = sourceFiles.asList() + DefaultComposeSource
        verbose = false
        kspIncremental = true
        kspAllWarningsAsErrors = true
        kspArgs["app.cash.paparazzi.preview.namespace"] = "test"
      }

  private fun compile(vararg sourceFiles: SourceFile): KspCompileResult {
    val compilation = prepareCompilation(*sourceFiles)
    val result = compilation.compile()
    return KspCompileResult(
      result,
      findGeneratedFiles(compilation)
    )
  }

  private fun findGeneratedFiles(compilation: KotlinCompilation): List<File> {
    return compilation.kspSourcesDir
      .walkTopDown()
      .filter { it.isFile }
      .toList()
  }
}
