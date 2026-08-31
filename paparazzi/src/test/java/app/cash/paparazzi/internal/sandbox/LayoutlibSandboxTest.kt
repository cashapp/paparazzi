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

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.internal.PaparazziLogger
import com.android.ide.common.rendering.api.ILayoutLog
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Test
import java.io.File
import java.util.Locale

/**
 * End-to-end proof that the sandbox actually isolates layoutlib.
 *
 * Booting two `Bridge` instances in a single JVM is impossible today: `Bridge.sJniLibLoaded` is a
 * static, and `System.load` refuses the same canonical path from a second class loader. These tests
 * assert that a sandbox solves both.
 */
class LayoutlibSandboxTest {
  private val stagingRoot = NativeLibraryStaging.defaultStagingRoot()
  private val sandboxes = mutableListOf<Sandbox>()

  private val layoutlibRuntimeRoot =
    File(requireNotNull(System.getProperty("paparazzi.layoutlib.runtime.root")))
  private val layoutlibResourcesRoot =
    File(requireNotNull(System.getProperty("paparazzi.layoutlib.resources.root")))

  @After
  fun tearDown() {
    sandboxes.forEach { sandbox ->
      runCatching { disposeBridge(sandbox) }
      sandbox.close()
    }
  }

  @Test
  fun `each sandbox stages its own native libraries`() {
    val first = newSandbox()
    val second = newSandbox()

    val firstDir = requireNotNull(first.nativeLibDir)
    val secondDir = requireNotNull(second.nativeLibDir)

    assertThat(firstDir.canonicalPath).isNotEqualTo(secondDir.canonicalPath)

    // Each platform makes a library unique in a different way, so the assertion has to differ.
    if (isMac) {
      // File names are preserved: the Mach-O binaries import dlopen/dlsym and break when renamed.
      // Uniqueness comes from the install name, without which dyld resolves the second sandbox's
      // @rpath dependency to the first sandbox's already-loaded image.
      val names = firstDir.listFiles()!!.map { it.name }
      assertThat(names).containsExactlyElementsIn(secondDir.listFiles()!!.map { it.name })
      assertThat(names).contains("layoutlib_jni.dylib")
      assertThat(names).contains("libandroid_runtime.dylib")

      fun installName(dir: File) =
        ProcessBuilder("otool", "-D", "libandroid_runtime.dylib")
          .directory(dir)
          .start()
          .inputStream.bufferedReader().readText()

      assertThat(installName(firstDir)).isNotEqualTo(installName(secondDir))
    } else if (isWindows) {
      // The Windows loader reuses any already-loaded module with a matching name, whatever
      // directory it came from, so both libraries are renamed and the import repointed.
      // Both libraries are renamed, so identify them by role: the JNI library is the one that
      // imports its sibling.
      fun dlls(dir: File) = dir.listFiles()!!.filter { it.name.endsWith(".dll", ignoreCase = true) }
      fun jni(dir: File) =
        dlls(dir).single { library ->
          PePatcher.readImportedModules(library).any { it in dlls(dir).map(File::getName) }
        }
      fun runtime(dir: File) = dlls(dir).single { it.name != jni(dir).name }

      assertThat(jni(firstDir).name).isNotEqualTo(jni(secondDir).name)
      assertThat(runtime(firstDir).name).isNotEqualTo(runtime(secondDir).name)

      // Each library must call itself what it is called on disk, and reach its own sibling.
      assertThat(PePatcher.readExportName(jni(firstDir))).isEqualTo(jni(firstDir).name)
      assertThat(PePatcher.readImportedModules(jni(firstDir))).contains(runtime(firstDir).name)
      assertThat(PePatcher.readImportedModules(jni(secondDir))).contains(runtime(secondDir).name)
    } else {
      // ELF has no header slack, so no absolute path fits in DT_NEEDED. The library is renamed
      // instead - the differing file name *is* the isolation, and glibc compares the requested
      // name against loaded objects' names and DT_SONAME.
      fun runtimeLibrary(dir: File) = dir.listFiles()!!.single { it.name.startsWith("libandroid_") }

      assertThat(firstDir.listFiles()!!.map { it.name }).contains("layoutlib_jni.so")
      assertThat(runtimeLibrary(firstDir).name).isNotEqualTo(runtimeLibrary(secondDir).name)
      assertThat(ElfPatcher.readSoname(runtimeLibrary(firstDir)))
        .isNotEqualTo(ElfPatcher.readSoname(runtimeLibrary(secondDir)))

      // The dependent has to follow the rename, or it would fall back to a system copy.
      assertThat(ElfPatcher.readNeeded(File(firstDir, "layoutlib_jni.so")))
        .contains(runtimeLibrary(firstDir).name)
      assertThat(ElfPatcher.readNeeded(File(secondDir, "layoutlib_jni.so")))
        .contains(runtimeLibrary(secondDir).name)
    }
  }

