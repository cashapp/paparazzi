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

import app.cash.paparazzi.internal.apng.PngConstants.Header
import app.cash.paparazzi.internal.apng.PngConstants.Header.ACTL
import app.cash.paparazzi.internal.apng.PngConstants.Header.FCTL
import app.cash.paparazzi.internal.apng.PngConstants.Header.FDAT
import app.cash.paparazzi.internal.apng.PngConstants.Header.IDAT
import app.cash.paparazzi.internal.apng.PngConstants.Header.IEND
import app.cash.paparazzi.internal.apng.PngConstants.Header.IHDR
import app.cash.paparazzi.internal.apng.PngConstants.PNG_COLOR_TYPE_RGB
import app.cash.paparazzi.internal.apng.PngConstants.PNG_COLOR_TYPE_RGBA
import app.cash.paparazzi.internal.apng.PngConstants.PNG_SIG
import okio.Buffer
import okio.BufferedSource
import okio.FileHandle
import okio.buffer
import java.awt.image.BufferedImage
import java.awt.image.ColorModel
import java.awt.image.DataBufferInt
import java.awt.image.Raster
import java.io.Closeable
import java.io.IOException
import java.util.zip.CRC32
import java.util.zip.Inflater

/**
 * Provides a simple sub-spec implementation for reading APNG files. Only handles
 * chunks which are implemented by the writer.
 */
internal class ApngReader(
  private val fileHandle: FileHandle
) : Closeable {
  private val crcEngine = CRC32()
  private val source = fileHandle.source().buffer()

  private lateinit var scratchSpace: IntArray

  private var frameInfo: FrameInfo? = null
  private var hasAlpha = false
  private var sequenceNumber = -1

  var frameCount = 1
  var frameNumber = 0
  var width = 0
  var height = 0

  init {
    if (!source.rangeEquals(0L, PNG_SIG)) {
      throw IOException(
        "Missing valid PNG header " +
          "expected: [${PNG_SIG.hex()}] " +
          "actual: [${source.readByteString(PNG_SIG.size.toLong()).hex()}]"
      )
    }

    source.skip(PNG_SIG.size.toLong())
    processMetadata()
  }

  fun readNextFrame(): BufferedImage? {
    if (isFinished()) return null

    if (frameNumber == 0) {
      processTo(IDAT)
    } else {
      processTo(FDAT)
    }

    return getFrame()
  }

  fun getFps(): Int {
    return frameInfo?.delayDenominator?.toInt() ?: 0
  }

  fun reset() {
    frameNumber = 0
    sequenceNumber = -1
    frameInfo = null

    fileHandle.reposition(source, PNG_SIG.size.toLong())
    processMetadata()
  }

  private fun processMetadata() {
    processTo(IHDR)

    if (source.peekNextChunkId().contentEquals(ACTL.bytes)) {
      processTo(ACTL)
    }
  }

  fun isFinished() = source.peekNextChunkId().contentEquals(IEND.bytes)

  override fun close() {
    fileHandle.close()
    source.close()
  }

  private fun processTo(header: Header) {
    do {
      val (currentHeader, data) = source.readChunk()
      when (currentHeader) {
        IHDR -> data.readIHDR()
        ACTL -> data.readACTL()
        FCTL -> data.readFCTL()
        IDAT -> data.readIDAT()
        FDAT -> data.readFDAT()
        IEND -> throw IOException("PNG ended while processing to $header")
      }
    } while (!header.bytes.contentEquals(currentHeader.bytes) && !isFinished())
  }

  private fun BufferedSource.readIHDR() {
    width = readInt()
    height = readInt()
    scratchSpace = IntArray(width * height)

    skip(1L) // Bit depth
    hasAlpha = when (readByte()) {
      PNG_COLOR_TYPE_RGB -> false
      PNG_COLOR_TYPE_RGBA -> true
      else -> throw IOException("Unsupported color model")
    }
  }

  private fun BufferedSource.readACTL() {
    frameCount = readInt()
  }

  private fun BufferedSource.readFCTL() {
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
      throw IOException("Out of order sequence, expected: ${sequenceNumber + 1} actual: $nextSequence")
    }
    sequenceNumber = nextSequence
  }

  private fun BufferedSource.readIDAT() {
    decompress().decodeToScratchSpace()
  }

  private fun BufferedSource.readFDAT() {
    val nextSequence = readInt()
    if (sequenceNumber + 1 != nextSequence) {
      throw IOException("Out of order sequence, expected: ${sequenceNumber + 1} actual: $nextSequence")
    }
    sequenceNumber = nextSequence
    decompress().decodeToScratchSpace()
  }

  private fun BufferedSource.decompress(): Buffer {
    val inflater = Inflater().apply {
      setInput(readByteArray())
    }

    val buffer = ByteArray(1000 * 1024) // Arbitrary buffer size for Inflater
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
        throw IOException("Only filter None is supported, $filterByte")
      }

      for (x in 0 until frameWidth) {
        val rgba = if (hasAlpha) {
          readInt()
        } else {
          (readByte().toInt() shl 24) or
            (readByte().toInt() shl 16) or
            (readByte().toInt() shl 8)
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
      throw IOException("CRC Mismatch decoding ${chunkId.decodeToString()}, invalid data")
    }

    return Header.valueOf(chunkId.decodeToString().uppercase()) to Buffer().apply { write(data) }
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
}
