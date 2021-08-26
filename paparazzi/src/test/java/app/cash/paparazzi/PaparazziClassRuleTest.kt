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
package app.cash.paparazzi

import android.graphics.Canvas
import android.view.View
import com.android.tools.layoutlib.java.System_Delegate
import org.assertj.core.api.Assertions.assertThat
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit

class PaparazziClassRuleTest {
  companion object {
    @get:ClassRule
    @get:Rule
    @JvmStatic
    val paparazzi = Paparazzi()
  }

  @Test
  fun drawCalls() {
    val log = mutableListOf<String>()

    val view = object : View(paparazzi.context) {
      override fun onDraw(canvas: Canvas) {
        log += "onDraw time=$time"
      }
    }

    paparazzi.snapshot(view)

    assertThat(log).containsExactly("onDraw time=0")
  }

  private val time: Long
    get() {
      return TimeUnit.NANOSECONDS.toMillis(System_Delegate.nanoTime() - Paparazzi.TIME_OFFSET_NANOS)
    }
}

