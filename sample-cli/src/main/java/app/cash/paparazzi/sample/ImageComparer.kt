package app.cash.paparazzi.sample

import java.awt.Color
import java.awt.image.BufferedImage
import java.awt.image.BufferedImage.TYPE_INT_ARGB
import java.io.File
import java.io.File.separatorChar
import javax.imageio.ImageIO

public fun main() {
  val image1 =
    "sample-cli/app.cash.paparazzi.sample_RenderingIssuesTest_example_mac-arm-7ca13900b_.png"
  val image2 =
    "sample-cli/app.cash.paparazzi.sample_RenderingIssuesTest_example_mac-arm-3339ae53a_.png"
  val image3 = "sample-cli/app.cash.paparazzi.sample_RenderingIssuesTest_example_linux-3339ae5_.png"
  val image4 = "sample-cli/app.cash.paparazzi.sample_RenderingIssuesTest_example_win-3339ae5_.png"

  compareImages(image1, image2)
  compareImages(image2, image3)
  compareImages(image3, image4)
}

private fun readImage(image: String): BufferedImage {
  val file = File(image)
  println("Reading: ${file.absolutePath}")
  return ImageIO.read(file)
}

public fun compareImages(image1: String, image2: String) {
  val goldenImage: BufferedImage = readImage(image1)
  val image: BufferedImage = readImage(image2)

  val goldenImageWidth = goldenImage.width
  val goldenImageHeight = goldenImage.height

  val imageWidth = image.width
  val imageHeight = image.height

  val deltaWidth = Math.max(goldenImageWidth, imageWidth)
  val deltaHeight = Math.max(goldenImageHeight, imageHeight)

  println("image1: $goldenImageWidth x $goldenImageHeight")
  println("image2: $imageWidth x $imageHeight")
  println("image1 (type): " + goldenImage.type)
  println("image2 (type): " + image.type)

  // Blur the images to account for the scenarios where there are pixel
  // differences
  // in where a sharp edge occurs
  // goldenImage = blur(goldenImage, 6);
  // image = blur(image, 6);
  val width = goldenImageWidth + deltaWidth + imageWidth
  val deltaImage = BufferedImage(width, deltaHeight, TYPE_INT_ARGB)
  val g = deltaImage.graphics

  var equalPixels: Long = 0
  var differentPixels: Long = 0

  // Compute delta map
  var delta: Long = 0
  for (y in 0 until deltaHeight) {
    for (x in 0 until deltaWidth) {
      val goldenRgb = if (x >= goldenImageWidth || y >= goldenImageHeight) {
        0x00808080
      } else {
        goldenImage.getRGB(x, y)
      }

      val rgb = if (x >= imageWidth || y >= imageHeight) {
        0x00808080
      } else {
        image.getRGB(x, y)
      }

      if (goldenRgb == rgb) {
        deltaImage.setRGB(goldenImageWidth + x, y, 0x00808080)
        equalPixels++
        continue
      }

      // If the pixels have no opacity, don't delta colors at all
      if (goldenRgb and -0x1000000 == 0 && rgb and -0x1000000 == 0) {
        deltaImage.setRGB(goldenImageWidth + x, y, 0x00808080)
        continue
      }

      differentPixels++
      val r = (rgb and 0xFF0000).ushr(16)
      val goldenR = (goldenRgb and 0xFF0000).ushr(16)
      val g = (rgb and 0x00FF00).ushr(8)
      val goldenG = (goldenRgb and 0x00FF00).ushr(8)
      val b = (rgb and 0x0000FF)
      val goldenB = (goldenRgb and 0x0000FF)
      println("Different pixel at ($x, $y): ($r, $g, $b) => ($goldenR, $goldenG, $goldenB)")

      val deltaR = (rgb and 0xFF0000).ushr(16) - (goldenRgb and 0xFF0000).ushr(16)
      val newR = 128 + deltaR and 0xFF
      val deltaG = (rgb and 0x00FF00).ushr(8) - (goldenRgb and 0x00FF00).ushr(8)
      val newG = 128 + deltaG and 0xFF
      val deltaB = (rgb and 0x0000FF) - (goldenRgb and 0x0000FF)
      val newB = 128 + deltaB and 0xFF

      val avgAlpha =
        ((goldenRgb and -0x1000000).ushr(24) + (rgb and -0x1000000).ushr(24)) / 2 shl 24

      val newRGB = avgAlpha or (newR shl 16) or (newG shl 8) or newB
      deltaImage.setRGB(goldenImageWidth + x, y, newRGB)

      delta += Math.abs(deltaR)
        .toLong()
      delta += Math.abs(deltaG)
        .toLong()
      delta += Math.abs(deltaB)
        .toLong()
    }
  }

  println("Equal pixels: $equalPixels")
  println("Different pixels: $differentPixels")

  // 3 different colors, 256 color levels
  val total = deltaHeight.toLong() * deltaWidth.toLong() * 3L * 256L
  val percentDifference = (delta * 100 / total.toDouble()).toFloat()

  var error: String? = null
  val imageName = getName(image2)
  if (percentDifference > 0.0) {
    error = String.format("Images differ (by %f%%)", percentDifference)
  } else if (Math.abs(goldenImageWidth - imageWidth) >= 2) {
    error = "Widths differ too much for " + imageName + ": " +
      goldenImageWidth + "x" + goldenImageHeight +
      "vs" + imageWidth + "x" + imageHeight
  } else if (Math.abs(goldenImageHeight - imageHeight) >= 2) {
    error = "Heights differ too much for " + imageName + ": " +
      goldenImageWidth + "x" + goldenImageHeight +
      "vs" + imageWidth + "x" + imageHeight
  }

  if (error != null) {
    // Expected on the left
    // Golden on the right
    g.drawImage(goldenImage, 0, 0, null)
    g.drawImage(image, goldenImageWidth + deltaWidth, 0, null)

    // Labels
    if (deltaWidth > 80) {
      g.color = Color.RED
      g.drawString("Expected", 10, 20)
      g.drawString("Actual", goldenImageWidth + deltaWidth + 10, 20)
    }

    val output = File("delta-$imageName")
    if (output.exists()) {
      output.delete()
    }
    ImageIO.write(deltaImage, "PNG", output)
    error += " - see details in file://" + output.absolutePath
    val output1 = File(getName(image2))
    if (output1.exists()) {
      output1.delete()
    }
    ImageIO.write(image, "PNG", output1)

    println("${ANSI_RED}$error$ANSI_RESET")
  } else {
    println("Images are identical!")
  }

  g.dispose()
}

private fun getName(relativePath: String): String {
  return relativePath.substring(relativePath.lastIndexOf(separatorChar) + 1)
}

public const val ANSI_RESET: String = "\u001B[0m"
public const val ANSI_RED: String = "\u001B[31m"
