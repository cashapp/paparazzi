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
import com.google.common.truth.Truth.assertThat
import okio.FileSystem
import okio.Path.Companion.toPath
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.io.IOException
import javax.imageio.ImageIO

class ApngReaderTest {

  @Test
  fun decodesAllFrames() {
    val file = javaClass.classLoader.getResource("simple_animation.png")
    val reader = ApngReader(FileSystem.SYSTEM.openReadOnly(file.path.toPath()))

    for (i in 0 until 3) {
      val expectedFile = javaClass.classLoader.getResource("simple_animation_$i.png")
      val expectedImage = ImageIO.read(expectedFile)
      val actualImage = reader.readNextFrame()!!
      ImageUtils.assertImageSimilar(
        expectedFile!!.path,
        File(file.path).parentFile,
        expectedImage,
        actualImage,
        0.0
      )
    }

    assertThat(reader.isFinished()).isTrue()
    assertThat(reader.frameCount).isEqualTo(reader.frameNumber)
  }

  @Test
  fun enforcesAnimationChunkSequence() {
    val file = javaClass.classLoader.getResource("invalid_sequence.png")

    try {
      val reader = ApngReader(FileSystem.SYSTEM.openReadOnly(file.path.toPath()))
      while (!reader.isFinished()) { reader.readNextFrame() }
      fail("Chunks are out of order, expected to fail to decode")
    } catch (e: IOException) {
      assertThat(e).hasMessageThat().isEqualTo("Out of order sequence, expected: 1 actual: 0")
    }
  }

  @Test
  fun enforcesCRC() {
    val file = javaClass.classLoader.getResource("invalid_crc.png")

    try {
      val reader = ApngReader(FileSystem.SYSTEM.openReadOnly(file.path.toPath()))
      while (!reader.isFinished()) { reader.readNextFrame() }
      fail("File has invalid CRC, should fail")
    } catch (e: IOException) {
      assertThat(e).hasMessageThat().isEqualTo("CRC Mismatch decoding IHDR, invalid data")
    }
  }

  @Test
  fun failsOnMissingPNGHeader() {
    val file = File.createTempFile("image.png", null)
    file.writeBytes((0..10).map { it.toByte() }.toByteArray())

    try {
      ApngReader(FileSystem.SYSTEM.openReadOnly(file.path.toPath()))
      fail("Invalid png file")
    } catch (e: IOException) {
      assertThat(e).hasMessageThat().isEqualTo("Missing valid PNG header expected: [89504e470d0a1a0a] actual: [0001020304050607]")
    }
  }
}
