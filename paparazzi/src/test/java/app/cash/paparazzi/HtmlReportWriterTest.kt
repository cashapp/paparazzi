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

import app.cash.paparazzi.FileSubject.Companion.assertThat
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileTime
import java.time.Instant
import java.util.Date
import javax.imageio.ImageIO

class HtmlReportWriterTest {
  @get:Rule
  val reportRoot: TemporaryFolder = TemporaryFolder()

  @get:Rule
  val snapshotRoot: TemporaryFolder = TemporaryFolder()

  private val anyImage = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
  private val anyImageHash = "2ef6a6e795a2b8cffabefb6d9a2066a183c8e3b6"
  private val anyVideoHash = "7325996fabd92c7411c0d03ed62641323ffdf0d1"

  @Test
  fun happyPathImages() {
    val htmlReportWriter = HtmlReportWriter("run_one", reportRoot.root)
    htmlReportWriter.use {
      val frameHandler = htmlReportWriter.newFrameHandler(
        snapshot = Snapshot(
          name = "loading",
          testName = TestName("app.cash.paparazzi", "CelebrityTest", "testSettings"),
          timestamp = Instant.parse("2019-03-20T10:27:43Z").toDate(),
          tags = listOf("redesign")
        ),
        frameCount = 1,
        fps = -1
      )
      frameHandler.use {
        frameHandler.handle(anyImage)
      }
    }

    assertThat(File("${reportRoot.root}/index.js")).hasContent(
      """
        |window.all_runs = [
        |  "run_one"
        |];
      """.trimMargin()
    )

    assertThat(File("${reportRoot.root}/runs/run_one.js")).hasContent(
      """
        |window.runs["run_one"] = [
        |  {
        |    "name": "loading",
        |    "testName": "app.cash.paparazzi.CelebrityTest#testSettings",
        |    "timestamp": "2019-03-20T10:27:43.000Z",
        |    "tags": [
        |      "redesign"
        |    ],
        |    "file": "images/$anyImageHash.png"
        |  }
        |];
      """.trimMargin()
    )
  }

  @Test
  fun invalidCharSnapshot() {
    val htmlReportWriter = HtmlReportWriter("run_one", reportRoot.root)
    htmlReportWriter.use {
      val frameHandler = htmlReportWriter.newFrameHandler(
        snapshot = Snapshot(
          name = "bad input ?<>|",
          testName = TestName("app.cash.paparazzi", "CelebrityTest", "testSettings"),
          timestamp = Instant.parse("2019-03-20T10:27:43Z").toDate(),
          tags = listOf("error")
        ),
        frameCount = 1,
        fps = -1
      )
      frameHandler.use {
        frameHandler.handle(anyImage)
      }
    }

    assertThat(File("${reportRoot.root}/index.js")).hasContent(
      """
        |window.all_runs = [
        |  "run_one"
        |];
      """.trimMargin()
    )

    assertThat(File("${reportRoot.root}/runs/run_one.js")).hasContent(
      """
        |window.runs["run_one"] = [
        |  {
        |    "name": "bad input ?<>|",
        |    "testName": "app.cash.paparazzi.CelebrityTest#testSettings",
        |    "timestamp": "2019-03-20T10:27:43.000Z",
        |    "tags": [
        |      "error"
        |    ],
        |    "file": "images/$anyImageHash.png"
        |  }
        |];
      """.trimMargin()
    )
  }

  @Test
  fun sanitizeForFilename() {
    assertThat("0 Dollars".sanitizeForFilename()).isEqualTo("0_dollars")
    assertThat("`!#$%&*+=|\\'\"<>?/".sanitizeForFilename()).isEqualTo("_________________")
    assertThat("~@^()[]{}:;,.".sanitizeForFilename()).isEqualTo("~@^()[]{}:;,.")
    assertThat("app.cash.paparazzi.SampleTest.sampleMethod_snapshot_\\'\"<>?/".sanitizeForFilename(false))
      .isEqualTo("app.cash.paparazzi.SampleTest.sampleMethod_snapshot________")
  }

