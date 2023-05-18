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
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.ide.common.rendering.api.ResourceValue
import com.android.resources.ResourceType
import com.android.resources.ResourceVisibility
import com.android.utils.HashCodes
import com.google.common.base.MoreObjects

/**
 * Ported from: [BasicResourceItemBase.java](https://cs.android.com/android-studio/platform/tools/base/+/18047faf69512736b8ddb1f6a6785f58d47c893f:resource-repository/main/java/com/android/resources/base/BasicResourceItemBase.java)
 *
 * Base class for implementations of the [BasicResourceItem] interface.
 *
 */
abstract class BasicResourceItemBase(
  private val type: ResourceType,
  private val name: String,
  visibility: ResourceVisibility
) : BasicResourceItem, ResourceValue {
  // Store enums as their ordinals in byte form to minimize memory footprint.
  private val typeOrdinal: Byte = type.ordinal.toByte()
  private val visibilityOrdinal: Byte = visibility.ordinal.toByte()

  override fun getType() = resourceType

  override fun getNamespace(): ResourceNamespace = repository.namespace

  override fun getName() = name

  override fun getLibraryName() = repository.libraryName

  override fun getResourceType() = ResourceType.values()[typeOrdinal.toInt()]

  override fun getVisibility() = ResourceVisibility.values()[visibilityOrdinal.toInt()]

  override fun getReferenceToSelf() = asReference()

  override fun getResourceValue() = this

  override fun isUserDefined() = repository.containsUserDefinedResources()

  override fun isFramework() = namespace == ResourceNamespace.ANDROID

  override fun asReference() = ResourceReference(namespace, resourceType, name)

  /**
   * Returns the repository this resource belongs to.
   *
   *
   * Framework resource items may move between repositories with the same origin.
   * @see RepositoryConfiguration.transferOwnershipTo
   */
  override fun getRepository() = getRepositoryConfiguration().repository

  override fun getConfiguration() = getRepositoryConfiguration().folderConfiguration

  abstract fun getRepositoryConfiguration(): RepositoryConfiguration

  override fun getKey(): String {
    val qualifiers = configuration.qualifierString
    return if (qualifiers.isNotEmpty()) {
      (type.getName() + '-') + qualifiers + '/' + name
    } else (type.getName() + '/') + name
  }

  override fun setValue(value: String?) = throw UnsupportedOperationException()

  override fun equals(obj: Any?): Boolean {
    if (this === obj) {
      return true
    }
    if (obj == null || javaClass != obj.javaClass) {
      return false
    }

    val other = obj as BasicResourceItemBase
    return typeOrdinal == other.typeOrdinal && name == other.name && visibilityOrdinal == other.visibilityOrdinal
  }

  override fun hashCode(): Int {
    // The visibilityOrdinal field is intentionally not included in hash code because having two resource items
    // differing only by visibility in the same hash table is extremely unlikely.
    return HashCodes.mix(typeOrdinal.toInt(), name.hashCode())
  }

  override fun toString(): String {
    return MoreObjects.toStringHelper(this)
      .add("namespace", namespace)
      .add("type", resourceType)
      .add("name", name)
      .add("value", value)
      .toString()
  }
}
