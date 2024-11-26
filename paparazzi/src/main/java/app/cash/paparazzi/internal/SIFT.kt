package app.cash.paparazzi.internal

import org.opencv.core.Core
import org.opencv.core.Core.NORM_HAMMING
import org.opencv.core.CvType
import org.opencv.core.DMatch
import org.opencv.core.Mat
import org.opencv.core.MatOfDMatch
import org.opencv.core.MatOfKeyPoint
import org.opencv.features2d.BFMatcher
import org.opencv.features2d.Features2d
import org.opencv.features2d.ORB
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import java.awt.image.BufferedImage

public object FLIPOpenCv : Differ {

  init {
    // Load the OpenCV library
    System.loadLibrary(Core.NATIVE_LIBRARY_NAME)
  }

  // Convert BufferedImage to Mat (OpenCV)
  private fun bufferedImageToMat(image: BufferedImage): Mat {
    val width = image.width
    val height = image.height
    val mat = Mat(height, width, CvType.CV_8UC3)

    for (y in 0 until height) {
      for (x in 0 until width) {
        val rgb = image.getRGB(x, y)
        val blue = (rgb and 0xFF)
        val green = (rgb shr 8 and 0xFF)
        val red = (rgb shr 16 and 0xFF)
        mat.put(y, x, byteArrayOf(blue.toByte(), green.toByte(), red.toByte()))
      }
    }
    return mat
  }

  // Detect keypoints and descriptors using ORB
  private fun detectORBFeatures(image: Mat): Pair<MatOfKeyPoint, Mat> {
    val orb = ORB.create() // Using ORB for feature detection and description
    val keypoints = MatOfKeyPoint()
    val descriptors = Mat()
    orb.detectAndCompute(image, Mat(), keypoints, descriptors)
    return Pair(keypoints, descriptors)
  }

  // Match descriptors using Brute-Force Matcher (BFMatcher)
  private fun matchDescriptors(descriptors1: Mat, descriptors2: Mat): List<DMatch> {
    val bfMatcher = BFMatcher(NORM_HAMMING, true) // Using Hamming distance for binary descriptors
    val matches = MatOfDMatch()
    bfMatcher.match(descriptors1, descriptors2, matches)
    return matches.toList()
  }

  override fun compare(expected: BufferedImage, actual: BufferedImage): Differ.DiffResult {
    // Convert BufferedImage to Mat
    val mat1 = bufferedImageToMat(expected)
    val mat2 = bufferedImageToMat(actual)

    // Convert images to grayscale
    val grayMat1 = Mat()
    val grayMat2 = Mat()
    Imgproc.cvtColor(mat1, grayMat1, Imgproc.COLOR_BGR2GRAY)
    Imgproc.cvtColor(mat2, grayMat2, Imgproc.COLOR_BGR2GRAY)

    // Detect keypoints and descriptors using ORB
    val (keypoints1, descriptors1) = detectORBFeatures(grayMat1)
    val (keypoints2, descriptors2) = detectORBFeatures(grayMat2)

    // Match descriptors
    val matches = matchDescriptors(descriptors1, descriptors2)

    // Filter matches (using some threshold on the distance)
    val goodMatches = matches.filter { it.distance < 50.0 } // Threshold distance can be adjusted

    // Draw matches on the images
    val imgMatches = Mat()
    Features2d.drawMatches(mat1, keypoints1, mat2, keypoints2, MatOfDMatch(*goodMatches.toTypedArray()), imgMatches)

    // Save the result image with matches drawn
    Imgcodecs.imwrite("matched_result.jpg", imgMatches)

    // Calculate similarity based on the number of good matches
    println("Number of good matches: ${goodMatches.size} / ${matches.size}")
    return Differ.DiffResult.Identical(expected)
  }

  // Main function to test FLIP image comparison
  //   @JvmStatic
  //   fun main(args: Array<String>) {
  //     // Load images as BufferedImage
  //     val image1 = ImageIO.read(File("image1.jpg"))
  //     val image2 = ImageIO.read(File("image2.jpg"))
  //
  //     // Compare images using FLIP
  //     val similarity = compareImages(image1, image2)
  //
  //     println("Image similarity score: $similarity")
  //     if (similarity > 0.1) {
  //       println("The images are similar!")
  //     } else {
  //       println("The images are different!")
  //     }
  //   }
  // }
}
