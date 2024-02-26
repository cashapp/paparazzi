/*
 * Copyright (C) 2021 Square, Inc.
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
package app.cash.paparazzi.internal.parsers

import com.android.SdkConstants.AAPT_URI
import com.android.SdkConstants.ANDROID_NS_NAME
import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_TAG
import com.android.SdkConstants.TAG_ATTR
import com.android.SdkConstants.TAG_DATA
import com.android.SdkConstants.TAG_LAYOUT
import org.kxml2.io.KXmlParser
import java.io.InputStream

/**
 * An XML resource parser that creates a tree of [TagSnapshot]s
 */
internal class ResourceParser(private val fileName: String? = null, inputStream: InputStream) : KXmlParser() {
  init {
    setFeature(FEATURE_PROCESS_NAMESPACES, true)
    setInput(inputStream, null)

    require(START_DOCUMENT, null, null)
    next()
  }

  fun createTagSnapshot(layoutDistance: Int = -1, extraLayoutAttribute: AttributeSnapshot? = null): TagSnapshot {
    require(START_TAG, null, null)

    // need to store now, since TagSnapshot is created on end tag after parser mark has moved
    val tagName = name
    val tagNamespace = namespace
    val prefix = prefix

    val attributes = createAttributesForTag()
    if (tagName != TAG_DATA) {
      extraLayoutAttribute?.let { attributes += it }
    }

    var hasDeclaredAaptAttrs = false
    var last: TagSnapshot? = null
    var tagCount = 0
    val children = mutableListOf<TagSnapshot>()
    while (eventType != END_DOCUMENT) {
      when (next()) {
        START_TAG -> {
          if (AAPT_URI == namespace) {
            if (TAG_ATTR == name) {
              val attrAttribute = createAttrTagSnapshot()
              if (attrAttribute != null) {
                attributes += attrAttribute
                hasDeclaredAaptAttrs = true
              }
            }
            // Since we save the aapt:attr tags as an attribute, we do not save them as a child element. Skip.
          } else {
            val child = if (tagName == TAG_LAYOUT) {
              createTagSnapshot(
                1,
                AttributeSnapshot(
                  ANDROID_URI,
                  ANDROID_NS_NAME,
                  ATTR_TAG,
                  "layout/${fileName}_${tagCount}"
                )
              )
            } else {
              if (layoutDistance > 0) {
                createTagSnapshot(layoutDistance + 1, AttributeSnapshot(
                  ANDROID_URI,
                  ANDROID_NS_NAME,
                  ATTR_TAG,
                  "binding_${layoutDistance}"
                ))
              } else {
                createTagSnapshot(layoutDistance)
              }
            }

            hasDeclaredAaptAttrs = hasDeclaredAaptAttrs || child.hasDeclaredAaptAttrs
            children += child
            if (last != null) {
              last.next = child
            }
            last = child
          }
        }
        END_TAG -> {
          return TagSnapshot(
            tagName,
            tagNamespace,
            prefix,
            attributes,
            children.toList(),
            hasDeclaredAaptAttrs
          ).getRootTag()
        }
      }
    }

    throw IllegalStateException("We should never reach here")
  }

  private fun TagSnapshot.getRootTag(): TagSnapshot {
    if (name == TAG_LAYOUT) {
      for (subTag in children) {
        val subTagName: String = subTag.name
        if (subTagName != TAG_DATA) {
          return subTag
        }
      }
      throw IllegalStateException("<layout> without a child")
    }

    return this
  }

  private fun createAttrTagSnapshot(): AaptAttrSnapshot? {
    require(START_TAG, null, "attr")

    val name = getAttributeValue(null, "name") ?: return null
    val prefix = findPrefixByQualifiedName(name)
    val namespace = getNamespace(prefix)
    val localName = findLocalNameByQualifiedName(name)
    val id = (++uniqueId).toString()

    var bundleTagSnapshot: TagSnapshot? = null
    loop@ while (eventType != END_TAG) {
      when (nextTag()) {
        START_TAG -> {
          bundleTagSnapshot = createTagSnapshot()
        }
        END_TAG -> {
          break@loop
        }
      }
    }

    return if (bundleTagSnapshot != null) {
      // swallow end tag
      nextTag()
      require(END_TAG, null, "attr")

      AaptAttrSnapshot(namespace, prefix, localName, id, bundleTagSnapshot)
    } else {
      null
    }
  }

  private fun findPrefixByQualifiedName(name: String): String {
    val prefixEnd = name.indexOf(':')
    return if (prefixEnd > 0) {
      name.substring(0, prefixEnd)
    } else {
      ""
    }
  }

  private fun findLocalNameByQualifiedName(name: String): String {
    return name.substring(name.indexOf(':') + 1)
  }

  private fun createAttributesForTag(): MutableList<AttributeSnapshot> {
    return buildList {
      for (i in 0 until attributeCount) {
        add(
          AttributeSnapshot(
            getAttributeNamespace(i),
            getAttributePrefix(i),
            getAttributeName(i),
            getAttributeValue(i)
          )
        )
      }
    }.toMutableList()
  }

  companion object {
    private var uniqueId = 0L
  }
}
