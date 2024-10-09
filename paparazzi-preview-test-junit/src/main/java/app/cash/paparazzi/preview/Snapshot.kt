// Copyright Square, Inc.
package app.cash.paparazzi.preview

import app.cash.paparazzi.Paparazzi
import app.cash.paparazzi.annotations.PaparazziPreviewData
import com.google.testing.junit.testparameterinjector.TestParameter.TestParameterValuesProvider

/**
 * Take a snapshot of the given [previewData].
 */
public fun Paparazzi.snapshot(
  previewData: PaparazziPreviewData,
  name: String? = null
) {
  when (previewData) {
    is PaparazziPreviewData.Default -> snapshotDefault(previewData, name)
    is PaparazziPreviewData.Empty -> Unit
    is PaparazziPreviewData.Error -> error(previewData.message)
  }
}

/**
 * A `@TestParameter` values provider for the given [annotations].
 *
 * Example usage:
 * ```
 * private class ValuesProvider : PaparazziValuesProvider(paparazziAnnotations)
 * ```
 */
public open class PaparazziValuesProvider(
  private val annotations: List<PaparazziPreviewData>
) : TestParameterValuesProvider {
  override fun provideValues(): List<PaparazziPreviewData> = annotations
}
