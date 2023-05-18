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
import com.android.resources.ResourceType
import com.android.resources.ResourceVisibility
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

  override fun getElementCount() = elements.size

  override fun getElement(index: Int) = elements[index]

  override fun iterator() = Collections.unmodifiableList(elements).iterator()

  override fun getValue(): String? = if (elements.isEmpty()) null else elements[defaultIndex]

  override fun equals(obj: Any?): Boolean {
    if (this === obj) return true
    if (!super.equals(obj)) return false
    val other = obj as BasicArrayResourceItem
    return elements == other.elements
  }
}
