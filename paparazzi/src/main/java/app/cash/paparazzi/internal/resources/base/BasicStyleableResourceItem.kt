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
import app.cash.paparazzi.internal.resources.ResourceSourceFile
import com.android.ide.common.rendering.api.AttrResourceValue
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.StyleableResourceValue
import com.android.ide.common.resources.ResourceRepository
import com.android.resources.ResourceType
import com.android.resources.ResourceVisibility
import com.android.utils.Base128InputStream
import com.android.utils.Base128InputStream.StreamFormatException
import java.io.IOException

/**
 * Ported from: [BasicStyleableResourceItem.java](https://cs.android.com/android-studio/platform/tools/base/+/18047faf69512736b8ddb1f6a6785f58d47c893f:resource-repository/main/java/com/android/resources/base/BasicStyleableResourceItem.java)
 *
 * Resource item representing a styleable resource.
 */
class BasicStyleableResourceItem(
  name: String,
  sourceFile: ResourceSourceFile,
  visibility: ResourceVisibility,
  attrs: List<AttrResourceValue>
) : BasicValueResourceItemBase(ResourceType.STYLEABLE, name, sourceFile, visibility),
  StyleableResourceValue {
  private val attrs: List<AttrResourceValue> = attrs.toList()

  override fun getAllAttributes(): List<AttrResourceValue> = attrs

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (!super.equals(other)) return false
    val that = other as BasicStyleableResourceItem
    return attrs == that.attrs
  }

  companion object {
    /**
     * Creates a [BasicStyleableResourceItem] by reading its contents from the given stream.
     */
    @Throws(IOException::class)
    fun deserialize(
      stream: Base128InputStream,
      name: String,
      visibility: ResourceVisibility,
      sourceFile: ResourceSourceFile,
      resolver: ResourceNamespace.Resolver,
      configurations: List<RepositoryConfiguration>,
      sourceFiles: List<ResourceSourceFile>,
      namespaceResolvers: List<ResourceNamespace.Resolver>
    ): BasicStyleableResourceItem {
      val n = stream.readInt()
      val attrs = if (n == 0) {
        emptyList()
      } else {
        buildList {
          for (i in 0 until n) {
            val attrItem = deserialize(stream, configurations, sourceFiles, namespaceResolvers)
            if (attrItem !is AttrResourceValue) {
              throw StreamFormatException.invalidFormat()
            }
            add(getCanonicalAttr(attrItem as AttrResourceValue, sourceFile.repository))
          }
        }
      }
      val item = BasicStyleableResourceItem(name, sourceFile, visibility, attrs)
      item.namespaceResolver = resolver
      return item
    }

    /**
     * For an attr reference that doesn't contain formats tries to find an attr definition the reference is pointing to.
     * If such attr definition belongs to this resource repository and has the same description and group name as
     * the attr reference, returns the attr definition. Otherwise returns the attr reference passed as the parameter.
     */
    fun getCanonicalAttr(
      attr: AttrResourceValue,
      repository: ResourceRepository
    ): AttrResourceValue {
      if (attr.formats.isEmpty()) {
        val items = repository.getResources(attr.namespace, ResourceType.ATTR, attr.name)
        val item = items.filterIsInstance<AttrResourceValue>()
          .find { it.description == attr.description && it.groupName == attr.groupName }
        if (item != null) {
          return item
        }
      }
      return attr
    }
  }
}
