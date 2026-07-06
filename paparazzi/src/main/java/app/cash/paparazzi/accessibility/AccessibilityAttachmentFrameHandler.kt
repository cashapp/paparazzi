/*
 * Copyright (C) 2026 Square, Inc.
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
package app.cash.paparazzi.accessibility

import app.cash.paparazzi.Snapshot
import app.cash.paparazzi.SnapshotHandler.FrameHandler
import app.cash.paparazzi.toFileName
import java.awt.image.BufferedImage
import java.io.File

/**
 * Decorates a [FrameHandler] so that, in native report mode, each rendered
 * frame also produces an SVG side-car attachment composed from any
 * accessibility elements stashed by [AccessibilityRenderExtension].
 *
 * The decorator is a no-op when:
 *  - `paparazzi.reportType` is not `native`
 *  - `paparazzi.attachments.dir` is unset
 *  - no accessibility elements were stashed (the test isn't using
 *    AccessibilityRenderExtension)
 *
 * Files are written as `a11y-<package>_<class>_<method>[_<label>].svg`. The
 * JUnit Platform engine wrapper scans this directory at test finish and emits
 * each matching file via `fileEntryPublished`, surfacing it in the Gradle 9.4+
 * attachments tab.
 */
internal class AccessibilityAttachmentFrameHandler(
  private val delegate: FrameHandler,
  private val snapshot: Snapshot
) : FrameHandler {

  override fun handle(image: BufferedImage) {
    writeSvgIfActive(image)
    delegate.handle(image)
  }

  override fun close() {
    delegate.close()
  }

  private fun writeSvgIfActive(image: BufferedImage) {
    if (System.getProperty(SYSTEM_PROPERTY_REPORT_TYPE) != REPORT_TYPE_NATIVE) return
    val attachmentsDir = System.getProperty(SYSTEM_PROPERTY_ATTACHMENTS_DIR) ?: return
    val elements = CollectedAccessibilityElements.consume() ?: return
    if (elements.isEmpty()) return

    val svg = AccessibilitySvgComposer.compose(image, elements)
    val outputDir = File(attachmentsDir).apply { mkdirs() }
    val fileName = "a11y-" + snapshot.toFileName(extension = "svg")
    File(outputDir, fileName).writeText(svg)
  }

  private companion object {
    const val SYSTEM_PROPERTY_REPORT_TYPE = "paparazzi.reportType"
    const val SYSTEM_PROPERTY_ATTACHMENTS_DIR = "paparazzi.attachments.dir"
    const val REPORT_TYPE_NATIVE = "native"
  }
}
