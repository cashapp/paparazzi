package app.cash.paparazzi.plugin.test

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.ide.common.resources.configuration.LocaleQualifier
import org.junit.Rule
import org.junit.Test

class LocaleQualifierXmlTest {
  @get:Rule
  val paparazzi = Paparazzi()

  @Test
  fun localeQualifierXml() {
    paparazzi.unsafeUpdateConfig(
      deviceConfig = DeviceConfig.NEXUS_5,
    )
    paparazzi.snapshot(paparazzi.inflate(R.layout.title_color))
    paparazzi.unsafeUpdateConfig(
      deviceConfig = DeviceConfig.NEXUS_5.copy(
        locale = LocaleQualifier.getQualifier("en-rGB"),
      ),
    )
    paparazzi.snapshot(paparazzi.inflate(R.layout.title_color))
    paparazzi.unsafeUpdateConfig(
      deviceConfig = DeviceConfig.NEXUS_5.copy(
        locale = LocaleQualifier.getQualifier("uk"),
      ),
    )
    paparazzi.snapshot(paparazzi.inflate(R.layout.title_color))
    paparazzi.unsafeUpdateConfig(
      deviceConfig = DeviceConfig.NEXUS_5.copy(
        locale = LocaleQualifier.getQualifier("ar"),
      ),
    )
    paparazzi.snapshot(paparazzi.inflate(R.layout.title_color))
  }
}
