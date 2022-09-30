/*
 * Copyright (C) 2017 The Android Open Source Project
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
import app.cash.paparazzi.internal.parsers.LayoutPullParser
import com.android.SdkConstants
import com.android.ide.common.rendering.api.AssetRepository
import com.android.ide.common.rendering.api.HardwareConfig
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.ide.common.rendering.api.SessionParams
import com.android.ide.common.rendering.api.SessionParams.Key
import com.android.ide.common.rendering.api.SessionParams.RenderingMode
import com.android.ide.common.rendering.api.SessionParams.RenderingMode.SizeAction.EXPAND
import com.android.ide.common.resources.ResourceResolver
import com.android.ide.common.resources.ResourceValueMap
import com.android.ide.common.resources.deprecated.ResourceRepository
import com.android.layoutlib.bridge.Bridge
import com.android.resources.LayoutDirection
import com.android.resources.ResourceType

/** Creates [SessionParams] objects. */
internal data class SessionParamsBuilder(
  private val layoutlibCallback: PaparazziCallback,
  private val logger: PaparazziLogger,
  private val frameworkResources: ResourceRepository,
  private val assetRepository: AssetRepository,
  private val projectResources: ResourceRepository,
  private val deviceConfig: DeviceConfig = DeviceConfig.NEXUS_5,
  private val renderingMode: RenderingMode = RenderingMode.NORMAL,
  private val targetSdk: Int = 22,
  private val flags: Map<Key<*>, Any> = mapOf(),
  private val themeName: String? = null,
  private val isProjectTheme: Boolean = false,
  private val layoutPullParser: LayoutPullParser? = null,
  private val projectKey: Any? = null,
  private val minSdk: Int = 0,
  private val supportsRtl: Boolean = false,
  private val viewOnly: Boolean = true
) {
  fun withTheme(
    themeName: String,
    isProjectTheme: Boolean
  ): SessionParamsBuilder {
    return copy(themeName = themeName, isProjectTheme = isProjectTheme)
  }

  fun withTheme(themeName: String): SessionParamsBuilder {
    return when {
      themeName.startsWith(SdkConstants.PREFIX_ANDROID) -> {
        withTheme(themeName.substring(SdkConstants.PREFIX_ANDROID.length), false)
      }
      else -> withTheme(themeName, true)
    }
  }

  fun plusFlag(
    flag: SessionParams.Key<*>,
    value: Any
  ) = copy(flags = flags + (flag to value))

  fun build(): SessionParams {
    require(themeName != null)

    val folderConfiguration = deviceConfig.folderConfiguration
    val resourceResolver = ResourceResolver.create(
      mapOf<ResourceNamespace, Map<ResourceType, ResourceValueMap>>(
        ResourceNamespace.ANDROID to frameworkResources.getConfiguredResources(
          folderConfiguration
        ),
        ResourceNamespace.TODO() to projectResources.getConfiguredResources(
          folderConfiguration
        )
      ),
      ResourceReference(
        ResourceNamespace.fromBoolean(!isProjectTheme),
        ResourceType.STYLE,
        themeName
      )
    )

    val hardwareConfig = when {
      viewOnly -> viewOnlyHardwareConfig
      else -> deviceConfig.hardwareConfig
    }

    val result = SessionParams(
      layoutPullParser, renderingMode, projectKey /* for caching */,
      hardwareConfig, resourceResolver, layoutlibCallback, minSdk, targetSdk, logger
    )
    result.fontScale = deviceConfig.fontScale
    result.uiMode = deviceConfig.uiModeMask

    val localeQualifier = folderConfiguration.localeQualifier
    val layoutDirectionQualifier = folderConfiguration.layoutDirectionQualifier
    // https://cs.android.com/android-studio/platform/tools/adt/idea/+/mirror-goog-studio-main:android/src/com/android/tools/idea/rendering/RenderTask.java;l=645
    if (LayoutDirection.RTL == layoutDirectionQualifier.value && !Bridge.isLocaleRtl(localeQualifier.tag)) {
      result.locale = "ur"
    } else {
      result.locale = localeQualifier.tag
    }
    result.setRtlSupport(supportsRtl)

    for ((key, value) in flags) {
      result.setFlag(key as Key<Any>, value)
    }
    result.setAssetRepository(assetRepository)

    if (viewOnly) {
      result.setForceNoDecor()
    }

    return result
  }

  /**
   * If we're rendering a view and not a full device frame, make a canvas size that fits the view
   * snugly.
   *
   * For [RenderingMode.FULL_EXPAND], this returns a 1x1 canvas that'll expand to fit the rendered
   * view.
   *
   * For [RenderingMode.V_SCROLL], this returns a canvas that's 1px tall and as wide as the regular
   * device width. Symmetrically, for [RenderingMode.H_SCROLL], this returns a canvas that's 1px
   * wide and as tall as the regular device height.
   *
   * Most users will prefer one of the scrolling modes over [RenderingMode.FULL_EXPAND]; otherwise
   * text never wraps and isn't representative of real UIs.
   */
  private val viewOnlyHardwareConfig: HardwareConfig
    get() {
      val screenHeight = when (renderingMode.vertAction) {
        EXPAND -> 1
        else -> deviceConfig.screenHeight
      }
      val screenWidth = when (renderingMode.horizAction) {
        EXPAND -> 1
        else -> deviceConfig.screenWidth
      }
      return deviceConfig.copy(
        screenHeight = screenHeight,
        screenWidth = screenWidth
      ).hardwareConfig
    }
}
