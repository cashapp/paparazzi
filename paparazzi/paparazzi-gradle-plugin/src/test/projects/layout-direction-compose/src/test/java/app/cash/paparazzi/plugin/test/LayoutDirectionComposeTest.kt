package app.cash.paparazzi.plugin.test

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.ide.common.resources.configuration.LocaleQualifier
import com.android.resources.LayoutDirection
import org.junit.Rule
import org.junit.Test

class LayoutDirectionComposeTest {
  @get:Rule
  val paparazzi = Paparazzi()

  @Test
  fun rtlCompose() {
    paparazzi.unsafeUpdateConfig(
      deviceConfig = DeviceConfig.NEXUS_5.copy(
        layoutDirection = LayoutDirection.RTL,
      ),
    )
    paparazzi.snapshot {
      TitleColor()
    }
    paparazzi.unsafeUpdateConfig(
      deviceConfig = DeviceConfig.NEXUS_5.copy(
        layoutDirection = LayoutDirection.RTL,
        locale = LocaleQualifier.getQualifier("en-rGB"),
      ),
    )
    paparazzi.snapshot {
      TitleColor()
    }
  }
}
