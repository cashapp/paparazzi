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

import com.android.layoutlib.bridge.impl.LayoutParserWrapper
import org.xmlpull.v1.XmlPullParser

/**
 * [com.android.layoutlib.bridge.impl.LayoutParserWrapper] adds support for data-binding, but
 * throws [UnsupportedOperationException] for certain methods not needed for that use case.
 *
 * We override those methods here.
 */
class PaparazziLayoutParserWrapper(
  private val parser: XmlPullParser
) : LayoutParserWrapper(parser) {
  override fun getAttributeCount(): Int {
    return parser.attributeCount
  }

  override fun getAttributeName(i: Int): String {
    return parser.getAttributeName(i)
  }

  override fun getAttributeNamespace(i: Int): String {
    return parser.getAttributeNamespace(i)
  }
}
