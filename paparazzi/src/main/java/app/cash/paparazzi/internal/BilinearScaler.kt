/*
 * Copyright (C) 2026 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package app.cash.paparazzi.internal

import java.awt.image.BufferedImage
import java.awt.image.BufferedImage.TYPE_INT_ARGB_PRE
import java.awt.image.DataBufferInt
import java.awt.image.SinglePixelPackedSampleModel
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor
import java.util.concurrent.Future
import java.util.concurrent.FutureTask
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Bilinear resampling of premultiplied ARGB pixels that produces the same pixels as drawing a
 * `TYPE_INT_ARGB_PRE` image into a new, transparent `TYPE_INT_ARGB_PRE` image with
 * `Graphics2D.drawImage(src, 0, 0, dw, dh, 0, 0, sw, sh, null)`, bilinear interpolation and the
 * default `SrcOver` composite. Layoutlib renders into `TYPE_INT_ARGB_PRE` images.
 *
 * Sampling model:
 *  - Each destination pixel center is mapped into the source using 32.32 fixed-point
 *    coordinates, starting at `0.5 * sw / dw` and stepping by `sw / dw` per pixel (same for y).
 *  - Destination pixels whose mapped center falls outside the source stay transparent.
 *  - The four nearest source pixels (clamped at the edges) are blended per 8-bit channel using
 *    the top 8 bits `w` of the fractional offsets as weights `(256 - w, w)`, horizontally then
 *    vertically, and rounded to nearest.
 *  - A result with zero alpha leaves the transparent destination pixel untouched.
 *
 * Large passes are split into bands of rows computed on up to [MAX_THREADS] threads: the caller
 * plus any free helper (see [helpers]). Rows are independent, so the split does not affect the
 * output.
 *
 * [ImageUtilsScaleTest] verifies equivalence against Java2D on the running JDK.
 */
internal object BilinearScaler {
  private const val ONE = 1L shl 32
  private const val ONE_HALF = 1L shl 31
  private const val RB = 0x00ff00ff

  /** Upper bound on threads (including the caller) used for one pass. */
  private const val MAX_THREADS = 4

  /** Smallest share of a pass worth handing to another thread. */
  private const val MIN_PIXELS_PER_BAND = 1L shl 16

  private val helperCount = AtomicInteger()

  /**
   * Helpers for large passes, shared by all callers of this loaded class (one set per class
   * loader) and separate from [java.util.concurrent.ForkJoinPool.commonPool]. See
   * [newHelperExecutor].
   */
  internal val helpers = newHelperExecutor()

  /**
   * An executor with no queue: a band is only handed off when a helper is free, otherwise the
   * caller computes it, so at most `MAX_THREADS - 1` bands are outstanding across its callers.
   * Idle helpers exit after one second.
   */
  internal fun newHelperExecutor(): ThreadPoolExecutor =
    ThreadPoolExecutor(0, MAX_THREADS - 1, 1, TimeUnit.SECONDS, SynchronousQueue(), ::newHelperThread)

  /** Helpers carry no state from the test thread that happened to start them. */
  internal fun newHelperThread(task: Runnable): Thread =
    Thread(null, task, "paparazzi-image-scaler-${helperCount.incrementAndGet()}", 0, false).apply {
      isDaemon = true
      contextClassLoader = null
    }

  /** Rounds both 32-bit lanes to nearest before dropping their 16 fractional bits. */
  private const val ROUNDING = (1L shl 15) or (1L shl 47)

  /** Whether [image] is a plain `TYPE_INT_ARGB_PRE` image whose pixels can be read directly. */
  fun supports(image: BufferedImage): Boolean {
    if (image.javaClass != BufferedImage::class.java) return false
    if (image.type != TYPE_INT_ARGB_PRE || !image.isAlphaPremultiplied) return false
    val raster = image.raster
    val sampleModel = raster.sampleModel as? SinglePixelPackedSampleModel ?: return false
    val dataBuffer = raster.dataBuffer as? DataBufferInt ?: return false
    return raster.parent == null &&
      raster.minX == 0 &&
      raster.minY == 0 &&
      raster.sampleModelTranslateX == 0 &&
      raster.sampleModelTranslateY == 0 &&
      sampleModel.scanlineStride == image.width &&
      dataBuffer.numBanks == 1 &&
      dataBuffer.offset == 0 &&
      dataBuffer.size >= image.width * image.height
  }

