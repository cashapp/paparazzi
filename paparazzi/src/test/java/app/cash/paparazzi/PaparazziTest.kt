/*
 * Copyright (C) 2019 Square, Inc.
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

import android.animation.AnimationHandler
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Color
import android.view.Choreographer
import android.view.Choreographer.CALLBACK_ANIMATION
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.Button
import android.widget.TextView
import com.android.internal.lang.System_Delegate
import com.google.common.truth.Truth.assertThat
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit

class PaparazziTest {
  @get:Rule
  val testRule = PaparazziTestRule()

  val paparazzi
    get() = testRule.paparazzi

  @Test
  fun drawCalls() {
    val log = mutableListOf<String>()

    val view = object : View(paparazzi.context) {
      override fun onDraw(canvas: Canvas) {
        log += "onDraw time=$time"
      }
    }

    paparazzi.snapshot(view)

    assertThat(log).containsExactly("onDraw time=0")
  }

  @Test
  fun resetsAnimationHandler() {
    assertThat(AnimationHandler.sAnimatorHandler.get()).isNull()

    // Why Button?  Because it sets a StateListAnimator on window attach
    // See https://github.com/cashapp/paparazzi/pull/319
    paparazzi.snapshot(Button(paparazzi.context))

    assertThat(AnimationHandler.sAnimatorHandler.get()).isNull()
  }

  @Test
  fun animationEvents() {
    val log = mutableListOf<String>()

    val animator = ValueAnimator.ofFloat(0.0f, 1.0f)
    animator.addListener(object : AnimatorListenerAdapter() {
      override fun onAnimationStart(animation: Animator) {
        log += "onAnimationStart time=$time animationElapsed=${animator.animatedValue}"
      }

      override fun onAnimationEnd(animation: Animator) {
        log += "onAnimationEnd time=$time animationElapsed=${animator.animatedValue}"
      }
    })

    val view = object : View(paparazzi.context) {
      override fun onDraw(canvas: Canvas) {
        log += "onDraw time=$time animationElapsed=${animator.animatedValue}"
      }
    }

    animator.addUpdateListener {
      log += "onAnimationUpdate time=$time animationElapsed=${animator.animatedValue}"

      val colorComponent = it.animatedFraction
      view.setBackgroundColor(Color.argb(1f, colorComponent, colorComponent, colorComponent))
    }

    animator.startDelay = 2_000L
    animator.duration = 1_000L
    animator.interpolator = LinearInterpolator()
    animator.start()

    paparazzi.gif(view, start = 1_000L, end = 4_000L, fps = 4)

    assertThat(log).containsExactly(
      "onDraw time=1000 animationElapsed=0.0",
      "onAnimationStart time=2000 animationElapsed=0.0",
      "onAnimationUpdate time=2000 animationElapsed=0.0",
      "onDraw time=2000 animationElapsed=0.0",
      "onAnimationUpdate time=2250 animationElapsed=0.25",
      "onDraw time=2250 animationElapsed=0.25",
      "onAnimationUpdate time=2500 animationElapsed=0.5",
      "onDraw time=2500 animationElapsed=0.5",
      "onAnimationUpdate time=2750 animationElapsed=0.75",
      "onDraw time=2750 animationElapsed=0.75",
      "onAnimationUpdate time=3000 animationElapsed=1.0",
      "onAnimationEnd time=3000 animationElapsed=1.0",
      "onDraw time=3000 animationElapsed=1.0"
    )
  }

  @Test
  fun animationCallbacksForStaticSnapshots() {
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
        log += "onAnimationStart uptimeMillis=$time"
      }

      override fun onAnimationEnd(animator: Animator) {
        log += "onAnimationEnd uptimeMillis=$time"
      }
    })

    animator.startDelay = 2_000L
    animator.duration = 1_000L
    animator.interpolator = LinearInterpolator()
    assertThat(AnimationHandler.getAnimationCount()).isEqualTo(0)
    animator.start()
    assertThat(AnimationHandler.getAnimationCount()).isEqualTo(1)

    paparazzi.snapshot(view, offsetMillis = 0L)
    assertThat(log).containsExactly(
      "onDraw text="
    )
    log.clear()

    paparazzi.snapshot(view, offsetMillis = 2_000L)
    assertThat(log).containsExactly(
      "onAnimationStart uptimeMillis=2000",
      "onDraw text=0.0"
    )
    log.clear()

    paparazzi.snapshot(view, offsetMillis = 2_500L)
    assertThat(log).containsExactly(
      "onDraw text=0.5"
    )
    log.clear()

    paparazzi.snapshot(view, offsetMillis = 3_000L)
    assertThat(log).containsExactly(
      "onAnimationEnd uptimeMillis=3000",
      "onDraw text=1.0"
    )
    assertThat(AnimationHandler.getAnimationCount()).isEqualTo(0)
    log.clear()
  }

  @Test
  @Ignore
  fun frameCallbacksExecutedAfterLayout() {
    val log = mutableListOf<String>()

    val view = object : View(paparazzi.context) {
      override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        Choreographer.getInstance()
          .postCallback(
            CALLBACK_ANIMATION,
            { log += "view width=$width height=$height" },
            false
          )
      }
    }

    paparazzi.snapshot(view)

    assertThat(log).containsExactly("view width=1080 height=1776")
  }

  @Test
  fun throwsRenderingExceptions() {
    val view = object : View(paparazzi.context) {
      override fun onAttachedToWindow() {
        throw Throwable("Oops")
      }
    }

    val thrown = try {
      paparazzi.snapshot(view)
      false
    } catch (exception: Throwable) {
      true
    }

    assertThat(thrown).isTrue()
  }

  private val time: Long
    get() {
      return TimeUnit.NANOSECONDS.toMillis(System_Delegate.nanoTime() - PaparazziSdk.TIME_OFFSET_NANOS)
    }
}
