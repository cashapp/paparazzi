package app.cash.paparazzi.internal

import org.bytedeco.javacv.Java2DFrameConverter
import org.bytedeco.javacv.OpenCVFrameConverter
import org.bytedeco.opencv.global.opencv_core.CV_8UC3
import org.bytedeco.opencv.global.opencv_core.NORM_L2
import org.bytedeco.opencv.global.opencv_features2d.drawMatches
import org.bytedeco.opencv.global.opencv_imgproc.COLOR_BGR2GRAY
import org.bytedeco.opencv.global.opencv_imgproc.cvtColor
import org.bytedeco.opencv.opencv_core.DMatchVector
import org.bytedeco.opencv.opencv_core.KeyPointVector
import org.bytedeco.opencv.opencv_core.Mat
import org.bytedeco.opencv.opencv_features2d.BFMatcher
import org.bytedeco.opencv.opencv_features2d.SIFT
import java.awt.image.BufferedImage
import kotlin.math.max

public object SIFTJavaCv: Differ {
  private fun bufferedImageToMat(image: BufferedImage): Mat {
    val mat = Mat(image.height, image.width, CV_8UC3)
    val pixels = image.getRGB(0, 0, image.width, image.height, null, 0, image.width)
    val data = ByteArray(image.width * image.height * 3)

    for (i in pixels.indices) {
      val pixel = pixels[i]
      data[i * 3] = (pixel and 0xFF).toByte()          // Blue
      data[i * 3 + 1] = ((pixel shr 8) and 0xFF).toByte() // Green
      data[i * 3 + 2] = ((pixel shr 16) and 0xFF).toByte() // Red
    }

    mat.data().put(*data)
    return mat
  }

  private fun convertMatToBufferedImage(mat: Mat): BufferedImage {
    // Convert Mat to Frame
    val matConverter = OpenCVFrameConverter.ToMat()
    val frame = matConverter.convert(mat)

    // Convert Frame to BufferedImage
    val imageConverter = Java2DFrameConverter()
    return imageConverter.convert(frame)
  }

  override fun compare(expected: BufferedImage, actual: BufferedImage): Differ.DiffResult {
    val expectedMat = bufferedImageToMat(expected)
    val actualMat = bufferedImageToMat(actual)

    // Convert images to grayscale
    val gray1 = Mat()
    val gray2 = Mat()
    cvtColor(expectedMat, gray1, COLOR_BGR2GRAY)
    cvtColor(actualMat, gray2, COLOR_BGR2GRAY)

    // Create SIFT detector
    val sift = SIFT.create()

    // Detect keypoints and compute descriptors
    val keyPoints1 = KeyPointVector()
    val descriptors1 = Mat()
    val keyPoints2 = KeyPointVector()
    val descriptors2 = Mat()

    sift.detectAndCompute(gray1, Mat(), keyPoints1, descriptors1)
    sift.detectAndCompute(gray2, Mat(), keyPoints2, descriptors2)

    // Match descriptors using BFMatcher
    val bfMatcher = BFMatcher.create(NORM_L2, false)
    val matches = DMatchVector()
    bfMatcher.match(descriptors1, descriptors2, matches)

    // Draw matches
    val outputImage = Mat()
    drawMatches(expectedMat, keyPoints1, actualMat, keyPoints2, matches, outputImage)
    val delta = convertMatToBufferedImage(outputImage)

    val percentDiff = 1f - matches.size() / max(keyPoints1.size(), keyPoints2.size()).toFloat()

    return when {
      percentDiff == 0f -> Differ.DiffResult.Identical(expected)
      else -> Differ.DiffResult.Different(delta = delta, percentDifference = percentDiff, numDifferentPixels = matches.size())
    }
  }
}
