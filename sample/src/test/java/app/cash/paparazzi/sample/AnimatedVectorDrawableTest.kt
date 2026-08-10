/*
 * Copyright (C) 2026 Square, Inc.
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
package app.cash.paparazzi.sample

import android.graphics.drawable.AnimatedVectorDrawable
import android.widget.FrameLayout
import android.widget.ImageView
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test

/**
 * Pixel-level verification for spike 001: native [AnimatedVectorDrawable] timing.
 *
 * The AVD rotates a full turn over one second. Its rotation angle is advanced by layoutlib's native
 * animator, which reads frame time from RenderSession#setElapsedFrameTimeNanos. Capturing a GIF
 * across the animation proves the rendered pixels actually change per frame (rather than freezing at
 * the initial angle), which the delegate-value unit test alone cannot demonstrate.
 */
class AnimatedVectorDrawableTest {
  @get:Rule
  val paparazzi = Paparazzi()

  @Test
  fun rotatesAcrossFrames() {
    val imageView = ImageView(paparazzi.context).apply {
      setImageResource(R.drawable.animated_vector_rotation)
    }

    val root = FrameLayout(paparazzi.context).apply {
      addView(imageView)
    }

    (imageView.drawable as AnimatedVectorDrawable).start()

    // Eight frames spanning the 1s rotation: expect eight distinct angles (0, 45, 90, ... 315).
    paparazzi.gif(root, "rotation", start = 0, end = 1000, fps = 8)
  }
}
