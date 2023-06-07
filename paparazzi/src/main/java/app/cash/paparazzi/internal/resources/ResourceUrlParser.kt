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

import com.android.SdkConstants.PREFIX_RESOURCE_REF
import com.android.SdkConstants.PREFIX_THEME_REF

/**
 * Ported from: [ResourceUrlParser.java](https://cs.android.com/android-studio/platform/tools/base/+/47d204001bf0cb6273d8b135c7eece3a982cf0e0:resource-repository/main/java/com/android/resources/base/ResourceUrlParser.java)
 *
 * Parser of resource URLs. Unlike [com.android.resources.ResourceUrl], this class is resilient to
 * URL syntax errors that doesn't create any GC overhead.
 */
class ResourceUrlParser {
  private var resourceUrl = ""
  private var colonPos = 0
  private var slashPos = 0
  private var typeStart = 0
  private var namespacePrefixStart = 0
  private var nameStart = 0

  /**
   * Parses resource URL and sets the fields of this object to point to different parts of the URL.
   *
   * @param resourceUrl the resource URL to parse
   */
  fun parseResourceUrl(resourceUrl: String) {
    this.resourceUrl = resourceUrl
    colonPos = -1
    slashPos = -1
    typeStart = -1
    namespacePrefixStart = -1

    var prefixEnd = when {
      resourceUrl.startsWith(PREFIX_RESOURCE_REF) -> if (resourceUrl.startsWith("@+")) 2 else 1
      resourceUrl.startsWith(PREFIX_THEME_REF) -> 1
      else -> 0
    }
    if (resourceUrl.startsWith("*", prefixEnd)) {
      prefixEnd++
    }

    val len = resourceUrl.length
    var start = prefixEnd
    loop@ for (i in prefixEnd until len) {
      when (resourceUrl[i]) {
        '/' ->
          if (slashPos < 0) {
            slashPos = i
            typeStart = start
            start = i + 1
            if (colonPos >= 0) {
              break@loop
            }
          }

        ':' ->
          if (colonPos < 0) {
            colonPos = i
            namespacePrefixStart = start
            start = i + 1
            if (slashPos >= 0) {
              break@loop
            }
          }
      }
    }
    nameStart = start
  }

  /**
   * Returns the namespace prefix of the resource URL, or null if the URL doesn't contain a prefix.
   */
  val namespacePrefix: String?
    get() = if (colonPos >= 0) resourceUrl.substring(namespacePrefixStart, colonPos) else null

  /**
   * Returns the type of the resource URL, or null if the URL don't contain a type.
   */
  val type: String?
    get() = if (slashPos >= 0) resourceUrl.substring(typeStart, slashPos) else null

  /**
   * Returns the name part of the resource URL.
   */
  val name: String
    get() = resourceUrl.substring(nameStart)

  /**
   * Returns the qualified name of the resource without any prefix or type.
   */
  val qualifiedName: String
    get() {
      if (colonPos < 0) {
        return name
      }
      return if (nameStart == colonPos + 1) {
        resourceUrl.substring(namespacePrefixStart)
      } else {
        resourceUrl.substring(namespacePrefixStart, colonPos + 1) + name
      }
    }

  /**
   * Checks if the resource URL has the given type.
   */
  fun hasType(type: String): Boolean =
    if (slashPos < 0) {
      false
    } else {
      slashPos == typeStart + type.length && resourceUrl.startsWith(type, typeStart)
    }

  /**
   * Checks if the resource URL has the given namespace prefix.
   */
  fun hasNamespacePrefix(namespacePrefix: String): Boolean =
    if (colonPos < 0) {
      false
    } else {
      colonPos == namespacePrefixStart + namespacePrefix.length &&
        resourceUrl.startsWith(namespacePrefix, namespacePrefixStart)
    }
}
