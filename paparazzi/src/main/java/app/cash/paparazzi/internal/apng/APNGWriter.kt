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
import app.cash.paparazzi.internal.apng.PngConsts.Header
import app.cash.paparazzi.internal.apng.PngConsts.Header.IDAT
import app.cash.paparazzi.internal.apng.PngConsts.Header.IEND
import app.cash.paparazzi.internal.apng.PngConsts.Header.IHDR
import app.cash.paparazzi.internal.apng.PngConsts.Header.acTL
import app.cash.paparazzi.internal.apng.PngConsts.Header.fcTL
import app.cash.paparazzi.internal.apng.PngConsts.Header.fdAT
import app.cash.paparazzi.internal.apng.PngConsts.PNG_BITS_PER_PIXEL
import app.cash.paparazzi.internal.apng.PngConsts.PNG_COLOR_TYPE_RGB
import app.cash.paparazzi.internal.apng.PngConsts.PNG_COLOR_TYPE_RGBA
import okio.Buffer
import okio.BufferedSink
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.buffer
import java.awt.Rectangle
import java.awt.image.BufferedImage
import java.io.Closeable
import java.io.File
import java.lang.IllegalStateException
import java.lang.Integer.max
import java.util.zip.CRC32
import java.util.zip.Deflater

/**
 * Provides a basic implementation for encoding APNG's.
 *
 * Refs: https://www.w3.org/TR/png/#4Concepts.Encoding, https://wiki.mozilla.org/APNG_Specification
 */
