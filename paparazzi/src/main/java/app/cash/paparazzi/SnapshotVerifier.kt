/*
 * Copyright (C) 2020 Square, Inc.
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

import app.cash.paparazzi.Differ
import app.cash.paparazzi.SnapshotHandler.FrameHandler
import app.cash.paparazzi.internal.ImageUtils
import app.cash.paparazzi.internal.apng.ApngVerifier
import app.cash.paparazzi.internal.differs.DeltaE2000
import app.cash.paparazzi.internal.differs.Flip
import app.cash.paparazzi.internal.differs.Mssim
import app.cash.paparazzi.internal.differs.OffByTwo
import app.cash.paparazzi.internal.differs.PixelPerfect
import app.cash.paparazzi.internal.differs.Sift
import com.squareup.moshi.JsonReader
import okio.Buffer
import okio.Path.Companion.toOkioPath
import java.awt.image.BufferedImage
import java.awt.image.BufferedImage.TYPE_INT_ARGB
import java.io.File
import javax.imageio.ImageIO

public class SnapshotVerifier @JvmOverloads constructor(
  private val maxPercentDifference: Double,
  rootDirectory: File = File(System.getProperty("paparazzi.snapshot.dir")),
  private val differ: Differ = determineDiffer()
) : SnapshotHandler {
  private val imagesDirectory: File = File(rootDirectory, "images")
  private val videosDirectory: File = File(rootDirectory, "videos")
  private val artifactsDirectory: File = File(rootDirectory, ARTIFACTS_DIRECTORY_NAME)

  init {
    imagesDirectory.mkdirs()
    videosDirectory.mkdirs()
    artifactsDirectory.mkdirs()
  }

  override fun newFrameHandler(snapshot: Snapshot, frameCount: Int, fps: Int): FrameHandler {
    return object : FrameHandler {
      val snapshotDir = if (fps == -1) imagesDirectory else videosDirectory
      val expected = File(snapshotDir, snapshot.toFileName(extension = "png"))
      val failurePath = File(failureDir, "delta-${expected.name}").toOkioPath()
      val pngVerifier: ApngVerifier? = if (fps != -1) {
        ApngVerifier(expected.toOkioPath(), failurePath, fps, frameCount, maxPercentDifference, differ = differ)
      } else {
        null
      }

      override fun handle(image: BufferedImage) {
        if (pngVerifier != null) {
          pngVerifier.verifyFrame(image)
          return
        }

        val goldenImage = if (!expected.exists()) {
          // Stub image for comparison and to proceed with failure output
          BufferedImage(image.width, image.height, TYPE_INT_ARGB)
        } else {
          ImageIO.read(expected)
        } ?: throw NullPointerException(
          """
          Failed to read the snapshot file from the file system.

          If your project uses git LFS, it's possible that it's misconfigured on your machine and
          Paparazzi has just loaded a pointer file instead of the real snapshot file. Follow git
          LFS troubleshooting instructions and try again.

          """.trimIndent()
        )
        ImageUtils.assertImageSimilar(
          relativePath = expected.path,
          image = image,
          goldenImage = goldenImage,
          maxPercentDifferent = maxPercentDifference,
          failureDir = failureDir,
          differ = differ
        )
      }

      override fun close() {
        try {
          pngVerifier?.assertFinished()
        } finally {
          pngVerifier?.close()
        }
      }

      override fun handleArtifact(name: String, content: String) {
        val expected = snapshot.artifactFile(name, artifactsDirectory)
        val failureFileBaseName = "${expected.parentFile.name.sanitizeForFilename()}-${expected.name}"
        val actualFile = File(failureDir, "actual-$failureFileBaseName")
        val diffFile = File(failureDir, "diff-$failureFileBaseName.txt")
        if (!expected.exists()) {
          actualFile.writeAtomically(content)
          diffFile.writeAtomically(
            buildString {
              appendLine("Golden artifact not found for '$name'.")
              appendLine("Expected file: ${expected.path}")
              appendLine("Actual file: ${actualFile.path}")
              appendLine()
              appendLine("Actual:")
              appendLine(content)
            }
          )
          throw AssertionError(
            "Golden artifact '$name' not found at ${expected.path}. " +
              "See ${actualFile.path} and ${diffFile.path}."
          )
        }

        val expectedContent = expected.readText()
        val expectedJson = expectedContent.parseJsonSafely()
        val actualJson = content.parseJsonSafely()
        if (expectedJson == actualJson) return

        actualFile.writeAtomically(content)
        diffFile.writeAtomically(
          buildString {
            appendLine("Artifact mismatch for '$name'.")
            appendLine("Expected file: ${expected.path}")
            appendLine()
            appendLine("Expected:")
            appendLine(expectedContent)
            appendLine()
            appendLine("Actual:")
            appendLine(content)
          }
        )

        throw AssertionError(
          "Artifact '$name' mismatch for ${expected.path}. " +
            "See ${actualFile.path} and ${diffFile.path}."
        )
      }
    }
  }

  override fun close(): Unit = Unit

  private fun String.parseJsonSafely(): Any? {
    return try {
      JsonReader.of(Buffer().writeUtf8(this)).readJsonValue()
    } catch (_: Exception) {
      this
    }
  }

  private fun File.writeAtomically(content: String) {
    val tmpFile = File(parentFile, "$name.tmp")
    tmpFile.writeText(content)
    delete()
    tmpFile.renameTo(this)
  }

  private companion object {
    /** Directory where to write the thumbnails and deltas. */
    private val failureDir: File
      get() {
        val buildDirString = System.getProperty("paparazzi.build.dir")
        val failureDir = File(buildDirString, "paparazzi/failures")
        failureDir.mkdirs()
        return failureDir
      }
  }
}

internal fun determineDiffer() =
  System.getProperty("app.cash.paparazzi.differ")?.lowercase().let { differ ->
    when (differ) {
      "offbytwo" -> OffByTwo
      "pixelperfect" -> PixelPerfect
      "mssim" -> Mssim
      "sift" -> Sift
      "flip" -> Flip
      "de2000" -> DeltaE2000
      null, "", "default" -> OffByTwo
      else -> error("Unknown differ type '$differ'.")
    }
  }
