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
import org.junit.Assert.fail
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
  private val fileSystem: FileSystem = FileSystem.SYSTEM
) : Closeable {
  private val pngReader = ApngReader(fileSystem.openReadOnly(goldenFilePath))
  private val blankFrame by lazy { createBlankFrame(pngReader.width, pngReader.height) }
  private val invalidFrames = mutableListOf<Float>()

  private var deltaWriter: ApngWriter? = null
  private var commonFrameRate: Int = -1
  private var actualDeltasPerFrame: Int = 1
  private var expectedDeltasPerFrame: Int = 1

  private lateinit var currentGoldenFrame: BufferedImage

  fun verifyFrame(image: BufferedImage) {
    if (!::currentGoldenFrame.isInitialized) {
      currentGoldenFrame = pngReader.readNextFrame() ?: blankFrame
      commonFrameRate = leastCommonMultiple(fps, pngReader.getFps())
      actualDeltasPerFrame = commonFrameRate / fps
      expectedDeltasPerFrame = commonFrameRate / pngReader.getFps()
      if (fps != pngReader.getFps()) {
        deltaWriter = initializeWriter(1)
      }
    } else if (deltaWriter == null) {
      currentGoldenFrame = pngReader.readNextFrame() ?: blankFrame
    }

    val (expectedFrame, actualFrame) = resize(currentGoldenFrame, image)
    val (deltaImage, percentDifferent) = ImageUtils.compareImages(expectedFrame, actualFrame)
    if (percentDifferent > maxPercentDifference) {
      if (deltaWriter == null) {
        val failFrameOffset = if (currentGoldenFrame == blankFrame) 0 else 1
        // We've already calculated the current frame's deltaImage so initialize our delta
        // writer up to the last good frame. When the current golden frame is not blank we don't
        // want the writer to initialize this frame again so we offset to adjust.
        deltaWriter = initializeWriter(failFrameOffset)
        // And then skip the current frame
        pngReader.readNextFrame()
      }
      invalidFrames += percentDifferent
    }

    var currentDelta = deltaImage
    deltaWriter?.let { writer ->
      repeat(actualDeltasPerFrame) {
        writer.writeImage(currentDelta)
        if (writer.frameCount % expectedDeltasPerFrame == 0) {
          currentGoldenFrame = pngReader.readNextFrame() ?: blankFrame
          val (expectedFrame, actualFrame) = resize(currentGoldenFrame, image)
          currentDelta = ImageUtils.compareImages(expectedFrame, actualFrame).first
        }
      }
    }
  }

  fun assertFinished() {
    while (!pngReader.isFinished()) {
      if (deltaWriter == null) {
        deltaWriter = initializeWriter()
      }

      val nextFrame = pngReader.readNextFrame() ?: blankFrame
      val (deltaImage, percentDifferent) = ImageUtils.compareImages(
        goldenImage = nextFrame,
        image = blankFrame
      )
      repeat(expectedDeltasPerFrame) { deltaWriter!!.writeImage(deltaImage) }
      invalidFrames += percentDifferent
    }

    if (deltaWriter != null) {
      val error = buildString {
        if (invalidFrames.size >= 1) {
          appendLine(
            "${invalidFrames.size} frames differed by more than %.1f%%".format(maxPercentDifference)
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
        fail(error)
      }
    }
  }

  /**
   * Lazy initialize the delta writer up to a target frame. All frames at this point have
   * been correct so we seed all the delta frames by comparing each frame to itself
   * and writing the no difference delta frames. This saves us from generating a delta
   * animation in the happy path at the expense of recalculating these delta frames on failure.
   */
  private fun initializeWriter(targetFrameOffset: Int = 0): ApngWriter {
    return ApngWriter(deltaFilePath, commonFrameRate, fileSystem).apply {
      val currentFrameNumber = pngReader.frameNumber
      pngReader.reset()
      for (i in 0 until currentFrameNumber - targetFrameOffset) {
        val nextFrame = pngReader.readNextFrame() ?: blankFrame
        val (deltaImage) = ImageUtils.compareImages(
          goldenImage = nextFrame,
          image = nextFrame
        )

        writeImage(deltaImage)
      }
    }
  }

  override fun close() {
    pngReader.close()
    deltaWriter?.close()
  }

  private fun createBlankFrame(width: Int, height: Int): BufferedImage {
    val bufferedImage = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
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

  private fun resize(
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
