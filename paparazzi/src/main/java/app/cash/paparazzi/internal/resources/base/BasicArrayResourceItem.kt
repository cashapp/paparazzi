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
import com.android.ide.common.rendering.api.ArrayResourceValue
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.resources.ResourceType
import com.android.resources.ResourceVisibility
import com.android.utils.Base128InputStream
import com.android.utils.Base128InputStream.StreamFormatException
import java.io.IOException
import java.util.Collections

/**
 * Ported from: [BasicArrayResourceItem.java](https://cs.android.com/android-studio/platform/tools/base/+/18047faf69512736b8ddb1f6a6785f58d47c893f:resource-repository/main/java/com/android/resources/base/BasicArrayResourceItem.java)
 *
 * Resource item representing an array resource.
 */
class BasicArrayResourceItem(
  name: String,
  sourceFile: ResourceSourceFile,
  visibility: ResourceVisibility,
  private val elements: List<String>,
  private val defaultIndex: Int
) : BasicValueResourceItemBase(ResourceType.ARRAY, name, sourceFile, visibility), ArrayResourceValue {
  init {
    assert(elements.isEmpty() || defaultIndex < elements.size)
  }

  override fun getElementCount(): Int = elements.size

  override fun getElement(index: Int): String = elements[index]

  override fun iterator() = Collections.unmodifiableList(elements).iterator()

  override fun getValue(): String? = if (elements.isEmpty()) null else elements[defaultIndex]

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (!super.equals(other)) return false
    val that = other as BasicArrayResourceItem
    return elements == that.elements
  }

  companion object {
    /**
     * Creates a [BasicArrayResourceItem] by reading its contents from the given stream.
     */
    @Throws(IOException::class)
    fun deserialize(
      stream: Base128InputStream,
      name: String,
      visibility: ResourceVisibility,
      sourceFile: ResourceSourceFile,
      resolver: ResourceNamespace.Resolver
    ): BasicArrayResourceItem {
      val n = stream.readInt()
      val elements = if (n == 0) {
        emptyList()
      } else {
        buildList(n) {
          for (i in 0 until n) {
            add(stream.readString()!!)
          }
        }
      }
      val defaultIndex = stream.readInt()
      if (elements.isNotEmpty() && defaultIndex >= elements.size) {
        throw StreamFormatException.invalidFormat()
      }
      val item = BasicArrayResourceItem(name, sourceFile, visibility, elements, defaultIndex)
      item.namespaceResolver = resolver
      return item
    }
  }
}
