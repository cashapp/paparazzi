package app.cash.paparazzi.internal

import java.awt.image.BufferedImage

public object ComboDiffer : Differ {
  override fun compare(expected: BufferedImage, actual: BufferedImage): Differ.DiffResult {
    // val flip = FLIP.compare(expected, actual)
    val siftJavaCv = SIFTJavaCv.compare(expected, actual)
    val ssimJavaCv = SSIMJavaCv.compare(expected, actual)
    val offByTwo = OffByTwo.compare(expected, actual)
    val pixelPerfect = PixelPerfect.compare(expected, actual)
    val perceptualDiffer = PerceptualDeltaEDiffer().compare(expected, actual)

    // println("Flip: $flip")
    println("SiftJavaCv: $siftJavaCv")
    println("SsimJavaCv: $ssimJavaCv")
    println("OffByTwo: $offByTwo")
    println("PixelPerfect: $pixelPerfect")
    println("PerceptualDiffer: $perceptualDiffer")

    return offByTwo
  }
}