  /** Returns the pixels of an image accepted by [supports], without copying. */
  fun pixels(image: BufferedImage): IntArray = (image.raster.dataBuffer as DataBufferInt).data

  /**
   * Resamples [src] (`srcWidth` x `srcHeight`, premultiplied ARGB) to `dstWidth` x `dstHeight`.
   *
   * [dst] must be zero-filled (fully transparent) and hold at least `dstWidth * dstHeight` pixels.
   */
  fun scale(
    src: IntArray,
    srcWidth: Int,
    srcHeight: Int,
    dstWidth: Int,
    dstHeight: Int,
    dst: IntArray = IntArray(dstWidth * dstHeight),
    bands: Int = bandCount(dstWidth.toLong() * dstHeight),
    executor: Executor = helpers
  ): IntArray {
    // Source distance per destination pixel, computed as the inverse of the dst/src scale.
    val xStep = 1.0 / (dstWidth.toDouble() / srcWidth)
    val yStep = 1.0 / (dstHeight.toDouble() / srcHeight)
    val xStepFixed = (xStep * ONE).toLong()
    val yStepFixed = (yStep * ONE).toLong()
    val xOrigin = ((0.5 * xStep) * ONE).toLong()
    val yOrigin = ((0.5 * yStep) * ONE).toLong()

    // Columns whose centers land inside the source; the mapping is monotonic.
    var xStart = 0
    while (xStart < dstWidth && !inside(xOrigin + xStart * xStepFixed, srcWidth)) xStart++
    var xEnd = dstWidth
    while (xEnd > xStart && !inside(xOrigin + (xEnd - 1) * xStepFixed, srcWidth)) xEnd--

    val left = IntArray(dstWidth)
    val right = IntArray(dstWidth)
    val xWeight = IntArray(dstWidth)
    for (x in xStart until xEnd) {
      val position = xOrigin + x * xStepFixed - ONE_HALF
      left[x] = clampedFloor(position)
      right[x] = clampedNext(position, srcWidth)
      xWeight[x] = weight(position)
    }

    val pass = Pass(src, srcWidth, srcHeight, dstWidth, dst, yOrigin, yStepFixed, xStart, xEnd, left, right, xWeight)
    if (bands <= 1) {
      pass.rows(0, dstHeight)
    } else {
      // Rows are independent and each is computed exactly as it would be serially, so the output
      // does not depend on how they are split or where each band runs.
      val bounds = IntArray(bands + 1) { (dstHeight.toLong() * it / bands).toInt() }
      val handedOff = ArrayList<FutureTask<Unit>>(bands - 1)
      var failure: Throwable? = null
      try {
        for (band in 1 until bands) {
          val task = FutureTask { pass.rows(bounds[band], bounds[band + 1]) }
          try {
            executor.execute(task)
          } catch (e: RejectedExecutionException) {
            task.run() // No helper free: compute it here.
          }
          handedOff += task
        }
        pass.rows(bounds[0], bounds[1])
      } catch (t: Throwable) {
        failure = t
      }
      // Never return while another thread may still be reading src or writing dst.
      failure = drain(handedOff, failure)
      if (failure != null) throw failure
    }
    return dst
  }

  /**
   * Waits for every task, even if interrupted or if some fail, and returns [failure] or else the
   * first task failure, with any other distinct failures suppressed. Interrupt status is kept.
   */
  internal fun drain(tasks: List<Future<*>>, failure: Throwable?): Throwable? {
    var primary = failure
    var interrupted = false
    for (task in tasks) {
      while (true) {
        try {
          task.get()
          break
        } catch (e: InterruptedException) {
          interrupted = true
        } catch (e: ExecutionException) {
          val cause = e.cause ?: e
          when {
            primary == null -> primary = cause
            // The same exception object can be thrown more than once; never self-suppress.
            cause !== primary && primary.suppressed.none { it === cause } -> primary.addSuppressed(cause)
          }
          break
        }
      }
    }
    if (interrupted) Thread.currentThread().interrupt()
    return primary
  }

