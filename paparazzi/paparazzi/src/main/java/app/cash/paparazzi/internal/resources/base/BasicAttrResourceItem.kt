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
import com.android.resources.ResourceType
import com.android.resources.ResourceVisibility

/**
 * Ported from: [BasicAttrResourceItem.java](https://cs.android.com/android-studio/platform/tools/base/+/18047faf69512736b8ddb1f6a6785f58d47c893f:resource-repository/main/java/com/android/resources/base/BasicAttrResourceItem.java)
 *
 * Resource item representing an attr resource.
 */
open class BasicAttrResourceItem(
  name: String,
  sourceFile: ResourceSourceFile,
  visibility: ResourceVisibility,
  private val description: String?,
  private val groupName: String?,
  formats: Set<AttributeFormat>,
  valueMap: Map<String, Int>,
  valueDescriptionMap: Map<String, String>
) : BasicValueResourceItemBase(ResourceType.ATTR, name, sourceFile, visibility), AttrResourceValue {
  private var formats: Set<AttributeFormat> = formats.toSet()

  /** The keys are enum or flag names, the values are corresponding numeric values.  */
  private val valueMap: Map<String, Int> = valueMap.toMap()

  /** The keys are enum or flag names, the values are the value descriptions.  */
  private val valueDescriptionMap: Map<String, String> = valueDescriptionMap.toMap()

  override fun getFormats(): Set<AttributeFormat> = formats

  /**
   * Replaces the set of the allowed attribute formats. Intended to be called only by the resource repository code.
   *
   * @param formats the new set of the allowed attribute formats
   */
  fun setFormats(formats: Set<AttributeFormat>) {
    this.formats = formats.toSet()
  }

  override fun getAttributeValues(): Map<String, Int> = valueMap

  override fun getValueDescription(valueName: String): String? = valueDescriptionMap[valueName]

  override fun getDescription(): String? = description

  override fun getGroupName(): String? = groupName

  override fun equals(obj: Any?): Boolean {
    if (this === obj) return true
    if (!super.equals(obj)) return false
    val other = obj as BasicAttrResourceItem
    return description == other.description && groupName == other.groupName && formats == other.formats && attributeValues == other.attributeValues && valueDescriptionMap == other.valueDescriptionMap
  }

  /**
   * Creates and returns an [BasicAttrReference] pointing to this attribute.
   */
  fun createReference(): BasicAttrReference {
    val attrReference =
      BasicAttrReference(namespace, name, sourceFile, visibility, description, groupName)
    attrReference.namespaceResolver = namespaceResolver
    return attrReference
  }
}
