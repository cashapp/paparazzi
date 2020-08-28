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
import com.android.ide.common.resources.deprecated.FrameworkResources
import com.android.io.FolderWrapper
import com.android.layoutlib.bridge.Bridge
import com.android.layoutlib.bridge.android.RenderParamsFlags
import com.android.layoutlib.bridge.impl.DelegateManager
import java.io.Closeable
import java.io.File

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
    val platformDataDir = File("${environment.platformDir}/data")
    val platformDataResDir = File("${environment.platformDir}/data/res")
    val frameworkResources = FrameworkResources(FolderWrapper(platformDataResDir)).apply {
      loadResources()
      loadPublicResources(logger)
    }

    val projectResources = layoutlibCallback.createProjectResources()
    projectResources.loadResources()

    sessionParamsBuilder = SessionParamsBuilder(
        layoutlibCallback = layoutlibCallback,
        logger = logger,
        frameworkResources = frameworkResources,
        projectResources = projectResources,
        assetRepository = layoutlibCallback.createAssetsRepository()
    )
        .plusFlag(RenderParamsFlags.FLAG_DO_NOT_RENDER_ON_CREATE, true)
        .withTheme("AppTheme", true)

    val fontLocation = File(platformDataDir, "fonts")
    val buildProp = File(environment.platformDir, "build.prop")
    val attrs = File(platformDataResDir, "values" + File.separator + "attrs.xml")
    bridge = Bridge().apply {
      init(
          DeviceConfig.loadProperties(buildProp),
          fontLocation,
          null,
          DeviceConfig.getEnumMap(attrs),
          logger
      )
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

  override fun close() {
    bridge = null

    Gc.gc()

    println("Objects still linked from the DelegateManager:")
    DelegateManager.dump(System.out)
  }

}
