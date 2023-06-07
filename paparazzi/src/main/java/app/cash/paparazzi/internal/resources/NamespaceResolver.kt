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
package app.cash.paparazzi.internal.resources

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.utils.Base128InputStream
import com.android.utils.Base128InputStream.StreamFormatException
import org.xmlpull.v1.XmlPullParser

/**
 * Ported from: [NamespaceResolver.java](https://cs.android.com/android-studio/platform/tools/base/+/18047faf69512736b8ddb1f6a6785f58d47c893f:resource-repository/main/java/com/android/resources/base/NamespaceResolver.java)
 *
 * Simple implementation of the [ResourceNamespace.Resolver] interface intended to be used
 * together with [XmlPullParser].
 */
class NamespaceResolver : ResourceNamespace.Resolver {
  /** Interleaved prefixes and the corresponding URIs in order of descending priority.  */
  private val prefixesAndUris: Array<String>

  internal constructor(parser: XmlPullParser) {
    val namespaceCount = parser.getNamespaceCount(parser.depth)
    var j = namespaceCount * 2
    prefixesAndUris = arrayOfNulls<String>(j).apply {
      for (i in 0 until namespaceCount) {
        this[--j] = parser.getNamespaceUri(i)
        this[--j] = parser.getNamespacePrefix(i)
      }
    }.requireNoNulls()
  }

  private constructor(prefixesAndUris: Array<String>) {
    this.prefixesAndUris = prefixesAndUris
  }

  val namespaceCount: Int
    get() = prefixesAndUris.size / 2

  override fun prefixToUri(namespacePrefix: String): String? {
    for (i in prefixesAndUris.indices step 2) {
      if (namespacePrefix == prefixesAndUris[i]) {
        return prefixesAndUris[i + 1]
      }
    }
    return null
  }

  override fun uriToPrefix(namespaceUri: String): String? {
    for (i in prefixesAndUris.indices step 2) {
      if (namespaceUri == prefixesAndUris[i + 1]) {
        return prefixesAndUris[i]
      }
    }
    return null
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null || javaClass != other.javaClass) return false
    val that = other as NamespaceResolver
    return prefixesAndUris.contentEquals(that.prefixesAndUris)
  }

  override fun hashCode(): Int = prefixesAndUris.contentHashCode()

  companion object {
    private val EMPTY_STRING_ARRAY = arrayOfNulls<String>(0).requireNoNulls()
    val EMPTY: NamespaceResolver = NamespaceResolver(EMPTY_STRING_ARRAY)

    /**
     * Creates a namespace resolver by reading its contents from the given stream.
     */
    fun deserialize(stream: Base128InputStream): NamespaceResolver {
      val n = stream.readInt() * 2
      val prefixesAndUris = arrayOfNulls<String>(n).apply {
        for (i in 0 until n) {
          this[i] = stream.readString() ?: throw StreamFormatException.invalidFormat()
        }
      }.requireNoNulls()
      return NamespaceResolver(prefixesAndUris)
    }
  }
}
