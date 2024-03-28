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

import app.cash.paparazzi.internal.ImageUtils
import app.cash.paparazzi.internal.ImageUtils.resize
import app.cash.paparazzi.internal.apng.PngConstants.Header
import app.cash.paparazzi.internal.apng.PngConstants.Header.ACTL
import app.cash.paparazzi.internal.apng.PngConstants.Header.FCTL
import app.cash.paparazzi.internal.apng.PngConstants.Header.FDAT
import app.cash.paparazzi.internal.apng.PngConstants.Header.IDAT
import app.cash.paparazzi.internal.apng.PngConstants.Header.IEND
import app.cash.paparazzi.internal.apng.PngConstants.Header.IHDR
import app.cash.paparazzi.internal.apng.PngConstants.PNG_BITS_PER_PIXEL
import app.cash.paparazzi.internal.apng.PngConstants.PNG_COLOR_TYPE_RGB
import app.cash.paparazzi.internal.apng.PngConstants.PNG_COLOR_TYPE_RGBA
import okio.Buffer
import okio.BufferedSink
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.buffer
import java.awt.Rectangle
import java.awt.image.BufferedImage
import java.io.Closeable
import java.lang.Integer.max
import java.util.zip.CRC32
import java.util.zip.Deflater

/**
 * Provides a basic implementation for encoding APNG's.
 *
 * Refs: https://www.w3.org/TR/png/#4Concepts.Encoding, https://wiki.mozilla.org/APNG_Specification
 */
