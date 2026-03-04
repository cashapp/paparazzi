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
import java.io.File

/**
 * Handles writing snapshot output files. Implementations receive rendered frames via
 * [FrameHandler.handle] and produce output files (e.g. PNG images or animated PNGs).
 *
 * @see HtmlReportWriter
 * @see SnapshotVerifier
 */
public interface SnapshotHandler : Closeable {
  /** Creates a new [FrameHandler] for the given [snapshot]. */
  public fun newFrameHandler(snapshot: Snapshot, frameCount: Int, fps: Int): FrameHandler

  /**
   * Receives rendered frames for a single snapshot or gif and produces an output file.
   */
  public interface FrameHandler : Closeable {
    /** Receives a single rendered frame. */
    public fun handle(image: BufferedImage)

    /**
     * The output file produced by this handler, available after [close].
     * For single-frame snapshots this is the PNG image; for animations this is the animated PNG.
     * Returns `null` if this handler does not produce an output file (e.g. during verification).
     */
    public val outputFile: File? get() = null
  }
}

public fun detectMaxPercentDifferenceDefault(): Double =
  System.getProperty("app.cash.paparazzi.maxPercentDifferenceDefault")?.toDoubleOrNull() ?: 0.01
