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
import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterInjector
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileTime
import java.time.Instant
import java.util.Date

@RunWith(TestParameterInjector::class)
class HtmlReportWriterTest {
  @get:Rule
  val reportRoot: TemporaryFolder = TemporaryFolder()

  @get:Rule
  val snapshotRoot: TemporaryFolder = TemporaryFolder()

  private val anyImage = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
  private val anyImageHash = "5007e8fef3bc5eeffa89d2797c63768390460f90"
  private val anyVideoHash = "80ff0587a0fbd0ede0247a021edfb1a3aaf9ccb5"

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
  fun sanitizeForFilename() {
    assertThat("0 Dollars".sanitizeForFilename()).isEqualTo("0_dollars")
    assertThat("`!#$%&*+=|\\'\"<>?/".sanitizeForFilename()).isEqualTo("_________________")
    assertThat("~@^()[]{}:;,.".sanitizeForFilename()).isEqualTo("~@^()[]{}:;,.")
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
  fun snapshotsOverwriteOnRecord(@TestParameter params: SnapshotOverwriteTestParams) {
    try {
      // set record mode
      System.setProperty("paparazzi.test.record", "true")

      val htmlReportWriter = HtmlReportWriter(
        "record_run",
        reportRoot.root,
        snapshotRoot.root,
        params.maxPercentDifference
      )
      htmlReportWriter.use {
        val now = Instant.parse("2021-02-23T10:27:43Z")
        val snapshot = Snapshot(
          name = "test",
          testName = TestName("app.cash.paparazzi", "HomeView", "testSettings"),
          timestamp = now.toDate()
        )
        val golden =
          File(
            "${snapshotRoot.root}/${params.snapshotType.goldenSnapshotFolder}/" +
              "app.cash.paparazzi_HomeView_testSettings_test.png"
          )

        // precondition
        assertThat(golden).doesNotExist()

        // take 1
        val frameHandler1 = htmlReportWriter.newFrameHandler(
          snapshot = snapshot,
          frameCount = params.frames1.size,
          fps = params.snapshotType.fps
        )
        frameHandler1.use { handler -> params.frames1.forEach(handler::handle) }
        assertThat(golden).exists()
        val timeFirstWrite = golden.lastModifiedTime()

        // I know....but guarantees writes won't happen in same tick
        Thread.sleep(100)

        // take 2
        val frameHandler2 = htmlReportWriter.newFrameHandler(
          snapshot = snapshot.copy(timestamp = now.plusSeconds(1).toDate()),
          frameCount = params.frames2.size,
          fps = params.snapshotType.fps
        )
        frameHandler2.use { handler -> params.frames2.forEach(handler::handle) }
        assertThat(golden).exists()
        val timeOverwrite = golden.lastModifiedTime()

        if (params.shouldBeOverwritten) {
          assertThat(timeOverwrite).isGreaterThan(timeFirstWrite)
        } else {
          assertThat(timeOverwrite).isEqualTo(timeFirstWrite)
        }
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

  private fun Instant.toDate() = Date(toEpochMilli())

  private fun File.lastModifiedTime(): FileTime {
    return Files.readAttributes(this.toPath(), BasicFileAttributes::class.java).lastModifiedTime()
  }

  @Suppress("unused") // Used by TestParameterInjector
  enum class SnapshotOverwriteTestParams(
    val snapshotType: SnapshotType,
    val maxPercentDifference: Double?,
    val frames1: Collection<BufferedImage>,
    val frames2: Collection<BufferedImage>,
    val shouldBeOverwritten: Boolean
  ) {
    ImageWithoutThresholdShouldBeOverwritten(
      snapshotType = SnapshotType.Image,
      maxPercentDifference = null,
      frames1 = listOf(BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)),
      frames2 = listOf(BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)),
      shouldBeOverwritten = true
    ),
    ImageWithThresholdShouldBeOverwritten(
      snapshotType = SnapshotType.Image,
      maxPercentDifference = 20.0, // 20% < ~25%
      frames1 = listOf(BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB)),
      frames2 = listOf(
        BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB).apply {
          setRGB(0, 0, 0xFFFFFFFF.toInt())
        }
      ),
      shouldBeOverwritten = true
    ),
    ImageWithThresholdShouldNotBeOverwritten(
      snapshotType = SnapshotType.Image,
      maxPercentDifference = 30.0, // 30% > ~25%
      frames1 = listOf(BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB)),
      frames2 = listOf(
        BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB).apply {
          setRGB(0, 0, 0xFFFFFFFF.toInt())
        }
      ),
      shouldBeOverwritten = false
    ),
    VideoWithoutThresholdShouldBeOverwritten(
      snapshotType = SnapshotType.Video,
      maxPercentDifference = null,
      frames1 = listOf(
        BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB),
        BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
      ),
      frames2 = listOf(
        BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB),
        BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
      ),
      shouldBeOverwritten = true
    ),
    VideoWithThresholdShouldBeOverwritten(
      snapshotType = SnapshotType.Video,
      maxPercentDifference = 20.0, // 20% < ~25%
      frames1 = listOf(
        BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB),
        BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB)
      ),
      frames2 = listOf(
        BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB),
        BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB).apply { setRGB(0, 0, 0xFFFFFFFF.toInt()) }
      ),
      shouldBeOverwritten = true
    ),
    VideoWithThresholdShouldNotBeOverwritten(
      snapshotType = SnapshotType.Video,
      maxPercentDifference = 30.0, // 30% > ~25%
      frames1 = listOf(
        BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB),
        BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB)
      ),
      frames2 = listOf(
        BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB),
        BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB).apply { setRGB(0, 0, 0xFFFFFFFF.toInt()) }
      ),
      shouldBeOverwritten = false
    )
    ;

    sealed class SnapshotType(
      val goldenSnapshotFolder: String,
      val fps: Int
    ) {
      data object Image : SnapshotType(goldenSnapshotFolder = "images", fps = -1)
      data object Video : SnapshotType(goldenSnapshotFolder = "videos", fps = 1)
    }
  }
}
