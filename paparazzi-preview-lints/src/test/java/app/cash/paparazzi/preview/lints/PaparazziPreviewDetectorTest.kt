package app.cash.paparazzi.preview.lints

import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import com.android.tools.lint.checks.infrastructure.TestMode
import org.junit.Test

class PaparazziPreviewDetectorTest {
  @Test
  fun simplePreview() {
    lint()
      .files(
        kotlin(
          """
          package test

          import androidx.compose.runtime.Composable
          import androidx.compose.ui.tooling.preview.Preview
          import app.cash.paparazzi.annotations.Paparazzi

          @Paparazzi
          @Preview
          @Composable
          fun SamplePreview() {}
          """
        ).indented(),
        *COMPOSE_SOURCES.toTypedArray(),
        PAPARAZZI_ANNOTATION
      )
      .detector(PaparazziPreviewDetector())
      .skipTestModes(TestMode.SUPPRESSIBLE)
      .run()
      .expectClean()
  }

  @Test
  fun multiplePreviews() {
    lint()
      .files(
        kotlin(
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
          fun SamplePreview() {}
          """
        ).indented(),
        *COMPOSE_SOURCES.toTypedArray(),
        PAPARAZZI_ANNOTATION
      )
      .detector(PaparazziPreviewDetector())
      .skipTestModes(TestMode.SUPPRESSIBLE)
      .run()
      .expectClean()
  }

  @Test
  fun notComposable() {
    lint()
      .files(
        kotlin(
          """
          package test

          import androidx.compose.ui.tooling.preview.Preview
          import app.cash.paparazzi.annotations.Paparazzi

          @Paparazzi
          @Preview
          fun SamplePreview() {}
          """
        ).indented(),
        *COMPOSE_SOURCES.toTypedArray(),
        PAPARAZZI_ANNOTATION
      )
      .issues(PaparazziPreviewDetector.COMPOSABLE_NOT_DETECTED)
      .skipTestModes(TestMode.SUPPRESSIBLE)
      .run()
      .expect(
        """
        src/test/test.kt:6: Error: SamplePreview is not annotated with @Composable. [ComposableAnnotationNotFound]
        @Paparazzi
        ~~~~~~~~~~
        1 errors, 0 warnings
        """.trimIndent()
      )
  }

  @Test
  fun notPreview() {
    lint()
      .files(
        kotlin(
          """
          package test

          import androidx.compose.runtime.Composable
          import app.cash.paparazzi.annotations.Paparazzi

          @Paparazzi
          @Composable
          fun SamplePreview() {}
          """
        ).indented(),
        *COMPOSE_SOURCES.toTypedArray(),
        PAPARAZZI_ANNOTATION
      )
      .issues(PaparazziPreviewDetector.PREVIEW_NOT_DETECTED)
      .skipTestModes(TestMode.SUPPRESSIBLE)
      .run()
      .expect(
        """
        src/test/test.kt:6: Error: SamplePreview is not annotated with @Preview. [PreviewAnnotationNotFound]
        @Paparazzi
        ~~~~~~~~~~
        1 errors, 0 warnings
        """.trimIndent()
      )
  }

  @Test
  fun privatePreview() {
    lint()
      .files(
        kotlin(
          """
          package test

          import androidx.compose.runtime.Composable
          import androidx.compose.ui.tooling.preview.Preview
          import app.cash.paparazzi.annotations.Paparazzi

          @Paparazzi
          @Preview
          @Composable
          private fun SamplePreview() {}
          """
        ).indented(),
        *COMPOSE_SOURCES.toTypedArray(),
        PAPARAZZI_ANNOTATION
      )
      .issues(PaparazziPreviewDetector.PRIVATE_PREVIEW_DETECTED)
      .skipTestModes(TestMode.SUPPRESSIBLE)
      .run()
      .expect(
        """
        src/test/test.kt:7: Error: SamplePreview is private. Make it internal or public to generate a snapshot. [PrivatePreviewDetected]
        @Paparazzi
        ~~~~~~~~~~
        1 errors, 0 warnings
        """.trimIndent()
      )
  }

  @Test
  fun previewParameters() {
    lint()
      .files(
        kotlin(
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
          ) {}

          object SamplePreviewParameter: PreviewParameterProvider<String> {
            override val values: Sequence<String> = sequenceOf("test")
          }
          """
        ).indented(),
        *COMPOSE_SOURCES.toTypedArray(),
        PAPARAZZI_ANNOTATION
      )
      .issues(PaparazziPreviewDetector.PREVIEW_PARAMETERS_NOT_SUPPORTED)
      .skipTestModes(TestMode.SUPPRESSIBLE)
      .run()
      .expect(
        """
        src/test/SamplePreviewParameter.kt:9: Error: @Preview of SamplePreview uses PreviewParameters which aren't currently supported. [PreviewParametersNotSupported]
        @Paparazzi
        ~~~~~~~~~~
        1 errors, 0 warnings
        """.trimIndent()
      )
  }

  private companion object {
    private val COMPOSE_SOURCES =
      listOf(
        kotlin(
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
          """
        ).indented(),
        kotlin(
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
          """
        ).indented(),
        kotlin(
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
          """
        ).indented()
      )

    val PAPARAZZI_ANNOTATION = kotlin(
      """
      package app.cash.paparazzi.annotations

      @Target(AnnotationTarget.FUNCTION)
      @Retention(AnnotationRetention.BINARY)
      annotation class Paparazzi
      """
    ).indented()
  }
}
