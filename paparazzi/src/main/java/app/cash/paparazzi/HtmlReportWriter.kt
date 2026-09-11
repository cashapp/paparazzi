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

import app.cash.paparazzi.SnapshotHandler.FrameHandler
import app.cash.paparazzi.internal.ImageUtils
import app.cash.paparazzi.internal.PaparazziJson
import app.cash.paparazzi.internal.apng.ApngVerifier
import app.cash.paparazzi.internal.apng.ApngWriter
import com.google.common.base.CharMatcher
import com.google.common.io.Files
import okio.BufferedSink
import okio.HashingSink
import okio.Path.Companion.toOkioPath
import okio.Path.Companion.toPath
import okio.blackholeSink
import okio.buffer
import okio.sink
import okio.source
import java.awt.image.BufferedImage
import java.awt.image.BufferedImage.TYPE_INT_ARGB
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.imageio.ImageIO

/**
 * Creates an HTML report that avoids writing files that have already been written.
 *
 * Images and videos are named by hashes of their contents. Paparazzi won't write two images or videos with the same
 * contents. Note that the images/ directory includes the individual frames of each video.
 *
 * Runs are named by their date.
 *
 * When `paparazzi.test.verify` is set, snapshots are compared against their golden instead of being recorded.
 * The report is still generated for the run: failed snapshots write expected/actual/delta images into the report's
 * images/ directory (referenced from the run data) and the failure delta into the failures directory, and the test
 * fails with an [AssertionError].
 *
 * ```
 * images
 *   088c60580f06efa95c37fd8e754074729ee74a06.png
 *   93f9a81cb594280f4b3898d90dfad8c8ea969b01.png
 *   22d37abd0841ba2a8d0bd635954baf7cbfaa269b.png
 *   a4769e43cc5901ef28c0d46c46a44ea6429cbccc.png
 * videos
 *   d1cddc5da2224053f2af51f4e69a76de4e61fc41.mov
 * runs
 *   20190626002322_b9854e.js
 *   20190626002345_b1e882.js
 * index.html
 * index.js
 * paparazzi.js
 * ```
 */
