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
package app.cash.paparazzi.plugin.test

import android.os.Build
import app.cash.paparazzi.Paparazzi
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test

class BuildClassTest {
  @get:Rule
  val paparazzi = Paparazzi()

  @Test
  fun verifyFields() {
    assertThat(Build.ID).isEqualTo("URA8.230510.004")
    assertThat(Build.DISPLAY).isEqualTo("sdk_phone_armv7-userdebug UpsideDownCakePrivacySandbox URA8.230510.004 10115423 test-keys")
    assertThat(Build.PRODUCT).isEqualTo("unknown")
    assertThat(Build.DEVICE).isEqualTo("generic")
    assertThat(Build.BOARD).isEqualTo("unknown")
    assertThat(Build.MANUFACTURER).isEqualTo("generic")
    assertThat(Build.BRAND).isEqualTo("generic")
    assertThat(Build.MODEL).isEqualTo("unknown")
    assertThat(Build.SOC_MANUFACTURER).isEqualTo("unknown")
    assertThat(Build.SOC_MODEL).isEqualTo("unknown")
    assertThat(Build.BOOTLOADER).isEqualTo("unknown")
    assertThat(Build.RADIO).isEqualTo("unknown")
    assertThat(Build.HARDWARE).isEqualTo("unknown")
    assertThat(Build.SKU).isEqualTo("unknown")
    assertThat(Build.ODM_SKU).isEqualTo("unknown")

    assertThat(Build.VERSION.INCREMENTAL).isEqualTo("10115423")
    assertThat(Build.VERSION.RELEASE).isNotNull()
    assertThat(Build.VERSION.RELEASE_OR_CODENAME).isNotNull()
    assertThat(Build.VERSION.BASE_OS).isEqualTo("")
    assertThat(Build.VERSION.SECURITY_PATCH).isNotNull()
    assertThat(Build.VERSION.MEDIA_PERFORMANCE_CLASS).isEqualTo(0)
    assertThat(Build.VERSION.SDK).isNotNull()
    assertThat(Build.VERSION.SDK_INT).isNotEqualTo(0)
    assertThat(Build.VERSION.CODENAME).isEqualTo("UpsideDownCakePrivacySandbox")
  }
}
