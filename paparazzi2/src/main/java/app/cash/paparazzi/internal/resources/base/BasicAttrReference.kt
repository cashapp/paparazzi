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

import app.cash.paparazzi.internal.resources.ResourceSourceFile
import com.android.ide.common.rendering.api.AttrResourceValue
import com.android.ide.common.rendering.api.AttributeFormat
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.resources.ResourceType
import com.android.resources.ResourceVisibility
import com.android.utils.HashCodes

/**
 * Ported from: [BasicAttrReference.java](https://cs.android.com/android-studio/platform/tools/base/+/18047faf69512736b8ddb1f6a6785f58d47c893f:resource-repository/main/java/com/android/resources/base/BasicAttrReference.java)
 *
 * Resource value representing a reference to an attr resource, but potentially with its own description
 * and group name. Unlike [BasicAttrResourceItem], does not contain formats and enum or flag information.
 */
class BasicAttrReference(
  private val namespace: ResourceNamespace,
  name: String,
  sourceFile: ResourceSourceFile,
  visibility: ResourceVisibility,
  private val description: String?,
  private val groupName: String?
) : BasicValueResourceItemBase(ResourceType.ATTR, name, sourceFile, visibility), AttrResourceValue {
  override fun getNamespace(): ResourceNamespace = namespace

  override fun getFormats(): Set<AttributeFormat> = emptySet()

  override fun getAttributeValues(): Map<String, Int> = emptyMap()

  override fun getValueDescription(valueName: String): String? = null

  override fun getDescription(): String? = description

  override fun getGroupName(): String? = groupName

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (!super.equals(other)) return false
    val that = other as BasicAttrReference
    return namespace == that.namespace &&
      description == that.description &&
      groupName == that.groupName
  }

  override fun hashCode(): Int {
    // groupName is not included in hash code intentionally since it doesn't improve quality of hashing.
    return HashCodes.mix(super.hashCode(), namespace.hashCode(), description.hashCode())
  }
}
