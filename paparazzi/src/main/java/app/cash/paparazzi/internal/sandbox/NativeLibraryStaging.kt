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

import app.cash.paparazzi.getFieldReflectively
import app.cash.paparazzi.setStaticValue
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Gives each sandbox a genuinely independent copy of layoutlib's JNI libraries.
 *
 * This turned out to require two separate tricks, because there are two independent dedupe caches
 * between us and a second copy of the native runtime.
 *
 * ### 1. The JVM dedupes by canonical file path
 *
 * `Bridge.loadNativeLibraries` calls `System.load(new File(nativeLibDir, name).getAbsolutePath())`.
 * `System.load` funnels into `NativeLibraries.loadLibrary`, which canonicalises the path and rejects
 * it if the same canonical path is already loaded by a different class loader:
 *
 * ```
 * Native Library <path> already loaded in another classloader
 * ```
 *
 * Copying the libraries to a per-sandbox directory clears this. A symlink would not — it
 * canonicalises to the same target. Nor would a hard link, for the reason below.
 *
 * ### 2. The dynamic linker dedupes by install name / soname
 *
 * On macOS and Linux `Bridge` only loads `layoutlib_jni` explicitly; `libandroid_runtime` is pulled
 * in as an `@rpath` / `DT_NEEDED` dependency. `dyld` and `ld.so` both resolve such a dependency
 * against images that are *already loaded*, matching on install name (`LC_ID_DYLIB`) or soname
 * (`DT_SONAME`) rather than on path. So a second sandbox's freshly copied `layoutlib_jni` would bind
 * to the *first* sandbox's `libandroid_runtime`, silently sharing the very global state we are
 * trying to separate. The observable symptom is `Bridge.init` failing during `JNI_OnLoad` with:
 *
 * ```
 * failed to set system property "ro.product.system.name" to "mainline"
 * ```
 *
 * The fix is to give each copy a unique install name and repoint its dependents. The unique name
 * used here is the copy's own absolute path, which keeps the *file* names untouched —
 * `layoutlib_jni` looks some symbols up through `dlopen` by leaf name, so renaming the files aborts
 * the process with `Failed to find required symbol ANativeWindow_fromSurface`.
 *
 * `install_name_tool` can only rewrite a load command in place, within the pad the linker reserved.
 * For these libraries that is roughly 50 characters, which is why [defaultStagingRoot] is
 * deliberately short rather than the usual deep temp directory.
 *
 * Linux needs the same uniqueness for a different reason and by a different route; see
 * [rewriteElf]. It is handled by [ElfPatcher] rather than by shelling out to `patchelf`, which is
 * absent from most CI images.
 *
 * ### Cost
 *
 * `libandroid_runtime` is ~25 MB, so every live sandbox costs roughly that much disk plus a
 * separate resident mapping. That is why [SandboxManager] bounds the number of live sandboxes
 * rather than creating one per test.
 */
internal object NativeLibraryStaging {
  /** Libraries layoutlib loads by name, whose file names must therefore survive staging. */
  private val EXPLICITLY_LOADED = setOf("layoutlib_jni")

  private val counter = AtomicLong()

  /**
   * A short staging root, so that a staged library's absolute path still fits the install-name pad.
   *
   * The platform temp directory is unsuitable on macOS, where it is something like
   * `/var/folders/4n/8l1xl.../T/` and blows the budget on its own.
   */
  fun defaultStagingRoot(): Path {
    val root = if (platform == Platform.WINDOWS) {
      Paths.get(System.getProperty("java.io.tmpdir"), "pz-sb")
    } else {
      Paths.get("/tmp", "pz-sb")
    }
    Files.createDirectories(root)
    pruneStale(root)
    return root
  }

  /**
   * Removes staging directories left behind by a crashed run.
   *
   * The age threshold is far longer than any plausible test run, so this cannot race a live JVM
   * that is still using its directory.
   */
  private fun pruneStale(root: Path) {
    val cutoff = System.currentTimeMillis() - STALE_AFTER_MILLIS
    try {
      root.toFile().listFiles()
        ?.filter { it.isDirectory && it.lastModified() < cutoff }
        ?.forEach(::unstage)
    } catch (ignored: SecurityException) {
      // Nothing to do; a stale directory is wasted disk, not a correctness problem.
    }
  }

