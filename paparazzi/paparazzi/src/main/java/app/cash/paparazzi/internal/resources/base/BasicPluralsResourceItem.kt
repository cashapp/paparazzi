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
import com.android.ide.common.rendering.api.PluralsResourceValue
import com.android.resources.Arity
import com.android.resources.ResourceType
import com.android.resources.ResourceVisibility

/**
 * Ported from: [BasicPluralsResourceItem.java](https://cs.android.com/android-studio/platform/tools/base/+/18047faf69512736b8ddb1f6a6785f58d47c893f:resource-repository/main/java/com/android/resources/base/BasicPluralsResourceItem.java)
 *
 * Resource item representing a plurals resource.
 */
class BasicPluralsResourceItem private constructor(
  name: String,
  sourceFile: ResourceSourceFile,
  visibility: ResourceVisibility,
  private val arities: Array<Arity>,
  private val values: Array<String>,
  private val defaultIndex: Int
) : BasicValueResourceItemBase(ResourceType.PLURALS, name, sourceFile, visibility),
  PluralsResourceValue {
  constructor(
    name: String,
    sourceFile: ResourceSourceFile,
    visibility: ResourceVisibility,
    quantityValues: Map<Arity, String>,
    defaultArity: Arity?
  ) : this(
    name,
    sourceFile,
    visibility,
    quantityValues.keys.toTypedArray(),
    quantityValues.values.toTypedArray(),
    getIndex(defaultArity, quantityValues.keys)
  )

  init {
    assert(arities.size == values.size)
    assert(values.isEmpty() || defaultIndex < values.size)
  }

  override fun getPluralsCount() = arities.size

  override fun getQuantity(index: Int): String = arities[index].getName()

  override fun getValue(index: Int): String = values[index]

  override fun getValue(quantity: String): String? {
    val index = arities.indexOfFirst { it.name == quantity }
    return if (index != -1) values[index] else null
  }

  override fun getValue(): String? {
    return if (values.isEmpty()) null else values[defaultIndex]
  }

  override fun equals(obj: Any?): Boolean {
    if (this === obj) return true
    if (!super.equals(obj)) return false
    val other = obj as BasicPluralsResourceItem
    return arities contentEquals other.arities && values contentEquals other.values
  }

  companion object {
    private fun getIndex(arity: Arity?, arities: Collection<Arity>): Int {
      if (arity == null || arities.isEmpty()) {
        return 0
      }

      val index = arities.indexOf(arity)
      return if (index != -1) index else throw IllegalArgumentException()
    }
  }
}
