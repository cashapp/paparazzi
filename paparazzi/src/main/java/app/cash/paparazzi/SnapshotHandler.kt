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

import app.cash.paparazzi.internal.ImageUtils
import java.awt.image.BufferedImage
import java.io.Closeable
import java.io.File

interface SnapshotHandler : Closeable {
    fun newFrameHandler(
            snapshot: Snapshot,
            frameCount: Int,
            fps: Int
    ): FrameHandler

    interface FrameHandler : Closeable {
        fun handleTestFrame(image: BufferedImage)
    }
}

internal interface TestMediaWriter: Closeable {
  fun writeSnapshotFrame(image: BufferedImage)

  /**
   * returns the generated media file
   */
  fun finishSnapshot(snapshot: Snapshot, fps: Int): File
}

interface MediaVerifier {
  fun verify(snapshot: Snapshot, generatedImage: File)
}

internal class PaparazziTestMediaHandler(
        private val mediaWriter: TestMediaWriter,
        private val mediaVerifier: MediaVerifier) : SnapshotHandler {

    override fun newFrameHandler(
            snapshot: Snapshot,
            frameCount: Int,
            fps: Int
    ): SnapshotHandler.FrameHandler {
        return object : SnapshotHandler.FrameHandler {

            override fun handleTestFrame(image: BufferedImage) {
                mediaWriter.writeSnapshotFrame(image)
            }

            override fun close() {
                mediaWriter.finishSnapshot(snapshot, fps).also { generatedImage ->
                  mediaVerifier.verify(snapshot, generatedImage)
                }
            }
        }
    }

    override fun close() {
        mediaWriter.close()
    }
}