internal class ApngWriter(
  private val path: Path,
  private val fps: Int,
  private val fileSystem: FileSystem = FileSystem.SYSTEM
) : Closeable {
  private val afterFirstFrameDataPath: Path = "$path.ApngWriter.tmp".toPath()
  private val crcEngine = CRC32()

  private lateinit var firstFrame: BufferedImage
  private lateinit var previousFrame: BufferedImage
  private lateinit var afterFirstFrameDataSink: BufferedSink

  private var maxWidth = 0
  private var maxHeight = 0
  private var ihdrWidth = 0
  private var ihdrHeight = 0
  private var sequenceNumber = 1
  internal var frameCount = 0

  fun writeImage(image: BufferedImage) {
    if (frameCount == 0) {
      firstFrame = image.copy() // Defensive copy
    } else {
      // If we have multiple frames, write them to a temp file. We must always write the first
      // frame first, but we don't know its final dimensions just yet.
      if (frameCount == 1) {
        afterFirstFrameDataSink = fileSystem.sink(afterFirstFrameDataPath).buffer()
      }

      val subRect = ImageUtils.smallestDiffRect(previousFrame, image) ?: MIN_FRAME_RECT
      afterFirstFrameDataSink.writeFCTL(sequenceNumber++, subRect)
      afterFirstFrameDataSink.writeFDAT(sequenceNumber++, subRect, image)
    }

    maxWidth = max(maxWidth, image.width)
    maxHeight = max(maxHeight, image.height)
    previousFrame = image.copy() // Defensive copy
    frameCount++
  }

  override fun close() {
    // Only when we close do we actually create the .png file!
    fileSystem.write(path) {
      // First frame.
      writePngSignature()
      val firstFrameResized = firstFrame.resize(maxWidth, maxHeight)
      writeIHDR(firstFrameResized)
      if (frameCount > 1) {
        writeACTL(frameCount, 0)
        writeFCTL(0, Rectangle(maxWidth, maxHeight))
      }
      writeIDAT(firstFrameResized)

      // Copy over the subsequent frames, if we have any.
      if (frameCount > 1) {
        afterFirstFrameDataSink.close()
        fileSystem.source(afterFirstFrameDataPath).use { tempPathSource ->
          writeAll(tempPathSource)
        }
        fileSystem.delete(afterFirstFrameDataPath)
      }

      writeIEND()
    }
  }

  private fun BufferedSink.writePngSignature() {
    write(PngConstants.PNG_SIG)
  }

  private fun BufferedSink.writeIHDR(bufferedImage: BufferedImage) {
    writeChunk(IHDR) {
      writeInt(bufferedImage.width)
      writeInt(bufferedImage.height)
      ihdrWidth = bufferedImage.width
      ihdrHeight = bufferedImage.height

      // https://www.w3.org/TR/png/#3colourType
      val colorType = when (bufferedImage.type) {
        BufferedImage.TYPE_INT_RGB -> PNG_COLOR_TYPE_RGB
        BufferedImage.TYPE_INT_ARGB -> PNG_COLOR_TYPE_RGBA
        else -> throw IllegalStateException("Unsupported image type")
      }

      writeByte(PNG_BITS_PER_PIXEL.toInt())
      writeByte(colorType.toInt())
      writeByte(0) // Compression
      writeByte(0) // Filter
      writeByte(0) // Interlace
    }
  }

  private fun BufferedSink.writeACTL(frameCount: Int, loopCount: Int) {
    writeChunk(ACTL) {
      writeInt(frameCount)
      writeInt(loopCount)
    }
  }

  private fun BufferedSink.writeFCTL(
    sequenceNumber: Int,
    rectangle: Rectangle
  ) {
    writeChunk(FCTL) {
      writeInt(sequenceNumber)
      writeInt(rectangle.width)
      writeInt(rectangle.height)
      writeInt(rectangle.x)
      writeInt(rectangle.y)
      writeShort(1) // Delay Numerator
      writeShort(fps) // Delay Denominator
      writeByte(0) // Dispose Operation
      writeByte(0) // Blend Operation
    }
  }

  private fun BufferedSink.writeIDAT(
    image: BufferedImage
  ) {
    writeChunk(IDAT) {
      val encodedImageBytes = Buffer()
      image.encodeBytes(encodedImageBytes, Rectangle(image.width, image.height))
      encodedImageBytes.compress(this, Deflater.BEST_COMPRESSION)
    }
  }

  private fun BufferedSink.writeFDAT(
    sequenceNumber: Int,
    subRect: Rectangle,
    image: BufferedImage
  ) {
    writeChunk(FDAT) {
      writeInt(sequenceNumber)
      val encodedImageBytes = Buffer()
      image.encodeBytes(encodedImageBytes, subRect)
      encodedImageBytes.compress(this, Deflater.BEST_COMPRESSION)
    }
  }

  private fun BufferedSink.writeIEND() {
    writeChunk(IEND) { }
  }

  private fun BufferedImage.encodeBytes(sink: BufferedSink, subRect: Rectangle) {
    sink.apply {
      for (y in subRect.y until subRect.y + subRect.height) {
        writeByte(0) // Row Filter: None
        for (x in subRect.x until subRect.x + subRect.width) {
          val argb = if (x < width && y < height) getRGB(x, y) else 0

          writeByte(argb ushr 16)
          writeByte(argb ushr 8)
          writeByte(argb)
          if (raster.numBands == 4) {
            writeByte(argb ushr 24)
          }
        }
      }
    }
  }

  private fun Buffer.compress(sink: BufferedSink, level: Int) {
    val deflater = Deflater(level).apply {
      setInput(readByteArray())
      finish()
    }

    val buffer = ByteArray(1000 * 1024)
    while (!deflater.finished()) {
      val compressedDataLength = deflater.deflate(buffer)
      sink.write(buffer, 0, compressedDataLength)
    }
  }

  private fun BufferedSink.writeChunk(header: Header, data: Buffer.() -> Unit) {
    val buffer = Buffer().apply(data)
    crcEngine.reset()
    crcEngine.update(header.bytes, 0, 4)
    val dataBytes = buffer.readByteArray()
    if (dataBytes.isNotEmpty()) crcEngine.update(dataBytes, 0, dataBytes.size)
    val crc = crcEngine.value.toInt()

    writeInt(dataBytes.size)
    write(header.bytes)
    write(dataBytes)
    writeInt(crc)
  }

  private fun BufferedImage.copy() = resize(width, height)

  companion object {
    private val MIN_FRAME_RECT = Rectangle(0, 0, 1, 1)
  }
}
