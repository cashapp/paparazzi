/*
 * Copyright (C) 2023 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package app.cash.paparazzi.internal.resources.base

import app.cash.paparazzi.internal.resources.RepositoryConfiguration
import com.android.ide.common.rendering.api.DensityBasedResourceValue
import com.android.resources.Density
import com.android.resources.ResourceType
import com.android.resources.ResourceVisibility
import com.android.utils.HashCodes
import com.google.common.base.MoreObjects

/**
 * Ported from: [BasicDensityBasedFileResourceItem.java](https://cs.android.com/android-studio/platform/tools/base/+/47d204001bf0cb6273d8b135c7eece3a982cf0e0:resource-repository/main/java/com/android/resources/base/BasicDensityBasedFileResourceItem.java)
 *
 * Resource item representing a density-specific file resource inside an AAR, e.g. a drawable or a layout.
 */
class BasicDensityBasedFileResourceItem(
  type: ResourceType,
  name: String,
  configuration: RepositoryConfiguration,
  visibility: ResourceVisibility,
  relativePath: String,
  private val density: Density
) : BasicFileResourceItem(type, name, configuration, visibility, relativePath),
  DensityBasedResourceValue {
  override fun getResourceDensity(): Density = density

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (!super.equals(other)) return false
    val that = other as BasicDensityBasedFileResourceItem
    return density == that.density
  }

  override fun hashCode(): Int {
    return HashCodes.mix(super.hashCode(), density.hashCode())
  }

  override fun toString(): String {
    return MoreObjects.toStringHelper(this)
      .add("name", name)
      .add("namespace", namespace)
      .add("type", resourceType)
      .add("source", source)
      .add("density", resourceDensity)
      .toString()
  }
}
