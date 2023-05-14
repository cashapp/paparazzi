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
import org.junit.Rule
import org.junit.Test

class BuildClassTest {
  @get:Rule
  val paparazzi = Paparazzi()

  @Test
  fun verifyFields() {
    println(Build.MODEL)
    assert(Build.ID == "UPB2.230407.013")
    assert(Build.DISPLAY == "sdk_phone_armv7-userdebug UpsideDownCake UPB2.230407.013 10043902 test-keys")
    assert(Build.PRODUCT == "unknown")
    assert(Build.DEVICE == "generic")
    assert(Build.BOARD == "unknown")
    assert(Build.MANUFACTURER == "generic")
    assert(Build.BRAND == "generic")
    assert(Build.MODEL == "unknown")
    assert(Build.SOC_MANUFACTURER == "unknown")
    assert(Build.SOC_MODEL == "unknown")
    assert(Build.BOOTLOADER == "unknown")
    assert(Build.RADIO == "unknown")
    assert(Build.HARDWARE == "unknown")
    assert(Build.SKU == "unknown")
    assert(Build.ODM_SKU == "unknown")

    assert(Build.VERSION.INCREMENTAL == "10043902")
    assert(Build.VERSION.RELEASE != null)
    assert(Build.VERSION.RELEASE_OR_CODENAME != null)
    assert(Build.VERSION.BASE_OS == "")
    assert(Build.VERSION.SECURITY_PATCH != null)
    assert(Build.VERSION.MEDIA_PERFORMANCE_CLASS == 0)
    assert(Build.VERSION.SDK != null)
    assert(Build.VERSION.SDK_INT != 0)
    assert(Build.VERSION.CODENAME == "UpsideDownCake")
  }
}
