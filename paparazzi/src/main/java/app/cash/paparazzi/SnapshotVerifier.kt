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
import app.cash.paparazzi.internal.VideoUtils
import org.jcodec.api.awt.AWTSequenceEncoder
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

class SnapshotVerifier @JvmOverloads constructor(
  private val maxPercentDifference: Double,
  rootDirectory: File = File("src/test/snapshots"),
) : SnapshotHandler {
  private val imagesDirectory: File = File(rootDirectory, "images")
  private val videosDirectory: File = File(rootDirectory, "videos")

  init {
    imagesDirectory.mkdirs()
    videosDirectory.mkdirs()
  }

  override fun newFrameHandler(
    snapshot: Snapshot,
    frameCount: Int,
    fps: Int
  ): FrameHandler {
    return object : FrameHandler {

      val tempVideo: File by lazy { File(videosDirectory, snapshot.toFileName(extension = "tmp.mov")) }
      val encoder: AWTSequenceEncoder by lazy { AWTSequenceEncoder.createSequenceEncoder(tempVideo, fps) }

      override fun handle(image: BufferedImage) {
        if (fps == -1) {
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
          encoder.encodeImage(image)
        }
      }

      override fun close() {
        if (fps != -1) {
          encoder.finish()

          try {
            val expected = File(videosDirectory, snapshot.toFileName(extension = "mov"))
            if (!expected.exists()) {
              throw AssertionError("File $expected does not exist")
            }

            VideoUtils.assertVideoSimilar(
              goldenVideo = expected,
              testVideo = tempVideo,
              maxPercentDifferent = maxPercentDifference
            )
          } finally {
            tempVideo.delete()
          }
        }
      }
    }
  }

  override fun close() = Unit
}