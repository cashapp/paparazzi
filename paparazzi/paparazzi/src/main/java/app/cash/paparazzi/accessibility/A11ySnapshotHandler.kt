/*
 * Copyright 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.cash.paparazzi.accessibility

import app.cash.paparazzi.Snapshot
import app.cash.paparazzi.SnapshotHandler
import java.awt.AlphaComposite
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Composite
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import kotlin.math.max

/**
 * A Paparazzi SnapshotHandler that renders the snapshot, with a light coloured overlay,
 * and adjacent to a legend with matching colours.
 */
class A11ySnapshotHandler(
  private val delegate: SnapshotHandler,
  private val accessibilityStateFn: () -> AccessibilityState,
  private val overlayRenderer: (AccessibilityState, BufferedImage) -> BufferedImage =
    { accessibilityState, image ->
      drawBoxes(accessibilityState, image)
    },
  private val legendRenderer: (AccessibilityState, BufferedImage) -> BufferedImage =
    { accessibilityState, image ->
      drawLegend(accessibilityState, image)
    }
) : SnapshotHandler {

  override fun close() {
    delegate.close()
  }

  override fun newFrameHandler(
    snapshot: Snapshot,
    frameCount: Int,
    fps: Int
  ): SnapshotHandler.FrameHandler {
    val delegateFrameHandler = delegate.newFrameHandler(snapshot, frameCount, fps)
    return object : SnapshotHandler.FrameHandler {
      override fun close() {
        delegateFrameHandler.close()
      }

      override fun handle(image: BufferedImage) {
        val accessibilityState = accessibilityStateFn()

        val overlay = overlayRenderer(accessibilityState, image)
        val legend = legendRenderer(accessibilityState, image)

        val modifiedImage = concatImages(overlay, legend, image)

        delegateFrameHandler.handle(modifiedImage)
      }
    }
  }

  public companion object {
    private val colors =
      listOf(
        Color.BLUE,
        Color.CYAN,
        Color.GREEN,
        Color.GRAY,
        Color.PINK,
        Color.MAGENTA,
        Color.YELLOW,
        Color.ORANGE
      )

    private fun concatImages(
      overlay: BufferedImage,
      legend: BufferedImage,
      image: BufferedImage
    ): BufferedImage {
      val modifiedImage =
        BufferedImage(
          overlay.width + legend.width,
          max(overlay.height, legend.height),
          image.type
        )

      modifiedImage.withGraphics2D {
        drawImage(overlay, 0, 0, overlay.width, overlay.height, null)
        drawImage(legend, overlay.width, 0, legend.width, legend.height, null)
      }
      return modifiedImage
    }

    private fun Graphics2D.withComposite(newComposite: Composite, fn: () -> Unit = {}) {
      val current = composite
      composite = newComposite

      fn()

      composite = current
    }

    private fun BufferedImage.withGraphics2D(fn: Graphics2D.() -> Unit = {}): BufferedImage {
      createGraphics().apply {
        try {
          fn()
        } finally {
          dispose()
        }
      }

      return this
    }

    internal fun drawBoxes(
      accessibilityState: AccessibilityState,
      image: BufferedImage
    ): BufferedImage {
      val modifiedImage = BufferedImage(image.width, image.height, image.type)

      val scale = 1000f / max(accessibilityState.height, accessibilityState.width)

      return modifiedImage.withGraphics2D {
        withComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.7f)) {
          drawImage(image, 0, 0, image.width, image.height, null)
        }

        accessibilityState.elements.forEachIndexed { i, it ->
          paint = colorForIndex(i)
          stroke = BasicStroke(3f)
          val element = it.scaleBy(scale)
          drawRect(
            element.displayBounds.left,
            element.displayBounds.top,
            element.displayBounds.width(),
            element.displayBounds.height()
          )
          paint = Color(color.red, color.green, color.blue, 255 / 4)
          fillRect(
            element.displayBounds.left,
            element.displayBounds.top,
            element.displayBounds.width(),
            element.displayBounds.height()
          )
          if (element.touchBounds != null) {
            drawRect(
              element.touchBounds.left,
              element.touchBounds.top,
              element.touchBounds.width(),
              element.touchBounds.height()
            )
          }
        }
      }
    }

    internal fun drawLegend(
      accessibilityState: AccessibilityState,
      image: BufferedImage
    ): BufferedImage {
      val modifiedImage = BufferedImage(600, image.height, image.type)

      return modifiedImage.withGraphics2D {
        paint = Color.WHITE
        fillRect(0, 0, modifiedImage.width, modifiedImage.height)

        font = font.deriveFont(20f)
        stroke = BasicStroke(3f)

        var index = 1

        fun drawItem(s: String) {
          drawString(s, 50f, 28f * index++)
        }

        accessibilityState.elements.forEachIndexed { i, it ->
          paint = Color.BLACK

          val start = index
          if (it.role != null || it.disabled || it.heading) {
            val role = if (it.role != null) "Role " + it.role + " " else ""
            val heading = if (it.heading) "Heading " else ""
            val disabled = if (it.disabled) "Disabled" else ""
            drawItem(role + heading + disabled)
          }
          if (it.contentDescription != null) {
            drawItem("Content Description \"${it.contentDescription.joinToString(", ")}\"")
          } else if (it.text != null) {
            drawItem("Text \"${it.text.joinToString(", ")}\"")
          }
          if (it.stateDescription != null) {
            drawItem("State Description \"${it.stateDescription}\"")
          }
          if (it.onClickLabel != null) {
            drawItem("On Click \"${it.onClickLabel}\"")
          }
          if (it.progress != null) {
            drawItem("Progress \"${it.progress}\"")
          }
          if (it.customActions != null) {
            it.customActions.forEach {
              drawItem("Custom Action \"${it.label}\"")
            }
          }
          val end = index

          paint = colorForIndex(i)
          drawRect(10, start * 28 - 21, modifiedImage.width - 20, (end - start) * 28)

          index++
        }
      }
    }

    private fun colorForIndex(i: Int): Color {
      return colors[i % colors.size]
    }
  }
}
