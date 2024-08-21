package app.cash.paparazzi.gradle

private const val PREVIEW_TEST_SOURCE = """
import app.cash.paparazzi.Paparazzi
import app.cash.paparazzi.preview.DefaultLocaleRule
import app.cash.paparazzi.preview.PaparazziPreviewData
import app.cash.paparazzi.preview.PaparazziValuesProvider
import app.cash.paparazzi.preview.deviceConfig
import app.cash.paparazzi.preview.locale
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
    deviceConfig = preview.deviceConfig(),
    renderingMode = SHRINK,
    maxPercentDifference = 0.11,
  )

  @get:Rule
  val localeRule = DefaultLocaleRule(preview.locale())

  @Test
  fun preview() {
    assumeTrue(preview !is PaparazziPreviewData.Empty)
    paparazzi.snapshot(preview)
  }
}
"""
