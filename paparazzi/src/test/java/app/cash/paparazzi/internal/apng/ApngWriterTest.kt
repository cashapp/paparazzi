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

import app.cash.paparazzi.accessibility.RenderSettings.toColorInt
import app.cash.paparazzi.internal.apng.PngConstants.Header
import app.cash.paparazzi.internal.apng.PngConstants.Header.ACTL
import app.cash.paparazzi.internal.apng.PngConstants.Header.FCTL
import app.cash.paparazzi.internal.apng.PngConstants.Header.FDAT
import app.cash.paparazzi.internal.apng.PngConstants.Header.IDAT
import app.cash.paparazzi.internal.apng.PngConstants.Header.IHDR
import app.cash.paparazzi.internal.apng.PngConstants.PNG_BITS_PER_PIXEL
import app.cash.paparazzi.internal.apng.PngConstants.PNG_COLOR_TYPE_RGBA
import app.cash.paparazzi.internal.apng.PngConstants.PNG_SIG
import app.cash.paparazzi.internal.apng.TestPngUtils.BACKGROUND_COLOR
import app.cash.paparazzi.internal.apng.TestPngUtils.createImage
import com.google.common.truth.Truth.assertThat
import okio.Buffer
import okio.BufferedSource
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.buffer
import okio.internal.commonToUtf8String
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.awt.Point
import java.util.zip.CRC32
import java.util.zip.Inflater

class ApngWriterTest {

  @get:Rule
  val tempFolderRule = TemporaryFolder()

  @Test
  fun writesAnimationMetadata() {
    val testPath = tempFolderRule.newFile("writesAnimationMetadata.png").path.toPath()
    ApngWriter(testPath, 1).use { writer ->
      writer.writeImage(createImage(squareOffset = Point(5, 5)))
      writer.writeImage(createImage(squareOffset = Point(25, 25)))
      writer.writeImage(createImage(squareOffset = Point(45, 45)))
    }

    FileSystem.SYSTEM.openReadOnly(testPath).use { testHandle ->
      testHandle.source(0L).buffer().use { buffer ->
        buffer.skip(PNG_SIG.size.toLong()) // Header
        val (ihdr, ihdrData) = buffer.assertNextChunk()
        assertThat(ihdr).isEqualTo(IHDR)
        assertThat(ihdrData.readInt()).isEqualTo(DEFAULT_SIZE)
        assertThat(ihdrData.readInt()).isEqualTo(DEFAULT_SIZE)
        assertThat(ihdrData.readByte()).isEqualTo(PNG_BITS_PER_PIXEL)
        assertThat(ihdrData.readByte()).isEqualTo(PNG_COLOR_TYPE_RGBA)
        assertThat(ihdrData.readByte()).isEqualTo(0)
        assertThat(ihdrData.readByte()).isEqualTo(0)
        assertThat(ihdrData.readByte()).isEqualTo(0)
        assertThat(ihdrData.exhausted()).isTrue()

        val (ACTL, ACTLData) = buffer.assertNextChunk()
        assertThat(ACTL).isEqualTo(ACTL)
        assertThat(ACTLData.readInt()).isEqualTo(3) // 3 Frames total
        assertThat(ACTLData.readInt()).isEqualTo(0) // Loops forever
        assertThat(ACTLData.exhausted()).isTrue()
      }
    }
  }

  @Test
  fun writesSingleImageWithNoAnimationMetadata() {
    val testPath = tempFolderRule.newFile("writesSingleImageWithNoAnimationMetadata.png").path.toPath()
    ApngWriter(testPath, 1).use { writer ->
      writer.writeImage(createImage(squareOffset = Point(5, 5)))
    }

    FileSystem.SYSTEM.openReadOnly(testPath).use { testHandle ->
      testHandle.source(0L).buffer().use { buffer ->
        buffer.skip(PNG_SIG.size.toLong()) // Header
        assertThat(buffer.assertNextChunk().first).isEqualTo(IHDR)

        val (idat, idatData) = buffer.assertNextChunk()
        assertThat(idat).isEqualTo(IDAT)
        val imageData = idatData.decompress()
        assertThat(imageData.size).isEqualTo((DEFAULT_SIZE * DEFAULT_SIZE * 4L) + DEFAULT_SIZE)
      }
    }
  }

