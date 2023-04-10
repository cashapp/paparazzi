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
import app.cash.paparazzi.internal.ImageUtils
import app.cash.paparazzi.internal.writeImage
import okio.HashingSink
import okio.blackholeSink
import okio.buffer
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

class SnapshotVerifier @JvmOverloads constructor(
  private val maxPercentDifference: Double,
  rootDirectory: File = File("src/test/snapshots")
) : SnapshotHandler {
  private val imagesDirectory: File = File(rootDirectory, "images")
  private val videosDirectory: File = File(rootDirectory, "videos")
  private val verificationDirectory: File = File(rootDirectory, "verificationImages")

  init {
    imagesDirectory.mkdirs()
    videosDirectory.mkdirs()
    verificationDirectory.mkdir()
  }

  override fun newFrameHandler(
    snapshot: Snapshot,
    frameCount: Int,
    fps: Int
  ): FrameHandler {
    return object : FrameHandler {
      val hashes = mutableListOf<String>()

      override fun handle(image: BufferedImage) {
        hashes += image.writeImage(verificationDirectory)
      }

      override fun close() {
        if (hashes.size == 1) {
          val original = File(verificationDirectory, "${hashes[0]}.png")
          if (!original.exists()) {
            throw AssertionError("File $original does not exist")
          }
          val image = ImageIO.read(original)

          val expected = File(imagesDirectory, snapshot.toFileName(extension = "png"))
          if (!expected.exists()) {
            throw AssertionError("File $expected does not exist")
          }

          val goldenImage = ImageIO.read(expected)
          ImageUtils.assertImageSimilar(
            relativePath = expected.path,
            image = image,
            goldenImage = goldenImage,
            maxPercentDifferent = maxPercentDifference
          )
        } else {
          for ((index, frameHash) in hashes.withIndex()) {
            val original = File(verificationDirectory, "${frameHash}.png")
            if (!original.exists()) {
              throw AssertionError("File $original does not exist")
            }
            val image = ImageIO.read(original)

            val frameSnapshot = snapshot.copy(name = "${snapshot.name} $index")
            val goldenFile = File(imagesDirectory, frameSnapshot.toFileName("_", "png"))
            if (!goldenFile.exists()) {
              throw AssertionError("File $goldenFile does not exist")
            }

            val goldenImage = ImageIO.read(goldenFile)
            ImageUtils.assertImageSimilar(
              relativePath = goldenFile.path,
              image = image,
              goldenImage = goldenImage,
              maxPercentDifferent = maxPercentDifference
            )
          }
        }
      }
    }
  }

  override fun close() {
    // verification directory is only for storing the comparison snapshots so clean it up.
    verificationDirectory.deleteRecursively()
  }
}
