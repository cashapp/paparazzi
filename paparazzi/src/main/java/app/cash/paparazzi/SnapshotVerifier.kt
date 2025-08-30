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

import app.cash.paparazzi.SnapshotHandler.FrameHandler
import app.cash.paparazzi.internal.Differ
import app.cash.paparazzi.internal.ImageUtils
import app.cash.paparazzi.internal.apng.ApngVerifier
import okio.Path.Companion.toOkioPath
import java.awt.image.BufferedImage
import java.awt.image.BufferedImage.TYPE_INT_ARGB
import java.io.File
import javax.imageio.ImageIO

public class SnapshotVerifier @JvmOverloads constructor(
  private val maxPercentDifference: Double,
  private val withExpectedActualLabels: Boolean = true,
  rootDirectory: File = File(System.getProperty("paparazzi.snapshot.dir")),
  private val differ: Differ
) : SnapshotHandler {
  private val imagesDirectory: File = File(rootDirectory, "images")
  private val videosDirectory: File = File(rootDirectory, "videos")

  init {
    imagesDirectory.mkdirs()
    videosDirectory.mkdirs()
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
          withExpectedActualLabels = withExpectedActualLabels,
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
    }
  }

  override fun close(): Unit = Unit

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
