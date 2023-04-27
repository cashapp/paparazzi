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

import android.os.SystemProperties
import android.util.ArraySet
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Environment
import app.cash.paparazzi.Flags
import app.cash.paparazzi.Paparazzi
import app.cash.paparazzi.deprecated.com.android.ide.common.resources.deprecated.FrameworkResources
import app.cash.paparazzi.deprecated.com.android.ide.common.resources.deprecated.ResourceItem
import app.cash.paparazzi.deprecated.com.android.ide.common.resources.deprecated.ResourceRepository
import app.cash.paparazzi.deprecated.com.android.io.FolderWrapper
import app.cash.paparazzi.getFieldReflectively
import app.cash.paparazzi.setFieldValue
import app.cash.paparazzi.setFieldValueInt
import app.cash.paparazzi.setFieldValueList
import app.cash.paparazzi.setStaticValue
import com.android.layoutlib.bridge.Bridge
import com.android.layoutlib.bridge.android.RenderParamsFlags
import com.android.layoutlib.bridge.impl.DelegateManager
import java.io.Closeable
import java.io.File
import java.util.Locale

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

    val useNewResourceLoading = System.getProperty(Flags.NEW_RESOURCE_LOADING).toBoolean()
    val (frameworkResources, projectResources) =
      if (!useNewResourceLoading) {
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
        TODO("New resource loading coming soon")
      }

    sessionParamsBuilder = SessionParamsBuilder(
      layoutlibCallback = layoutlibCallback,
      logger = logger,
      frameworkResources = frameworkResources,
      projectResources = projectResources,
      assetRepository = PaparazziAssetRepository(environment.assetsDir)
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
      forceBuildStaticFields()
      forceBuildVersionStaticFields()
    }
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

  private fun forceBuildStaticFields() {
    val buildClass = try {
      Paparazzi::class.java.classLoader.loadClass("android.os.Build")
    } catch (e: ClassNotFoundException) {
      // Project unit tests don't load Android platform code
      return
    }

    buildClass.setFieldValue("MANUFACTURER", "ro.product.manufacturer")
    buildClass.setFieldValue("ID", "ro.build.id")
    buildClass.setFieldValue("DISPLAY", "ro.build.display.id")
    buildClass.setFieldValue("PRODUCT", "ro.product.name")
    buildClass.setFieldValue("DEVICE", "ro.product.device")
    buildClass.setFieldValue("BOARD", "ro.product.board")
    buildClass.setFieldValue("BRAND", "ro.product.brand")
    buildClass.setFieldValue("MODEL", "ro.product.model")
    buildClass.setFieldValue("BOOTLOADER", "ro.bootloader")
    buildClass.setFieldValue("HARDWARE", "ro.hardware")
    buildClass.setFieldValue("SKU", "ro.boot.hardware.sku")
    buildClass.setFieldValue("ODM_SKU", "ro.boot.product.hardware.sku")
  }

  private fun forceBuildVersionStaticFields() {
    val buildVersionClass = try {
      Paparazzi::class.java.classLoader.loadClass("android.os.Build\$Version")
    } catch (e: ClassNotFoundException) {
      // Project unit tests don't load Android platform code
      return
    }

    buildVersionClass.setFieldValue(fieldName = "INCREMENTAL", systemProp = "ro.build.version.incremental")
    buildVersionClass.setFieldValue(fieldName = "RELEASE", systemProp = "ro.build.version.release")
    buildVersionClass.setFieldValue(fieldName = "RELEASE_OR_CODENAME", systemProp = "ro.build.version.release_or_codename")
    buildVersionClass.setFieldValue(fieldName = "RELEASE_OR_PREVIEW_DISPLAY", systemProp = "ro.build.version.release_or_preview_display")
    buildVersionClass.setFieldValue(
      fieldName = "BASE_OS", systemProp = "ro.build.version.base_os", defaultValue = ""
    )
    buildVersionClass.setFieldValue(
      fieldName = "SECURITY_PATCH", systemProp = "ro.build.version.security_patch", defaultValue = ""
    )
    buildVersionClass.setFieldValueInt(fieldName = "SDK", systemProp = "ro.build.version.sdk")
    buildVersionClass.setFieldValueInt(
      fieldName = "DEVICE_INITIAL_SDK_INT", systemProp = "ro.product.first_api_level"
    )
    buildVersionClass.setFieldValueInt(
      fieldName = "PREVIEW_SDK_INT", systemProp = "ro.build.version.preview_sdk"
    )
    buildVersionClass.setFieldValue(
      fieldName = "PREVIEW_SDK_FINGERPRINT",
      systemProp = "ro.build.version.preview_sdk_fingerprint", defaultValue = "REL"
    )
    buildVersionClass.setFieldValue(
      fieldName = "CODENAME", systemProp = "ro.build.version.codename"
    )
    buildVersionClass.setFieldValueList(
      fieldName = "KNOWN_CODENAMES", systemProp = "ro.build.version.known_codenames"
    ) { it.toSet() }
    buildVersionClass.setFieldValueList(
      fieldName = "ALL_CODENAMES", systemProp = "ro.build.version.all_codenames"
    ) { it }
    buildVersionClass.setFieldValueInt(
      fieldName = "MIN_SUPPORTED_TARGET_SDK_INT", systemProp = "ro.build.version.min_supported_target_sdk"
    )
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
