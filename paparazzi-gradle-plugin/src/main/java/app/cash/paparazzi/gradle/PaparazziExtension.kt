/*
 * Copyright (C) 2026 Square, Inc.
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
package app.cash.paparazzi.gradle

import org.gradle.api.provider.Property

/**
 * Configuration for the Paparazzi Gradle plugin.
 *
 * ```
 * paparazzi {
 *   // Render with an older/newer layoutlib than the one Paparazzi was built against.
 *   layoutlibVersion = "15.1.2"
 * }
 * ```
 */
public abstract class PaparazziExtension {
  /**
   * The `com.android.tools.layoutlib` version used for rendering. This controls the `layoutlib`
   * jar on the test runtime classpath as well as the `layoutlib-runtime` (native libs, fonts,
   * ICU data) and `layoutlib-resources` (framework resources) artifacts.
   *
   * Defaults to the version this Paparazzi release was built and tested against. Can also be set
   * with the `app.cash.paparazzi.layoutlibVersion` Gradle property.
   *
   * **Experimental:** Paparazzi calls into layoutlib internals (e.g. `Bridge`, `RenderSessionImpl`),
   * so versions other than the default may fail at runtime with linkage errors or render
   * differently. Only versions sharing binary-compatible internals are expected to work.
   */
  public abstract val layoutlibVersion: Property<String>
}
