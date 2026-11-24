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
package app.cash.paparazzi.sample

import android.widget.LinearLayout
import app.cash.paparazzi.DeviceConfig.Companion.NEXUS_5
import app.cash.paparazzi.DeviceConfig.Companion.PIXEL_3
import app.cash.paparazzi.Paparazzi
import com.android.resources.ScreenOrientation.LANDSCAPE
import org.junit.Rule
import org.junit.Test

class LaunchViewTest {
  @get:Rule
  val paparazzi = Paparazzi(deviceConfig = PIXEL_3)

  @Test
  fun pixel3() {
    val launch = paparazzi.inflate<LinearLayout>(R.layout.launch)
    paparazzi.snapshot(launch)
  }

  @Test
  fun pixel3_differentThemes() {
    paparazzi.unsafeUpdateConfig(theme = "android:Theme.Material.Light")
    var launch = paparazzi.inflate<LinearLayout>(R.layout.launch)
    paparazzi.snapshot(view = launch, name = "light")

    paparazzi.unsafeUpdateConfig(theme = "android:Theme.Material.Light.NoActionBar")
    launch = paparazzi.inflate(R.layout.launch)
    paparazzi.snapshot(view = launch, name = "light no_action_bar")
  }

  @Test
  fun nexus5_differentOrientations() {
    paparazzi.unsafeUpdateConfig(deviceConfig = NEXUS_5)
    var launch = paparazzi.inflate<LinearLayout>(R.layout.launch)
    paparazzi.snapshot(launch, "portrait")

    paparazzi.unsafeUpdateConfig(deviceConfig = NEXUS_5.copy(orientation = LANDSCAPE))
    launch = paparazzi.inflate(R.layout.launch)
    paparazzi.snapshot(launch, "landscape")
  }
}
