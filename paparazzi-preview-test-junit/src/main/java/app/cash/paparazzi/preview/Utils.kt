// Copyright Square, Inc.
package app.cash.paparazzi.preview

import android.content.res.Configuration
import android.util.DisplayMetrics
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.cash.paparazzi.annotations.PaparazziPreviewData
import app.cash.paparazzi.annotations.PreviewData
import com.android.resources.NightMode
import com.android.resources.UiMode
import java.util.Locale
import kotlin.math.roundToInt

internal fun String.deviceConfig() =
  when (this) {
    "id:Nexus 7" -> DeviceConfig.NEXUS_7
    "id:Nexus 7 2013" -> DeviceConfig.NEXUS_7_2012
    "id:Nexus 5" -> DeviceConfig.NEXUS_5
    "id:Nexus 6" -> DeviceConfig.NEXUS_7
    "id:Nexus 9" -> DeviceConfig.NEXUS_10
    "name:Nexus 10" -> DeviceConfig.NEXUS_10
    "id:Nexus 5X" -> DeviceConfig.NEXUS_5
    "id:Nexus 6P" -> DeviceConfig.NEXUS_7
    "id:pixel_c" -> DeviceConfig.PIXEL_C
    "id:pixel" -> DeviceConfig.PIXEL
    "id:pixel_xl" -> DeviceConfig.PIXEL_XL
    "id:pixel_2" -> DeviceConfig.PIXEL_2
    "id:pixel_2_xl" -> DeviceConfig.PIXEL_2_XL
    "id:pixel_3" -> DeviceConfig.PIXEL_3
    "id:pixel_3_xl" -> DeviceConfig.PIXEL_3_XL
    "id:pixel_3a" -> DeviceConfig.PIXEL_3A
    "id:pixel_3a_xl" -> DeviceConfig.PIXEL_3A_XL
    "id:pixel_4" -> DeviceConfig.PIXEL_4
    "id:pixel_4_xl" -> DeviceConfig.PIXEL_4_XL
    "id:pixel_5" -> DeviceConfig.PIXEL_5
    "id:pixel_6" -> DeviceConfig.PIXEL_6
    "id:pixel_6_pro" -> DeviceConfig.PIXEL_6_PRO
    "id:wearos_small_round" -> DeviceConfig.WEAR_OS_SMALL_ROUND
    "id:wearos_square" -> DeviceConfig.WEAR_OS_SQUARE
    else -> null
  }

internal fun Int.uiMode() =
  when (this and Configuration.UI_MODE_TYPE_MASK) {
    Configuration.UI_MODE_TYPE_NORMAL -> UiMode.NORMAL
    Configuration.UI_MODE_TYPE_CAR -> UiMode.CAR
    Configuration.UI_MODE_TYPE_DESK -> UiMode.DESK
    Configuration.UI_MODE_TYPE_APPLIANCE -> UiMode.APPLIANCE
    Configuration.UI_MODE_TYPE_WATCH -> UiMode.WATCH
    Configuration.UI_MODE_TYPE_VR_HEADSET -> UiMode.VR_HEADSET
    else -> null
  }

internal fun Int.nightMode() =
  when (this and Configuration.UI_MODE_NIGHT_MASK) {
    Configuration.UI_MODE_NIGHT_NO -> NightMode.NOTNIGHT
    Configuration.UI_MODE_NIGHT_YES -> NightMode.NIGHT
    else -> null
  }

internal fun String.localeQualifierString() =
  Locale.forLanguageTag(this).run {
    "$language-r$country"
  }

internal fun PreviewData?.deviceConfig(defaultDeviceConfig: DeviceConfig) =
  (this?.device?.deviceConfig() ?: defaultDeviceConfig).let { config ->
    config.copy(
      screenWidth = this?.widthDp?.toPx(config.density.dpiValue) ?: config.screenWidth,
      screenHeight = this?.heightDp?.toPx(config.density.dpiValue) ?: config.screenHeight,
      fontScale = this?.fontScale ?: config.fontScale,
      uiMode = this?.uiMode?.uiMode() ?: config.uiMode,
      nightMode = this?.uiMode?.nightMode() ?: config.nightMode,
      locale = this?.locale?.localeQualifierString() ?: config.locale
    )
  }

private fun Int.toPx(dpi: Int) = (this * (dpi.toFloat() / DisplayMetrics.DENSITY_DEFAULT)).roundToInt()

internal fun Paparazzi.snapshotDefault(
  previewData: PaparazziPreviewData.Default,
  name: String?,
  localInspectionMode: Boolean,
  wrapper: @Composable (@Composable () -> Unit) -> Unit = { it() }
) {
  snapshot(name) {
    PreviewWrapper(previewData.preview.backgroundColor, localInspectionMode) {
      wrapper { previewData.composable() }
    }
  }
}

internal fun <T> Paparazzi.snapshotProvider(
  previewData: PaparazziPreviewData.Provider<T>,
  name: String?,
  localInspectionMode: Boolean,
  wrapper: @Composable (@Composable () -> Unit) -> Unit = { it() }
) {
  val paramValue = previewData.previewParameter.values
    .elementAt(previewData.previewParameter.index)

  snapshot(name) {
    PreviewWrapper(previewData.preview.backgroundColor, localInspectionMode) {
      wrapper { previewData.composable(paramValue) }
    }
  }
}

@Composable
private fun PreviewWrapper(
  backgroundColor: String?,
  localInspectionMode: Boolean,
  content: @Composable BoxScope.() -> Unit
) {
  CompositionLocalProvider(LocalInspectionMode provides localInspectionMode) {
    Box(
      modifier = Modifier
        .then(
          backgroundColor?.toLong(16)
            ?.let { Modifier.background(Color(it)) }
            ?: Modifier
        ),
      content = content
    )
  }
}
