package app.cash.paparazzi.internal

import org.jcodec.api.FrameGrab
import org.jcodec.api.MediaInfo
import org.jcodec.api.awt.AWTSequenceEncoder
import org.jcodec.common.io.NIOUtils
import org.jcodec.scale.AWTUtil
import org.junit.Assert
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import java.io.IOException
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Utilities related to video processing.
 */
internal object VideoUtils {

  /** Directory where to write the deltas. */
  private val failureDir: File
    get() {
      val workingDirString = System.getProperty("user.dir")
      val failureDir = File(workingDirString, "out/failures")
      failureDir.mkdirs()
      return failureDir
    }

  private const val BLANK_TEXT = "Intentionally left blank"

  @Throws(IOException::class)
  fun assertVideoSimilar(
    goldenVideo: File,
    testVideo: File,
    maxPercentDifferent: Double
  ) {
    val goldenChannel = NIOUtils.readableChannel(goldenVideo)
    val testChannel = NIOUtils.readableChannel(testVideo)

    try {
      val goldenFrameGrab = FrameGrab.createFrameGrab(goldenChannel)
      val testFrameGrab = FrameGrab.createFrameGrab(testChannel)

      val goldenMeta = goldenFrameGrab.videoTrack.meta
      val testMeta = testFrameGrab.videoTrack.meta
      val fps = (goldenMeta.totalFrames / goldenMeta.totalDuration).toInt()
      val sameFrameCount = goldenMeta.totalFrames == testMeta.totalFrames
      val sameDuration = goldenMeta.totalDuration == testMeta.totalDuration

      val output = File(failureDir, "delta-${testVideo.toPath().fileName}")
      if (output.exists()) {
        val deleted = output.delete()
        Assert.assertTrue(deleted)
      }

      // If the metadata does not line up, initialize delta encoder from the start. Else lazy init
      // after the first bad frame
      var encoder: AWTSequenceEncoder? =
        if (!sameFrameCount || !sameDuration) AWTSequenceEncoder.createSequenceEncoder(output, fps) else null
      var badFrameCount = 0
      val goldenDefaultImage = goldenFrameGrab.mediaInfo.createBlankFrame()
      val testDefaultImage = testFrameGrab.mediaInfo.createBlankFrame()
      for (index in 0 until max(testMeta.totalFrames, goldenMeta.totalFrames)) {
        val goldenImage = goldenFrameGrab.nextFrame(goldenDefaultImage)
        val testImage = testFrameGrab.nextFrame(testDefaultImage)
        val goldenFrameIndex = goldenFrameGrab.videoTrack.curFrame
        val testFrameIndex = testFrameGrab.videoTrack.curFrame

        val result = calculateDifference(goldenImage, testImage)
        if (result.percentDifferent > maxPercentDifferent) {
          if (encoder == null) {
            //Lazy init delta encoder after we've found a bad frame
            encoder = AWTSequenceEncoder.createSequenceEncoder(output, fps).also {
              //Encode the deltas up to the current frame
              goldenFrameGrab.seekToFramePrecise(0)
              testFrameGrab.seekToFramePrecise(0)
              for (i in 0 until index) {
                val goldenImage2 = goldenFrameGrab.nextFrame(goldenDefaultImage)
                val testImage2 = testFrameGrab.nextFrame(testDefaultImage)

                val result = calculateDifference(goldenImage2, testImage2)
                it.encodeImage(result.deltaImage)
              }

              //Skip current frame since we already have it
              goldenFrameGrab.nextFrame(goldenDefaultImage)
              testFrameGrab.nextFrame(testDefaultImage)
              assert(goldenFrameIndex == goldenFrameGrab.videoTrack.curFrame)
              assert(testFrameIndex == testFrameGrab.videoTrack.curFrame)
            }
          }

          badFrameCount++
        }

        encoder?.encodeImage(result.deltaImage)
      }
      encoder?.finish()

      var error = ""
      if (badFrameCount > 0) {
        error += String.format("$badFrameCount video frames differed by more than %.1f%%\n", maxPercentDifferent)
      }

      if (!sameDuration) {
        error += "Mismatched video duration expected: ${goldenMeta.totalDuration} actual: ${testMeta.totalDuration}\n"
      }

      if (!sameFrameCount) {
        error += "Mismatched frame count expected: ${goldenMeta.totalFrames} actual: ${testMeta.totalFrames}\n"
      }

      if (error.isNotEmpty()) {
        Assert.fail(error + "find delta video at ${failureDir.absoluteFile}")
      }
    } finally {
      NIOUtils.closeQuietly(goldenChannel)
      NIOUtils.closeQuietly(testChannel)
    }
  }

