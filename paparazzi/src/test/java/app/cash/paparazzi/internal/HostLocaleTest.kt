/*
 * Copyright (C) 2026 Square, Inc.
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

import app.cash.paparazzi.internal.apng.PngTestUtils.createImage
import app.cash.paparazzi.internal.differs.OffByTwo
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.awt.Point
import java.util.Locale

class HostLocaleTest {
  @get:Rule
  val tempDir = TemporaryFolder()

  private val defaultLocale = Locale.getDefault()

  @Before
  fun setup() {
    // Simulate code under test switching the JVM default locale.
    Locale.setDefault(Locale.forLanguageTag("ar"))
  }

  @After
  fun tearDown() {
    Locale.setDefault(defaultLocale)
  }

  @Test
  fun hostLocaleIgnoresDefaultLocaleChanges() {
    // The test JVM is started with user.language=en, user.country=US.
    assertThat(hostLocale).isEqualTo(Locale.US)
    assertThat("%f%%".formatForHost(12.5f)).isEqualTo("12.500000%")
  }

  @Test
  fun imageDiffMessageUsesHostLocale() {
    val golden = createImage(squareOffset = Point(5, 5))
    val actual = createImage(squareOffset = Point(25, 25))

    try {
      ImageUtils.assertImageSimilar(
        relativePath = "golden.png",
        goldenImage = golden,
        image = actual,
        maxPercentDifferent = 0.01,
        failureDir = tempDir.newFolder(),
        differ = OffByTwo
      )
      fail("Should have already failed")
    } catch (e: AssertionError) {
      assertThat(e.message).containsMatch("""^Images differ \(by \d+\.\d{6}%\)""")
    }
  }
}
