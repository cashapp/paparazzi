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

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.cash.paparazzi.accessibility.AccessibilityRenderExtension
import org.junit.Rule
import org.junit.Test

class VerifyTest {
  @get:Rule
  val paparazzi = Paparazzi(
    theme = "Theme.AppCompat.Light.NoActionBar",
    deviceConfig = DeviceConfig.PIXEL,
    renderExtensions = setOf(AccessibilityRenderExtension())
  )

  @Test
  fun verify() {
    // The committed golden was recorded against a partial MixedView (legacy
    // views + SimpleComposable, no CompositeComposable). Verifying against the
    // full MixedView produces a focused diff: just the CompositeComposable
    // block was added at the bottom.
    paparazzi.snapshot(MixedView(paparazzi.context))
  }
}
