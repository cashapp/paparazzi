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
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.ide.common.rendering.api.StyleItemResourceValue
import com.android.ide.common.rendering.api.StyleItemResourceValueImpl
import com.android.ide.common.rendering.api.StyleResourceValue
import com.android.resources.ResourceType
import com.android.resources.ResourceVisibility
import com.android.utils.Base128InputStream
import com.android.utils.Base128InputStream.StreamFormatException
import com.google.common.collect.ImmutableTable
import com.google.common.collect.Table
import java.io.IOException
import java.util.logging.Logger

/**
 * Ported from: [BasicStyleResourceItem.java](https://cs.android.com/android-studio/platform/tools/base/+/f3c4ef558ce2a3ac9eacf7007aee8f6e056235eb:resource-repository/main/java/com/android/resources/base/BasicStyleResourceItem.java)
 *
 * Resource item representing a style resource.
 */
class BasicStyleResourceItem(
  name: String,
  sourceFile: ResourceSourceFile,
  visibility: ResourceVisibility,
  private val parentStyle: String?,
  styleItems: Collection<StyleItemResourceValue>
) : BasicValueResourceItemBase(ResourceType.STYLE, name, sourceFile, visibility),
  StyleResourceValue {
  /** Style items keyed by the namespace and the name of the attribute they define.  */
  private val styleItemTable: Table<ResourceNamespace, String, StyleItemResourceValue>

  init {
    val tableBuilder = ImmutableTable.builder<ResourceNamespace, String, StyleItemResourceValue>()
    val duplicateCheckMap = mutableMapOf<ResourceReference, StyleItemResourceValue>()
    for (styleItem in styleItems) {
      val attr = styleItem.attr
      if (attr != null) {
        // Check for duplicate style item definitions. Such duplicate definitions are present in the framework resources.
        val previouslyDefined = duplicateCheckMap.put(attr, styleItem)
        if (previouslyDefined == null) {
          tableBuilder.put(attr.namespace, attr.name, styleItem)
        } else if (previouslyDefined != styleItem) {
          LOG.warning("Conflicting definitions of \"${styleItem.attrName}\" in style \"$name\"")
        }
      }
    }
    styleItemTable = tableBuilder.build()
  }

  override fun getParentStyleName(): String? = parentStyle

  override fun getItem(namespace: ResourceNamespace, name: String): StyleItemResourceValue? =
    styleItemTable.get(namespace, name)

  override fun getItem(attr: ResourceReference): StyleItemResourceValue? =
    if (attr.resourceType == ResourceType.ATTR) {
      styleItemTable.get(attr.namespace, attr.name)
    } else {
      null
    }

  override fun getDefinedItems(): Collection<StyleItemResourceValue> = styleItemTable.values()

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (!super.equals(other)) return false
    val that = other as BasicStyleResourceItem
    return parentStyle == that.parentStyle && styleItemTable == that.styleItemTable
  }

  companion object {
    private val LOG: Logger = Logger.getLogger(BasicStyleResourceItem::class.java.name)

    /**
     * Creates a [BasicStyleResourceItem] by reading its contents from the given stream.
     */
    @Throws(IOException::class)
    fun deserialize(
      stream: Base128InputStream,
      name: String,
      visibility: ResourceVisibility,
      sourceFile: ResourceSourceFile,
      resolver: ResourceNamespace.Resolver,
      namespaceResolvers: List<ResourceNamespace.Resolver>
    ): BasicStyleResourceItem {
      val repository = sourceFile.repository
      val namespace = repository.namespace
      val libraryName = repository.libraryName
      val parentStyle = stream.readString()
      val n = stream.readInt()
      val styleItems = if (n == 0) {
        emptyList()
      } else {
        buildList {
          for (i in 0 until n) {
            val attrName = stream.readString() ?: throw StreamFormatException.invalidFormat()
            val value = stream.readString()
            val itemResolver = namespaceResolvers[stream.readInt()]
            val styleItem = StyleItemResourceValueImpl(namespace, attrName, value, libraryName)
            styleItem.namespaceResolver = itemResolver
            add(styleItem)
          }
        }
      }
      val item = BasicStyleResourceItem(name, sourceFile, visibility, parentStyle, styleItems)
      item.namespaceResolver = resolver
      return item
    }
  }
}
