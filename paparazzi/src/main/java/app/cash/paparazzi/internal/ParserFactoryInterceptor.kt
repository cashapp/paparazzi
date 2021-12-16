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
package app.cash.paparazzi.internal

import app.cash.paparazzi.internal.parsers.PaparazziLayoutParserWrapper
import com.google.common.io.ByteStreams
import org.kxml2.io.KXmlParser
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.io.IOException

/**
 * Workaround to prevent [com.android.layoutlib.bridge.impl.LayoutParserWrapper] from throwing
 * exceptions on calls to certain unimplemented methods.
 *
 * Sampled from https://cs.android.com/android/platform/superproject/+/master:frameworks/layoutlib/bridge/src/com/android/layoutlib/bridge/impl/ParserFactory.java;l=52-64
 */
object ParserFactoryInterceptor {
  @JvmStatic
  @Throws(XmlPullParserException::class)
  fun intercept(
    filePath: String,
    isLayout: Boolean
  ): XmlPullParser? {
    val parser = createXmlParserForFile(filePath)
    return if (parser != null && isLayout) {
      try {
        PaparazziLayoutParserWrapper(parser).peekTillLayoutStart()
      } catch (e: IOException) {
        throw XmlPullParserException(null as String?, parser, e)
      }
    } else {
      parser
    }
  }

  private fun createXmlParserForFile(fileName: String): XmlPullParser? {
    try {
      FileInputStream(fileName).use { fileStream ->
        // Read data fully to memory to be able to close the file stream.
        val byteOutputStream = ByteArrayOutputStream()
        ByteStreams.copy(fileStream, byteOutputStream)
        val parser = KXmlParser()
        parser.setInput(ByteArrayInputStream(byteOutputStream.toByteArray()), null)
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        return parser
      }
    } catch (e: IOException) {
      return null
    } catch (e: XmlPullParserException) {
      return null
    }
  }
}
