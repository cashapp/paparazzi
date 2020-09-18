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

import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.charset.Charset
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.kxml2.io.KXmlParser
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParser.END_DOCUMENT
import org.xmlpull.v1.XmlPullParser.END_TAG
import org.xmlpull.v1.XmlPullParser.START_DOCUMENT
import org.xmlpull.v1.XmlPullParser.START_TAG

class ResourceSnapshotPullParserTest {

  val aaptEncodedContent = """
        |<?xml version="1.0" encoding="utf-8"?>
        |<vector xmlns:android="http://schemas.android.com/apk/res/android"
        |        xmlns:aapt="http://schemas.android.com/aapt"
        |        android:layout_width="match_parent"
        |        android:layout_height="match_parent">
        |  <aapt:attr name="android:fillColor">
        |    <gradient android:endX="0" android:endY="0" android:startX="81" android:startY="81" android:type="linear">
        |      <item android:color="#FFFFFF" android:offset="0"/>
        |      <item android:color="#000000" android:offset="1"/> 
        |    </gradient> 
        |  </aapt:attr>
        |</vector>
        """.trimMargin()

  val expectedDecodedContent =
    """
        |<?xml version="1.0" encoding="utf-8"?>
        |<vector xmlns:android="http://schemas.android.com/apk/res/android"
        |        xmlns:aapt="http://schemas.android.com/aapt"
        |        android:layout_width="match_parent"
        |        android:layout_height="match_parent"
        |        android:fillColor="@aapt:_aapt/aapt1"/>
        """.trimMargin()

  val expectedDecodedAapt = """
        |<gradient xmlns:android="http://schemas.android.com/apk/res/android" 
        |          android:endX="0" android:endY="0" android:startX="81" android:startY="81" android:type="linear">
        |  <item android:color="#FFFFFF" android:offset="0"/>
        |  <item android:color="#000000" android:offset="1"/> 
        |</gradient>
        """.trimMargin()

  @Before
  fun setUp() {
    ResourceSnapshot.uniqueId = 0
  }

  @Test
  fun shouldConvertAaptTagToAttribute() {
    val xmlPullParser = createXmlParser(expectedDecodedContent)
    val resourceSnapshotPullParser = ResourceSnapshotPullParser(
        ResourceSnapshot(
            ByteArrayInputStream(aaptEncodedContent.toByteArray(Charset.forName("UTF-8")))
        )
    )

    assertEquals(resourceSnapshotPullParser, xmlPullParser)
    assertThat(xmlPullParser.getNamespace("aapt")).isEqualTo("http://schemas.android.com/aapt")
  }

  @Test
  fun shouldExtractAaptResource() {
    val resourceSnapshot = ResourceSnapshot(
        ByteArrayInputStream(aaptEncodedContent.toByteArray(Charset.forName("UTF-8")))
    )
    assertThat(resourceSnapshot.aaptSnapshots.size).isEqualTo(1)

    val actualPullParser = ResourceSnapshotPullParser(resourceSnapshot.aaptSnapshots.getValue("1"))
    val expectedPullParser = createXmlParser(expectedDecodedAapt)

    assertEquals(actualPullParser, expectedPullParser)
  }

  private fun createXmlParser(content: String): KXmlParser {
    return KXmlParser().apply {
      setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
      setInput(
          ByteArrayInputStream(content.toByteArray(Charset.forName("UTF-8"))), null
      )
    }
  }

  private fun assertEquals(
    resourceSnapshotPullParser: ResourceSnapshotPullParser,
    xmlPullParser: KXmlParser
  ) {
    assertThat(resourceSnapshotPullParser.next()).isEqualTo(START_DOCUMENT)
    var actualNext = resourceSnapshotPullParser.next()
    do {
      assertThat(getNextSupportedTag(xmlPullParser)).isEqualTo(actualNext)
      when (actualNext) {
        START_TAG -> {
          assertThat(resourceSnapshotPullParser.depth).isEqualTo(xmlPullParser.depth)
          assertThat(resourceSnapshotPullParser.name).isEqualTo(xmlPullParser.name)
          assertThat(xmlPullParser.getNamespace("android")).isEqualTo(
              resourceSnapshotPullParser.getNamespace("android")
          )
          assertThat(resourceSnapshotPullParser.attributeCount).isEqualTo(
              xmlPullParser.attributeCount
          )
          for (i in 0 until resourceSnapshotPullParser.attributeCount) {
            assertThat(resourceSnapshotPullParser.getAttributeNamespace(i)).isEqualTo(
                xmlPullParser.getAttributeNamespace(i)
            )
            assertThat(resourceSnapshotPullParser.getAttributeName(i)).isEqualTo(
                xmlPullParser.getAttributeName(i)
            )
            assertThat(resourceSnapshotPullParser.getAttributeValue(i)).isEqualTo(
                xmlPullParser.getAttributeValue(i)
            )

            assertThat(
                resourceSnapshotPullParser.getAttributeValue(
                    resourceSnapshotPullParser.getAttributeNamespace(i),
                    resourceSnapshotPullParser.getAttributeName(i)
                )
            ).isEqualTo(
                xmlPullParser.getAttributeValue(
                    xmlPullParser.getAttributeNamespace(i),
                    xmlPullParser.getAttributeName(i)
                )
            )
          }
        }
      }

      actualNext = resourceSnapshotPullParser.next()
    } while (actualNext != END_DOCUMENT)
  }

  private fun getNextSupportedTag(xmlPullParser: KXmlParser): Int {
    var expectedNext = xmlPullParser.next()
    while (expectedNext != START_DOCUMENT
        && expectedNext != END_DOCUMENT
        && expectedNext != START_TAG
        && expectedNext != END_TAG
    ) {
      expectedNext = xmlPullParser.next()
    }
    return expectedNext
  }
}