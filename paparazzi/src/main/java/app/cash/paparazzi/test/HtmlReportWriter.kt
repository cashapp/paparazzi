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
package app.cash.paparazzi.test

import app.cash.paparazzi.files.writeAtomically
import app.cash.paparazzi.files.writeImage
import app.cash.paparazzi.files.writeVideo
import app.cash.paparazzi.formatImage
import app.cash.paparazzi.internal.PaparazziJson
import app.cash.paparazzi.snapshotter.Clip
import app.cash.paparazzi.snapshotter.Snapshot
import com.google.common.base.CharMatcher
import okio.source
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.runBlocking

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
 * index.html
 * index.js
 * paparazzi.js
 * ```
 */
class HtmlReportWriter @JvmOverloads constructor(
  private val runName: String = defaultRunName(),
  private val rootDirectory: File = File(System.getProperty("paparazzi.report.dir")),
  snapshotRootDirectory: File = File(System.getProperty("paparazzi.snapshot.dir"))
) : TestSnapshotHandler {
  private val runsDirectory: File = File(rootDirectory, "runs")
  private val imagesDirectory: File = File(rootDirectory, "images")
  private val videosDirectory: File = File(rootDirectory, "videos")

  private val goldenImagesDirectory = File(snapshotRootDirectory, "images")
  private val goldenVideosDirectory = File(snapshotRootDirectory, "videos")

  private val shots = mutableListOf<TestRecord>()

  private val isRecording: Boolean =
    System.getProperty("paparazzi.test.record")?.toBoolean() == true

  init {
    runsDirectory.mkdirs()
    imagesDirectory.mkdirs()
    videosDirectory.mkdirs()
    writeStaticFiles()
    writeRunJs()
    writeIndexJs()
  }

  override fun handleSnapshot(snapshot: Snapshot, testRecord: TestRecord) {
    val formattedImage = snapshot.image.formatImage(snapshot.spec)
    val imageHash = writeImage(formattedImage, imagesDirectory)

    val original = File(imagesDirectory, "$imageHash.png")
    if (isRecording) {
      val goldenFile = File(goldenImagesDirectory, testRecord.toFileName("_", "png"))
      original.copyTo(goldenFile, overwrite = true)
    }

    shots += testRecord.copy(file = original.toJsonPath())
  }

  override fun handleClip(clip: Clip, testRecord: TestRecord) {
    val hashes = mutableListOf<String>()

    runBlocking {
      clip.images.collect { image ->
        val formattedImage = image.formatImage(clip.spec.frameSpec)
        hashes += writeImage(formattedImage, imagesDirectory)
      }
    }

    if (hashes.isEmpty()) return

    val hash = writeVideo(hashes, clip.spec.fps, imagesDirectory, videosDirectory)

    if (isRecording) {
      for ((index, frameHash) in hashes.withIndex()) {
        val originalFrame = File(imagesDirectory, "$frameHash.png")
        val frameSnapshot = testRecord.copy(name = "${testRecord.name} $index")
        val goldenFile = File(goldenImagesDirectory, frameSnapshot.toFileName("_", "png"))
        if (!goldenFile.exists()) {
          originalFrame.copyTo(goldenFile)
        }
      }
    }
    val original = File(videosDirectory, "$hash.mov")
    if (isRecording) {
      val goldenFile = File(goldenVideosDirectory, testRecord.toFileName("_", "mov"))
      if (!goldenFile.exists()) {
        original.copyTo(goldenFile)
      }
    }
    shots += testRecord.copy(file = original.toJsonPath())
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

internal fun String.sanitizeForFilename(): String? {
  return filenameSafeChars.negate().replaceFrom(toLowerCase(Locale.US), '_')
}
