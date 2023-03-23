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

import app.cash.paparazzi.internal.apng.PngConsts.Header
import app.cash.paparazzi.internal.apng.PngConsts.Header.IDAT
import app.cash.paparazzi.internal.apng.PngConsts.Header.IEND
import app.cash.paparazzi.internal.apng.PngConsts.Header.IHDR
import app.cash.paparazzi.internal.apng.PngConsts.Header.acTL
import app.cash.paparazzi.internal.apng.PngConsts.Header.fcTL
import app.cash.paparazzi.internal.apng.PngConsts.Header.fdAT
import app.cash.paparazzi.internal.apng.PngConsts.PNG_COLOR_TYPE_RGB
import app.cash.paparazzi.internal.apng.PngConsts.PNG_COLOR_TYPE_RGBA
import app.cash.paparazzi.internal.apng.PngConsts.PNG_SIG
import okio.Buffer
import okio.BufferedSource
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.buffer
import okio.internal.commonToUtf8String
import java.awt.image.BufferedImage
import java.awt.image.ColorModel
import java.awt.image.DataBufferInt
import java.awt.image.Raster
import java.io.Closeable
import java.io.File
import java.lang.IllegalStateException
import java.util.zip.CRC32
import java.util.zip.Inflater

/**
 * Provides a simple sub-spec implementation for reading APNG files. Only handles
 * chunks which are implemented by the writer.
 */
internal class APNGReader(file: File) : Closeable {
  private val crcEngine = CRC32()
  private val fileHandle = FileSystem.SYSTEM.openReadOnly(file.path.toPath())
  private val buffer = fileHandle.source().buffer()

  private lateinit var scratchSpace: IntArray

  private var frameInfo: FrameInfo? = null
  private var hasAlpha = false
  private var sequenceNumber = -1

  var frameCount = 1
  var frameNumber = 0
  var width = 0
  var height = 0

  init {
    val header = buffer.readByteArray(PNG_SIG.size.toLong())
    if (!header.contentEquals(PNG_SIG)) {
      throw IllegalStateException("Input is not a valid PNG")
    }

    processMetadata()
  }

  fun getNextFrame(): BufferedImage? {
    if (finished()) return null

    if (frameNumber == 0) {
      processTo(IDAT)
    } else {
      processTo(fdAT)
    }

    return getFrame()
  }

  fun getDelay(): Pair<Int, Int> {
    return frameInfo?.let { it.delayNumerator.toInt() to it.delayDenominator.toInt() } ?: (1 to 0)
  }

  fun reset() {
    frameNumber = 0
    sequenceNumber = -1
    frameInfo = null

    fileHandle.reposition(buffer, PNG_SIG.size.toLong())
    processMetadata()
  }

  private fun processMetadata() {
    processTo(IHDR)

    if (buffer.peekNextChunkId().contentEquals(acTL.bytes)) {
      processTo(acTL)
    }
  }

  fun finished() = buffer.peekNextChunkId().contentEquals(IEND.bytes)

  override fun close() {
    fileHandle.close()
    buffer.close()
  }

  private fun processTo(header: Header) {
    do {
      val (currentHeader, data) = buffer.readChunk()
      when (currentHeader) {
        IHDR -> data.parseIHDR()
        acTL -> data.parseACTL()
        fcTL -> data.parseFCTL()
        IDAT -> data.parseIDAT()
        fdAT -> data.parseFDAT()
        IEND -> throw IllegalStateException("PNG ended while processing to $header")
      }
    } while (!header.bytes.contentEquals(currentHeader.bytes) && !finished())
  }

  private fun BufferedSource.parseIHDR() {
    width = readInt()
    height = readInt()
    scratchSpace = IntArray(width * height)

    skip(1L) // Bit depth
    hasAlpha = when (readByte()) {
      PNG_COLOR_TYPE_RGB -> false
      PNG_COLOR_TYPE_RGBA -> true
      else -> throw IllegalStateException("Unsupported color model")
    }
  }

