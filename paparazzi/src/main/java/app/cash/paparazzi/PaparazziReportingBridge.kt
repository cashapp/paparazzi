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
package app.cash.paparazzi

import java.io.File

/**
 * Static thread-local bridge between [Paparazzi] (JUnit 4) and the
 * `paparazzi-junit4-reporting` engine module.
 *
 * When `PaparazziVintageTestEngine` is on the classpath, it sets
 * [currentPublisher] to a callback that emits a `fileEntryPublished` event on
 * the JUnit Platform [EngineExecutionListener] for the currently-running test.
 * [Paparazzi.reportOutputFile] calls [publishFile] after every snapshot or gif,
 * so snapshots are automatically attached to Gradle's HTML test report.
 *
 * When the engine module is absent (JUnit 4 without the wrapper, or JUnit 5
 * via [PaparazziExtension]), [currentPublisher] is null and this is a no-op.
 */
public object PaparazziReportingBridge {
  /**
   * A per-thread callback installed by `PaparazziVintageTestEngine` at the
   * start of each leaf test and removed when the test finishes.
   */
  public val currentPublisher: ThreadLocal<((File) -> Unit)?> = ThreadLocal.withInitial { null }

  public fun publishFile(file: File) {
    currentPublisher.get()?.invoke(file)
  }
}
