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
import app.cash.paparazzi.internal.ImageUtils.resize
import okio.FileSystem
import okio.Path
import java.awt.image.BufferedImage
import java.io.Closeable
import kotlin.math.max
import kotlin.math.min

internal class ApngVerifier(
  goldenFilePath: Path,
  private val deltaFilePath: Path,
  private val fps: Int,
  private val frameCount: Int,
  private val maxPercentDifference: Double,
  private val fileSystem: FileSystem = FileSystem.SYSTEM,
  private val withErrorText: Boolean = true
) : Closeable {
  private val pngReader = ApngReader(fileSystem.openReadOnly(goldenFilePath))
  private val blankFrame by lazy { createBlankFrame(pngReader.width, pngReader.height) }

  private var deltaWriter: ApngWriter? = null
  private var commonFrameRate: Int = -1
  private var actualDeltasPerFrame: Int = 1
  private var expectedDeltasPerFrame: Int = 1
  private var currentGoldenFrame: BufferedImage?
  private var invalidFrames = 0

  init {
    currentGoldenFrame = pngReader.readNextFrame()
    commonFrameRate = leastCommonMultiple(fps, pngReader.getFps())
    actualDeltasPerFrame = commonFrameRate / fps
    expectedDeltasPerFrame = commonFrameRate / pngReader.getFps()
    if (frameCount != pngReader.frameCount || fps != pngReader.getFps()) {
      deltaWriter = ApngWriter(deltaFilePath, commonFrameRate, fileSystem)
    }
  }

  fun verifyFrame(image: BufferedImage) {
    val (expectedFrame, actualFrame) = resizeMaxBounds(currentGoldenFrame ?: blankFrame, image)
    val (deltaImage, percentDifferent) = ImageUtils.compareImages(expectedFrame, actualFrame, withErrorText)
    if (percentDifferent > maxPercentDifference) {
      if (deltaWriter == null) {
        deltaWriter = pngReader.initializeWriter()
      }
      invalidFrames++
    }

    val writer = deltaWriter
    if (writer == null) {
      currentGoldenFrame = pngReader.readNextFrame()
      return
    }

    // Write the delta frames for this frame
    var currentDelta = deltaImage
    repeat(actualDeltasPerFrame) {
      writer.writeImage(currentDelta)
      // While adding delta frames for this actual frame, compare the current output video's frame #
      // with the expected video's delta frame count to decide if we need to prepare the next
      // expected frame for the delta image diff.
      if (writer.frameCount % expectedDeltasPerFrame == 0) {
        currentGoldenFrame = pngReader.readNextFrame()
        val (expectedFrame, actualFrame) = resizeMaxBounds(currentGoldenFrame ?: blankFrame, image)
        currentDelta = ImageUtils.compareImages(expectedFrame, actualFrame, withErrorText).first
      }
    }
  }

  fun assertFinished() {
    val deltaWriter = deltaWriter ?: return
    currentGoldenFrame?.let { lastFrame ->
      val (expectedFrame, actualFrame) = resizeMaxBounds(lastFrame, blankFrame)
      val (currentDelta) = ImageUtils.compareImages(expectedFrame, actualFrame, withErrorText)

      val times = expectedDeltasPerFrame - (deltaWriter.frameCount % expectedDeltasPerFrame)
      repeat(times) { deltaWriter.writeImage(currentDelta) }
      invalidFrames++
    }

    while (!pngReader.isFinished()) {
      val (deltaImage) = ImageUtils.compareImages(
        goldenImage = pngReader.readNextFrame()!!,
        image = blankFrame,
        withText = withErrorText
      )
      repeat(expectedDeltasPerFrame) { deltaWriter.writeImage(deltaImage) }
      invalidFrames++
    }

    val error = buildString {
      if (invalidFrames >= 1) {
        appendLine(
          "$invalidFrames frames differed by more than %.1f%%".format(maxPercentDifference)
        )
      }
      if (pngReader.getFps() != fps) {
        appendLine("Mismatched video fps expected: ${pngReader.getFps()} actual: $fps")
      }
      if (pngReader.frameCount != frameCount) {
        appendLine("Mismatched frame count expected: ${pngReader.frameCount} actual: $frameCount")
      }
      appendLine(" - see details in file://$deltaFilePath\n")
    }

    if (error.isNotEmpty()) {
      throw AssertionError(error)
    }
  }

  override fun close() {
    pngReader.close()
    deltaWriter?.close()
  }

  /**
   * Initializes the writer that generates delta images for all frames excluding the current
   * frame which is about to be written by the [verifyFrame] method.
   */
  private fun ApngReader.initializeWriter(): ApngWriter {
    val currentFrameNumber = frameNumber - 1
    reset()

    return ApngWriter(deltaFilePath, commonFrameRate, fileSystem).apply {
      repeat(currentFrameNumber) {
        val nextFrame = readNextFrame() ?: blankFrame
        val (deltaImage) = ImageUtils.compareImages(
          goldenImage = nextFrame,
          image = nextFrame,
          withText = withErrorText
        )

        writeImage(deltaImage)
      }
      readNextFrame()
    }
  }

  private fun createBlankFrame(width: Int, height: Int): BufferedImage {
    val bufferedImage = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    if (!withErrorText) {
      return bufferedImage
    }

    val g = bufferedImage.graphics

    g.font = g.font.deriveFont(40F)
    var bounds = g.font.getStringBounds(BLANK_TEXT, g.fontMetrics.fontRenderContext)
    val scale = min(width / (bounds.width + BLANK_PADDING), height / (bounds.height + BLANK_PADDING))
    if (scale < 1) {
      g.font = g.font.deriveFont(40F * scale.toFloat())
      bounds = g.font.getStringBounds(BLANK_TEXT, g.fontMetrics.fontRenderContext)
    }

    val xOffset = (bufferedImage.width / 2.0f) - (bounds.width / 2.0f)
    val yOffset = (bufferedImage.height / 2.0f) + (bounds.height / 2.0f)
    g.drawString(BLANK_TEXT, xOffset.toInt(), yOffset.toInt())
    g.dispose()
    return bufferedImage
  }

  private fun resizeMaxBounds(
    firstImage: BufferedImage,
    secondImage: BufferedImage
  ): Pair<BufferedImage, BufferedImage> {
    val width = max(firstImage.width, secondImage.width)
    val height = max(firstImage.height, secondImage.height)

    return firstImage.updateSize(width, height) to secondImage.updateSize(width, height)
  }

  private fun leastCommonMultiple(first: Int, second: Int): Int {
    return (first * second) / greatestCommonDenominator(first, second)
  }

  private fun greatestCommonDenominator(first: Int, second: Int): Int {
    var first = first
    var second = second
    while (first != second) {
      if (first > second) {
        first -= second
      } else {
        second -= first
      }
    }
    return first
  }

  private fun BufferedImage.updateSize(targetWidth: Int, targetHeight: Int) =
    if (width != targetWidth || height != targetHeight) {
      resize(targetWidth, targetHeight)
    } else {
      this
    }

  companion object {
    private const val BLANK_TEXT = "Intentionally left blank"
    private const val BLANK_PADDING = 20
  }
}