internal class APNGWriter(
  private val file: File,
  private val totalFrameCount: Int,
  fps: Int
) : Closeable {

  private val fpsNum = 1
  private val fpsDen = fps
  private val tempFile = File(FileSystem.SYSTEM_TEMPORARY_DIRECTORY.toFile(), "temp-${file.name}").apply { deleteOnExit() }
  private val fileHandle = FileSystem.SYSTEM.openReadWrite(tempFile.path.toPath())
  private val sink = fileHandle.sink().buffer()
  private val crcEngine = CRC32()

  private lateinit var firstFrame: BufferedImage
  private lateinit var previousFrame: BufferedImage

  private var maxWidth = 0
  private var maxHeight = 0
  private var ihdrWidth = 0
  private var ihdrHeight = 0
  private var iDATChunkEndOffset = 0L
  private var frameCount = 0
  private var sequenceNumber = 0

  fun writeImage(image: BufferedImage) {
    if (frameCount == 0) {
      sink.writePngSignature()
      sink.writeIHDR(image)

      if (totalFrameCount > 1) {
        sink.writeACTL(totalFrameCount, 0)
        sink.writeFCTL(sequenceNumber++, Rectangle(image.width, image.height))
      }

      sink.writeIDAT(image)
      iDATChunkEndOffset = fileHandle.position(sink)
      firstFrame = image
    } else {
      val subRect = ImageUtils.smallestDiffRect(previousFrame, image) ?: MIN_FRAME_RECT
      sink.writeFCTL(sequenceNumber++, subRect)
      sink.writeFDAT(sequenceNumber++, subRect, image)
    }

    maxWidth = max(maxWidth, image.width)
    maxHeight = max(maxHeight, image.height)
    previousFrame = image
    frameCount++
  }

  override fun close() {
    if (frameCount != totalFrameCount) {
      throw IllegalStateException("Expected $totalFrameCount total frames, actual count $frameCount")
    }

    sink.writeIEND()
    fileHandle.resize(fileHandle.position(sink))
    sink.close()

    if (ihdrWidth == maxWidth && ihdrHeight == maxHeight) {
      tempFile.copyTo(file, true)
    } else {
      correctFirstFrame()
    }

    fileHandle.close()
  }

  /**
   * The IHDR chunk defines the image size from which all animation frames must be
   * encapsulated. If over the course of the animation we need to render a larger
   * image than the first frame we need to resize the first frame such that the
   * entire animation will fit within. Subsequent animation frames are still correct
   * and don't need to be re-computed.
   */
  private fun correctFirstFrame() {
    val resizedFirstFrame = firstFrame.resize(maxWidth, maxHeight)
    val unstableSource = fileHandle.source(iDATChunkEndOffset)
    val stableHandle = FileSystem.SYSTEM.openReadWrite(file.path.toPath())
    val stableSink = stableHandle.sink().buffer()
    stableSink.writePngSignature()
    stableSink.writeIHDR(resizedFirstFrame)
    stableSink.writeACTL(frameCount, 0)
    stableSink.writeFCTL(0, Rectangle(maxWidth, maxHeight))
    stableSink.writeIDAT(resizedFirstFrame)
    stableSink.write(unstableSource, fileHandle.size() - iDATChunkEndOffset)
    stableHandle.resize(stableHandle.position(stableSink))
    stableSink.close()
    stableHandle.close()
    unstableSource.close()
  }

  private fun BufferedSink.writePngSignature() {
    write(PngConsts.PNG_SIG)
  }

  private fun BufferedSink.writeIHDR(bufferedImage: BufferedImage) {
    val data = Buffer().apply {
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

    writeChunk(IHDR, data)
  }

  private fun BufferedSink.writeACTL(frameCount: Int, loopCount: Int) {
    val data = Buffer().apply {
      writeInt(frameCount)
      writeInt(loopCount)
    }

    writeChunk(acTL, data)
  }

  private fun BufferedSink.writeFCTL(
    sequenceNumber: Int,
    rectangle: Rectangle
  ) {
    val data = Buffer().apply {
      writeInt(sequenceNumber)
      writeInt(rectangle.width)
      writeInt(rectangle.height)
      writeInt(rectangle.x)
      writeInt(rectangle.y)
      writeShort(fpsNum)
      writeShort(fpsDen)
      writeByte(0) // Dispose Operation
      writeByte(0) // Blend Operation
    }

    writeChunk(fcTL, data)
  }

  private fun BufferedSink.writeIDAT(
    image: BufferedImage
  ) {
    val imageBytes = image.encodeBytes(Rectangle(image.width, image.height))
    val compressedData = imageBytes.compress(Deflater.BEST_COMPRESSION)

    writeChunk(IDAT, compressedData)
  }

  private fun BufferedSink.writeFDAT(
    sequenceNumber: Int,
    subRect: Rectangle,
    image: BufferedImage
  ) {
    val imageBytes = image.encodeBytes(subRect)
    val compressedBytes = imageBytes.compress(Deflater.BEST_COMPRESSION)

    val data = Buffer().apply {
      writeInt(sequenceNumber)
      write(compressedBytes, compressedBytes.size)
    }

    writeChunk(fdAT, data)
  }

  private fun BufferedSink.writeIEND() {
    writeChunk(IEND, Buffer())
  }

  private fun BufferedImage.encodeBytes(subRect: Rectangle): Buffer {
    return Buffer().apply {
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

  private fun Buffer.compress(level: Int): Buffer {
    val deflater = Deflater(level).apply {
      setInput(readByteArray())
      finish()
    }

    val buffer = ByteArray(BUFFER_SIZE)
    return Buffer().apply {
      while (!deflater.finished()) {
        val compressedDataLength = deflater.deflate(buffer)
        write(buffer, 0, compressedDataLength)
      }
    }
  }

  private fun BufferedSink.writeChunk(header: Header, data: Buffer) {
    crcEngine.reset()
    crcEngine.update(header.bytes, 0, 4)
    val dataBytes = data.readByteArray()
    if (dataBytes.isNotEmpty()) crcEngine.update(dataBytes, 0, dataBytes.size)
    val crc = crcEngine.value.toInt()

    writeInt(dataBytes.size)
    write(header.bytes)
    write(dataBytes)
    writeInt(crc)
  }

  companion object {
    private const val BUFFER_SIZE = 1_024_000
    private val MIN_FRAME_RECT = Rectangle(0, 0, 1, 1)
  }
}
