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
package com.squareup.cash.screenshot.jvm

import com.google.common.base.CharMatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.runBlocking
import okio.BufferedSink
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

internal class RunWriter(
  private val runName: String = defaultRunName(),
  private val rootDirectory: File = File("build/reports/paparazzi")
) : SnapshotHandler {
  private val runDirectory = File(rootDirectory, runName.sanitizeForFilename())
  private val shots = mutableListOf<Shot>()
  private val queue = Channel<Pair<Shot, BufferedImage>>(UNLIMITED)
  private var writerDeferred = GlobalScope.async(Dispatchers.IO) {
    writeLoop()
  }

  /** Enqueue shot for writing. */
  override fun add(
    shot: Shot,
    image: BufferedImage
  ) {
    runBlocking {
      queue.send(shot to image)
    }
  }

  /** Release all resources and block until everything has been written to the file system. */
  override fun close() {
    queue.close()

    runBlocking {
      writerDeferred.join()
    }
  }

  /** Write shots from the queue. */
  private suspend fun writeLoop() {
    runDirectory.mkdirs()
    writeStaticFiles()
    writeRunJs()
    writeIndexJs()

    while (true) {
      val (shot, bufferedImage) = queue.receiveOrNull() ?: break

      val file = File(runDirectory, shot.name.sanitizeForFilename() + ".png")
      file.writeAtomically(bufferedImage)

      shots += shot.copy(file = file.name)

      // Flush the index unless we're immediately about to update it.
      if (queue.isEmpty) {
        writeRunJs()
      }
    }

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
    for (dir in rootDirectory.listFiles().sorted()) {
      if (File(dir, "run.js").exists()) {
        runNames += dir.name
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
   *     "testName": "com.squareup.paparazzi.CelebrityTest#testSettings",
   *     "timestamp": "2019-03-20T10:27:43Z",
   *     "tags": ["redesign"],
   *     "file": "loading.png"
   *   },
   *   {
   *     "name": "error",
   *     "testName": "com.squareup.paparazzi.CelebrityTest#testSettings",
   *     "timestamp": "2019-03-20T10:27:43Z",
   *     "tags": ["redesign"],
   *     "file": "error.png"
   *   }
   * ];
   * ```
   */
  private fun writeRunJs() {
    File(runDirectory, "run.js").writeAtomically {
      writeUtf8("window.runs[\"$runName\"] = ")
      PaparazziJson.listOfShotsAdapter.toJson(this, shots)
      writeUtf8(";")
    }
  }

  private fun writeStaticFiles() {
    val loader = RunWriter::class.java.classLoader
    File(rootDirectory, "index.html").writeAtomically {
      writeAll(loader.getResourceAsStream("index.html").source())
    }
    File(rootDirectory, "paparazziRenderer.js").writeAtomically {
      writeAll(loader.getResourceAsStream("paparazziRenderer.js").source())
    }
  }

  private fun File.writeAtomically(bufferedImage: BufferedImage) {
    val tmpFile = File(parentFile, "$name.tmp")
    ImageIO.write(bufferedImage, "PNG", tmpFile)
    tmpFile.renameTo(this)
  }

  private fun File.writeAtomically(writerAction: BufferedSink.() -> Unit) {
    val tmpFile = File(parentFile, "$name.tmp")
    tmpFile.sink()
        .buffer()
        .use { sink ->
          sink.writerAction()
        }
    tmpFile.renameTo(this)
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

internal fun String.sanitizeForFilename(): String? {
  return filenameSafeChars.negate().replaceFrom(toLowerCase(Locale.US), '_')
}