  @Test
  fun noSnapshotOnFailure() {
    val htmlReportWriter = HtmlReportWriter("run_one", reportRoot.root)
    htmlReportWriter.use {
      val frameHandler = htmlReportWriter.newFrameHandler(
        snapshot = Snapshot(
          name = "loading",
          testName = TestName("app.cash.paparazzi", "CelebrityTest", "testSettings"),
          timestamp = Instant.parse("2019-03-20T10:27:43Z").toDate()
        ),
        frameCount = 4,
        fps = -1
      )
      frameHandler.use {
        // intentionally empty, to simulate no content written on exception
      }
    }

    assertThat(File(reportRoot.root, "images")).isEmptyDirectory()
    assertThat(File(reportRoot.root, "videos")).isEmptyDirectory()
  }

  @Test
  fun imagesAlwaysOverwriteOnRecord() {
    try {
      // set record mode
      System.setProperty("paparazzi.test.record", "true")

      val htmlReportWriter = HtmlReportWriter("record_run", reportRoot.root, snapshotRoot.root)
      htmlReportWriter.use {
        val now = Instant.parse("2021-02-23T10:27:43Z")
        val snapshot = Snapshot(
          name = "test",
          testName = TestName("app.cash.paparazzi", "HomeView", "testSettings"),
          timestamp = now.toDate()
        )
        val golden =
          File("${snapshotRoot.root}/images/app.cash.paparazzi_HomeView_testSettings_test.png")

        // precondition
        assertThat(golden).doesNotExist()

        // take 1
        val frameHandler1 = htmlReportWriter.newFrameHandler(
          snapshot = snapshot,
          frameCount = 1,
          fps = -1
        )
        frameHandler1.use { frameHandler1.handle(anyImage) }
        assertThat(golden).exists()
        val timeFirstWrite = golden.lastModifiedTime()

        // I know....but guarantees writes won't happen in same tick
        Thread.sleep(100)

        // take 2
        val frameHandler2 = htmlReportWriter.newFrameHandler(
          snapshot = snapshot.copy(timestamp = now.plusSeconds(1).toDate()),
          frameCount = 1,
          fps = -1
        )
        frameHandler2.use { frameHandler2.handle(anyImage) }
        assertThat(golden).exists()
        val timeOverwrite = golden.lastModifiedTime()

        // should always overwrite
        assertThat(timeOverwrite).isGreaterThan(timeFirstWrite)
      }
    } finally {
      System.clearProperty("paparazzi.test.record")
    }
  }

  @Test
  fun happyPathVideos() {
    val htmlReportWriter = HtmlReportWriter("run_one", reportRoot.root)
    htmlReportWriter.use {
      val frameHandler = htmlReportWriter.newFrameHandler(
        Snapshot(
          name = "loading",
          testName = TestName("app.cash.paparazzi", "CelebrityTest", "testSettings"),
          timestamp = Instant.parse("2019-03-20T10:27:43Z").toDate(),
          tags = listOf("redesign")
        ),
        frameCount = 2,
        fps = 1
      )
      frameHandler.use {
        frameHandler.handle(anyImage)
        frameHandler.handle(anyImage)
      }
    }

    assertThat(File("${reportRoot.root}/index.js")).hasContent(
      """
        |window.all_runs = [
        |  "run_one"
        |];
      """.trimMargin()
    )

    assertThat(File("${reportRoot.root}/runs/run_one.js")).hasContent(
      """
        |window.runs["run_one"] = [
        |  {
        |    "name": "loading",
        |    "testName": "app.cash.paparazzi.CelebrityTest#testSettings",
        |    "timestamp": "2019-03-20T10:27:43.000Z",
        |    "tags": [
        |      "redesign"
        |    ],
        |    "file": "videos/$anyVideoHash.png"
        |  }
        |];
      """.trimMargin()
    )
  }

