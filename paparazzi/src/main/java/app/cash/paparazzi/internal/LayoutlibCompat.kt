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
package app.cash.paparazzi.internal

import com.android.layoutlib.bridge.Bridge
import java.lang.reflect.Method

/**
 * Bridges layoutlib internal API differences so Paparazzi can run against layoutlib versions other
 * than the one it was compiled against (see `paparazzi { layoutlibVersion = ... }`).
 *
 * Any layoutlib internal whose signature varies between supported versions should be accessed
 * through here rather than referenced directly.
 */
internal object LayoutlibCompat {
  // Removed in layoutlib 16.2.4; RenderAction now prepares/cleans up the main Looper itself.
  private val prepareThread: Method? = Bridge::class.java.staticMethodOrNull("prepareThread")
  private val cleanupThread: Method? = Bridge::class.java.staticMethodOrNull("cleanupThread")

  fun prepareThread() {
    prepareThread?.invoke(null)
  }

  fun cleanupThread() {
    cleanupThread?.invoke(null)
  }

  private fun Class<*>.staticMethodOrNull(name: String): Method? =
    try {
      getMethod(name)
    } catch (_: NoSuchMethodException) {
      null
    }
}
