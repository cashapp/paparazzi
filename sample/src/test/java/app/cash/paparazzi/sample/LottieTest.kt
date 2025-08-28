package app.cash.paparazzi.sample

import app.cash.paparazzi.Paparazzi
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieCompositionFactory
import com.airbnb.lottie.LottieTask
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.Executor

class LottieTest {
  @get:Rule
  var paparazzi = Paparazzi()


  @Before
  fun setup() {
    LottieTask.EXECUTOR = Executor(Runnable::run)
  }

  @Test
  fun lottie() {
    val composition = LottieCompositionFactory.fromRawResSync(paparazzi.context, R.raw.lottie_logo)
      .value!!
    val view = LottieAnimationView(paparazzi.context)
    view.setComposition(composition)

    view.playAnimation()
    paparazzi.snapshot(view, name = "start", offsetMillis = 0L)
    paparazzi.snapshot(view, name = "middle", offsetMillis = 2500L)
    paparazzi.snapshot(view, name = "end", offsetMillis = 5000L)
    view.cancelAnimation()

    view.progress = 1.0f
    paparazzi.snapshot(view, "lottie logo")

    view.playAnimation()
    paparazzi.gif(view, "lottie logo", start = 0L, end = 5000L, fps = 60)
  }

  @Test
  fun lottie2() {
    val composition = LottieCompositionFactory.fromRawResSync(paparazzi.context, R.raw.masks).value!!
    val view = LottieAnimationView(paparazzi.context)
    view.setComposition(composition)
    view.progress = 0.0f
    paparazzi.snapshot(view, "masks0")

    view.progress = 0.5f
    paparazzi.snapshot(view, "masks1")

    view.progress = 1.0f
    paparazzi.snapshot(view, "masks2")

    view.playAnimation()
    paparazzi.gif(view, "masks", start = 0L, end = 5000L, fps = 60)
  }
}
