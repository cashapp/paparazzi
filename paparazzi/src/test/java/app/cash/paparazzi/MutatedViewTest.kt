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

import android.graphics.Color
import android.view.ViewGroup.LayoutParams
import android.widget.TextView
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import java.awt.image.BufferedImage

/**
 * Regression coverage for views that are mutated between snapshots taken from a single
 * Paparazzi session.
 *
 * layoutlib 17.0.1 only captures a frame that HWUI actually produced, and HWUI drops a frame whose
 * vsync has not advanced. Because Paparazzi pins the simulated clock at 0, every snapshot in a
 * session was signalled with the same vsync, so only the first `render()` rasterized and later
 * snapshots silently returned the first frame's pixels. Existing coverage could not catch this:
 * `RenderExtensionTest` snapshots the same *unmodified* view twice, where identical output is the
 * expected result.
 */
class MutatedViewTest {
  private val frames = CapturingSnapshotHandler()

  @get:Rule
  val paparazzi = Paparazzi(snapshotHandler = frames)

  @Test
  fun `mutating a view between snapshots renders the mutation`() {
    val textView = TextView(paparazzi.context).apply {
      layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
      setBackgroundColor(Color.WHITE)
      setTextColor(Color.BLACK)
      textSize = 32f
      text = "before"
    }

    paparazzi.snapshot(textView, name = "before")

    textView.text = "after"
    paparazzi.snapshot(textView, name = "after")

    assertThat(frames.images).hasSize(2)
    val (before, after) = frames.images
    assertThat(before.differsFrom(after)).isTrue()
  }

  private fun BufferedImage.differsFrom(other: BufferedImage): Boolean {
    if (width != other.width || height != other.height) return true
    for (y in 0 until height) {
      for (x in 0 until width) {
        if (getRGB(x, y) != other.getRGB(x, y)) return true
      }
    }
    return false
  }

  private class CapturingSnapshotHandler : SnapshotHandler {
    val images = mutableListOf<BufferedImage>()

    override fun newFrameHandler(
      snapshot: Snapshot,
      frameCount: Int,
      fps: Int
    ): SnapshotHandler.FrameHandler =
      object : SnapshotHandler.FrameHandler {
        override fun handle(image: BufferedImage) {
          images += image
        }

        override fun close() = Unit
      }

    override fun close() = Unit
  }
}
