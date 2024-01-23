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
  fun textShouldSayRound() {
    paparazzi.snapshot(
      view = paparazzi.inflate<RoundView>(R.layout.custom_view),
      name = configuration.testName
    )
  }
}

enum class ScreenRoundTestConfiguration(
  val round: ScreenRound,
  val testName: String
) {
  ROUND(
    round = ScreenRound.ROUND,
    testName = "round"
  ),
  NOT_ROUND(
    round = ScreenRound.NOTROUND,
    testName = "not_round"
  )
}
