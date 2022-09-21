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

import android.animation.Animator.AnimatorListener
import android.animation.ValueAnimator
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * Sets animation duration scale to 0 so all animations run instantly. Use this with Paparazzi to
 * skip animations and snapshot their terminal state.
 *
 * Note that animation side effects are still performed, including calls like
 * [AnimatorListener.onAnimationEnd]. This way views from fade-ins and moves are rendered as they
 * do when the animations complete.
 */
class InstantAnimationsRule : TestRule {
  private val getDurationScale = ValueAnimator::class.java.getDeclaredMethod(
    "getDurationScale"
  )

  private val setDurationScale = ValueAnimator::class.java.getDeclaredMethod(
    "setDurationScale",
    Float::class.javaPrimitiveType
  )

  private var durationScale: Float
    get() {
      return getDurationScale.invoke(null) as Float
    }
    set(value) {
      setDurationScale.invoke(null, value)
    }

  override fun apply(base: Statement, description: Description): Statement {
    return object : Statement() {
      override fun evaluate() {
        val scaleBefore = durationScale
        durationScale = 0.0f
        try {
          base.evaluate()
        } finally {
          durationScale = scaleBefore
        }
      }
    }
  }
}