  private fun BufferedSource.parseACTL() {
    frameCount = readInt()
  }

  private fun BufferedSource.parseFCTL() {
    val nextSequence = readInt()
    frameInfo = FrameInfo(
      width = readInt(),
      height = readInt(),
      offsetX = readInt(),
      offsetY = readInt(),
      delayNumerator = readShort(),
      delayDenominator = readShort()
    )

    if (sequenceNumber + 1 != nextSequence) {
      throw IllegalStateException("Out of order sequence, expected: ${sequenceNumber + 1} actual: $nextSequence")
    }
    sequenceNumber = nextSequence
  }

  private fun BufferedSource.parseIDAT() {
    decompress().decodeToScratchSpace()
  }

  private fun BufferedSource.parseFDAT() {
    val nextSequence = readInt()
    if (sequenceNumber + 1 != nextSequence) {
      throw IllegalStateException("Out of order sequence, expected: ${sequenceNumber + 1} actual: $nextSequence")
    }
    sequenceNumber = nextSequence
    decompress().decodeToScratchSpace()
  }

  private fun BufferedSource.decompress(): Buffer {
    val inflater = Inflater().apply {
      setInput(readByteArray())
    }

    val buffer = ByteArray(BUFFER_SIZE)
    return Buffer().apply {
      do {
        val byteCount = inflater.inflate(buffer)
        write(buffer, 0, byteCount)
      } while (!inflater.finished() && byteCount != 0)
    }
  }

  private fun BufferedSource.decodeToScratchSpace() {
    val frameWidth = frameInfo?.width ?: width
    val frameHeight = frameInfo?.height ?: height
    val frameOffsetX = frameInfo?.offsetX ?: 0
    val frameOffsetY = frameInfo?.offsetY ?: 0

    for (y in 0 until frameHeight) {
      val filterByte = readByte()
      if (filterByte != 0.toByte()) {
        throw IllegalStateException("Only filter None is supported, $filterByte")
      }

      for (x in 0 until frameWidth) {
        val pixelBytes = if (hasAlpha) readByteArray(4) else readByteArray(3)
        val rgba = Buffer().run {
          write(pixelBytes)
          if (!hasAlpha) {
            writeByte(0)
          }

          readInt()
        }

        val argb = rgba.rotateRight(8)
        val position = ((frameOffsetY + y) * width) + frameOffsetX + x
        scratchSpace[position] = argb
      }
    }

    frameNumber++
  }

  private fun BufferedSource.readChunk(): Pair<Header, BufferedSource> {
    val dataLength = readInt()
    val chunkId = readByteArray(4L)
    val data = readByteArray(dataLength.toLong())
    val crc = readInt()

    crcEngine.reset()
    crcEngine.update(chunkId, 0, 4)
    if (dataLength > 0) crcEngine.update(data, 0, dataLength)

    if (crcEngine.value.toInt() != crc) {
      throw IllegalStateException("CRC Mismatch decoding ${chunkId.commonToUtf8String()}, invalid data")
    }

    return Header.valueOf(chunkId.commonToUtf8String()) to Buffer().apply { write(data) }
  }

  private fun BufferedSource.peekNextChunkId() =
    peek().run {
      skip(4L) // Ignored Data Length
      readByteArray(4L)
    }

  private fun getFrame(): BufferedImage {
    val buffer = DataBufferInt(scratchSpace, scratchSpace.size)
    val bandMasks = intArrayOf(0xFF0000, 0xFF00, 0xFF, -0x1000000)
    val raster = Raster.createPackedRaster(buffer, width, height, width, bandMasks, null)
    val cm = ColorModel.getRGBdefault()

    return BufferedImage(cm, raster, cm.isAlphaPremultiplied, null)
  }

  private data class FrameInfo(
    val width: Int,
    val height: Int,
    val offsetX: Int,
    val offsetY: Int,
    val delayNumerator: Short,
    val delayDenominator: Short
  )

  companion object {
    private const val BUFFER_SIZE = 1_024_000
  }
}
