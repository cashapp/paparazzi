// Copyright Square, Inc.
package app.cash.paparazzi.preview

import androidx.compose.runtime.Composable
import app.cash.paparazzi.Paparazzi
import app.cash.paparazzi.preview.runtime.PaparazziPreviewData
import com.google.testing.junit.testparameterinjector.TestParameter.TestParameterValuesProvider

/**
 * Take a snapshot of the given [previewData].
 */
public fun Paparazzi.snapshot(
  previewData: PaparazziPreviewData,
  name: String? = null,
  wrapper: @Composable (@Composable () -> Unit) -> Unit = { it() }
) {
  when (previewData) {
    is PaparazziPreviewData.Default -> snapshotDefault(previewData, name, wrapper)
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
) : TestParameterValuesProvider() {
  override fun provideValues(context: Context?): MutableList<*> = annotations.toMutableList()
}