  @Test
  fun `sandboxed bitmap density does not leak between sandboxes`() {
    val first = newSandbox()
    val second = newSandbox()
    assertThat(initBridge(first)).isTrue()
    assertThat(initBridge(second)).isTrue()

    // Bitmap.sDefaultDensity is process-global today: PaparazziSdk.prepare() calls
    // setDefaultDensity on every test and the value leaks into whatever runs next.
    fun bitmapIn(sandbox: Sandbox) = sandbox.loadClass("android.graphics.Bitmap")

    fun defaultDensityOf(sandbox: Sandbox): Int =
      sandbox.runOnSandbox {
        bitmapIn(sandbox).getDeclaredMethod("getDefaultDensity")
          .apply { isAccessible = true }
          .invoke(null) as Int
      }

    val originalSecondDensity = defaultDensityOf(second)

    first.runOnSandbox {
      bitmapIn(first).getDeclaredMethod("setDefaultDensity", Int::class.javaPrimitiveType)
        .invoke(null, 480)
    }

    assertThat(defaultDensityOf(first)).isEqualTo(480)
    assertThat(defaultDensityOf(second)).isEqualTo(originalSecondDensity)
  }

  @Test
  fun `two sandboxes each initialise their own bridge`() {
    val first = newSandbox()
    val second = newSandbox()

    assertThat(initBridge(first)).isTrue()
    assertThat(initBridge(second)).isTrue()

    // The proof: distinct Class objects means distinct sJniLibLoaded, distinct Choreographer,
    // distinct Build.VERSION.SDK_INT.
    assertThat(first.loadClass(BRIDGE)).isNotSameInstanceAs(second.loadClass(BRIDGE))
  }

  @Test
  fun `sandboxed bridge does not disturb host statics`() {
    val sandbox = newSandbox()
    assertThat(initBridge(sandbox)).isTrue()

    val sandboxedBuild = sandbox.loadClass("android.os._Original_Build")
    val hostBuild = Class.forName("android.os._Original_Build", false, javaClass.classLoader)

    assertThat(sandboxedBuild).isNotSameInstanceAs(hostBuild)
    assertThat(sandboxedBuild.classLoader).isSameInstanceAs(sandbox.classLoader)
  }

  private fun newSandbox(): Sandbox =
    Sandbox.create(
      configuration = SandboxConfiguration.default(),
      layoutlibRuntimeRoot = layoutlibRuntimeRoot,
      stagingRoot = stagingRoot
    ).also { sandboxes += it }

  /** Mirrors `Renderer.prepare`, but against a sandboxed `Bridge` reached reflectively. */
  private fun initBridge(sandbox: Sandbox): Boolean {
    val bridgeClass = sandbox.loadClass(BRIDGE)
    // Renderer does this before init; driving Bridge directly means doing it here too, or the
    // renamed libraries would be loaded under names that no longer exist on disk.
    NativeLibraryStaging.remapLibraryNames(bridgeClass, sandbox.nativeLibraryRenames)
    val bridge = bridgeClass.getDeclaredConstructor().newInstance()

    val platformDataDir = File(layoutlibRuntimeRoot, "data")
    val layoutlibResDir = File(layoutlibResourcesRoot, "res")

    val init = bridgeClass.getMethod(
      "init",
      Map::class.java,
      File::class.java,
      String::class.java,
      String::class.java,
      String::class.java,
      Array<String>::class.java,
      Map::class.java,
      ILayoutLog::class.java
    )

    return sandbox.runOnSandbox {
      init.invoke(
        bridge,
        DeviceConfig.loadProperties(File(layoutlibRuntimeRoot, "build.prop")) +
          mapOf("debug.choreographer.frametime" to "false"),
        File(platformDataDir, "fonts"),
        requireNotNull(sandbox.nativeLibDir).path,
        File(platformDataDir, "icu${File.separator}icudt76l.dat").path,
        File(platformDataDir, "hyphen-data").path,
        arrayOf(File(platformDataDir, "keyboards${File.separator}Generic.kcm").path),
        DeviceConfig.getEnumMap(File(layoutlibResDir, "values${File.separator}attrs.xml")),
        PaparazziLogger()
      ) as Boolean
    }
  }

  private fun disposeBridge(sandbox: Sandbox) {
    val bridgeClass = sandbox.classLoader.loadClass(BRIDGE)
    val bridge = bridgeClass.getDeclaredConstructor().newInstance()
    bridgeClass.getMethod("dispose").invoke(bridge)
  }

  private companion object {
    const val BRIDGE = "com.android.layoutlib.bridge.Bridge"

    private val osName = System.getProperty("os.name").lowercase(Locale.US)
    val isMac = osName.startsWith("mac")
    val isWindows = osName.startsWith("windows")
  }
}
