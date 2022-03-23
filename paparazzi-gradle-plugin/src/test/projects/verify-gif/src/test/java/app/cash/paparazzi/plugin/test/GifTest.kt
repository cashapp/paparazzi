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

import android.widget.LinearLayout
import app.cash.paparazzi.Paparazzi
import android.widget.ImageView
import android.graphics.drawable.AnimatedVectorDrawable
import org.junit.Rule
import org.junit.Test

class GifTest {
  @get:Rule
  val paparazzi = Paparazzi()

  @Test
  fun testViews() {
    val launch = paparazzi.inflate<LinearLayout>(R.layout.launch)
    launch.getChildAt(0)
        .animate()
        .alpha(0F)
        .rotation(360F)
        .setDuration(1_000)
        .start()

    paparazzi.gif(launch, end = 1_000L, fps = 24)
  }
}
