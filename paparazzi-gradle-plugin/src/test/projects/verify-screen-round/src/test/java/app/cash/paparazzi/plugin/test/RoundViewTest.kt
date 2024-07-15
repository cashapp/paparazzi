package app.cash.paparazzi.plugin.test

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.ScreenRound
import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterInjector
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(TestParameterInjector::class)
class RoundViewTest(
  @TestParameter val configuration: ScreenRoundTestConfiguration
) {
  @get:Rule
  val paparazzi = Paparazzi(
    deviceConfig = DeviceConfig.GALAXY_WATCH4_CLASSIC_LARGE.copy(screenRound = configuration.round),
    theme = "android:Theme.Material.Light.NoActionBar"
  )

  @Test
  fun test() {
    paparazzi.snapshot(
      view = paparazzi.inflate(R.layout.custom_view)
    )
  }
}

enum class ScreenRoundTestConfiguration(val round: ScreenRound) {
  ROUND(round = ScreenRound.ROUND),
  NOT_ROUND(round = ScreenRound.NOTROUND)
}
