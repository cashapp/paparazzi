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

package com.squareup.paparazzi.internal

import com.android.SdkConstants
import com.android.ide.common.rendering.api.AssetRepository
import com.android.ide.common.rendering.api.LayoutlibCallback
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.ide.common.rendering.api.SessionParams
import com.android.ide.common.rendering.api.SessionParams.Key
import com.android.ide.common.rendering.api.SessionParams.RenderingMode
import com.android.ide.common.resources.ResourceResolver
import com.android.ide.common.resources.ResourceValueMap
import com.android.ide.common.resources.deprecated.ResourceRepository
import com.android.resources.ResourceType
import com.squareup.paparazzi.PaparazziLogger
import java.util.LinkedHashMap

/**
 * Builder to help setting up [SessionParams] objects.
 */
class SessionParamsBuilder {
  private var layoutPullParser: LayoutPullParser? = null
  private var renderingMode = RenderingMode.NORMAL
  private val projectKey: Any? = null
  private var deviceConfig = DeviceConfig.NEXUS_5
  private var frameworkResources: ResourceRepository? = null
  private var projectResources: ResourceRepository? = null
  private var themeName: String? = null
  private var isProjectTheme: Boolean = false
  private var layoutlibCallback: LayoutlibCallback? = null
  private var targetSdk: Int = 0
  private var minSdk = 0
  private var logger: PaparazziLogger? = null
  private val flags = LinkedHashMap<SessionParams.Key<*>, Any>()
  private var assetRepository: AssetRepository? = null
  private var decor = true

  fun setParser(layoutParser: LayoutPullParser): SessionParamsBuilder {
    this.layoutPullParser = layoutParser
    return this
  }

  fun setRenderingMode(renderingMode: RenderingMode): SessionParamsBuilder {
    this.renderingMode = renderingMode
    return this
  }

  fun setDeviceConfig(deviceConfig: DeviceConfig): SessionParamsBuilder {
    this.deviceConfig = deviceConfig
    return this
  }

  fun setProjectResources(resources: ResourceRepository): SessionParamsBuilder {
    projectResources = resources
    return this
  }

  fun setFrameworkResources(resources: ResourceRepository): SessionParamsBuilder {
    frameworkResources = resources
    return this
  }

  fun setTheme(themeName: String, isProjectTheme: Boolean): SessionParamsBuilder {
    this.themeName = themeName
    this.isProjectTheme = isProjectTheme
    return this
  }

  fun setTheme(themeName: String): SessionParamsBuilder {
    var themeName = themeName
    val isProjectTheme: Boolean
    if (themeName.startsWith(SdkConstants.PREFIX_ANDROID)) {
      themeName = themeName.substring(SdkConstants.PREFIX_ANDROID.length)
      isProjectTheme = false
    } else {
      isProjectTheme = true
    }
    return setTheme(themeName, isProjectTheme)
  }

  fun setCallback(callback: LayoutlibCallback): SessionParamsBuilder {
    layoutlibCallback = callback
    return this
  }

  fun setTargetSdk(targetSdk: Int): SessionParamsBuilder {
    this.targetSdk = targetSdk
    return this
  }

  fun setMinSdk(minSdk: Int): SessionParamsBuilder {
    this.minSdk = minSdk
    return this
  }

  fun setLogger(logger: PaparazziLogger): SessionParamsBuilder {
    this.logger = logger
    return this
  }

  fun setFlag(flag: SessionParams.Key<*>, value: Any): SessionParamsBuilder {
    flags[flag] = value
    return this
  }

  fun setAssetRepository(repository: AssetRepository): SessionParamsBuilder {
    assetRepository = repository
    return this
  }

  fun disableDecoration(): SessionParamsBuilder {
    decor = false
    return this
  }

  fun build(): SessionParams {
    require(frameworkResources != null)
    require(projectResources != null)
    require(themeName != null)
    require(logger != null)
    require(layoutlibCallback != null)

    val folderConfiguration = deviceConfig.folderConfiguration
    val resourceResolver = ResourceResolver.create(
        mapOf<ResourceNamespace, Map<ResourceType, ResourceValueMap>>(
            ResourceNamespace.ANDROID to frameworkResources!!.getConfiguredResources(folderConfiguration),
            ResourceNamespace.TODO() to projectResources!!.getConfiguredResources(folderConfiguration)
        ),
        ResourceReference(
            ResourceNamespace.fromBoolean(!isProjectTheme),
            ResourceType.STYLE,
            themeName
        )
    )

    val result = SessionParams(layoutPullParser, renderingMode, projectKey /* for caching */,
        deviceConfig.hardwareConfig, resourceResolver, layoutlibCallback, minSdk, targetSdk, logger)

    for ((key, value) in flags) {
      result.setFlag(key as Key<Any>, value)
    }
    result.setAssetRepository(assetRepository)

    if (!decor) {
      result.setForceNoDecor()
    }

    return result
  }
}
