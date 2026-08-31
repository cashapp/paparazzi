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
  private val layoutlibRuntimeRoot: File?,
  private val stagingRoot: Path
) : Closeable {
  val classLoader: SandboxClassLoader = SandboxClassLoader(configuration)

  private var closed = false
  private var staged: NativeLibraryStaging.Staged? = null

  /**
   * The directory to pass to `Bridge.init` as `nativeLibPath`, or null if this sandbox was created
   * without a layoutlib runtime root.
   *
   * Staged on first access rather than at construction. Copying layoutlib's JNI libraries costs
   * ~26 MB and a few hundred milliseconds, and a sandbox that never renders never needs them — so
   * a sandbox used only to reload classes pays nothing. It also means the platform checks in
   * [NativeLibraryStaging] fire when layoutlib is actually about to be loaded, rather than
   * pre-emptively.
   */
  @get:Synchronized
  val nativeLibDir: File?
    get() = stage()?.directory

  /**
   * Libraries renamed while staging, as original name to staged name.
   *
   * Empty unless the platform achieves uniqueness by renaming. `Renderer` maps layoutlib's
   * hardcoded native library list through this before calling `Bridge.init`, since the files no
   * longer have the names layoutlib expects.
   */
  @get:Synchronized
  val nativeLibraryRenames: Map<String, String>
    get() = stage()?.renames.orEmpty()

  private fun stage(): NativeLibraryStaging.Staged? {
    val root = layoutlibRuntimeRoot ?: return null
    return staged ?: NativeLibraryStaging
      .stage(NativeLibraryStaging.nativeLibDirIn(root), stagingRoot)
      .also { staged = it }
  }

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
    staged?.directory?.let(NativeLibraryStaging::unstage)
  }

  override fun toString(): String = "Sandbox($classLoader)"

  companion object {
    /**
     * Creates a sandbox that can stage layoutlib's JNI libraries from [layoutlibRuntimeRoot].
     *
     * Nothing is copied until [nativeLibDir] is first read.
     */
    fun create(
      configuration: SandboxConfiguration = SandboxConfiguration.default(),
      layoutlibRuntimeRoot: File? = null,
      stagingRoot: Path
    ): Sandbox = Sandbox(configuration, layoutlibRuntimeRoot, stagingRoot)
  }
}
