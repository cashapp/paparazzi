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

import app.cash.paparazzi.Flags
import java.io.Closeable
import java.io.File
import java.nio.file.Path

/**
 * A bounded, least-recently-used cache of [Sandbox] instances, keyed by the configuration that
 * produced them.
 *
 * The bound is the crux of the design, and it is the same trade-off Robolectric's `SandboxManager`
 * makes. Creating a sandbox per test would give perfect isolation and unusable performance: every
 * sandbox re-copies ~25 MB of JNI libraries, re-runs `Bridge.init`, and re-links the framework.
 * Reusing one sandbox forever would be fast and would reintroduce exactly the static-state leakage
 * this exists to remove. Caching by configuration means tests that share a configuration share a
 * sandbox — cheap — while a test that changes the configuration gets a genuinely clean one.
 *
 * Eviction closes the sandbox, which drops the loader and makes its entire class graph collectable.
 * Set too high a bound and metaspace grows without limit; this is the same failure mode as a
 * Robolectric suite with many distinct `@Config` values.
 */
internal class SandboxManager(
  private val maxSize: Int = defaultMaxSize(),
  private val stagingRoot: Path = NativeLibraryStaging.defaultStagingRoot()
) : Closeable {
  private data class Key(
    val configuration: SandboxConfiguration,
    val layoutlibRuntimeRoot: String?
  )

  // accessOrder = true, so `get` counts as a use and the eviction candidate is the least recently
  // used sandbox rather than the oldest.
  private val cache = object : LinkedHashMap<Key, Sandbox>(16, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, Sandbox>): Boolean {
      val evict = size > this@SandboxManager.maxSize
      if (evict) eldest.value.close()
      return evict
    }
  }

  /** Number of live sandboxes. */
  val size: Int
    get() = synchronized(this) { cache.size }

  /**
   * Returns the sandbox for [configuration], creating it if absent.
   *
   * @param layoutlibRuntimeRoot exploded `layoutlib-runtime` artifact. When non-null the sandbox
   *   gets its own copy of the JNI libraries and can call `Bridge.init` independently.
   */
  fun getSandbox(
    configuration: SandboxConfiguration = SandboxConfiguration.default(),
    layoutlibRuntimeRoot: File? = null
  ): Sandbox {
    val key = Key(configuration, layoutlibRuntimeRoot?.absolutePath)
    synchronized(this) {
      cache[key]?.let { return it }
      val sandbox = Sandbox.create(
        configuration = configuration,
        layoutlibRuntimeRoot = layoutlibRuntimeRoot,
        stagingRoot = stagingRoot
      )
      cache[key] = sandbox
      return sandbox
    }
  }

  override fun close() {
    synchronized(this) {
      // Each sandbox removes its own staged directory; the root is shared, so leave it in place.
      cache.values.forEach(Sandbox::close)
      cache.clear()
    }
  }

  companion object {
    /**
     * Deliberately small. Each sandbox holds a separate mapping of `libandroid_runtime`, so this
     * bounds native memory as much as it bounds metaspace.
     */
    const val DEFAULT_MAX_SIZE: Int = 3

    private fun defaultMaxSize(): Int =
      System.getProperty(Flags.SANDBOX_MAX_SIZE)?.toIntOrNull()?.coerceAtLeast(1) ?: DEFAULT_MAX_SIZE
  }
}
