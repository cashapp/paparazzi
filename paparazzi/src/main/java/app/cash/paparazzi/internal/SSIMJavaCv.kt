package app.cash.paparazzi.internal

import org.bytedeco.javacv.Java2DFrameConverter
import org.bytedeco.javacv.OpenCVFrameConverter
import org.bytedeco.opencv.global.opencv_core.CV_32F
import org.bytedeco.opencv.global.opencv_core.CV_8UC3
import org.bytedeco.opencv.global.opencv_core.add
import org.bytedeco.opencv.global.opencv_core.divide
import org.bytedeco.opencv.global.opencv_core.mean
import org.bytedeco.opencv.global.opencv_core.multiply
import org.bytedeco.opencv.global.opencv_core.subtract
import org.bytedeco.opencv.global.opencv_imgproc.COLOR_BGR2GRAY
import org.bytedeco.opencv.global.opencv_imgproc.GaussianBlur
import org.bytedeco.opencv.global.opencv_imgproc.cvtColor
import org.bytedeco.opencv.opencv_core.Mat
import org.bytedeco.opencv.opencv_core.Scalar
import org.bytedeco.opencv.opencv_core.Size
import java.awt.image.BufferedImage

public object SSIMJavaCv: Differ {
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

  // SSIM computation taken from
  private fun getSSIM(i1: Mat, i2: Mat): Scalar {
    val C1 = 6.5025
    val C2 = 58.5225

    val d = CV_32F

    val I1 = Mat()
    val I2 = Mat()
    i1.convertTo(I1, d)
    i2.convertTo(I2, d)

    val I2_2 = I2.mul(I2).asMat()
    val I1_2 = I1.mul(I1).asMat()
    val I1_I2 = I1.mul(I2).asMat()

    val mu1 = Mat()
    val mu2 = Mat()
    GaussianBlur(I1, mu1, Size(11, 11), 1.5)
    GaussianBlur(I2, mu2, Size(11, 11), 1.5)

    val mu1_2 = mu1.mul(mu1).asMat()
    val mu2_2 = mu2.mul(mu2).asMat()
    val mu1_mu2 = mu1.mul(mu2).asMat()

    val sigma1_2 = Mat()
    val sigma2_2 = Mat()
    val sigma12 = Mat()

    GaussianBlur(I1_2, sigma1_2, Size(11, 11), 1.5)
    subtract(sigma1_2, mu1_2) // sigma1_2 -= mu1_2

    GaussianBlur(I2_2, sigma2_2, Size(11, 11), 1.5)
    subtract(sigma2_2, mu2_2)  // sigma2_2 -= mu2_2

    GaussianBlur(I1_I2, sigma12, Size(11, 11), 1.5)
    subtract(sigma12, mu1_mu2) // sigma12 -= mu1_mu2

    add(multiply(mu1_mu2, 2.0), Scalar(C1))
    var t1: Mat = add(multiply(mu1_mu2, 2.0), Scalar(C1)).asMat() // 2.0 * mu1_mu2 + C1
    var t2: Mat = add(multiply(sigma12, 2.0), Scalar(C2)).asMat() // 2.0 * sigma12 + C2
    val t3: Mat = t1.mul(t2).asMat()

    t1 = add(add(mu1_2, mu2_2).asMat(), Scalar(C1)).asMat() // mu1_2 + mu2_2 + C1
    t2 = add(add(sigma1_2, sigma2_2).asMat(), Scalar(C2)).asMat() // sigma1_2 + sigma2_2 + C2
    t1 = t1.mul(t2).asMat()

    val ssimMap = Mat()
    divide(t3, t1, ssimMap)

    return mean(ssimMap)
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

    // Compute SSIM as a similarity measure
    val ssimValue = getSSIM(gray1, gray2)
    val percentDiff = 1f - ssimValue[0].toFloat()

    return when {
      percentDiff == 0f -> Differ.DiffResult.Identical(expected)
      else -> Differ.DiffResult.Different(delta = expected, percentDifference = percentDiff, numDifferentPixels = 0)
    }
  }
}
