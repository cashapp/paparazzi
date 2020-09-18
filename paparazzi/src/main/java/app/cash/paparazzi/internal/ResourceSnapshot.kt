/*
 * Copyright (C) 2020 Square, Inc.
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

package app.cash.paparazzi.internal

import com.google.common.collect.ImmutableMap
import org.kxml2.io.KXmlParser
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream

internal class ResourceSnapshot {

  val rootSnapshot: TagSnapshot
  val aaptSnapshots: Map<String, ResourceSnapshot>

  constructor(inputStream: InputStream) {
    val xmlParser = KXmlParser().apply {
      setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
      setInput(inputStream, null)
      require(KXmlParser.START_DOCUMENT, null, null)
      next()
    }
    rootSnapshot = createTagSnapshot(xmlParser)
    aaptSnapshots = extractAttributes(rootSnapshot)
  }

  private constructor(root: TagSnapshot) {
    rootSnapshot = root
    aaptSnapshots = extractAttributes(rootSnapshot)
  }

  private fun createTagSnapshot(
    parser: XmlPullParser
  ): TagSnapshot {
    parser.require(XmlPullParser.START_TAG, null, null)

    // need to store now, since TagSnapshot is created on end tag after parser mark has moved
    val attributes = createAttributesForTag(parser)
    val namespaces = mutableMapOf<String, String>()

    val children = mutableListOf<TagSnapshot>()
    while (parser.eventType != XmlPullParser.END_DOCUMENT) {
      when (parser.next()) {
        XmlPullParser.START_TAG -> {
          if (AAPT_NAMESPACE == parser.namespace && ATTR_NAME == parser.name) {
            namespaces[parser.prefix] = parser.namespace
            val attrAttribute = createAttrTagSnapshot(parser)
            if (attrAttribute != null) {
              attributes += attrAttribute
            }
            // Since we save the aapt:attr tags as an attribute, we do not save them as a child element. Skip.
          } else {
            children += createTagSnapshot(parser)
          }
        }
        XmlPullParser.END_TAG -> {
          namespaces[parser.getNamespacePrefix(0)] = parser.getNamespaceUri(0)
          return TagSnapshot(
              parser.name, parser.namespace, parser.prefix, attributes, children.toList(),
              namespaces
          )
        }
      }
    }

    throw IllegalStateException("We should never reach here")
  }

  private fun createAttrTagSnapshot(parser: XmlPullParser): AaptAttrSnapshot? {
    parser.require(XmlPullParser.START_TAG, null, "attr")

    val name = parser.getAttributeValue(null, "name") ?: return null
    val prefix = findPrefixByQualifiedName(name)
    val namespace = parser.getNamespace(prefix)
    val localName = findLocalNameByQualifiedName(name)
    val id = (++uniqueId).toString()

    var bundleTagSnapshot: TagSnapshot? = null
    loop@ while (parser.eventType != XmlPullParser.END_TAG) {
      when (parser.nextTag()) {
        XmlPullParser.START_TAG -> {
          bundleTagSnapshot = createTagSnapshot(parser)
        }
        XmlPullParser.END_TAG -> {
          break@loop
        }
      }
    }

    return if (bundleTagSnapshot != null) {
      // swallow end tag
      parser.nextTag()
      parser.require(XmlPullParser.END_TAG, null, "attr")

      AaptAttrSnapshot(namespace, prefix, localName, id, bundleTagSnapshot)
    } else {
      null
    }
  }

  private fun findPrefixByQualifiedName(name: String): String {
    val prefixEnd = name.indexOf(':')
    return if (prefixEnd > 0) {
      name.substring(0, prefixEnd)
    } else ""
  }

  private fun findLocalNameByQualifiedName(name: String): String {
    return name.substring(name.indexOf(':') + 1)
  }

  private fun createAttributesForTag(parser: XmlPullParser): MutableList<AttributeSnapshot> {
    return mutableListOf<AttributeSnapshot>().apply {
      for (i in 0 until parser.attributeCount) {
        add(
            AttributeSnapshot(
                parser.getAttributeNamespace(i),
                parser.getAttributePrefix(i),
                parser.getAttributeName(i),
                parser.getAttributeValue(i)
            )
        )
      }
    }
  }

  private fun extractAttributes(tagSnapshot: TagSnapshot): MutableMap<String, ResourceSnapshot> {
    val extractedAapts = mutableMapOf<String, ResourceSnapshot>()
    tagSnapshot.attributes.filterIsInstance<AaptAttrSnapshot>()
        .forEach { aaptAttr ->
          extractedAapts[aaptAttr.id] = ResourceSnapshot(aaptAttr.bundledTag)
        }

    tagSnapshot.children.forEach { extractedAapts.putAll(extractAttributes(it)) }

    return ImmutableMap.copyOf(extractedAapts)
  }

  internal data class TagSnapshot(
    val name: String,
    val namespace: String,
    val prefix: String?,
    val attributes: List<AttributeSnapshot>,
    val children: List<TagSnapshot>,
    val namespaces: Map<String, String>
  )

  internal open class AttributeSnapshot(
    open val namespace: String,
    open val prefix: String,
    open val name: String,
    open val value: String
  )

  internal class AaptAttrSnapshot(
    override val namespace: String,
    override val prefix: String,
    override val name: String,
    val id: String,
    val bundledTag: TagSnapshot
  ) : AttributeSnapshot(namespace, prefix, name, "$AAPT_ATTR_PREFIX$AAPT_PREFIX$id") {
    companion object {
      const val AAPT_ATTR_PREFIX = "@aapt:_aapt/"
      const val AAPT_PREFIX = "aapt"
    }
  }

  companion object {
    private const val AAPT_NAMESPACE = "http://schemas.android.com/aapt"
    private const val ATTR_NAME = "attr"

    internal var uniqueId = 0L
  }
}
