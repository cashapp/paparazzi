/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.cash.paparazzi.internal

import com.android.SdkConstants.ATTR_IGNORE
import com.android.SdkConstants.EXPANDABLE_LIST_VIEW
import com.android.SdkConstants.GRID_VIEW
import com.android.SdkConstants.LIST_VIEW
import com.android.SdkConstants.SPINNER
import com.android.SdkConstants.TOOLS_URI
import com.android.ide.common.rendering.api.ILayoutPullParser
import com.android.ide.common.rendering.api.ResourceNamespace
import org.kxml2.io.KXmlParser
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOError
import java.io.InputStream
import java.nio.charset.Charset
import java.util.HashMap

internal class LayoutPullParser private constructor(
  inputStream: InputStream
) : KXmlParser(), ILayoutPullParser {
  private var layoutNamespace = ResourceNamespace.RES_AUTO

  init {
    try {
      setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
      setInput(inputStream, null)
    } catch (e: XmlPullParserException) {
      throw IOError(e)
    }
  }

  override fun getViewCookie(): Any? {
    // TODO: Implement this properly.
    val name = super.getName() ?: return null

    // Store tools attributes if this looks like a layout we'll need adapter view
    // bindings for in the LayoutlibCallback.
    if (LIST_VIEW == name || EXPANDABLE_LIST_VIEW == name || GRID_VIEW == name || SPINNER == name) {
      var map: MutableMap<String, String>? = null
      val count = attributeCount
      for (i in 0 until count) {
        val namespace = getAttributeNamespace(i)
        if (namespace != null && namespace == TOOLS_URI) {
          val attribute = getAttributeName(i)
          if (attribute == ATTR_IGNORE) {
            continue
          }
          if (map == null) {
            map = HashMap(4)
          }
          map[attribute] = getAttributeValue(i)
        }
      }

      return map
    }

    return null
  }

  override fun getLayoutNamespace(): ResourceNamespace = layoutNamespace

  fun setLayoutNamespace(layoutNamespace: ResourceNamespace) {
    this.layoutNamespace = layoutNamespace
  }

  companion object {
    @Throws(FileNotFoundException::class)
    fun createFromFile(layoutFile: File) = LayoutPullParser(FileInputStream(layoutFile))

    /**
     * @param layoutPath Must start with '/' and be relative to test resources.
     */
    fun createFromPath(layoutPath: String): LayoutPullParser {
      var layoutPath = layoutPath
      if (layoutPath.startsWith("/")) {
        layoutPath = layoutPath.substring(1)
      }

      return LayoutPullParser(
          LayoutPullParser::class.java.classLoader.getResourceAsStream(layoutPath)
      )
    }

    fun createFromString(contents: String): LayoutPullParser {
      return LayoutPullParser(
          ByteArrayInputStream(contents.toByteArray(Charset.forName("UTF-8")))
      )
    }
  }
}
