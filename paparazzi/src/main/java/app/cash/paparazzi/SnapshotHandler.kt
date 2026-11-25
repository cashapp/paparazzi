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

import java.awt.image.BufferedImage
import java.io.Closeable

public interface SnapshotHandler : Closeable {
  public fun newFrameHandler(snapshot: Snapshot, frameCount: Int, fps: Int): FrameHandler

  public interface FrameHandler : Closeable {
    public fun handle(image: BufferedImage)
  }

  /**
   * A frame handler that may discard an image without paying for its preparation.
   *
   * Rendering, animation callbacks and accessibility validation still run for every frame. Only
   * image preparation (device framing and scaling) is deferred until [handle] requests the image.
   * Implementing this interface is an explicit opt-in: existing [FrameHandler] implementations,
   * including handlers that ignore their image argument, still receive prepared images eagerly.
   */
  public interface LazyFrameHandler : FrameHandler {
    /**
     * Called once for each rendered frame, even if [image] is never requested.
     *
     * [image] is valid only on the calling thread, during this call. Do not retain it or use it
     * asynchronously. Repeated requests return the same prepared image. To retain an image, request
     * it during this call and copy it: as with [FrameHandler], the renderer may reuse its buffer.
     */
    public fun handle(image: () -> BufferedImage)

    override fun handle(image: BufferedImage) {
      withLazyFrame({ image }, ::handle)
    }
  }
}

internal fun withLazyFrame(image: () -> BufferedImage, handle: (() -> BufferedImage) -> Unit) {
  val callingThread = Thread.currentThread()
  var active = true
  val preparedImage by lazy(LazyThreadSafetyMode.NONE, image)
  try {
    handle {
      check(active && Thread.currentThread() === callingThread) {
        "A frame image may only be requested synchronously during its handle call."
      }
      preparedImage
    }
  } finally {
    active = false
  }
}

public fun detectMaxPercentDifferenceDefault(): Double =
  System.getProperty("app.cash.paparazzi.maxPercentDifferenceDefault")?.toDoubleOrNull() ?: 0.01