  @Test
  fun writesAnimationChunksSequentially() {
    val testPath = tempFolderRule.newFile("writesAnimationChunksSequentially.png").path.toPath()
    ApngWriter(testPath, 1).use { writer ->
      writer.writeImage(createImage(squareOffset = Point(5, 5)))
      writer.writeImage(createImage(squareOffset = Point(15, 15)))
      writer.writeImage(createImage(squareOffset = Point(25, 25)))
    }

    FileSystem.SYSTEM.openReadOnly(testPath).use { testHandle ->
      testHandle.source(0L).buffer().use { buffer ->
        buffer.skip(PNG_SIG.size.toLong()) // Header

        var sequence = 0
        while (!buffer.exhausted()) {
          val (header, data) = buffer.assertNextChunk()
          if (header == FCTL || header == FDAT) {
            assertThat(data.readInt()).isEqualTo(sequence++)
          }
        }
      }
    }
  }

  @Test
  fun writesAllFramesWithSameFrameRate() {
    val testPath = tempFolderRule.newFile("writesAllFramesWithSameFrameRate.png").path.toPath()
    ApngWriter(testPath, 3).use { writer ->
      writer.writeImage(createImage(squareOffset = Point(5, 5)))
      writer.writeImage(createImage(squareOffset = Point(15, 15)))
      writer.writeImage(createImage(squareOffset = Point(25, 25)))
    }

    FileSystem.SYSTEM.openReadOnly(testPath).use { testHandle ->
      testHandle.source(0L).buffer().use { buffer ->
        buffer.skip(PNG_SIG.size.toLong()) // Header

        while (!buffer.exhausted()) {
          val (header, data) = buffer.assertNextChunk()
          if (header == FCTL) {
            data.skip(20)
            assertThat(data.readShort()).isEqualTo(1)
            assertThat(data.readShort()).isEqualTo(3)
          }
        }
      }
    }
  }

  @Test
  fun writesFramesAsSmallestDiffRect() {
    val testPath = tempFolderRule.newFile("writesFramesAsSmallestDiffRect.png").path.toPath()
    ApngWriter(testPath, 1).use { writer ->
      writer.writeImage(createImage(squareOffset = Point(5, 5)))
      writer.writeImage(createImage(squareOffset = Point(15, 15)))
    }

    FileSystem.SYSTEM.openReadOnly(testPath).use { testHandle ->
      testHandle.source(0L).buffer().use { buffer ->
        buffer.skip(PNG_SIG.size.toLong()) // Header
        assertThat(buffer.assertNextChunk().first).isEqualTo(IHDR)
        assertThat(buffer.assertNextChunk().first).isEqualTo(ACTL)
        assertThat(buffer.assertNextChunk().first).isEqualTo(FCTL)
        assertThat(buffer.assertNextChunk().first).isEqualTo(IDAT)

        val (header, data) = buffer.assertNextChunk()
        assertThat(header).isEqualTo(FCTL)
        assertThat(data.readInt()).isEqualTo(1)

        assertThat(data.readInt()).isEqualTo(60) // Width
        assertThat(data.readInt()).isEqualTo(60) // Height
        assertThat(data.readInt()).isEqualTo(5) // X Offset
        assertThat(data.readInt()).isEqualTo(5) // Y Offset
      }
    }
  }

