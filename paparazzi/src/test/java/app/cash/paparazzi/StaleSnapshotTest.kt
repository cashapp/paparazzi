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
package app.cash.paparazzi

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams
import android.view.Window
import android.view.WindowManager
import android.widget.FrameLayout
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import java.awt.image.BufferedImage

/**
 * Every snapshot has to capture the view as it stands when it is taken, not a frame left over from
 * an earlier one in the same session.
 *
 * Asserting against golden images cannot hold this line: a stale capture is a valid-looking image,
 * so re-recording makes the failure disappear without the staleness going away. These assertions
 * compare the frames to each other instead, and to the colours the views were given.
 */
class StaleSnapshotTest {
  private val frames = CapturingSnapshotHandler()

  @get:Rule
  val paparazzi = Paparazzi(snapshotHandler = frames, useDeviceResolution = true)

  @Test
  fun `each snapshot captures the view it was given`() {
    paparazzi.snapshot(paparazzi.filled(Color.RED))
    paparazzi.snapshot(paparazzi.filled(Color.GREEN))
    paparazzi.snapshot(paparazzi.filled(Color.BLUE))

    assertThat(frames.images.map { it.centre() })
      .containsExactly(Color.RED, Color.GREEN, Color.BLUE)
      .inOrder()
  }

  @Test
  fun `mutating a view between snapshots captures the mutation`() {
    val view = paparazzi.filled(Color.RED)

    paparazzi.snapshot(view)
    view.setBackgroundColor(Color.GREEN)
    paparazzi.snapshot(view)

    assertThat(frames.images.map { it.centre() })
      .containsExactly(Color.RED, Color.GREEN)
      .inOrder()
  }

  @Test
  fun `moving a dialog between snapshots captures its current position`() {
    val background = paparazzi.filled(Color.WHITE)
    val dialog = Dialog(paparazzi.context).apply {
      requestWindowFeature(Window.FEATURE_NO_TITLE)
      setContentView(paparazzi.filled(Color.RED))
    }
    try {
      dialog.show()
      val window = checkNotNull(dialog.window)
      window.setBackgroundDrawable(ColorDrawable(Color.RED))
      window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
      window.setLayout(100, 100)
      window.attributes = window.attributes.apply {
        gravity = Gravity.TOP or Gravity.LEFT
        x = 20
        y = 20
      }
      paparazzi.snapshot(background)

      window.attributes = window.attributes.apply { x = 200 }
      paparazzi.snapshot(background)

      assertThat(frames.images).hasSize(2)
      assertThat(frames.images.map { it.getRGB(30, 30) })
        .containsExactly(Color.RED, Color.WHITE).inOrder()
      assertThat(frames.images.map { it.getRGB(210, 30) })
        .containsExactly(Color.WHITE, Color.RED).inOrder()
    } finally {
      dialog.dismiss()
    }
  }

  private fun Paparazzi.filled(color: Int): View =
    FrameLayout(context).apply {
      layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
      setBackgroundColor(color)
    }

  private fun BufferedImage.centre(): Int = getRGB(width / 2, height / 2)

  private class CapturingSnapshotHandler : SnapshotHandler {
    val images = mutableListOf<BufferedImage>()

    override fun newFrameHandler(snapshot: Snapshot, frameCount: Int, fps: Int): SnapshotHandler.FrameHandler =
      object : SnapshotHandler.FrameHandler {
        override fun handle(image: BufferedImage) {
          images += image
        }

        override fun close() = Unit
      }

    override fun close() = Unit
  }
}
