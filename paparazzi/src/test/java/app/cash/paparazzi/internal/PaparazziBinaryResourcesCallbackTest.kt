/*
 * Copyright (C) 2019 Square, Inc.
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

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.xmlpull.v1.XmlPullParser
import java.io.File

class PaparazziBinaryResourcesCallbackTest {
    @Test
    fun testHappyPath() {
        val resourceApkFile = PaparazziBinaryResourcesCallbackTest::class.java.classLoader.getResource("sample-debug.apk").let {
            assertThat(it).isNotNull()
            File(it!!.path)
        }.also {
            assertThat(it)
                    .exists()
                    .canRead()
        }

        val resourceRClassUrl = PaparazziBinaryResourcesCallbackTest::class.java.classLoader.getResource("sample-debug-R.jar").let {
            assertThat(it).isNotNull()
            it!!
        }.also {
            File(it.path).apply {
                assertThat(this)
                        .exists()
                        .canRead()
            }
        }

        val binaryCallback = PaparazziBinaryResourcesCallback(
                PaparazziLogger(),
                "app.cash.paparazzi.sample",
                resourceApkFile,
                resourceRClassUrl)
        binaryCallback.initResources()
        assertThat(binaryCallback.namespace).isEqualTo("http://schemas.android.com/apk/res/app.cash.paparazzi.sample")
        assertThat(binaryCallback.createXmlParserForFile("res/layout/launch.xml")).isNotNull

        val expectedViews = mutableListOf("LinearLayout", "ImageView", "TextView")
        val expectedEndViews = mutableListOf("ImageView", "TextView", "LinearLayout")
        val expectedViewAttributesCount = mutableListOf(5, 3, 7)
        binaryCallback.createXmlParserForFile("res/layout/launch.xml")!!.apply {
            assertThat(eventType).isEqualTo(XmlPullParser.START_DOCUMENT)
            next()
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    assertThat(name).isEqualTo(expectedViews.first())
                    expectedViews.removeAt(0)
                    assertThat(attributeCount).isEqualTo(expectedViewAttributesCount.first())
                    expectedViewAttributesCount.removeAt(0)
                } else if (eventType == XmlPullParser.END_TAG) {
                    assertThat(name).isEqualTo(expectedEndViews.first())
                    expectedEndViews.removeAt(0)
                }
                next()
            }
            assertThat(expectedViews).isEmpty()
            assertThat(expectedEndViews).isEmpty()
            assertThat(expectedViewAttributesCount).isEmpty()
        }

    }
}