  /**
   * Copies every regular file in [sourceDir] into a fresh directory under [stagingRoot], rewriting
   * shared-library identifiers so the copies cannot be conflated with any other sandbox's.
   *
   * @return the staged directory, suitable for passing to `Bridge.init` as `nativeLibPath`.
   */

  /** A staged copy of layoutlib's native libraries, and any renames applied to make it unique. */
  class Staged(val directory: File, val renames: Map<String, String>)

  fun stage(sourceDir: File, stagingRoot: Path): Staged {
    require(sourceDir.isDirectory) { "Native library directory does not exist: $sourceDir" }

    Files.createDirectories(stagingRoot)
    // The staging root is shared, and Gradle may run several test workers at once, so the
    // directory name has to be unique across processes as well as within one.
    val token = "${ProcessHandle.current().pid().toString(36)}-${counter.getAndIncrement().toString(36)}"
    val target = stagingRoot.resolve(token).toFile()
    if (target.exists()) unstage(target)
    Files.createDirectories(target.toPath())

    sourceDir.listFiles()
      ?.filter { it.isFile }
      ?.forEach { source ->
        Files.copy(
          source.toPath(),
          File(target, source.name).toPath(),
          StandardCopyOption.REPLACE_EXISTING,
          StandardCopyOption.COPY_ATTRIBUTES
        )
      }

    val renames = when (platform) {
      Platform.MAC -> {
        rewriteMachO(target)
        emptyMap()
      }
      Platform.LINUX -> rewriteElf(target, token)
      Platform.WINDOWS -> rewritePe(target, token)
    }

    return Staged(target, renames)
  }

  /**
   * Gives each staged DLL a unique module name, and repoints its dependents.
   *
   * The Windows loader reuses an already-loaded module whenever the *name* matches, whatever
   * directory it came from, so a copy alone changes nothing. Both libraries are renamed here, not
   * just the dependency: `layoutlib_jni.dll` collides on its own name too.
   *
   * Renaming the files means `Bridge.WINDOWS_NATIVE_LIBRARIES` no longer matches what is on disk,
   * which is why the rename map is returned — `Renderer` maps that array through it before calling
   * `Bridge.init`.
   */
  private fun rewritePe(dir: File, token: String): Map<String, String> {
    val libraries = dir.listFiles()?.filter { it.name.endsWith(".dll", ignoreCase = true) }.orEmpty()
    if (libraries.isEmpty()) return emptyMap()

    val renames = libraries.associate { it.name to uniquify(it.name, token) }
      .filter { (from, to) -> from != to }
    if (renames.isEmpty()) return emptyMap()

    // Rewrite the strings first, while the files are still at their original names.
    for (library in libraries) {
      for ((from, to) in renames) PePatcher.replaceModuleName(library, from, to)
    }

    for (library in libraries) {
      val renamed = renames[library.name] ?: continue
      check(library.renameTo(File(dir, renamed))) {
        "Could not rename ${library.name} to $renamed while isolating native libraries."
      }
    }

    verifyPeRewrite(dir, renames)
    return renames
  }

  /**
   * Reads the patched libraries back.
   *
   * A corrupted import table surfaces as an opaque loader error much later, so it is worth a few
   * milliseconds to catch it here.
   */
  private fun verifyPeRewrite(dir: File, renames: Map<String, String>) {
    for (renamed in renames.values) {
      val library = File(dir, renamed)
      val exportName = PePatcher.readExportName(library)
      check(exportName == null || exportName == renamed) {
        "Failed to isolate $renamed: it still calls itself '$exportName'."
      }
      val stale = PePatcher.readImportedModules(library).firstOrNull { it in renames.keys }
      check(stale == null) {
        "Failed to isolate $renamed: it still imports '$stale', which no longer exists."
      }
    }
  }