public class HtmlReportWriter @JvmOverloads constructor(
  private val runName: String = defaultRunName(),
  private val rootDirectory: File = File(System.getProperty("paparazzi.report.dir")),
  private val maxPercentDifference: Double,
  private val differ: Differ = determineDiffer(),
  snapshotRootDirectory: File = File(System.getProperty("paparazzi.snapshot.dir"))
) : SnapshotHandler {
  private val runsDirectory: File = File(rootDirectory, "runs")
  private val imagesDirectory: File = File(rootDirectory, "images")
  private val videosDirectory: File = File(rootDirectory, "videos")

  private val goldenImagesDirectory = File(snapshotRootDirectory, "images")
  private val goldenVideosDirectory = File(snapshotRootDirectory, "videos")

  private val shots = mutableListOf<Snapshot>()

  private val isRecording: Boolean =
    System.getProperty("paparazzi.test.record")?.toBoolean() == true
  private val isVerifying: Boolean =
    System.getProperty("paparazzi.test.verify")?.toBoolean() == true
  private val overwriteOnMaxPercentDifference: Boolean =
    System.getProperty("paparazzi.test.record.overwriteOnMaxPercentDifference")?.toBoolean() == true

  init {
    runsDirectory.mkdirs()
    imagesDirectory.mkdirs()
    videosDirectory.mkdirs()
    writeStaticFiles()
    writeRunJs()
    writeIndexJs()
  }

  override fun newFrameHandler(snapshot: Snapshot, frameCount: Int, fps: Int): FrameHandler {
    return object : FrameHandler {
      val snapshotDir = if (fps == -1) imagesDirectory else videosDirectory
      val goldenDir = if (fps == -1) goldenImagesDirectory else goldenVideosDirectory
      val hashes = mutableListOf<String>()
      val snapshotTmpFile = File(snapshotDir, snapshot.toFileName(extension = "temp.png"))
      val writer = ApngWriter(snapshotTmpFile.path.toPath(), fps)
      var stillImage: BufferedImage? = null
      val pngVerifier: ApngVerifier? = if (isVerifying && fps != -1) {
        val fileName = snapshot.toFileName("_", "png")
        ApngVerifier(
          goldenFilePath = File(goldenDir, fileName).toOkioPath(),
          deltaFilePath = File(failureDir, "delta-$fileName").toOkioPath(),
          fps = fps,
          frameCount = frameCount,
          maxPercentDifference = maxPercentDifference,
          differ = differ
        )
      } else {
        null
      }

      override fun handle(image: BufferedImage) {
        writer.writeImage(image)
        pngVerifier?.verifyFrame(image)
        hashes += hash(image)
        if (fps == -1) {
          stillImage = image
        }
      }

      override fun close() {
        if (hashes.isEmpty()) return
        var verifyError: AssertionError? = null
        try {
          pngVerifier?.assertFinished()
        } catch (error: AssertionError) {
          verifyError = error
        } finally {
          pngVerifier?.close()
          writer.close()
          val snapshotFile = File(snapshotDir, "${hash(hashes)}.png")
          Files.move(snapshotTmpFile, snapshotFile)
          snapshotTmpFile.delete()

          if (isRecording) {
            val goldenFile = File(goldenDir, snapshot.toFileName("_", "png"))
            if (!overwriteOnMaxPercentDifference || !goldenFile.exists()) {
              snapshotFile.copyTo(target = goldenFile, overwrite = true)
            } else {
              val result = ImageUtils.compareImages(
                goldenImage = ImageIO.read(goldenFile),
                image = ImageIO.read(snapshotFile),
                differ = differ
              )
              if (result.second > maxPercentDifference) {
                snapshotFile.copyTo(target = goldenFile, overwrite = true)
              }
            }
          }

          if (isVerifying && fps == -1) {
            verifyStillSnapshot(snapshot, snapshotFile, checkNotNull(stillImage))
          } else if (verifyError != null) {
            // Failed video: surface the expected/actual/delta in the report like stills do.
            val imageName = snapshot.toFileName("_", "png")
            val expectedFile = File(imagesDirectory, "expected-$imageName")
            val actualFile = File(imagesDirectory, "actual-$imageName")
            val deltaFile = File(imagesDirectory, "delta-$imageName")
            File(goldenDir, imageName).copyTo(expectedFile, overwrite = true)
            snapshotFile.copyTo(actualFile, overwrite = true)
            File(failureDir, "delta-$imageName").copyTo(deltaFile, overwrite = true)
            shots += snapshot.copy(
              file = actualFile.toJsonPath(),
              expectedFile = expectedFile.toJsonPath(),
              deltaFile = deltaFile.toJsonPath()
            )
          } else {
            shots += snapshot.copy(file = snapshotFile.toJsonPath())
          }

          verifyError?.let { throw it }
        }
      }
    }
  }

  /**
   * Compares a still snapshot against its golden and records the outcome in the report.
   *
   * On failure the expected, actual and delta images are written into the report's images/
   * directory and referenced from the run data, so failed runs can be reviewed from the HTML
   * report. The delta and actual images are also written to the failures directory (as
   * [ImageUtils.assertImageSimilar] does) for the Gradle test report, and an [AssertionError]
   * is thrown to fail the test, mirroring [SnapshotVerifier].
   */
  private fun verifyStillSnapshot(snapshot: Snapshot, snapshotFile: File, image: BufferedImage) {
    val goldenFile = File(goldenImagesDirectory, snapshot.toFileName("_", "png"))
    val goldenImage = if (!goldenFile.exists()) {
      // No golden recorded yet: compare against a blank canvas so the run is reported as a failure.
      BufferedImage(image.width, image.height, TYPE_INT_ARGB)
    } else {
      ImageIO.read(goldenFile)
    } ?: throw NullPointerException(
      """
      Failed to read the snapshot file from the file system.

      If your project uses git LFS, it's possible that it's misconfigured on your machine and
      Paparazzi has just loaded a pointer file instead of the real snapshot file. Follow git
      LFS troubleshooting instructions and try again.

      """.trimIndent()
    )

    try {
      ImageUtils.assertImageSimilar(
        relativePath = goldenFile.path,
        image = image,
        goldenImage = goldenImage,
        maxPercentDifferent = maxPercentDifference,
        failureDir = failureDir,
        differ = differ
      )
      shots += snapshot.copy(file = snapshotFile.toJsonPath())
    } catch (error: AssertionError) {
      val imageName = snapshot.toFileName("_", "png")
      val expectedFile = File(imagesDirectory, "expected-$imageName")
      val actualFile = File(imagesDirectory, "actual-$imageName")
      val deltaFile = File(imagesDirectory, "delta-$imageName")
      ImageIO.write(goldenImage, "PNG", expectedFile)
      snapshotFile.copyTo(actualFile, overwrite = true)
      File(failureDir, "delta-$imageName").copyTo(deltaFile, overwrite = true)
      shots += snapshot.copy(
        file = actualFile.toJsonPath(),
        expectedFile = expectedFile.toJsonPath(),
        deltaFile = deltaFile.toJsonPath()
      )
      throw error
    }
  }

  /** Returns a SHA-1 hash of the pixels of [image]. */
  private fun hash(image: BufferedImage): String {
    val hashingSink = HashingSink.sha1(blackholeSink())
    hashingSink.buffer().use { sink ->
      sink.writeInt(image.width)
      sink.writeInt(image.height)
      for (y in 0 until image.height) {
        for (x in 0 until image.width) {
          sink.writeInt(image.getRGB(x, y))
        }
      }
    }
    return hashingSink.hash.hex()
  }

  /** Returns a SHA-1 hash of [lines]. */
  private fun hash(lines: List<String>): String {
    val hashingSink = HashingSink.sha1(blackholeSink())
    hashingSink.buffer().use { sink ->
      for (hash in lines) {
        sink.writeUtf8(hash)
        sink.writeUtf8("\n")
      }
    }
    return hashingSink.hash.hex()
  }

  /** Release all resources and block until everything has been written to the file system. */
  override fun close() {
    writeRunJs()
  }

  /**
   * Emits the all runs index, which reads like JSON with an executable header.
   *
   * ```
   * window.all_runs = [
   *   "20190319153912aaab",
   *   "20190319153917bcfe"
   * ];
   * ```
   */
  private fun writeIndexJs() {
    val runNames = mutableListOf<String>()
    val runs = runsDirectory.list().sorted()
    for (run in runs) {
      if (run.endsWith(".js")) {
        runNames += run.substring(0, run.length - 3)
      }
    }

    File(rootDirectory, "index.js").writeAtomically {
      writeUtf8("window.all_runs = ")
      PaparazziJson.listOfStringsAdapter.toJson(this, runNames)
      writeUtf8(";")
    }
  }

  /**
   * Emits a run index, which reads like JSON with an executable header.
   *
   * ```
   * window.runs["20190319153912aaab"] = [
   *   {
   *     "name": "loading",
   *     "testName": "app.cash.CelebrityTest#testSettings",
   *     "timestamp": "2019-03-20T10:27:43Z",
   *     "tags": ["redesign"],
   *     "file": "loading.png"
   *   },
   *   {
   *     "name": "error",
   *     "testName": "app.cash.CelebrityTest#testSettings",
   *     "timestamp": "2019-03-20T10:27:43Z",
   *     "tags": ["redesign"],
   *     "file": "error.png"
   *   }
   * ];
   * ```
   */
  private fun writeRunJs() {
    val runJs = File(runsDirectory, "${runName.sanitizeForFilename()}.js")
    runJs.writeAtomically {
      writeUtf8("window.runs[\"$runName\"] = ")
      PaparazziJson.listOfShotsAdapter.toJson(this, shots)
      writeUtf8(";")
    }
  }

  private fun writeStaticFiles() {
    for (staticFile in listOf("index.html", "paparazzi.js")) {
      File(rootDirectory, staticFile).writeAtomically {
        writeAll(HtmlReportWriter::class.java.classLoader.getResourceAsStream(staticFile).source())
      }
    }
  }

  private fun File.writeAtomically(writerAction: BufferedSink.() -> Unit) {
    val tmpFile = File(parentFile, "$name.tmp")
    tmpFile.sink()
      .buffer()
      .use { sink ->
        sink.writerAction()
      }
    delete()
    tmpFile.renameTo(this)
  }

  private fun File.toJsonPath(): String = relativeTo(rootDirectory).invariantSeparatorsPath

  private companion object {
    /** Directory where failure deltas and actual images are written, for the Gradle test report. */
    private val failureDir: File
      get() {
        val path = System.getProperty("paparazzi.failures.dir")
          ?: error("paparazzi.failures.dir system property is required in verify mode")
        return File(path).apply { mkdirs() }
      }
  }
}

internal fun defaultRunName(): String {
  val now = Date()
  val timestamp = SimpleDateFormat("yyyyMMddHHmmss", Locale.US).format(now)
  val token = UUID.randomUUID().toString().substring(0, 6)
  return "${timestamp}_$token"
}

internal val filenameSafeChars = CharMatcher.inRange('a', 'z')
  .or(CharMatcher.inRange('0', '9'))
  .or(CharMatcher.anyOf("_-.~@^()[]{}:;,"))

internal fun String.sanitizeForFilename(): String {
  return filenameSafeChars.negate().replaceFrom(lowercase(Locale.US), '_')
}
