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
import com.android.SdkConstants
import com.android.ide.common.rendering.api.AttrResourceValue
import com.android.ide.common.rendering.api.AttributeFormat
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.resources.ResourceType
import com.android.resources.ResourceVisibility
import com.android.utils.Base128InputStream
import com.android.utils.Base128InputStream.StreamFormatException
import java.io.IOException
import java.util.EnumSet

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

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (!super.equals(other)) return false
    val that = other as BasicAttrResourceItem
    return description == that.description &&
      groupName == that.groupName &&
      formats == that.formats &&
      valueMap == that.valueMap &&
      valueDescriptionMap == that.valueDescriptionMap
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

  companion object {
    /**
     * Creates a [BasicAttrResourceItem] by reading its contents from the given stream.
     */
    @Throws(IOException::class)
    fun deserialize(
      stream: Base128InputStream,
      name: String,
      visibility: ResourceVisibility,
      sourceFile: ResourceSourceFile,
      resolver: ResourceNamespace.Resolver
    ): BasicValueResourceItemBase {
      val namespaceSuffix = stream.readString()
      val description = stream.readString()
      val groupName = stream.readString()

      var formatMask = stream.readInt()
      val formats = EnumSet.noneOf(AttributeFormat::class.java)
      val attributeFormatValues = AttributeFormat.values()
      var ordinal = 0
      while (ordinal < attributeFormatValues.size && formatMask != 0) {
        if (formatMask and 0x1 != 0) {
          formats.add(attributeFormatValues[ordinal])
        }
        ordinal++
        formatMask = formatMask ushr 1
      }
      val n = stream.readInt()
      val (valueMap, descriptionMap) = if (n == 0) {
        emptyMap<String, Int>() to emptyMap<String, String>()
      } else {
        val valueTempMap = LinkedHashMap<String, Int>(n)
        val descriptionTempMap = LinkedHashMap<String, String>(n)
        for (i in 0 until n) {
          val valueName = stream.readString()!!
          val value = stream.readInt()
          if (value != Int.MIN_VALUE) {
            valueTempMap[valueName] = value - 1
          }
          val valueDescription = stream.readString()
          if (valueDescription != null) {
            descriptionTempMap[valueName] = valueDescription
          }
        }
        valueTempMap.toMap() to descriptionTempMap.toMap()
      }

      val item: BasicValueResourceItemBase =
        if (formats.isEmpty() && valueMap.isEmpty()) {
          val namespace = if (namespaceSuffix == null) {
            sourceFile.repository.namespace
          } else {
            ResourceNamespace.fromNamespaceUri(SdkConstants.URI_DOMAIN_PREFIX + namespaceSuffix)
          } ?: throw StreamFormatException.invalidFormat()
          BasicAttrReference(namespace, name, sourceFile, visibility, description, groupName)
        } else if (namespaceSuffix == null) {
          BasicAttrResourceItem(
            name, sourceFile, visibility, description, groupName, formats, valueMap, descriptionMap
          )
        } else {
          val namespace =
            ResourceNamespace.fromNamespaceUri(SdkConstants.URI_DOMAIN_PREFIX + namespaceSuffix)
              ?: throw StreamFormatException.invalidFormat()
          BasicForeignAttrResourceItem(
            namespace, name, sourceFile, description, groupName, formats, valueMap, descriptionMap
          )
        }
      item.namespaceResolver = resolver
      return item
    }
  }
}
