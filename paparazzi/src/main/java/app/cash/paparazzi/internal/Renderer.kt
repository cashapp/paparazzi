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
import app.cash.paparazzi.deprecated.com.android.ide.common.resources.deprecated.FrameworkResources
import app.cash.paparazzi.deprecated.com.android.ide.common.resources.deprecated.ResourceItem
import app.cash.paparazzi.deprecated.com.android.ide.common.resources.deprecated.ResourceRepository
import app.cash.paparazzi.deprecated.com.android.io.FolderWrapper
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
    val platformDataResDir = File("${environment.platformDir}/data/res")

    val useLegacyResourceLoading = System.getProperty(Flags.LEGACY_RESOURCE_LOADING).toBoolean()
    val (frameworkResources, projectResources) =
      if (useLegacyResourceLoading) {
        ResourceRepositoryBridge.Legacy(
          FrameworkResources(FolderWrapper(platformDataResDir))
            .apply {
              loadResources()
              loadPublicResources(logger)
            }
        ) to
          ResourceRepositoryBridge.Legacy(
            object : ResourceRepository(FolderWrapper(environment.resDir), false) {
              override fun createResourceItem(name: String): ResourceItem {
                return ResourceItem(name)
              }
            }.apply { loadResources() }
          )
      } else {
        ResourceRepositoryBridge.New(
          FrameworkResourceRepository.create(
            resourceDirectoryOrFile = platformDataResDir.toPath(),
            languagesToLoad = emptySet(),
            useCompiled9Patches = false
          )
        ) to
          ResourceRepositoryBridge.New(
            AppResourceRepository.create(
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
          )
      }

    val useLegacyAssetLoading = System.getProperty(Flags.LEGACY_ASSET_LOADING).toBoolean()
    sessionParamsBuilder = SessionParamsBuilder(
      layoutlibCallback = layoutlibCallback,
      logger = logger,
      frameworkResources = frameworkResources,
      projectResources = projectResources,
      assetRepository = PaparazziAssetRepository(
        assetPath = environment.assetsDir,
        assetDirs = if (useLegacyAssetLoading) {
          emptyList()
        } else {
          environment.allModuleAssetDirs + environment.libraryAssetDirs
        }
      )
    )
      .plusFlag(RenderParamsFlags.FLAG_DO_NOT_RENDER_ON_CREATE, true)
      .withTheme("AppTheme", true)

    val platformDataRoot = System.getProperty("paparazzi.platform.data.root")
      ?: throw RuntimeException("Missing system property for 'paparazzi.platform.data.root'")
    val platformDataDir = File(platformDataRoot, "data")
    val fontLocation = File(platformDataDir, "fonts")
    val nativeLibLocation = File(platformDataDir, getNativeLibDir())
    val icuLocation = File(platformDataDir, "icu" + File.separator + "icudt70l.dat")
    val keyboardLocation = File(platformDataDir, "keyboards" + File.separator + "Generic.kcm")
    val buildProp = File(environment.platformDir, "build.prop")
    val attrs = File(platformDataResDir, "values" + File.separator + "attrs.xml")
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

  private fun getNativeLibDir(): String {
    val osName = System.getProperty("os.name").toLowerCase(Locale.US)
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
