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
package app.cash.paparazzi.plugin.test

import org.junit.Test

/**
 * Regression test for https://github.com/cashapp/paparazzi/issues/1922
 *
 * Verifies that non-Paparazzi tests in a module with Paparazzi applied can
 * access android.os.SystemProperties without hitting an UnsatisfiedLinkError
 * on native_get. This previously failed because layoutlib's SystemProperties
 * has native_get as a non-native method, causing JNI RegisterNatives mismatches.
 */
class SystemPropertiesNativeLinkTest {
  @Test
  fun systemPropertiesGetDoesNotThrow() {
    // Directly trigger the code path from issue #1922.
    // ContentResolver.<clinit> -> Build.getString -> SystemProperties.get -> native_get
    val result = android.os.SystemProperties.get("test.property", "default")
    assert(result == "default") { "Expected 'default' but got '$result'" }
  }
}
