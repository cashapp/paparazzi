/*
 * Copyright (C) 2019 Square, Inc.
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
package app.cash.paparazzi

import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.awt.image.BufferedImage
import java.io.File
import java.time.Instant
import java.util.*

class PaparazziFrameHandlerTest {
    private lateinit var mediaWriter: FakeMediaWriter
    private lateinit var mediaVerifier: FakeMediaVerifier
    private lateinit var underTest: PaparazziTestMediaHandler

    @Before
    fun setUp() {
        mediaWriter = FakeMediaWriter()
        mediaVerifier = FakeMediaVerifier()
        underTest = PaparazziTestMediaHandler(mediaWriter, mediaVerifier)
    }

    @Test
    fun happyPathSingleImage() {
        underTest.use {
            val bufferedImage1 = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
            val snapshot1 = Snapshot("snapshot1", TestName("p", "c", "m"), Date.from(Instant.now()), emptyList())
            underTest.newFrameHandler(snapshot1, 1, -1).use {
                it.handleTestFrame(bufferedImage1)
                Assert.assertFalse(mediaWriter.isClosed)
                Assert.assertTrue(mediaWriter.snapshotsClosed.isEmpty())
                Assert.assertEquals(1, mediaWriter.writtenImage.size)
                Assert.assertSame(bufferedImage1, mediaWriter.writtenImage.first())
            }

            Assert.assertFalse(mediaWriter.isClosed)
            Assert.assertEquals(1, mediaWriter.snapshotsClosed.size)
            Assert.assertEquals(snapshot1 to -1, mediaWriter.snapshotsClosed.first())
        }

        Assert.assertTrue(mediaWriter.isClosed)
    }

    @Test
    fun happyPathMultipleImages() {
        underTest.use {
            val bufferedImage1 = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
            val snapshot1 = Snapshot("snapshot1", TestName("p", "c", "m"), Date.from(Instant.now()), emptyList())
            val bufferedImage2 = BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB)
            val snapshot2 = Snapshot("snapshot2", TestName("p", "c", "m"), Date.from(Instant.now()), emptyList())
            underTest.newFrameHandler(snapshot1, 1, -1).use {
                it.handleTestFrame(bufferedImage1)
                Assert.assertFalse(mediaWriter.isClosed)
                Assert.assertTrue(mediaWriter.snapshotsClosed.isEmpty())
                Assert.assertEquals(1, mediaWriter.writtenImage.size)
                Assert.assertSame(bufferedImage1, mediaWriter.writtenImage.first())
            }

            Assert.assertFalse(mediaWriter.isClosed)
            Assert.assertEquals(1, mediaWriter.snapshotsClosed.size)
            Assert.assertEquals(snapshot1 to -1, mediaWriter.snapshotsClosed.first())
            mediaWriter.writtenImage.clear()

            underTest.newFrameHandler(snapshot2, 1, -1).use {
                it.handleTestFrame(bufferedImage2)
                Assert.assertFalse(mediaWriter.isClosed)
                Assert.assertEquals(1, mediaWriter.writtenImage.size)
                Assert.assertSame(bufferedImage2, mediaWriter.writtenImage.first())
            }

            Assert.assertFalse(mediaWriter.isClosed)
            Assert.assertEquals(2, mediaWriter.snapshotsClosed.size)
            Assert.assertEquals(snapshot2 to -1, mediaWriter.snapshotsClosed[1]/*second*/)
        }

        Assert.assertTrue(mediaWriter.isClosed)
    }

    @Test
    fun happyPathVideo() {
        underTest.use {
            val bufferedImages = listOf(BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB),
                    BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB),
                    BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB),
                    BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB))

            val snapshot1 = Snapshot("snapshot1", TestName("p", "c", "m"), Date.from(Instant.now()), emptyList())
            underTest.newFrameHandler(snapshot1, bufferedImages.size, 20).use {
                bufferedImages.forEach { frame ->
                    it.handleTestFrame(frame)
                }
                Assert.assertFalse(mediaWriter.isClosed)
                Assert.assertTrue(mediaWriter.snapshotsClosed.isEmpty())
                Assert.assertEquals(bufferedImages.size, mediaWriter.writtenImage.size)
                bufferedImages.forEachIndexed { index, bufferedImage ->
                    Assert.assertSame(bufferedImage, mediaWriter.writtenImage[index])
                }
            }

            Assert.assertFalse(mediaWriter.isClosed)
            Assert.assertEquals(1, mediaWriter.snapshotsClosed.size)
            Assert.assertEquals(snapshot1 to 20, mediaWriter.snapshotsClosed.first())
        }

        Assert.assertTrue(mediaWriter.isClosed)
    }
}

private class FakeMediaWriter : TestMediaWriter {
    val writtenImage = mutableListOf<BufferedImage>()
    val snapshotsClosed = mutableListOf<Pair<Snapshot, Int>>()
    var isClosed = false

    override fun writeTestFrame(image: BufferedImage) {
        writtenImage.add(image)
    }

    override fun closeTestRun(snapshot: Snapshot, fps: Int): File {
        snapshotsClosed.add(snapshot to fps)
        return File(snapshot.name)
    }

    override fun close() {
        isClosed = true
    }

}

private class FakeMediaVerifier : MediaVerifier