  /** One resampling pass; [rows] may be called concurrently for disjoint row ranges. */
  private class Pass(
    private val src: IntArray,
    private val srcWidth: Int,
    private val srcHeight: Int,
    private val dstWidth: Int,
    private val dst: IntArray,
    private val yOrigin: Long,
    private val yStepFixed: Long,
    private val xStart: Int,
    private val xEnd: Int,
    private val left: IntArray,
    private val right: IntArray,
    private val xWeight: IntArray
  ) {
    fun rows(from: Int, until: Int) {
      for (y in from until until) {
        val center = yOrigin + y * yStepFixed
        if (!inside(center, srcHeight)) continue
        val position = center - ONE_HALF
        val top = clampedFloor(position) * srcWidth
        val bottom = clampedNext(position, srcHeight) * srcWidth
        val yW1 = weight(position)
        val yW0 = 256 - yW1
        val out = y * dstWidth
        for (x in xStart until xEnd) {
          val p00 = src[top + left[x]]
          val p01 = src[top + right[x]]
          val p10 = src[bottom + left[x]]
          val p11 = src[bottom + right[x]]
          val xW1 = xWeight[x]
          val xW0 = 256 - xW1
          // Blend two channels at once: each 16-bit lane holds c0 * (256 - w) + c1 * w <= 65280.
          val upperRb = (p00 and RB) * xW0 + (p01 and RB) * xW1
          val lowerRb = (p10 and RB) * xW0 + (p11 and RB) * xW1
          val upperAg = (p00 ushr 8 and RB) * xW0 + (p01 ushr 8 and RB) * xW1
          val lowerAg = (p10 ushr 8 and RB) * xW0 + (p11 ushr 8 and RB) * xW1
          // The vertical blend needs 24 bits per channel, so widen to 32-bit lanes of a Long.
          val ag = widen(upperAg) * yW0 + widen(lowerAg) * yW1 + ROUNDING
          val alpha = (ag ushr 48).toInt() and 0xff
          // A fully transparent result leaves the transparent destination pixel untouched.
          if (alpha == 0) continue
          val rb = widen(upperRb) * yW0 + widen(lowerRb) * yW1 + ROUNDING
          dst[out + x] = (alpha shl 24) or
            ((rb ushr 48).toInt() and 0xff shl 16) or
            ((ag ushr 16).toInt() and 0xff shl 8) or
            ((rb ushr 16).toInt() and 0xff)
        }
      }
    }
  }

  /**
   * Number of bands to split a pass of [pixels] destination pixels into: one per
   * [MIN_PIXELS_PER_BAND], capped by [MAX_THREADS] and the processors available to the JVM.
   */
  internal fun bandCount(pixels: Long): Int {
    val processors = Runtime.getRuntime().availableProcessors()
    return minOf(pixels / MIN_PIXELS_PER_BAND, MAX_THREADS.toLong(), processors.toLong()).toInt()
  }

  /** Moves the two 16-bit lanes of [lanes] into the two 32-bit halves of a Long. */
  @Suppress("NOTHING_TO_INLINE")
  private inline fun widen(lanes: Int): Long = (lanes and 0xffff).toLong() or ((lanes ushr 16).toLong() shl 32)

  /** Top 8 bits of the fractional part of a 32.32 fixed-point position. */
  private fun weight(position: Long): Int = position.toInt() ushr 24

  /** Integer part of [position], clamped to 0 below the first pixel. */
  private fun clampedFloor(position: Long): Int = maxOf((position shr 32).toInt(), 0)

  /** The neighbor after [clampedFloor], or the same pixel at either edge. */
  private fun clampedNext(position: Long, size: Int): Int {
    val floor = (position shr 32).toInt()
    return if (floor < 0 || floor + 1 >= size) maxOf(floor, 0) else floor + 1
  }

  /** Whether a 32.32 fixed-point pixel center lies within `[0, size)`. */
  private fun inside(position: Long, size: Int): Boolean = Integer.compareUnsigned((position shr 32).toInt(), size) < 0
}
