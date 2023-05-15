/*
 * Copyright (C) 2022 Block, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package app.cash.paparazzi

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.Canvas
import android.view.animation.LinearInterpolator
import android.widget.TextView
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test

class InstantAnimationsRuleTest {
  @get:Rule
  val paparazzi = Paparazzi()

  @get:Rule
  val instantAnimationsRule = InstantAnimationsRule()

  /**
   * Confirm that animations and their event listeners are all executed immediately, even though
   * they have both a start delay and a non-zero duration.
   */
  @Test
  fun happyPath() {
    val log = mutableListOf<String>()

    val view = object : TextView(paparazzi.context) {
      override fun onDraw(canvas: Canvas) {
        log += "onDraw text=$text"
      }
    }

    val animator = ValueAnimator.ofInt(200, 300)
    animator.addUpdateListener {
      view.text = it.animatedFraction.toString()
    }
    animator.addListener(object : AnimatorListenerAdapter() {
      override fun onAnimationStart(animation: Animator?, isReverse: Boolean) {
        log += "onAnimationStart"
      }

      override fun onAnimationEnd(animation: Animator?) {
        log += "onAnimationEnd"
      }
    })

    animator.startDelay = 20_000L
    animator.duration = 10_000L
    animator.interpolator = LinearInterpolator()
    animator.start()

    paparazzi.snapshot(view)
    assertThat(log)
      .containsExactly("onAnimationStart", "onAnimationEnd", "onDraw text=1.0").inOrder()
    log.clear()
  }
}
