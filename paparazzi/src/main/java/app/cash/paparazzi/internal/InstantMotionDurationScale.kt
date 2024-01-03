package app.cash.paparazzi.internal

import androidx.compose.ui.MotionDurationScale

/**
 * An implementation of [MotionDurationScale] that returns 0 for the scale factor to make compose
 * animations complete instantly.
 */
class InstantMotionDurationScale : MotionDurationScale {
  override val scaleFactor: Float
    get() = 0f
}
