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

import java.io.Closeable
import java.io.File
import java.nio.file.Path

/**
 * An isolated universe of Android framework and layoutlib classes.
 *
 * A sandbox owns a [SandboxClassLoader] and, if it was given a layoutlib runtime root, a private
 * copy of the JNI libraries. Everything reachable through [classLoader] — including
 * `com.android.layoutlib.bridge.Bridge` and its `sJniLibLoaded` flag, `android.os.Build.VERSION`,
 * `android.view.Choreographer` and `androidx.compose.ui.platform.WindowRecomposerPolicy` — is
 * private to this instance and dies with it.
 *
 * Nothing here is layoutlib-aware: the sandbox does not call `Bridge.init` itself. Initialisation
 * remains the caller's job, which keeps this class testable and lets the existing `Renderer` adopt
 * it incrementally.
 */
internal class Sandbox(
  val configuration: SandboxConfiguration,
  private val stagedNativeLibDir: File?
) : Closeable {
  val classLoader: SandboxClassLoader = SandboxClassLoader(configuration)

  private var closed = false

  /**
   * The directory to pass to `Bridge.init` as `nativeLibPath`, or null if this sandbox was created
   * without a layoutlib runtime root.
   */
  val nativeLibDir: File?
    get() = stagedNativeLibDir

  /** Loads [name] through this sandbox. Acquired names resolve to this sandbox's copy. */
  fun loadClass(name: String): Class<*> {
    check(!closed) { "Sandbox has been closed." }
    return classLoader.loadClass(name)
  }

  /**
   * Reloads [type] inside the sandbox.
   *
   * The returned class is *not* assignable to [type] unless the configuration delegates it — that
   * is the whole point. Callers interact with it reflectively, or through an interface that the
   * configuration delegates to the host.
   */
  fun bootstrappedClass(type: Class<*>): Class<*> = loadClass(type.name)

  /**
   * Runs [body] with this sandbox's loader installed as the thread context class loader, restoring
   * the previous one afterwards.
   *
   * Layoutlib and Compose both consult the TCCL in places (`ServiceLoader`, font providers), so the
   * swap matters even though the sandbox's own class resolution does not depend on it.
   */
  fun <T> runOnSandbox(body: () -> T): T {
    check(!closed) { "Sandbox has been closed." }
    val thread = Thread.currentThread()
    val previous = thread.contextClassLoader
    thread.contextClassLoader = classLoader
    try {
      return body()
    } finally {
      thread.contextClassLoader = previous
    }
  }

  /**
   * Releases the loader and the staged native libraries.
   *
   * Closing does not synchronously unload anything: the acquired classes become collectable only
   * once every reference to [classLoader] and to instances it defined is unreachable. That is a
   * property of the JVM, not of this class — the unit of unloading is the loader.
   */
  override fun close() {
    if (closed) return
    closed = true
    runCatching { classLoader.close() }
    stagedNativeLibDir?.let(NativeLibraryStaging::unstage)
  }

  override fun toString(): String = "Sandbox($classLoader)"

  companion object {
    /**
     * Creates a sandbox, staging layoutlib's JNI libraries when [layoutlibRuntimeRoot] is provided.
     *
     * Omit the runtime root for sandboxes that never call `Bridge.init` — staging copies ~25 MB.
     */
    fun create(
      configuration: SandboxConfiguration = SandboxConfiguration.default(),
      layoutlibRuntimeRoot: File? = null,
      stagingRoot: Path
    ): Sandbox {
      val staged = layoutlibRuntimeRoot?.let {
        NativeLibraryStaging.stage(NativeLibraryStaging.nativeLibDirIn(it), stagingRoot)
      }
      return Sandbox(configuration, staged)
    }
  }
}
