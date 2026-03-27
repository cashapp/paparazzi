package app.cash.paparazzi.plugin.test

import android.content.Context
import androidx.annotation.RawRes
import app.cash.paparazzi.Paparazzi
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieComposition
import com.airbnb.lottie.LottieCompositionFactory
import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterInjector
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(TestParameterInjector::class)
class SnapshotWithOffsetTest {

  @get:Rule
  var paparazzi = Paparazzi()

  enum class Offset(val current: Long) {
    NEGATIVE_OFFSET(-1L),
    AT_600(600L),
    AT_800(800L),
    AT_1000(1000L),
    AT_4000(4000L),
    LARGE_OFFSET(5000_000L)
  }

  @Test
  fun `verify animated view progress with offsets`(@TestParameter offset: Offset) {
    val composition = getCompositionLogo(res = R.raw.lottie_logo, context = paparazzi.context)

    val view = getLottieAnimationView(context = paparazzi.context, composition = composition)

    paparazzi.snapshot(view = view, offsetMillis = offset.current)
  }

  @Test
  fun `verify animated view is completed with gif`() {
    val composition = getCompositionLogo(res = R.raw.lottie_logo, context = paparazzi.context)

    val view = getLottieAnimationView(context = paparazzi.context, composition = composition)

    paparazzi.gif(view = view, end = 2000L, fps = 60)
  }

  private fun getLottieAnimationView(context: Context, composition: LottieComposition) =
    LottieAnimationView(context).apply {
      setComposition(composition)
      playAnimation()
    }

  private fun getCompositionLogo(@RawRes res: Int, context: Context) =
    LottieCompositionFactory.fromRawResSync(context, res).value!!
}
