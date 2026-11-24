package app.cash.paparazzi.internal.differs

import app.cash.paparazzi.Differ
import app.cash.paparazzi.Differ.DiffResult
import java.awt.Color
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.pow

internal object Sift : Differ {
  override fun compare(expected: BufferedImage, actual: BufferedImage): DiffResult {
    require(expected.width == actual.width && expected.height == actual.height)

    val width = expected.width
    val height = expected.height

    val grayscaleExpected = toGrayscale(expected)
    val grayscaleActual = toGrayscale(actual)

    val octaves = 4.coerceAtMost(min(width, height).coerceAtMost(128))
    val scalesPerOctave = 3.coerceAtMost(octaves)

    val gExpected = buildGaussianPyramid(grayscaleExpected, octaves, scalesPerOctave)
    val gActual = buildGaussianPyramid(grayscaleActual, octaves, scalesPerOctave)

    val dogExpected = buildDoGPyramid(gExpected)
    val dogActual = buildDoGPyramid(gActual)

    val keypointsExpected = detectDoGExtrema(dogExpected)
    val keypointsActual = detectDoGExtrema(dogActual)

    val matches = matchKeypoints(keypointsExpected, keypointsActual)

    val delta = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    val g = delta.createGraphics()
    g.drawImage(expected, 0, 0, null)
    drawKeypoints(g, keypointsExpected, Color.RED)
    drawKeypoints(g, matches, Color.GREEN)
    g.dispose()

    val total = keypointsExpected.size
    val matched = matches.size
    val percentMatch = if (total == 0) 0f else matched.toFloat() / total * 100f

    return when {
      percentMatch == 100f -> DiffResult.Identical(delta)
      percentMatch > 95f -> DiffResult.Similar(delta, matched.toLong())
      else -> DiffResult.Different(delta, 100f - percentMatch, (total - matched).toLong())
    }
  }

  private fun toGrayscale(image: BufferedImage): Array<DoubleArray> {
    val w = image.width
    val h = image.height
    return Array(h) { y ->
      DoubleArray(w) { x ->
        val rgb = Color(image.getRGB(x, y))
        0.299 * rgb.red + 0.587 * rgb.green + 0.114 * rgb.blue
      }
    }
  }

  private fun gaussianBlur(src: Array<DoubleArray>, sigma: Double): Array<DoubleArray> {
    val radius = ceil(3 * sigma).toInt()
    val size = radius * 2 + 1
    val kernel = DoubleArray(size)
    val blurred = Array(src.size) { DoubleArray(src[0].size) }

    val sigma2 = sigma * sigma
    var sum = 0.0
    for (i in -radius..radius) {
      val value = exp(-(i * i) / (2 * sigma2))
      kernel[i + radius] = value
      sum += value
    }
    for (i in kernel.indices) kernel[i] /= sum

    val temp = Array(src.size) { DoubleArray(src[0].size) }
    val h = src.size
    val w = src[0].size

    for (y in 0 until h) {
      for (x in 0 until w) {
        var acc = 0.0
        for (k in -radius..radius) {
          val ix = (x + k).coerceIn(0, w - 1)
          acc += kernel[k + radius] * src[y][ix]
        }
        temp[y][x] = acc
      }
    }

    for (y in 0 until h) {
      for (x in 0 until w) {
        var acc = 0.0
        for (k in -radius..radius) {
          val iy = (y + k).coerceIn(0, h - 1)
          acc += kernel[k + radius] * temp[iy][x]
        }
        blurred[y][x] = acc
      }
    }

    return blurred
  }

  private fun buildGaussianPyramid(
    image: Array<DoubleArray>,
    octaves: Int,
    scalesPerOctave: Int,
    baseSigma: Double = 1.6
  ): List<List<Array<DoubleArray>>> {
    val pyramid = mutableListOf<List<Array<DoubleArray>>>()
    for (octave in 0 until octaves) {
      val octaveScales = mutableListOf<Array<DoubleArray>>()
      val k = 2.0.pow(1.0 / scalesPerOctave)
      val baseImage = if (octave == 0) image else downsample(pyramid[octave - 1][scalesPerOctave])
      for (i in 0..(scalesPerOctave + 2)) {
        val sigma = baseSigma * k.pow(i)
        octaveScales.add(gaussianBlur(baseImage, sigma))
      }
      pyramid.add(octaveScales)
    }
    return pyramid
  }

  private fun buildDoGPyramid(gaussians: List<List<Array<DoubleArray>>>): List<List<Array<DoubleArray>>> {
    return gaussians.map { octave ->
      octave.zipWithNext { a, b -> subtractImages(b, a) }
    }
  }

  private fun subtractImages(a: Array<DoubleArray>, b: Array<DoubleArray>): Array<DoubleArray> {
    val h = a.size
    val w = a[0].size
    return Array(h) { y ->
      DoubleArray(w) { x ->
        a[y][x] - b[y][x]
      }
    }
  }

  private fun downsample(image: Array<DoubleArray>): Array<DoubleArray> {
    val h = image.size / 2
    val w = image[0].size / 2
    return Array(h) { y ->
      DoubleArray(w) { x ->
        image[y * 2][x * 2]
      }
    }
  }

  private fun detectDoGExtrema(dogPyramid: List<List<Array<DoubleArray>>>): List<SiftKeypoint> {
    val keypoints = mutableListOf<SiftKeypoint>()
    for (octave in dogPyramid.indices) {
      val scales = dogPyramid[octave]
      for (s in 1 until scales.size - 1) {
        val curr = scales[s]
        val prev = scales[s - 1]
        val next = scales[s + 1]
        val h = curr.size
        val w = curr[0].size
        for (y in 1 until h - 1) {
          for (x in 1 until w - 1) {
            val center = curr[y][x]
            var isMax = true
            var isMin = true
            loop@ for (dy in -1..1) {
              for (dx in -1..1) {
                for (ds in -1..1) {
                  if (dx == 0 && dy == 0 && ds == 0) continue
                  val v = when (ds) {
                    -1 -> prev[y + dy][x + dx]
                    0 -> curr[y + dy][x + dx]
                    1 -> next[y + dy][x + dx]
                    else -> 0.0
                  }
                  if (center <= v) isMax = false
                  if (center >= v) isMin = false
                  if (!isMax && !isMin) break@loop
                }
              }
            }
            if (isMax || isMin) keypoints.add(SiftKeypoint(x, y, octave, s))
          }
        }
      }
    }
    return keypoints
  }

  private fun matchKeypoints(expected: List<SiftKeypoint>, actual: List<SiftKeypoint>): List<SiftKeypoint> {
    val matches = mutableListOf<SiftKeypoint>()
    val tolerance = 2.0
    for (kp in expected) {
      val match = actual.firstOrNull {
        it.octave == kp.octave &&
          it.scale == kp.scale &&
          hypot((it.x - kp.x).toDouble(), (it.y - kp.y).toDouble()) <= tolerance
      }
      if (match != null) matches.add(kp)
    }
    return matches
  }

  private fun drawKeypoints(g: Graphics2D, points: List<SiftKeypoint>, color: Color) {
    g.color = color
    for (kp in points) {
      val x = kp.x shl kp.octave
      val y = kp.y shl kp.octave
      g.drawOval(x - 2, y - 2, 5, 5)
    }
  }
}

internal class SiftKeypoint(
  val x: Int,
  val y: Int,
  val octave: Int,
  val scale: Int
)