  @Test
  fun videosAlwaysOverwriteOnRecord() {
    try {
      // set record mode
      System.setProperty("paparazzi.test.record", "true")

      val htmlReportWriter = HtmlReportWriter("record_run", reportRoot.root, snapshotRoot.root)
      htmlReportWriter.use {
        val now = Instant.parse("2021-02-23T10:27:43Z")
        val snapshot = Snapshot(
          name = "test",
          testName = TestName("app.cash.paparazzi", "HomeView", "testSettings"),
          timestamp = now.toDate()
        )
        val golden =
          File("${snapshotRoot.root}/videos/app.cash.paparazzi_HomeView_testSettings_test.png")

        // precondition
        assertThat(golden).doesNotExist()

        // take 1
        val frameHandler1 = htmlReportWriter.newFrameHandler(
          snapshot = snapshot,
          frameCount = 2,
          fps = 1
        )
        frameHandler1.use {
          frameHandler1.handle(anyImage)
          frameHandler1.handle(anyImage)
        }
        assertThat(golden).exists()
        val timeFirstWrite = golden.lastModifiedTime()

        // I know....but guarantees writes won't happen in same tick
        Thread.sleep(100)

        // take 2
        val frameHandler2 = htmlReportWriter.newFrameHandler(
          snapshot = snapshot.copy(timestamp = now.plusSeconds(1).toDate()),
          frameCount = 2,
          fps = 1
        )
        frameHandler2.use {
          frameHandler2.handle(anyImage)
          frameHandler2.handle(anyImage)
        }
        assertThat(golden).exists()
        val timeOverwrite = golden.lastModifiedTime()

        // should always overwrite
        assertThat(timeOverwrite).isGreaterThan(timeFirstWrite)
      }
    } finally {
      // reset record mode
      System.setProperty("paparazzi.test.record", "false")
    }
  }

  @Test
  fun similarImagesProduceDifferentSnapshotFiles() {
    try {
      // set record mode
      System.setProperty("paparazzi.test.record", "true")

      val horizontal = ImageIO.read(File("src/test/resources/horizontal.png"))
      val convertedHorizontal = horizontal.convertImage()
      val vertical = ImageIO.read(File("src/test/resources/vertical.png"))
      val convertedVertical = vertical.convertImage()
      val square = ImageIO.read(File("src/test/resources/square.png"))
      val convertedSquare = square.convertImage()
      val htmlReportWriter = HtmlReportWriter("record_run", reportRoot.root, snapshotRoot.root)

      htmlReportWriter.use {
        val horizontalFrameHandler = htmlReportWriter.newFrameHandler(
          Snapshot(
            name = "horizontal",
            testName = TestName("app.cash.paparazzi", "SimilarViews", "horizontal"),
            timestamp = Instant.parse("2019-03-20T10:27:43Z").toDate(),
            tags = listOf("redesign")
          ),
          frameCount = 1,
          fps = -1
        )
        horizontalFrameHandler.use {
          horizontalFrameHandler.handle(convertedHorizontal)
        }
        val verticalFrameHandler = htmlReportWriter.newFrameHandler(
          Snapshot(
            name = "loading",
            testName = TestName("app.cash.paparazzi", "SimilarViews", "vertical"),
            timestamp = Instant.parse("2019-03-20T10:27:43Z").toDate(),
            tags = listOf("redesign")
          ),
          frameCount = 1,
          fps = -1
        )
        verticalFrameHandler.use {
          verticalFrameHandler.handle(convertedVertical)
        }
        val squareFrameHandler = htmlReportWriter.newFrameHandler(
          Snapshot(
            name = "loading",
            testName = TestName("app.cash.paparazzi", "SimilarViews", "square"),
            timestamp = Instant.parse("2019-03-20T10:27:43Z").toDate(),
            tags = listOf("redesign")
          ),
          frameCount = 1,
          fps = -1
        )
        squareFrameHandler.use {
          squareFrameHandler.handle(convertedSquare)
        }
      }
      // Verify that there are 3 files in the reports/paparazzi/images file
      // This should confirm that the snapshot images used in the test report are correct
      val snapshotDir = File(snapshotRoot.root, "images").listFiles()!!
      assertThat(snapshotDir.size).isEqualTo(3)

      val firstSnapshot = ImageIO.read(snapshotDir[0])
      val secondSnapshot = ImageIO.read(snapshotDir[1])
      val thirdSnapshot = ImageIO.read(snapshotDir[2])
      assertThat(firstSnapshot.pixelEqual(secondSnapshot)).isFalse()
      assertThat(firstSnapshot.pixelEqual(thirdSnapshot)).isFalse()
      assertThat(secondSnapshot.pixelEqual(thirdSnapshot)).isFalse()
    } finally {
      // reset record mode
      System.setProperty("paparazzi.test.record", "false")
    }
  }

