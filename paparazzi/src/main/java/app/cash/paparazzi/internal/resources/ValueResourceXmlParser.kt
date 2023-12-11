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
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException
import java.io.InputStream
import java.io.Reader
import java.util.function.Function

/**
 * Ported from: [ValueResourceXmlParser.java](https://cs.android.com/android-studio/platform/tools/base/+/47d204001bf0cb6273d8b135c7eece3a982cf0e0:resource-repository/main/java/com/android/resources/base/ValueResourceXmlParser.java)
 *
 * XML pull parser for value resource files. Provides access to the resource namespace resolver
 * for the current tag.
 */
internal class ValueResourceXmlParser : CommentTrackingXmlPullParser() {
  internal val namespaceResolverCache = mutableMapOf<NamespaceResolver, NamespaceResolver>()
  internal val resolverStack = ArrayDeque<NamespaceResolver>(4)

  /**
   * Returns the namespace resolver for the current XML node. The parser has to be positioned on a start tag
   * when this method is called.
   */
  @get:Throws(XmlPullParserException::class)
  val namespaceResolver: ResourceNamespace.Resolver
    get() {
      check(eventType == START_TAG)
      if (resolverStack.isEmpty()) {
        return ResourceNamespace.Resolver.EMPTY_RESOLVER
      }
      val resolver = resolverStack.last()
      return if (resolver.namespaceCount == 0) ResourceNamespace.Resolver.EMPTY_RESOLVER else resolver
    }

  @Throws(XmlPullParserException::class)
  override fun setInput(reader: Reader) {
    super.setInput(reader)
    resolverStack.clear()
  }

  @Throws(XmlPullParserException::class)
  override fun setInput(inputStream: InputStream, encoding: String?) {
    super.setInput(inputStream, encoding)
    resolverStack.clear()
  }

  @Throws(XmlPullParserException::class, IOException::class)
  override fun nextToken(): Int {
    val token = super.nextToken()
    processToken(token)
    return token
  }

  @Throws(XmlPullParserException::class, IOException::class)
  override operator fun next(): Int {
    val token = super.next()
    processToken(token)
    return token
  }

  @Throws(XmlPullParserException::class)
  private fun processToken(token: Int) {
    when (token) {
      START_TAG -> {
        val namespaceCount = getNamespaceCount(depth)
        val parent = if (resolverStack.isEmpty()) null else resolverStack.last()
        val current = if (parent != null && parent.namespaceCount == namespaceCount) parent else getOrCreateResolver
        resolverStack += current
        assert(resolverStack.size == depth)
      }

      END_TAG -> resolverStack.removeLast()
    }
  }

  @get:Throws(XmlPullParserException::class)
  private val getOrCreateResolver: NamespaceResolver
    get() = namespaceResolverCache.computeIfAbsent(NamespaceResolver(this), Function.identity())
}