  @Throws(IOException::class)
  private fun calculateDifference(
    goldenImage: BufferedImage,
    actualImage: BufferedImage
  ) : Result {
    var goldenImage = goldenImage
    if (goldenImage.type != BufferedImage.TYPE_INT_ARGB) {
      val temp = BufferedImage(
          goldenImage.width, goldenImage.height,
          BufferedImage.TYPE_INT_ARGB
      )
      temp.graphics.drawImage(goldenImage, 0, 0, null)
      goldenImage = temp
    }
    Assert.assertEquals(BufferedImage.TYPE_INT_ARGB.toLong(), goldenImage.type.toLong())

    val imageWidth = min(goldenImage.width, actualImage.width)
    val imageHeight = min(goldenImage.height, actualImage.height)

    val width = 3 * imageWidth
    val deltaImage = BufferedImage(width, imageHeight, BufferedImage.TYPE_INT_ARGB)
    val g = deltaImage.graphics

    // Compute delta map
    var delta: Long = 0
    for (y in 0 until imageHeight) {
      for (x in 0 until imageWidth) {
        val goldenRgb = goldenImage.getRGB(x, y)
        val rgb = actualImage.getRGB(x, y)
        if (goldenRgb == rgb) {
          deltaImage.setRGB(imageWidth + x, y, 0x00808080)
          continue
        }

        // If the pixels have no opacity, don't delta colors at all
        if (goldenRgb and -0x1000000 == 0 && rgb and -0x1000000 == 0) {
          deltaImage.setRGB(imageWidth + x, y, 0x00808080)
          continue
        }

        val deltaR = (rgb and 0xFF0000).ushr(16) - (goldenRgb and 0xFF0000).ushr(16)
        val newR = 128 + deltaR and 0xFF
        val deltaG = (rgb and 0x00FF00).ushr(8) - (goldenRgb and 0x00FF00).ushr(8)
        val newG = 128 + deltaG and 0xFF
        val deltaB = (rgb and 0x0000FF) - (goldenRgb and 0x0000FF)
        val newB = 128 + deltaB and 0xFF

        val avgAlpha =
          ((goldenRgb and -0x1000000).ushr(24) + (rgb and -0x1000000).ushr(24)) / 2 shl 24

        val newRGB = avgAlpha or (newR shl 16) or (newG shl 8) or newB
        deltaImage.setRGB(imageWidth + x, y, newRGB)

        delta += abs(deltaR).toLong()
        delta += abs(deltaG).toLong()
        delta += abs(deltaB).toLong()
      }
    }

    // 3 different colors, 256 color levels
    val total = imageHeight.toLong() * imageWidth.toLong() * 3L * 256L
    val percentDifference = (delta * 100 / total.toDouble()).toFloat()

    g.drawImage(goldenImage, 0, 0, null)
    g.drawImage(actualImage, 2 * imageWidth, 0, null)

    // Labels
    if (imageWidth > 80) {
      g.color = Color.RED
      g.drawString("Expected", 10, 20)
      g.drawString("Actual", 2 * imageWidth + 10, 20)
    }

    g.dispose()
    return Result(deltaImage, percentDifference)
  }

  private fun FrameGrab.nextFrame(default: BufferedImage): BufferedImage {
    if (videoTrack.curFrame >= videoTrack.meta.totalFrames) {
      return default
    }

    return AWTUtil.toBufferedImage(nativeFrame)
  }

  private fun MediaInfo.createBlankFrame(): BufferedImage {
    val bufferedImage = BufferedImage(dim.width, dim.height, BufferedImage.TYPE_INT_RGB)
    val g = bufferedImage.graphics

    g.font = g.font.deriveFont(40F)
    val bounds = g.font.getStringBounds(BLANK_TEXT, g.fontMetrics.fontRenderContext)
    val xOffset = (bufferedImage.width / 2.0f) - (bounds.width / 2.0f)
    val yOffset = bufferedImage.height / 2.0f - (bounds.height / 2.0f)
    g.drawString(BLANK_TEXT, xOffset.toInt(), yOffset.toInt())
    return bufferedImage
  }

  private class Result(val deltaImage: BufferedImage, val percentDifferent: Float)
}