  @Test
  fun similarImagesProduceDifferentReportFiles() {
    try {
      // set record mode
      System.setProperty("paparazzi.test.record", "true")

      val horizontal = ImageIO.read(File("src/test/resources/horizontal.png"))
      val convertedHorizontal = horizontal.convertImage()
      val vertical = ImageIO.read(File("src/test/resources/vertical.png"))
      val convertedVertical = vertical.convertImage()
      val square = ImageIO.read(File("src/test/resources/square.png"))
      val convertedSquare = square.convertImage()
      val htmlReportWriter = HtmlReportWriter("record_run", reportRoot.root, snapshotRoot.root)

      htmlReportWriter.use {
        val horizontalFrameHandler = htmlReportWriter.newFrameHandler(
          Snapshot(
            name = "horizontal",
            testName = TestName("app.cash.paparazzi", "SimilarViews", "horizontal"),
            timestamp = Instant.parse("2019-03-20T10:27:43Z").toDate(),
            tags = listOf("redesign")
          ),
          frameCount = 1,
          fps = -1
        )
        horizontalFrameHandler.use {
          horizontalFrameHandler.handle(convertedHorizontal)
        }
        val verticalFrameHandler = htmlReportWriter.newFrameHandler(
          Snapshot(
            name = "loading",
            testName = TestName("app.cash.paparazzi", "SimilarViews", "vertical"),
            timestamp = Instant.parse("2019-03-20T10:27:43Z").toDate(),
            tags = listOf("redesign")
          ),
          frameCount = 1,
          fps = -1
        )
        verticalFrameHandler.use {
          verticalFrameHandler.handle(convertedVertical)
        }
        val squareFrameHandler = htmlReportWriter.newFrameHandler(
          Snapshot(
            name = "loading",
            testName = TestName("app.cash.paparazzi", "SimilarViews", "square"),
            timestamp = Instant.parse("2019-03-20T10:27:43Z").toDate(),
            tags = listOf("redesign")
          ),
          frameCount = 1,
          fps = -1
        )
        squareFrameHandler.use {
          squareFrameHandler.handle(convertedSquare)
        }
      }
      // Verify that there are 3 files in the reports/paparazzi/images file
      // This should confirm that the snapshot images used in the test report are correct
      val reportDir = File(reportRoot.root, "images").listFiles()!!
      assertThat(reportDir.size).isEqualTo(3)
    } finally {
      // reset record mode
      System.setProperty("paparazzi.test.record", "false")
    }
  }

  private fun Instant.toDate() = Date(toEpochMilli())

  private fun File.lastModifiedTime(): FileTime {
    return Files.readAttributes(this.toPath(), BasicFileAttributes::class.java).lastModifiedTime()
  }

  // Function that takes in an existing Buffered image and converts its type to one
  // that ApngWriter can consume
  private fun BufferedImage.convertImage() = BufferedImage(
    width,
    height,
    BufferedImage.TYPE_INT_ARGB_PRE // Something ApngWriter can process
  ).apply {
    graphics.drawImage(this, 0, 0, null)
  }

  private fun BufferedImage.pixelEqual(other: BufferedImage): Boolean {
    if (width == other.width && height == other.height) {
      (0 until width).forEach { x ->
        (0 until height).forEach { y ->
          if (getRGB(x, y) != other.getRGB(x, y)) {
            return false
          }
        }
      }
    } else {
      return false
    }
    return true
  }
}

internal fun Instant.toDate() = Date(toEpochMilli())