  @Test
  fun writesEqualFramesAsSinglePixelFrameDiff() {
    val testPath = tempFolderRule.newFile("writesEqualFramesAsSinglePixelFrameDiff.png").path.toPath()
    ApngWriter(testPath, 1).use { writer ->
      writer.writeImage(createImage(squareOffset = Point(5, 5)))
      writer.writeImage(createImage(squareOffset = Point(5, 5)))
    }

    FileSystem.SYSTEM.openReadOnly(testPath).use { testHandle ->
      testHandle.source(0L).buffer().use { buffer ->
        buffer.skip(PNG_SIG.size.toLong()) // Header
        assertThat(buffer.assertNextChunk().first).isEqualTo(IHDR)
        assertThat(buffer.assertNextChunk().first).isEqualTo(ACTL)
        assertThat(buffer.assertNextChunk().first).isEqualTo(FCTL)
        assertThat(buffer.assertNextChunk().first).isEqualTo(IDAT)

        val (header, data) = buffer.assertNextChunk()
        assertThat(header).isEqualTo(FCTL)
        assertThat(data.readInt()).isEqualTo(1)

        assertThat(data.readInt()).isEqualTo(1) // Width
        assertThat(data.readInt()).isEqualTo(1) // Height
        assertThat(data.readInt()).isEqualTo(0) // X Offset
        assertThat(data.readInt()).isEqualTo(0) // Y Offset

        val (FDATHeader, FDATData) = buffer.assertNextChunk()
        assertThat(FDATHeader).isEqualTo(FDAT)
        FDATData.skip(4L) // Sequence Number

        val imageData = FDATData.decompress()
        assertThat(imageData.readByte()).isEqualTo(0) // Row filter None
        assertThat(imageData.readInt()).isEqualTo(BACKGROUND_PIXEL_INT)
        assertThat(FDATData.exhausted()).isTrue()
      }
    }
  }

  @Test
  fun rewritesFirstFrameWhenSmallerThanMaxFrame() {
    val testPath = tempFolderRule.newFile("rewritesFirstFrameWhenSmallerThanMaxFrame.png").path.toPath()
    ApngWriter(testPath, 1).use { writer ->
      writer.writeImage(createImage(imageSize = DEFAULT_SIZE, squareOffset = Point(5, 5)))
      writer.writeImage(createImage(imageSize = MAX_SIZE, squareOffset = Point(15, 15)))
    }

    FileSystem.SYSTEM.openReadOnly(testPath).use { testHandle ->
      testHandle.source(0L).buffer().use { buffer ->
        buffer.skip(PNG_SIG.size.toLong()) // Header
        val (ihdr, ihdrData) = buffer.assertNextChunk()
        assertThat(ihdr).isEqualTo(IHDR)
        assertThat(ihdrData.readInt()).isEqualTo(MAX_SIZE)
        assertThat(ihdrData.readInt()).isEqualTo(MAX_SIZE)

        assertThat(buffer.assertNextChunk().first).isEqualTo(ACTL)
        assertThat(buffer.assertNextChunk().first).isEqualTo(FCTL)

        val (idat, idatData) = buffer.assertNextChunk()
        assertThat(idat).isEqualTo(IDAT)
        val decompress = idatData.decompress()
        assertThat(decompress.size).isEqualTo((MAX_SIZE * MAX_SIZE * 4L) + MAX_SIZE) // 4 Bytes Per Pixel + 1 Byte Per Row
      }
    }
  }

  private fun BufferedSource.assertNextChunk(): Pair<Header, BufferedSource> {
    val crcEngine = CRC32()
    val dataLength = readInt()
    val chunkId = readByteArray(4L)
    val dataBuffer = Buffer().apply {
      write(this@assertNextChunk, dataLength.toLong())
    }

    val data = dataBuffer.peek().readByteArray()
    val crc = readInt()

    crcEngine.reset()
    crcEngine.update(chunkId, 0, 4)
    if (dataLength > 0) crcEngine.update(data, 0, dataLength)

    assertThat(crcEngine.value.toInt()).isEqualTo(crc)

    return Header.valueOf(chunkId.commonToUtf8String().uppercase()) to dataBuffer
  }

  private fun BufferedSource.decompress(): Buffer {
    val inflater = Inflater().apply {
      val readByteArray = readByteArray()
      setInput(readByteArray)
    }

    val buffer = ByteArray(1000 * 1024)
    return Buffer().apply {
      do {
        val byteCount = inflater.inflate(buffer)
        write(buffer, 0, byteCount)
      } while (!inflater.finished() && byteCount != 0)
    }
  }

  companion object {
    // ColorInt is encoded as ARGB, PNG is encoded as RGBA rotating to move A to the end
    private val BACKGROUND_PIXEL_INT = BACKGROUND_COLOR.toColorInt().rotateLeft(8)

    private const val DEFAULT_SIZE = 100
    private const val MAX_SIZE = 200
  }
}
