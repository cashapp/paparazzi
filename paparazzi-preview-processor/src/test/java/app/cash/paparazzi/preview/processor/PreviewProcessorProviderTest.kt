@file:OptIn(ExperimentalCompilerApi::class)

package app.cash.paparazzi.preview.processor

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.kspAllWarningsAsErrors
import com.tschuchort.compiletesting.kspArgs
import com.tschuchort.compiletesting.kspIncremental
import com.tschuchort.compiletesting.symbolProcessorProviders
import org.jetbrains.kotlin.cli.common.collectSources
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
          app.cash.paparazzi.preview.runtime.PaparazziPreviewData.Default(
            snapshotName = "SamplePreview_SamplePreview",
            composable = { test.SamplePreview() },
            preview = app.cash.paparazzi.preview.runtime.PreviewData(
            ),
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

    assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)

    assertThat(previewsFile.readText())
      .isEqualTo(
        """
        package test

        internal val paparazziPreviews = listOf<app.cash.paparazzi.preview.runtime.PaparazziPreviewData>(
          app.cash.paparazzi.preview.runtime.PaparazziPreviewData.Provider(
            snapshotName = "SamplePreview_SamplePreview",
            composable = { test.SamplePreview(it) },
            previewParameter = app.cash.paparazzi.preview.runtime.PreviewParameterData(
              name = "text",
              values = test.SamplePreviewParameter.values,
            ),
            preview = app.cash.paparazzi.preview.runtime.PreviewData(
            ),
          ),
        )
        """.trimIndent()
      )
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
          app.cash.paparazzi.preview.runtime.PaparazziPreviewData.Default(
            snapshotName = "SamplePreview_SamplePreview",
            composable = { test.SamplePreview() },
            preview = app.cash.paparazzi.preview.runtime.PreviewData(
            ),
          ),
          app.cash.paparazzi.preview.runtime.PaparazziPreviewData.Default(
            snapshotName = "SamplePreview_SamplePreview",
            composable = { test.SamplePreview() },
            preview = app.cash.paparazzi.preview.runtime.PreviewData(
              device = "id:pixel_4",
              uiMode = 32,
            ),
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

        kspAllWarningsAsErrors = true
        kspArgs["app.cash.paparazzi.preview.namespace"] = TEST_NAMESPACE
        kspIncremental = true
        symbolProcessorProviders = listOf(PreviewProcessorProvider())
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

        public object PaparazziPreviewDefaults {
          public const val DEVICE_ID: String = "id:pixel_5"
        }

        /**
         * Represents composables annotated with @Paparazzi annotation
         *
         * Default - Represents a composable with no parameters
         * Provider - Represents a composable with parameters using @PreviewParameter
         */
        sealed interface PaparazziPreviewData {

          data class Default(
            val snapshotName: String,
            val preview: PreviewData,
            val composable: @Composable () -> Unit
          ) : PaparazziPreviewData {
            override fun toString(): String =
              buildList {
                add(snapshotName)
                preview.toString().takeIf { it.isNotEmpty() }?.let(::add)
              }.joinToString(",")
          }

          data class Provider<T>(
            val snapshotName: String,
            val preview: PreviewData,
            val composable: @Composable (T) -> Unit,
            val previewParameter: PreviewParameterData<T>
          ) : PaparazziPreviewData {
            fun withPreviewParameterIndex(index: Int): Provider<T> =
              copy(previewParameter = previewParameter.copy(index = index))
          }
        }

        data class PreviewData(
          val fontScale: Float? = null,
          val device: String? = null,
          val widthDp: Int? = null,
          val heightDp: Int? = null,
          val uiMode: Int? = null,
          val locale: String? = null,
          val backgroundColor: String? = null
        )

        data class PreviewParameterData<T>(
          val name: String,
          val values: Sequence<T>,
          val index: Int = 0
        )

        /**
         * Maps [fontScale] to enum values similar to Preview
         * see:
        https://android.googlesource.com/platform/tools/adt/idea/+/refs/heads/mirror-goog-studio-main/compose-designer/src/com/android/tools/idea/compose/pickers/preview/enumsupport/PsiEnumValues.kt
         */
        internal fun Float.fontScale() = FontScale.CUSTOM.apply { value = this@fontScale }

        internal enum class FontScale(val value: Float?) {
          DEFAULT(1f),
          SMALL(0.85f),
          LARGE(1.15f),
          LARGEST(1.30f),
          CUSTOM(null);

          fun displayName() =
            when (this) {
              CUSTOM -> "fs_"
              else -> name
            }
        }

        internal fun Int.lightDarkName() =
          when (this and Configuration.UI_MODE_NIGHT_MASK) {
            Configuration.UI_MODE_NIGHT_NO -> "Light"
            Configuration.UI_MODE_NIGHT_YES -> "Dark"
            else -> null
          }

        internal fun Int.uiModeName() =
          when (this and Configuration.UI_MODE_TYPE_MASK) {
            Configuration.UI_MODE_TYPE_NORMAL -> "Normal"
            Configuration.UI_MODE_TYPE_CAR -> "Car"
            Configuration.UI_MODE_TYPE_DESK -> "Desk"
            Configuration.UI_MODE_TYPE_APPLIANCE -> "Appliance"
            Configuration.UI_MODE_TYPE_WATCH -> "Watch"
            Configuration.UI_MODE_TYPE_VR_HEADSET -> "VR_Headset"
            else -> null
          }

        /***
         * Values taken from `android.content.res.Configuration` to avoid dependency on Android SDK/LayoutLib
         */
        private object Configuration {
          const val UI_MODE_TYPE_MASK = 15
          const val UI_MODE_NIGHT_MASK = 48

          const val UI_MODE_TYPE_NORMAL = 1
          const val UI_MODE_TYPE_CAR = 3
          const val UI_MODE_TYPE_DESK = 2
          const val UI_MODE_TYPE_APPLIANCE = 5
          const val UI_MODE_TYPE_WATCH = 6
          const val UI_MODE_TYPE_VR_HEADSET = 7

          const val UI_MODE_NIGHT_NO = 16
          const val UI_MODE_NIGHT_YES = 32
        }
      """.trimIndent()
    )
  }
}
