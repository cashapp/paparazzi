package app.cash.paparazzi.plugin.test

import android.content.Context
import androidx.annotation.RawRes
import app.cash.paparazzi.Paparazzi
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieComposition
import com.airbnb.lottie.LottieCompositionFactory
import org.junit.Rule
import org.junit.Test

class SnapshotWithOffsetTest {

  @get:Rule
  var paparazzi = Paparazzi()

  @Test
  fun `verify animated view progress with multiple offsets`() {
    val composition = getCompositionLogo(res = R.raw.lottie_logo, context = paparazzi.context)

    val view = getLottieAnimationView(context = paparazzi.context, composition = composition)

    for (offsetInMs in 400..1000 step 200) {
      paparazzi.snapshot(
        view = view,
        name = "at ${offsetInMs}ms",
        offsetMillis = offsetInMs.toLong()
      )
    }
  }

  @Test
  fun `verify animated view is completed with large offset`() {
    val composition = getCompositionLogo(res = R.raw.lottie_logo, context = paparazzi.context)

    val view = getLottieAnimationView(context = paparazzi.context, composition = composition)

    paparazzi.snapshot(view = view, offsetMillis = 5000_000L)
  }

  @Test
  fun `verify animated view is blank with negative offset`() {
    val composition = getCompositionLogo(res = R.raw.lottie_logo, context = paparazzi.context)

    val view = getLottieAnimationView(context = paparazzi.context, composition = composition)

    paparazzi.snapshot(view = view, offsetMillis = -1L)
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
