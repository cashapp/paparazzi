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

import app.cash.paparazzi.DeviceConfig.Companion.PIXEL_3
import app.cash.paparazzi.Paparazzi
import app.cash.paparazzi.plugin.test.databinding.LayoutBinding
import org.junit.Rule
import org.junit.Test

class DataBindingTest {
  @get:Rule
  val paparazzi = Paparazzi(deviceConfig = PIXEL_3)

  @Test
  fun testMergeLayout() {
    paparazzi.snapshot(PaparazziFrameLayout(paparazzi.context))
  }

  @Test
  fun testBindingInflate() {
    val binding = LayoutBinding.inflate(paparazzi.layoutInflater)
    paparazzi.snapshot(binding.root)
  }
}
