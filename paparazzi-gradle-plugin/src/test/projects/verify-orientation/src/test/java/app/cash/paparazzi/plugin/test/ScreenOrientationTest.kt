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

import android.widget.FrameLayout
import app.cash.paparazzi.DeviceConfig.Companion.PIXEL_C
import app.cash.paparazzi.Paparazzi
import com.android.resources.ScreenOrientation
import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterInjector
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(TestParameterInjector::class)
class ScreenOrientationTest(
  @TestParameter val configuration: OrientationTestConfiguration
) {
  @get:Rule
  val paparazzi = Paparazzi(deviceConfig = PIXEL_C.copy(orientation = configuration.orientation))

  @Test
  fun test() {
    paparazzi.snapshot(
      view = FrameLayout(paparazzi.context).apply {
        setBackgroundColor(configuration.backgroundColor)
      }
    )
  }
}

enum class OrientationTestConfiguration(
  val orientation: ScreenOrientation,
  val backgroundColor: Int
) {
  PORTRAIT(
    orientation = ScreenOrientation.PORTRAIT,
    backgroundColor = -1
  ),
  LANDSCAPE(
    orientation = ScreenOrientation.LANDSCAPE,
    backgroundColor = 0
  )
}
