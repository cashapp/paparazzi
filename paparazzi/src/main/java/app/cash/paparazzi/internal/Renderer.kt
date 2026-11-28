/*
 * Copyright (C) 2016 The Android Open Source Project
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

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Environment
import app.cash.paparazzi.Flags
import app.cash.paparazzi.Paparazzi
import app.cash.paparazzi.getFieldReflectively
import app.cash.paparazzi.internal.resources.AarSourceResourceRepository
import app.cash.paparazzi.internal.resources.AppResourceRepository
import app.cash.paparazzi.internal.resources.FrameworkResourceRepository
import app.cash.paparazzi.setStaticValue
import com.android.layoutlib.bridge.Bridge
import com.android.layoutlib.bridge.android.RenderParamsFlags
import com.android.layoutlib.bridge.impl.DelegateManager
import java.io.Closeable
import java.io.File
import java.nio.file.Paths
import java.util.Locale
import kotlin.io.path.name

/** View rendering. */
internal class Renderer(
  private val environment: Environment,
  private val layoutlibCallback: PaparazziCallback,
  private val logger: PaparazziLogger
) : Closeable {
  private var bridge: Bridge? = null
  private lateinit var sessionParamsBuilder: SessionParamsBuilder

  /** Initialize the bridge and the resource maps. */
  fun prepare(): SessionParamsBuilder {
    val layoutlibResourcesRoot = System.getProperty("paparazzi.layoutlib.resources.root")
      ?: throw RuntimeException("Missing system property for 'paparazzi.layoutlib.resources.root'")
    val layoutlibResDir = File("$layoutlibResourcesRoot/res")

    val frameworkResources = FrameworkResourceRepository.create(
      resourceDirectoryOrFile = layoutlibResDir.toPath(),
      languagesToLoad = emptySet(),
      useCompiled9Patches = false
    )
    val projectResources = AppResourceRepository.create(
      localResourceDirectories = environment.localResourceDirs.map { File(it) },
      moduleResourceDirectories = environment.moduleResourceDirs.map { File(it) },
      libraryRepositories = environment.libraryResourceDirs.map { dir ->
        val resourceDirPath = Paths.get(dir)
        AarSourceResourceRepository.create(
          resourceDirectoryOrFile = resourceDirPath,
          libraryName = resourceDirPath.parent.fileName.name // segment before /res
        )
      }
    )

    sessionParamsBuilder = SessionParamsBuilder(
      layoutlibCallback = layoutlibCallback,
      logger = logger,
      frameworkResources = frameworkResources,
      projectResources = projectResources,
      assetRepository = PaparazziAssetRepository(
        assetDirs = environment.allModuleAssetDirs + environment.libraryAssetDirs
      )
    )
      .plusFlag(RenderParamsFlags.FLAG_DO_NOT_RENDER_ON_CREATE, true)
      .plusFlag(RenderParamsFlags.FLAG_KEY_DISABLE_BITMAP_CACHING, true)
      .withTheme("AppTheme", true)

    val layoutlibRuntimeRoot = System.getProperty("paparazzi.layoutlib.runtime.root")
      ?: throw RuntimeException("Missing system property for 'paparazzi.layoutlib.runtime.root'")
    val buildProp = File(layoutlibRuntimeRoot, "build.prop")
    val platformDataDir = File(layoutlibRuntimeRoot, "data")
    val fontLocation = File(platformDataDir, "fonts")
    val icuLocation = File(platformDataDir, "icu" + File.separator + "icudt76l.dat")
    val keyboardLocation = File(platformDataDir, "keyboards" + File.separator + "Generic.kcm")
    val nativeLibLocation = File(platformDataDir, getNativeLibDir())
    checkNoForeignAndroidRuntime(nativeLibLocation)
    val hyphenDataLocation = File(platformDataDir, "hyphen-data")
    val attrs = File(layoutlibResDir, "values" + File.separator + "attrs.xml")
    val systemProperties = DeviceConfig.loadProperties(buildProp) + mapOf(
      // We want Choreographer.USE_FRAME_TIME to be false so it uses System_Delegate.nanoTime()
      "debug.choreographer.frametime" to "false"
    )
    bridge = Bridge().apply {
      check(
        init(
          systemProperties,
          fontLocation,
          nativeLibLocation.path,
          icuLocation.path,
          hyphenDataLocation.path,
          arrayOf(keyboardLocation.path),
          DeviceConfig.getEnumMap(attrs),
          logger
        )
      ) { "Failed to init Bridge." }
    }
    configureBuildProperties()
    Bridge.getLock()
      .lock()
    try {
      Bridge.setLog(logger)
    } finally {
      Bridge.getLock()
        .unlock()
    }

    return sessionParamsBuilder
  }

  private fun configureBuildProperties() {
    val classLoader = Paparazzi::class.java.classLoader
    val buildClass = try {
      classLoader.loadClass("android.os.Build")
    } catch (e: ClassNotFoundException) {
      // Project unit tests don't load Android platform code
      return
    }
    val originalBuildClass = try {
      classLoader.loadClass("android.os._Original_Build")
    } catch (e: ClassNotFoundException) {
      // Project unit tests don't load Android platform code
      return
    }

    buildClass.fields.forEach {
      try {
        val originalField = originalBuildClass.getField(it.name)
        buildClass.getFieldReflectively(it.name).setStaticValue(originalField.get(null))
      } catch (e: NoSuchFieldException) {
        // android.os._Original_Build from layoutlib doesn't have this field, it's probably new.
        // Just ignore it and keep the value in android.os.Build
      }
    }

    buildClass.classes.forEach { inner ->
      val originalInnerClass = originalBuildClass.classes.single { it.simpleName == inner.simpleName }
      inner.fields.forEach {
        try {
          val originalField = originalInnerClass.getField(it.name)
          inner.getFieldReflectively(it.name).setStaticValue(originalField.get(null))
        } catch (e: NoSuchFieldException) {
          // android.os._Original_Build from layoutlib doesn't have this field, it's probably new.
          // Just ignore it and keep the value in android.os.Build
        }
      }
    }
  }

  /**
   * On Linux, layoutlib_jni.so finds its runtime through a DT_NEEDED on libandroid_runtime.so, and
   * glibc satisfies that with any library already loaded under that soname or name. Robolectric
   * loads its own libandroid_runtime.so for SDK 35 and above, and that runtime's own
   * dlopen("libandroid_runtime.so") attaches the name to it, so after a Robolectric test in the same
   * JVM, layoutlib would bind to Robolectric's runtime and abort the JVM (SIGABRT) while registering
   * its natives, losing every test still queued on the fork. Fail this test instead, with the cause.
   */
  private fun checkNoForeignAndroidRuntime(nativeLibLocation: File) {
    val maps = File("/proc/self/maps")
    if (!maps.canRead()) return
    val ownRuntime = File(nativeLibLocation, "libandroid_runtime.so").canonicalPath
    val foreign = maps.useLines { lines ->
      lines.map { line -> line.indexOf('/').let { if (it < 0) "" else line.substring(it) } }
        .firstOrNull { it.endsWith("/libandroid_runtime.so") && File(it).canonicalPath != ownRuntime }
    } ?: return
    throw IllegalStateException(
      "Another libandroid_runtime.so is already loaded in this JVM ($foreign), most likely " +
        "Robolectric's native runtime for SDK 35+. layoutlib would bind to it instead of " +
        "$ownRuntime and crash. Run Paparazzi tests in a JVM without Robolectric tests at " +
        "SDK 35+, e.g. a separate Test task or forkEvery = 1."
    )
  }

  private fun getNativeLibDir(): String {
    val osName = System.getProperty("os.name").lowercase(Locale.US)
    val osLabel = when {
      osName.startsWith("windows") -> "win"
      osName.startsWith("mac") -> {
        val osArch = System.getProperty("os.arch").lowercase(Locale.US)
        if (osArch.startsWith("x86")) "mac" else "mac-arm"
      }
      else -> "linux"
    }
    return "$osLabel/lib64"
  }

  override fun close() {
    bridge = null

    Gc.gc()

    dumpDelegates()
  }

  fun dumpDelegates() {
    if (System.getProperty(Flags.DEBUG_LINKED_OBJECTS) != null) {
      println("Objects still linked from the DelegateManager:")
      DelegateManager.dump(System.out)
    }
  }
}