  private fun rewriteMachO(dir: File) {
    val dylibs = dir.listFiles()?.filter { it.name.endsWith(".dylib") }.orEmpty()
    if (dylibs.isEmpty()) return

    val installNames = dylibs.associate { it.name to it.absolutePath }
    installNames.values.forEach(::checkInstallNameFits)

    for (lib in dylibs) {
      // A unique LC_ID_DYLIB stops dyld unifying this image with another sandbox's copy.
      run(dir, "install_name_tool", "-id", installNames.getValue(lib.name), lib.name)
      // Repoint every LC_LOAD_DYLIB that pointed at a sibling via @rpath.
      for ((name, absolutePath) in installNames) {
        if (name == lib.name) continue
        run(dir, "install_name_tool", "-change", "@rpath/$name", absolutePath, lib.name)
      }
    }

    // Editing load commands invalidates the signature, and arm64 refuses to map an unsigned image.
    run(dir, "codesign", "-f", "-s", "-", *dylibs.map { it.name }.toTypedArray())
  }

  /**
   * Gives each staged ELF library a unique `DT_SONAME`, and repoints its dependents.
   *
   * glibc resolves a `DT_NEEDED` entry by first scanning already-loaded objects, comparing the
   * requested name against each object's recorded library names and its `DT_SONAME`. A second
   * sandbox asking for `libandroid_runtime.so` would therefore be handed the first sandbox's copy,
   * however distinct the two files are on disk.
   *
   * Unlike Mach-O, the fix cannot be "point at an absolute path": `.dynstr` is packed, so a
   * replacement must be the same length as what it replaces, and no useful absolute path fits in
   * `libandroid_runtime.so`. The library is therefore renamed instead — both the file and the
   * strings referring to it — which is safe here in a way it is not on macOS. The macOS binaries
   * import `dlopen`/`dlsym` and break when their files are renamed; the Linux `layoutlib_jni.so`
   * imports neither, and each library's name appears exactly once, in its dynamic-linking metadata.
   *
   * Libraries layoutlib loads by name keep their file name; only their dependencies are renamed.
   */
  private fun rewriteElf(dir: File, token: String): Map<String, String> {
    val libraries = dir.listFiles()?.filter { it.name.endsWith(".so") }.orEmpty()
    if (libraries.isEmpty()) return emptyMap()

    val renames = mutableMapOf<String, String>()
    for (library in libraries) {
      val base = library.name.substringBefore('.')
      if (base in EXPLICITLY_LOADED) continue

      val soname = ElfPatcher.readSoname(library) ?: continue
      val renamed = uniquify(soname, token)
      if (renamed == soname) continue

      ElfPatcher.replaceDynamicString(library, soname, renamed)
      val renamedFile = File(dir, renamed)
      check(library.renameTo(renamedFile)) {
        "Could not rename ${library.name} to $renamed while isolating native libraries."
      }

      // Every sibling that depended on the old name must now ask for the new one, or the dynamic
      // loader would fall back to a system copy or fail outright.
      for (dependent in dir.listFiles()?.filter { it.name.endsWith(".so") }.orEmpty()) {
        ElfPatcher.replaceDynamicString(dependent, soname, renamed)
      }

      verifyElfRewrite(renamedFile, renamed)
      renames[library.name] = renamed
    }
    return renames
  }

  /**
   * Reads the patched library back.
   *
   * Cheap, and the alternative is a corrupted library surfacing much later as an unexplained
   * dynamic-linker error.
   */
  private fun verifyElfRewrite(library: File, expectedSoname: String) {
    val actual = ElfPatcher.readSoname(library)
    check(actual == expectedSoname) {
      "Failed to isolate ${library.name}: DT_SONAME reads '$actual', expected '$expectedSoname'."
    }
  }

  /**
   * Replaces the trailing characters of a library name with [token], preserving overall length.
   *
   * Length preservation is not cosmetic: `.dynstr` has no slack, so a name can only be swapped for
   * one of exactly the same size.
   */
  private fun uniquify(fileName: String, token: String): String {
    val base = fileName.substringBefore('.')
    val extension = fileName.removePrefix(base)
    return if (base.length <= token.length) fileName else base.dropLast(token.length) + token + extension
  }

