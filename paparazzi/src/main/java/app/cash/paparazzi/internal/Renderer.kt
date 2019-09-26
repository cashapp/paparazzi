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
import com.android.ide.common.rendering.api.SessionParams
import com.android.ide.common.resources.deprecated.FrameworkResources
import com.android.ide.common.resources.deprecated.ResourceItem
import com.android.ide.common.resources.deprecated.ResourceRepository
import com.android.io.FolderWrapper
import com.android.layoutlib.bridge.Bridge
import com.android.layoutlib.bridge.android.RenderParamsFlags
import com.android.layoutlib.bridge.impl.DelegateManager
import java.awt.image.BufferedImage
import java.io.Closeable
import java.io.File
import java.io.IOException

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

    val projectResources = object : ResourceRepository(FolderWrapper(environment.resDir), false) {
      override fun createResourceItem(name: String): ResourceItem {
        return ResourceItem(name)
      }
    }
    projectResources.loadResources()

    sessionParamsBuilder = SessionParamsBuilder(
        layoutlibCallback = layoutlibCallback,
        logger = logger,
        frameworkResources = frameworkResources,
        projectResources = projectResources,
        assetRepository = PaparazziAssetRepository(environment.assetsDir)
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

  fun render(
    bridge: com.android.ide.common.rendering.api.Bridge,
    params: SessionParams,
    frameTimeNanos: Long
  ): RenderResult {
    val session = bridge.createSession(params)

    try {
      if (frameTimeNanos != -1L) {
        session.setElapsedFrameTimeNanos(frameTimeNanos)
      }

      if (!session.result.isSuccess) {
        logger.error(session.result.exception, session.result.errorMessage)
      } else {
        // Render the session with a timeout of 50s.
        val renderResult = session.render(50000)
        if (!renderResult.isSuccess) {
          logger.error(session.result.exception, session.result.errorMessage)
        }
      }

      return session.toResult()
    } finally {
      session.dispose()
    }
  }

  /** Compares the golden image with the passed image. */
  fun verify(
    goldenImageName: String,
    image: BufferedImage
  ) {
    try {
      val goldenImagePath = environment.appTestDir + "/golden/" + goldenImageName
      ImageUtils.requireSimilar(goldenImagePath, image)
    } catch (e: IOException) {
      logger.error(e, e.message)
    }
  }

  /**
   * Create a new rendering session and test that rendering the given layout doesn't throw any
   * exceptions and matches the provided image.
   *
   * If frameTimeNanos is >= 0 a frame will be executed during the rendering. The time indicates
   * how far in the future is.
   */
  @JvmOverloads
  fun renderAndVerify(
    sessionParams: SessionParams,
    goldenFileName: String,
    frameTimeNanos: Long = -1
  ): RenderResult {
    val result = render(bridge!!, sessionParams, frameTimeNanos)
    verify(goldenFileName, result.image)
    return result
  }

  fun createParserFromPath(layoutPath: String): LayoutPullParser =
    LayoutPullParser.createFromPath("${environment.resDir}/layout/$layoutPath")

  /**
   * Create a new rendering session and test that rendering the given layout on given device
   * doesn't throw any exceptions and matches the provided image.
   */
  @JvmOverloads
  fun renderAndVerify(
    layoutFileName: String,
    goldenFileName: String,
    deviceConfig: DeviceConfig = DeviceConfig.NEXUS_5
  ): RenderResult {
    val sessionParams = sessionParamsBuilder
        .copy(
            layoutPullParser = createParserFromPath(layoutFileName),
            deviceConfig = deviceConfig
        )
        .build()
    return renderAndVerify(sessionParams, goldenFileName)
  }
}
