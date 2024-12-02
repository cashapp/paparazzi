package app.cash.paparazzi.internal

import org.bytedeco.opencv.global.opencv_core.CV_32F
import org.bytedeco.opencv.global.opencv_core.CV_8UC3
import org.bytedeco.opencv.global.opencv_core.absdiff
import org.bytedeco.opencv.global.opencv_core.add
import org.bytedeco.opencv.global.opencv_core.max
import org.bytedeco.opencv.global.opencv_core.merge
import org.bytedeco.opencv.global.opencv_core.min
import org.bytedeco.opencv.global.opencv_core.multiply
import org.bytedeco.opencv.global.opencv_core.norm
import org.bytedeco.opencv.global.opencv_core.split
import org.bytedeco.opencv.global.opencv_core.subtract
import org.bytedeco.opencv.global.opencv_imgproc.COLOR_BGR2Lab
import org.bytedeco.opencv.global.opencv_imgproc.cvtColor
import org.bytedeco.opencv.global.opencv_imgproc.filter2D
import org.bytedeco.opencv.global.opencv_imgproc.getDerivKernels
import org.bytedeco.opencv.global.opencv_imgproc.getGaussianKernel
import org.bytedeco.opencv.opencv_core.Mat
import org.bytedeco.opencv.opencv_core.MatVector
import org.bytedeco.opencv.opencv_core.Scalar
import java.awt.image.BufferedImage
import kotlin.math.abs
import kotlin.math.sqrt

public fun convolveWithCSFs(image: Mat, csfKernel: Mat): Mat {
  val result = Mat()
  filter2D(image, result, image.depth(), csfKernel)
  return result
}

public fun clamp(image: Mat): Mat {
  return min(max(image, 0.0).asMat(), 1.0).asMat()
}

public fun transformToCIELAB(image: Mat): Mat {
  val labImage = Mat()
  cvtColor(image, labImage, COLOR_BGR2Lab)
  return labImage
}

public fun convolveWithFirstGaussianDerivative(image: Mat, sigma: Double): Mat {
  val kernel = getGaussianKernel(5, sigma)
  val edges = Mat()
  filter2D(image, edges, image.depth(), kernel)
  return edges
}

public fun convolveWithSecondGaussianDerivative(image: Mat, sigma: Double): Mat {
  val kx = Mat()
  val ky = Mat()
  getDerivKernels(kx, ky, 2, 0, 5, true, CV_32F)
  val points = Mat()
  filter2D(image, points, image.depth(), kx)
  return points
}

public fun hyAB(preprocessedRef: Mat, preprocessedTest: Mat): Mat {
  val deltaE = Mat()
  absdiff(preprocessedRef, preprocessedTest, deltaE)
  return deltaE
}

public fun transformToOpponentSpace(image: Mat): Mat {
  val opponentMat = Mat(image.size(), image.type())
  val channels = MatVector()
  split(image, channels)

  // Opponent channels
  val O1 = Mat()
  val O2 = Mat()
  val O3 = Mat()

  // Compute opponent channels: O1 = (R - G), O2 = (R + G - 2B), O3 = (R + G + B)
  subtract(channels[2], channels[1], O1) // R - G
  add(channels[2], channels[1], O2)     // R + G
  subtract(O2, multiply(channels[0], 2.0).asMat(), O2)   // R + G - 2B
  add(channels[0], O2, O3)              // R + G + B

  merge(MatVector(O1, O2, O3), opponentMat)
  return opponentMat
}

public fun flipAlgorithm(ref: Mat, test: Mat, pixelsPerDegree: Double): Mat {
  // Step 1: Transform to opponent space
  val opponentRef = transformToOpponentSpace(ref)
  val opponentTest = transformToOpponentSpace(test)

  // Step 2: Spatial filtering
  val csfKernel = Mat() // Define or load a kernel for CSFs
  val filteredRef = clamp(convolveWithCSFs(opponentRef, csfKernel))
  val filteredTest = clamp(convolveWithCSFs(opponentTest, csfKernel))

  // Step 3: CIELAB transformation and perceptual adjustment
  val labRef = transformToCIELAB(filteredRef)
  val labTest = transformToCIELAB(filteredTest)

  // Step 4: Compute color difference
  val deltaEhyab = hyAB(labRef, labTest)
  val cmax = 1.0 // Compute based on color range

  // Step 5: Edge and point detection
  val edgesRef = convolveWithFirstGaussianDerivative(opponentRef, pixelsPerDegree)
  val pointsRef = convolveWithSecondGaussianDerivative(opponentRef, pixelsPerDegree)
  val edgesTest = convolveWithFirstGaussianDerivative(opponentTest, pixelsPerDegree)
  val pointsTest = convolveWithSecondGaussianDerivative(opponentTest, pixelsPerDegree)

  val deltaEf = kotlin.math.max(
    abs(norm(edgesRef) - norm(edgesTest)),
    abs(norm(pointsRef) - norm(pointsTest))
  ) / sqrt(2.0)

  // Combine color and feature metrics
  val deltaE = add(deltaEhyab, Scalar(deltaEf)).asMat()
  return deltaE
}
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

public object FLIP : Differ {
  override fun compare(expected: BufferedImage, actual: BufferedImage): Differ.DiffResult {
    val ret = flipAlgorithm(bufferedImageToMat(expected), bufferedImageToMat(actual), 67.0)
    println("FLIP Ret $ret")
    return Differ.DiffResult.Identical(expected)
  }
}
