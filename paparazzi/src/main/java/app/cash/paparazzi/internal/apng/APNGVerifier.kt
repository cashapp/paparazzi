/*
 * Copyright (C) 2023 Square, Inc.
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

package app.cash.paparazzi.internal.apng

import app.cash.paparazzi.internal.ImageUtils
import app.cash.paparazzi.internal.ImageUtils.failureDir
import org.junit.Assert.fail
import java.awt.image.BufferedImage
import java.io.File

internal class APNGVerifier(
  private val goldenFile: File,
  private val fps: Int,
  private val frameCount: Int,
  private val maxPercentDifference: Double
) {
  private val pngReader = APNGReader(goldenFile)
  private val blankFrame by lazy { createBlankFrame(pngReader.width, pngReader.height) }
  private val deltaFile by lazy { File(failureDir, "delta-${goldenFile.name}") }

  private var deltaWriter: APNGWriter? = null
  private val invalidFrames = mutableListOf<Float>()

  fun verifyFrame(image: BufferedImage) {
    val frame = pngReader.getNextFrame() ?: blankFrame

    val (expectedFrame, actualFrame) = ImageUtils.resize(frame, image)
    val (deltaImage, percentDifferent) = ImageUtils.compareImages(expectedFrame, actualFrame)
    if (percentDifferent > maxPercentDifference) {
      if (deltaWriter == null) {
        deltaWriter = initializeWriter()
      }
      invalidFrames += percentDifferent
    }

    deltaWriter?.writeImage(deltaImage)
  }

  fun assertFinished() {
    while (!pngReader.finished()) {
      if (deltaWriter == null) {
        deltaWriter = initializeWriter()
      }

      val nextFrame = pngReader.getNextFrame() ?: blankFrame
      val (deltaImage) = ImageUtils.compareImages(
        goldenImage = nextFrame,
        image = blankFrame
      )
      deltaWriter!!.writeImage(deltaImage)
    }

    if (deltaWriter != null) {
      val error = buildString {
        if (invalidFrames.size > 1) {
          appendLine(
            "${invalidFrames.size} frame differed by more than %.1f%%".format(maxPercentDifference)
          )
        }
        if (pngReader.getDelay() != (1 to fps)) {
          appendLine("Mismatched video fps expected: ${pngReader.getDelay()} actual: ${1 to fps}")
        }
        if (pngReader.frameCount != frameCount) {
          appendLine("Mismatched frame count expected: ${pngReader.frameCount} actual: $frameCount")
        }
        appendLine(" - see details in file://" + deltaFile.path + "\n")
      }

      if (error.isNotEmpty()) {
        fail(error)
      }
    }
  }

  private fun initializeWriter(): APNGWriter {
    return APNGWriter(deltaFile, frameCount, pngReader.getDelay().second).apply {
      val frameNumber = pngReader.frameNumber - 1
      pngReader.reset()
      for (i in 0 until frameNumber) {
        val nextFrame = pngReader.getNextFrame() ?: blankFrame
        val (deltaImage) = ImageUtils.compareImages(
          goldenImage = nextFrame,
          image = nextFrame
        )

        writeImage(deltaImage)
      }
      pngReader.getNextFrame()
    }
  }

  private fun createBlankFrame(width: Int, height: Int): BufferedImage {
    val bufferedImage = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    val g = bufferedImage.graphics

    g.font = g.font.deriveFont(40F)
    val bounds = g.font.getStringBounds(BLANK_TEXT, g.fontMetrics.fontRenderContext)
    val xOffset = (bufferedImage.width / 2.0f) - (bounds.width / 2.0f)
    val yOffset = bufferedImage.height / 2.0f - (bounds.height / 2.0f)
    g.drawString(BLANK_TEXT, xOffset.toInt(), yOffset.toInt())
    g.dispose()
    return bufferedImage
  }

  fun close() {
    pngReader.close()
    deltaWriter?.close()
  }

  companion object {
    private const val BLANK_TEXT = "Intentionally left blank"
  }
}
