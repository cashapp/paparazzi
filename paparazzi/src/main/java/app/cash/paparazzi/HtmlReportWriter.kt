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
import app.cash.paparazzi.internal.apng.ApngWriter
import com.google.common.base.CharMatcher
import com.google.common.io.Files
import okio.BufferedSink
import okio.HashingSink
import okio.Path.Companion.toPath
import okio.blackholeSink
import okio.buffer
import okio.sink
import okio.source
import java.awt.image.BufferedImage
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
 * suites
 *   app.cash.CelebrityTest.html
 *   app.cash.HomeViewTest.html
 * index.html
 * index.js
 * paparazzi.js
 * suites.js
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
  private val suitesDirectory: File = File(rootDirectory, "suites")

  private val goldenImagesDirectory = File(snapshotRootDirectory, "images")
  private val goldenVideosDirectory = File(snapshotRootDirectory, "videos")

  private val shots = mutableListOf<Snapshot>()

  private val isRecording: Boolean =
    System.getProperty("paparazzi.test.record")?.toBoolean() == true
  private val overwriteOnMaxPercentDifference: Boolean =
    System.getProperty("paparazzi.test.record.overwriteOnMaxPercentDifference")?.toBoolean() == true

  init {
    runsDirectory.mkdirs()
    imagesDirectory.mkdirs()
    videosDirectory.mkdirs()
    suitesDirectory.mkdirs()
    writeStaticFiles()
    writeRunJs()
    writeIndexJs()
    writeSuitesJs()
  }

  override fun newFrameHandler(snapshot: Snapshot, frameCount: Int, fps: Int): FrameHandler {
    return object : FrameHandler {
      val snapshotDir = if (fps == -1) imagesDirectory else videosDirectory
      val goldenDir = if (fps == -1) goldenImagesDirectory else goldenVideosDirectory
      val hashes = mutableListOf<String>()
      val snapshotTmpFile = File(snapshotDir, snapshot.toFileName(extension = "temp.png"))
      val writer = ApngWriter(snapshotTmpFile.path.toPath(), fps)

      override fun handle(image: BufferedImage) {
        writer.writeImage(image)
        hashes += hash(image)
      }

      override fun close() {
        if (hashes.isEmpty()) return
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

        shots += snapshot.copy(file = snapshotFile.toJsonPath())
      }
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
    writeSuites()
    writeSuitesJs()
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

  /**
   * Emits one HTML page per test suite, so that large reports don't end up as a single cluttered page.
   *
   * Each suite page loads the shared renderer and the run data, and only renders the snapshots
   * belonging to its suite. Pages are written once: their content doesn't depend on the run, so
   * later runs skip suites that were already emitted.
   */
  private fun writeSuites() {
    val suites = shots.map { it.testName.suiteName() }.distinct().sorted()
    if (suites.isEmpty()) return
    val template = HtmlReportWriter::class.java.classLoader
      .getResourceAsStream("suite.html")
      .source()
      .buffer()
      .readUtf8()
    for (suite in suites) {
      var suiteFile = File(suitesDirectory, "${suite.sanitizeForSuiteFilename()}.html")
      if (suiteFile.exists() && !suiteFile.wasWrittenFor(suite)) {
        // A different suite already claimed this file name, e.g. two suites differing only by
        // case on a case-insensitive file system. Disambiguate with a hash of the raw name so
        // no suite is ever dropped.
        suiteFile = File(suitesDirectory, "${suiteFile.name.substringBeforeLast('.')}-${hashOf(suite).take(8)}.html")
      }
      if (suiteFile.exists()) continue
      suiteFile.writeAtomically {
        writeUtf8(template.replace("__SUITE_NAME__", suite))
      }
    }
  }

  private fun File.wasWrittenFor(suite: String): Boolean = readText().contains("""window.suite = "$suite";""")

  /**
   * Emits the suites index, which reads like JSON with an executable header.
   *
   * ```
   * window.all_suites = [
   *   "app.cash.CelebrityTest",
   *   "app.cash.HomeViewTest"
   * ];
   * ```
   */
  private fun writeSuitesJs() {
    val suiteNames = mutableListOf<String>()
    val suites = suitesDirectory.list().sorted()
    for (suite in suites) {
      if (suite.endsWith(".html")) {
        suiteNames += suite.substring(0, suite.length - ".html".length)
      }
    }

    File(rootDirectory, "suites.js").writeAtomically {
      writeUtf8("window.all_suites = ")
      PaparazziJson.listOfStringsAdapter.toJson(this, suiteNames)
      writeUtf8(";")
    }
  }

  private fun File.writeAtomically(writerAction: BufferedSink.() -> Unit) {
    // Use a unique temp name and an atomic move so concurrent writers sharing the report
    // directory never clobber each other's temp files or leave the target missing.
    val tmpFile = File(parentFile, "$name.${UUID.randomUUID()}.tmp")
    tmpFile.sink()
      .buffer()
      .use { sink ->
        sink.writerAction()
      }
    try {
      java.nio.file.Files.move(
        tmpFile.toPath(),
        toPath(),
        java.nio.file.StandardCopyOption.ATOMIC_MOVE,
        java.nio.file.StandardCopyOption.REPLACE_EXISTING
      )
    } catch (_: java.io.IOException) {
      java.nio.file.Files.move(
        tmpFile.toPath(),
        toPath(),
        java.nio.file.StandardCopyOption.REPLACE_EXISTING
      )
    }
  }

  private fun File.toJsonPath(): String = relativeTo(rootDirectory).invariantSeparatorsPath
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

private fun TestName.suiteName(): String = "$packageName.$className"

/** Characters allowed in a test suite's file name: Java identifiers ('$' included) plus '.'. */
private val suiteFilenameSafeChars = filenameSafeChars
  .or(CharMatcher.inRange('A', 'Z'))
  .or(CharMatcher.anyOf("$"))

/**
 * Maps a test suite name to a file name. Names made only of filename-safe characters map to
 * themselves; anything else is escaped and suffixed with a hash of the original name so two
 * distinct suites are highly unlikely to collide on the same file.
 */
internal fun String.sanitizeForSuiteFilename(): String {
  val sanitized = suiteFilenameSafeChars.negate().replaceFrom(this, '_')
  return if (sanitized == this) this else "$sanitized-${hashOf(this).take(8)}"
}

private fun hashOf(value: String): String {
  val hashingSink = HashingSink.sha1(blackholeSink())
  hashingSink.buffer().use { sink ->
    sink.writeUtf8(value)
  }
  return hashingSink.hash.hex()
}
