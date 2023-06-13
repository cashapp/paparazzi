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
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.resources.Arity
import com.android.resources.ResourceType
import com.android.resources.ResourceVisibility
import com.android.utils.Base128InputStream
import com.android.utils.Base128InputStream.StreamFormatException
import java.io.IOException

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

  override fun getPluralsCount(): Int = arities.size

  override fun getQuantity(index: Int): String = arities[index].getName()

  override fun getValue(index: Int): String = values[index]

  override fun getValue(quantity: String): String? {
    val index = arities.indexOfFirst { it.getName() == quantity }
    return if (index != -1) values[index] else null
  }

  override fun getValue(): String? = if (values.isEmpty()) null else values[defaultIndex]

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (!super.equals(other)) return false
    val that = other as BasicPluralsResourceItem
    return arities.contentEquals(that.arities) && values.contentEquals(that.values)
  }

  companion object {
    private fun getIndex(arity: Arity?, arities: Collection<Arity>): Int {
      if (arity == null || arities.isEmpty()) {
        return 0
      }
      val index = arities.indexOf(arity)
      return if (index != -1) index else throw IllegalArgumentException()
    }

    /**
     * Creates a [BasicPluralsResourceItem] by reading its contents from the given stream.
     */
    @Throws(IOException::class)
    fun deserialize(
      stream: Base128InputStream,
      name: String,
      visibility: ResourceVisibility,
      sourceFile: ResourceSourceFile,
      resolver: ResourceNamespace.Resolver
    ): BasicPluralsResourceItem {
      val n = stream.readInt()
      val (arities, values) = if (n == 0) {
        Arity.EMPTY_ARRAY to emptyArray()
      } else {
        val arityList = mutableListOf<Arity>()
        val valuesList = mutableListOf<String>()
        for (i in 0 until n) {
          arityList.add(Arity.values()[stream.readInt()])
          valuesList.add(stream.readString()!!)
        }
        arityList.toTypedArray() to valuesList.toTypedArray()
      }

      val defaultIndex = stream.readInt()
      if (values.isNotEmpty() && defaultIndex >= values.size) {
        throw StreamFormatException.invalidFormat()
      }
      val item =
        BasicPluralsResourceItem(name, sourceFile, visibility, arities, values, defaultIndex)
      item.namespaceResolver = resolver
      return item
    }
  }
}
