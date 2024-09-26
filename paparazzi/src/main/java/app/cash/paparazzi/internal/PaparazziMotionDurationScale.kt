package app.cash.paparazzi.internal

import androidx.compose.ui.MotionDurationScale

internal object PaparazziMotionDurationScale : MotionDurationScale {
  var animationScaleFactor: Float = 1f

  override val scaleFactor: Float
    get() = animationScaleFactor
}
