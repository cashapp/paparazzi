/*
 * Copyright (C) 2022 Square, Inc.
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

import android.content.Context
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test

class IMMTest {
  @get:Rule
  val paparazzi = Paparazzi()

  @Test
  fun test() {
    paparazzi.snapshot(DummyLayout(paparazzi.context))
  }
}

class DummyLayout(context: Context) : FrameLayout(context) {
  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    // Fake environments such as snapshot tests may return null here, which we can safely ignore.
    val inputMethodManager = context.getSystemService(InputMethodManager::class.java)
    inputMethodManager.hideSoftInputFromWindow(this.windowToken, 0)
  }
}
