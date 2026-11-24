@file:OptIn(ExperimentalCompilerApi::class)

package app.cash.paparazzi.preview.processor

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.configureKsp
import com.tschuchort.compiletesting.kspIncremental
import com.tschuchort.compiletesting.kspProcessorOptions
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PreviewProcessorProviderTest {
  @get:Rule val temporaryFolder = TemporaryFolder()

  private val kspRoot: File
    get() = temporaryFolder.root.resolve("debug/ksp/sources")
  private val variantFile: File
    get() = kspRoot.resolve("resources/$TEST_NAMESPACE/paparazziVariant.txt")
  private val previewsFile: File
    get() = kspRoot.resolve("kotlin/$TEST_NAMESPACE/PaparazziPreviews.kt")

  @Test
  fun noAnnotatedPreviews() {
    val compilation = prepareCompilation(
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
        """.trimIndent()
      )
    )
    val result = compilation.compile()

    assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
    assertThat(result.messages).contains("i: [ksp] No functions found with @Paparazzi annotation.")
    // Assertion for variant has "sources" as we can't specify the variant name in testing KSP
    assertThat(variantFile.readText()).isEqualTo("sources")
  }

  @Test
  fun simplePreview() {
    val compilation = prepareCompilation(
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
        """.trimIndent()
      )
    )
    val result = compilation.compile()

    assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
    // Assertion for variant has "sources" as we can't specify the variant name in testing KSP
    assertThat(variantFile.readText()).isEqualTo("sources")
    assertThat(previewsFile.readText())
      .isEqualTo(
        """
        package test

        internal val paparazziPreviews = listOf<app.cash.paparazzi.preview.runtime.PaparazziPreviewData>(
          app.cash.paparazzi.preview.runtime.PaparazziPreviewData(
            snapshotName = "SamplePreview_SamplePreview",
            composable = { test.SamplePreview() },
          ),
        )
        """.trimIndent()
      )
  }

  @Test
  fun privatePreview() {
    val compilation = prepareCompilation(
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
        """.trimIndent()
      )
    )
    val result = compilation.compile()

    assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
    assertThat(result.messages)
      .contains("e: [ksp] test.SamplePreview is private. Make it internal or public to generate a snapshot.")
  }

  @Test
  fun previewParameters() {
    val compilation = prepareCompilation(
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
          override val values: Sequence<String> = sequenceOf("test")
        }
        """
      )
    )
    val result = compilation.compile()

    assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
    assertThat(result.messages)
      .contains("e: [ksp] test.SamplePreview preview uses @PreviewParameters which aren't currently supported.")
  }

  @Test
  fun multiplePreviews() {
    val compilation = prepareCompilation(
      SourceFile.kotlin(
        "SamplePreview.kt",
        """
        package test

        import androidx.compose.runtime.Composable
        import androidx.compose.ui.tooling.preview.Preview
        import app.cash.paparazzi.annotations.Paparazzi

        @Paparazzi
        @Preview
        @Preview(
           name = "Night Pixel 4",
           uiMode = 0x20, // uiMode maps to android.content.res.Configuration.UI_MODE_NIGHT_YES
           device = "id:pixel_4"
        )
        @Composable
        fun SamplePreview() = Unit
        """.trimIndent()
      )
    )
    val result = compilation.compile()

    assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
    assertThat(previewsFile.readText())
      .isEqualTo(
        """
        package test

        internal val paparazziPreviews = listOf<app.cash.paparazzi.preview.runtime.PaparazziPreviewData>(
          app.cash.paparazzi.preview.runtime.PaparazziPreviewData(
            snapshotName = "SamplePreview_SamplePreview",
            composable = { test.SamplePreview() },
          ),
          app.cash.paparazzi.preview.runtime.PaparazziPreviewData(
            snapshotName = "SamplePreview_SamplePreview",
            composable = { test.SamplePreview() },
          ),
        )
        """.trimIndent()
      )
  }

  private fun prepareCompilation(vararg sourceFiles: SourceFile): KotlinCompilation =
    KotlinCompilation()
      .apply {
        workingDir = File(temporaryFolder.root, "debug")
        inheritClassPath = true
        sources =
          sourceFiles.asList() + COMPOSE_SOURCES + PAPARAZZI_ANNOTATION_SOURCE + PAPARAZZI_PREVIEW_DATA_RUNTIME_SOURCE
        verbose = false
        // Needed for @PreviewParameterProvider annotation that uses `@JvmDefaultWithCompatibility`
        kotlincArguments = listOf("-Xjvm-default=all")

        configureKsp {
          allWarningsAsErrors = true
          kspProcessorOptions += "app.cash.paparazzi.preview.namespace" to TEST_NAMESPACE
          kspIncremental = true
          symbolProcessorProviders += PreviewProcessorProvider()
        }
      }

  private companion object {
    private const val TEST_NAMESPACE = "test"
    private val COMPOSE_SOURCES =
      listOf(
        SourceFile.kotlin(
          "Composable.kt",
          """
          package androidx.compose.runtime

          @Retention(AnnotationRetention.BINARY)
          @Target(
              AnnotationTarget.FUNCTION,
              AnnotationTarget.TYPE,
              AnnotationTarget.TYPE_PARAMETER,
              AnnotationTarget.PROPERTY_GETTER
          )
          annotation class Composable
          """.trimIndent()
        ),
        SourceFile.kotlin(
          "PreviewAnnotation.kt",
          """
          package androidx.compose.ui.tooling.preview

          @Retention(AnnotationRetention.BINARY)
          @Target(
              AnnotationTarget.ANNOTATION_CLASS,
              AnnotationTarget.FUNCTION
          )
          @Repeatable
          annotation class Preview(
            val name: String = "",
            val group: String = "",
            val apiLevel: Int = -1,
            val widthDp: Int = -1,
            val heightDp: Int = -1,
            val locale: String = "",
            val fontScale: Float = 1f,
            val showSystemUi: Boolean = false,
            val showBackground: Boolean = false,
            val backgroundColor: Long = 0,
            val uiMode: Int = 0,
            val device: String = "",
            val wallpaper: Int = 0,
          )
          """.trimIndent()
        ),
        SourceFile.kotlin(
          "PreviewParameter.kt",
          """
          package androidx.compose.ui.tooling.preview

          import kotlin.jvm.JvmDefaultWithCompatibility
          import kotlin.reflect.KClass

          @JvmDefaultWithCompatibility
          interface PreviewParameterProvider<T> {
              val values: Sequence<T>
              val count get() = values.count()
          }

          annotation class PreviewParameter(
              val provider: KClass<out PreviewParameterProvider<*>>,
              val limit: Int = Int.MAX_VALUE
          )
          """.trimIndent()
        )
      )

    private val PAPARAZZI_ANNOTATION_SOURCE =
      SourceFile.kotlin(
        "Paparazzi.kt",
        """
        package app.cash.paparazzi.annotations
        @Target(AnnotationTarget.FUNCTION)
        @Retention(AnnotationRetention.BINARY)
        annotation class Paparazzi
        """.trimIndent()
      )

    private val PAPARAZZI_PREVIEW_DATA_RUNTIME_SOURCE = SourceFile.kotlin(
      "PaparazziPreviewData.kt",
      """
        package app.cash.paparazzi.preview.runtime

        import androidx.compose.runtime.Composable

        data class PaparazziPreviewData(
          val snapshotName: String,
          val composable: @Composable () -> Unit
        )
      """.trimIndent()
    )
  }
}
