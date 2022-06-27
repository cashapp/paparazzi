package app.cash.paparazzi.plugin.test

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.ide.common.resources.configuration.LocaleQualifier
import com.android.resources.LayoutDirection
import org.junit.Rule
import org.junit.Test

class LayoutDirectionXmlTest {
  @get:Rule
  val paparazzi = Paparazzi()

  @Test
  fun rtlXml() {
    paparazzi.unsafeUpdateConfig(
      deviceConfig = DeviceConfig.NEXUS_5.copy(
        layoutDirection = LayoutDirection.RTL,
      ),
    )
    paparazzi.snapshot(paparazzi.inflate(R.layout.title_color))
    paparazzi.unsafeUpdateConfig(
      deviceConfig = DeviceConfig.NEXUS_5.copy(
        locale = LocaleQualifier.getQualifier("en-rGB"),
        layoutDirection = LayoutDirection.RTL,
      ),
    )
    paparazzi.snapshot(paparazzi.inflate(R.layout.title_color))
  }
}