  private fun checkInstallNameFits(path: String) {
    check(path.length <= MAX_INSTALL_NAME_LENGTH) {
      "Staged native library path is too long to rewrite in place ($path, ${path.length} chars, " +
        "max $MAX_INSTALL_NAME_LENGTH). Native libraries are linked with a fixed header pad, so a " +
        "sandbox staging directory must be short. Choose a shorter staging root."
    }
  }

  private fun run(workingDir: File, vararg command: String) {
    val process = ProcessBuilder(*command)
      .directory(workingDir)
      .redirectErrorStream(true)
      .start()

    val output = process.inputStream.bufferedReader().readText()
    if (!process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
      process.destroyForcibly()
      throw IOException("Timed out running ${command.joinToString(" ")}")
    }
    if (process.exitValue() != 0) {
      throw IOException(
        "Failed to isolate native libraries: `${command.joinToString(" ")}` exited " +
          "${process.exitValue()}.\n$output\n" +
          "Sandboxed rendering needs this tool to give each layoutlib copy a unique " +
          "install name; without it the dynamic linker would share one native runtime " +
          "across every sandbox. It ships with the Xcode command line tools."
      )
    }
  }

  /**
   * Points layoutlib's hardcoded native library names at their staged, renamed copies.
   *
   * `Bridge` loads its JNI libraries from `{LINUX,MAC,WINDOWS}_NATIVE_LIBRARIES`, baked in as plain
   * file names. Where a platform achieves uniqueness by renaming - Windows, whose loader reuses any
   * already-loaded module with a matching name - those names no longer exist on disk, and
   * `Bridge.init` cannot find them.
   *
   * A no-op when [renames] is empty, which covers macOS and every unsandboxed run.
   *
   * @param bridgeClass the sandbox's own `com.android.layoutlib.bridge.Bridge`.
   */
  fun remapLibraryNames(bridgeClass: Class<*>, renames: Map<String, String>) {
    if (renames.isEmpty()) return

    for (fieldName in NATIVE_LIBRARY_FIELDS) {
      val field = try {
        bridgeClass.getFieldReflectively(fieldName)
      } catch (e: RuntimeException) {
        continue // A given layoutlib build need not declare all three.
      }
      val current = field.get(null) as? Array<*> ?: continue
      val updated = current.map { renames[it] ?: it }.toTypedArray()
      if (!updated.contentEquals(current)) field.setStaticValue(updated)
    }
  }

  /** Best-effort recursive delete; a leftover temp directory must never fail a test. */
  fun unstage(directory: File) {
    try {
      directory.walkBottomUp().forEach { it.delete() }
    } catch (ignored: IOException) {
      // The OS may still hold the mapping open. The JVM temp dir will be reclaimed eventually.
    }
  }

  /**
   * The platform-specific native library directory inside an exploded `layoutlib-runtime` artifact,
   * mirroring the classifier logic in `paparazzi/build.gradle` and `PaparazziPlugin`.
   */
  fun nativeLibDirIn(layoutlibRuntimeRoot: File): File =
    File(layoutlibRuntimeRoot, "data${File.separator}$osLabel${File.separator}lib64")

  private enum class Platform { MAC, LINUX, WINDOWS }

  private val platform: Platform
    get() {
      val osName = System.getProperty("os.name").lowercase(Locale.US)
      return when {
        osName.startsWith("windows") -> Platform.WINDOWS
        osName.startsWith("mac") -> Platform.MAC
        else -> Platform.LINUX
      }
    }

  private val osLabel: String
    get() = when (platform) {
      Platform.WINDOWS -> "win"
      Platform.LINUX -> "linux"
      Platform.MAC ->
        if (System.getProperty("os.arch").lowercase(Locale.US).startsWith("x86")) "mac" else "mac-arm"
    }

  /** layoutlib's per-platform lists of JNI libraries to load, by field name. */
  private val NATIVE_LIBRARY_FIELDS = listOf(
    "LINUX_NATIVE_LIBRARIES",
    "MAC_NATIVE_LIBRARIES",
    "WINDOWS_NATIVE_LIBRARIES"
  )

  private const val STALE_AFTER_MILLIS = 24L * 60 * 60 * 1000
  private const val MAX_INSTALL_NAME_LENGTH = 50
  private const val PROCESS_TIMEOUT_SECONDS = 60L
}
