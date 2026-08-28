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

package app.cash.paparazzi.internal

/**
 * Per-sandbox settings injected by the host before any test runs.
 *
 * This class is *acquired*, so each sandbox gets its own copy and its own static state. The host
 * writes to it reflectively through the sandbox's class loader; nothing here may be read before
 * that write happens.
 *
 * It exists because the one input `Renderer` cannot take from a system property is the native
 * library directory: system properties are process-global, but each sandbox must load its own
 * private copy of layoutlib's JNI libraries from its own path.
 */
internal object SandboxRuntime {
  /**
   * Directory holding this sandbox's private copy of layoutlib's JNI libraries, or null when
   * running outside a sandbox, in which case `Renderer` falls back to the shared runtime root.
   */
  @JvmStatic
  var nativeLibDir: String? = null
}
