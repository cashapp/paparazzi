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
 * Owns the sandboxes for a JVM, one per distinct [SandboxConfiguration]. Sandboxes are never
 * evicted.
 *
 * ### Why there is no cache eviction
 *
 * A sandbox that has loaded layoutlib's JNI libraries can never be collected. `System.load` alone
 * is enough to pin it: `JNI_OnLoad` takes JNI global references to the classes it registers natives
 * for, those references pin the classes, and the classes pin the loader. `JNI_OnUnload` would
 * release them, but it only runs once the loader is collected — a cycle the JVM cannot break.
 * Measured on layoutlib 16.2.x: a bridge-initialised sandbox survives fifteen rounds of full GC
 * with allocation pressure, with or without `Bridge.dispose()`, and costs roughly 40 MB of resident
 * memory that is never returned. On top of that, `Bridge.init` memory-maps the system fonts into
 * direct byte buffers - a further ~65 MB per sandbox, also never released, which will exhaust the
 * default `MaxDirectMemorySize` well before metaspace becomes a problem.
 *
 * That makes an LRU cache actively harmful. Evicting a sandbox frees nothing, but it throws away
 * the ability to reuse it, so the next request allocates *another* 40 MB. Reuse is therefore
 * mandatory rather than an optimisation, and this class simply never lets go.
 *
 * ### What the limit is for
 *
 * [limit] is not a cache size — it is a cap on how many times layoutlib may be instantiated before
 * the JVM is leaking more than it should. Exceeding it fails loudly rather than silently degrading,
 * because the alternative is an out-of-memory kill much later with no obvious cause. Isolation
 * beyond this point needs a process boundary: Gradle's `forkEvery`, which the OS reclaims properly.
 */
internal class SandboxManager(
  private val limit: Int = defaultLimit(),
  private val stagingRoot: Path = NativeLibraryStaging.defaultStagingRoot()
) : Closeable {
  private data class Key(
    val configuration: SandboxConfiguration,
    val layoutlibRuntimeRoot: String?
  )

  private val sandboxes = linkedMapOf<Key, Sandbox>()

  /** Number of live sandboxes. */
  val size: Int
    get() = synchronized(this) { sandboxes.size }

  /**
   * Returns the sandbox for [configuration], creating it on first use.
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
      sandboxes[key]?.let { return it }

      check(sandboxes.size < limit) {
        "Refusing to create more than $limit layoutlib sandboxes in one JVM (requested a " +
          "${sandboxes.size + 1}th for $configuration).\n" +
          "Each sandbox permanently retains roughly 40 MB: layoutlib's JNI_OnLoad takes global " +
          "references that pin its class loader forever, so closing one frees nothing.\n" +
          "Either reduce the number of distinct sandbox configurations, or isolate with a process " +
          "boundary instead by setting `forkEvery` on the test task. The limit can be raised with " +
          "-D${Flags.SANDBOX_LIMIT}=<n> if you understand the cost."
      }

      val sandbox = Sandbox.create(
        configuration = configuration,
        layoutlibRuntimeRoot = layoutlibRuntimeRoot,
        stagingRoot = stagingRoot
      )
      sandboxes[key] = sandbox
      return sandbox
    }
  }

  /**
   * Closes every sandbox.
   *
   * This releases the staged native libraries from disk and prevents further class loading, but it
   * does not and cannot reclaim the memory layoutlib has pinned.
   */
  override fun close() {
    synchronized(this) {
      // Each sandbox removes its own staged directory; the root is shared, so leave it in place.
      sandboxes.values.forEach(Sandbox::close)
      sandboxes.clear()
    }
  }

  companion object {
    /**
     * Deliberately small. Raising this trades ~40 MB of unreclaimable memory and ~1.3 s of startup
     * per additional configuration.
     */
    const val DEFAULT_LIMIT: Int = 3

    private fun defaultLimit(): Int =
      System.getProperty(Flags.SANDBOX_LIMIT)?.toIntOrNull()?.coerceAtLeast(1) ?: DEFAULT_LIMIT
  }
}
