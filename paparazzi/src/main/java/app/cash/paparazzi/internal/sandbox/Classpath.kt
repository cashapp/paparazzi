/*
 * Copyright (C) 2024 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.cash.paparazzi.internal.sandbox

import java.io.File
import java.net.URL
import java.nio.file.Paths

/** Discovers the URLs a [SandboxClassLoader] should search. */
internal object Classpath {
  /**
   * The current JVM's application classpath, as URLs.
   *
   * Under Gradle this is the test worker's classpath and already contains layoutlib.jar, so the
   * sandbox needs no separate dependency resolution — unlike Robolectric, which downloads
   * `android-all` at runtime because it is deliberately absent from the app classpath.
   */
  fun current(): List<URL> =
    System.getProperty("java.class.path")
      .orEmpty()
      .split(File.pathSeparator)
      .filter { it.isNotBlank() }
      .map { Paths.get(it).toUri().toURL() }
}
