// Copyright Square, Inc.
package app.cash.paparazzi.preview

import androidx.compose.runtime.Composable
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.cash.paparazzi.annotations.PaparazziPreviewData
import com.google.testing.junit.testparameterinjector.TestParameter.TestParameterValuesProvider
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.util.Locale

/**
 * Take a snapshot of the given [previewData].
 */
public fun Paparazzi.snapshot(
  previewData: PaparazziPreviewData,
  name: String? = null,
  localInspectionMode: Boolean = true,
  wrapper: @Composable (@Composable () -> Unit) -> Unit = { it() }
) {
  when (previewData) {
    is PaparazziPreviewData.Default -> snapshotDefault(previewData, name, localInspectionMode, wrapper)
    is PaparazziPreviewData.Provider<*> -> snapshotProvider(previewData, name, localInspectionMode, wrapper)
    is PaparazziPreviewData.Empty -> Unit
    is PaparazziPreviewData.Error -> error(previewData.message)
  }
}

/**
 * Generate a Paparazzi DeviceConfig for the given preview
 * using the given [default] DeviceConfig.
 *
 * default: The IDE renders a preview with a higher resolution than
 * the default device set by Paparazzi (which is currently Nexus 5). Defaulting to
 * a larger device brings the previews and snapshots closer in parity.
 */
public fun PaparazziPreviewData.deviceConfig(default: DeviceConfig = DeviceConfig.PIXEL_5): DeviceConfig =
  when (this) {
    is PaparazziPreviewData.Default -> preview.deviceConfig(default)
    is PaparazziPreviewData.Provider<*> -> preview.deviceConfig(default)
    else -> default
  }

/**
 * Returns a locale for the given preview, or null if error or empty.
 */
public fun PaparazziPreviewData.locale(): String? =
  when (this) {
    is PaparazziPreviewData.Default -> preview.locale
    is PaparazziPreviewData.Provider<*> -> preview.locale
    else -> null
  }

/**
 * Convert a list of generated [PaparazziPreviewData]
 * to a flat list of [PaparazziPreviewData]s.
 */
public fun List<PaparazziPreviewData>.flatten(): List<PaparazziPreviewData> =
  flatMap {
    when (it) {
      is PaparazziPreviewData.Provider<*> -> List(it.previewParameter.values.count()) { i ->
        it.withPreviewParameterIndex(i)
      }
      else -> listOf(it)
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
  override fun provideValues(): List<PaparazziPreviewData> = annotations.flatten()
}

/**
 * Enforce a particular default locale for a test. Resets back to default on completion.
 */
public class DefaultLocaleRule(public val locale: String?) : TestRule {
  override fun apply(base: Statement, description: Description): Statement {
    return object : Statement() {
      override fun evaluate() {
        val default = Locale.getDefault()

        try {
          locale?.let { Locale.setDefault(Locale.forLanguageTag(it)) }
          base.evaluate()
        } finally {
          Locale.setDefault(default)
        }
      }
    }
  }
}
