package app.cash.paparazzi.sample

import android.animation.ValueAnimator
import org.junit.Test

class UnsatisfiedLinkErrorTest {
  @Test fun test() {
    ValueAnimator::class.java.declaredFields.forEach {
      println("declaredField: " + it.name)
    }
    android.animation.ValueAnimator()
  }
}
