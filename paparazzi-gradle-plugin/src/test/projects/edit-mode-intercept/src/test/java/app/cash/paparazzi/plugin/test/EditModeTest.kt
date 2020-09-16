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

import android.content.Context
import android.widget.LinearLayout
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test

class EditModeTest {
  @get:Rule
  val paparazzi = Paparazzi()

  @Test
  fun crashIfInEditMode() {
    val dummy = DummyLayout(paparazzi.context)
    paparazzi.snapshot(dummy, "edit mode")
  }
}

class DummyLayout(context: Context) : LinearLayout(context) {
  override fun onAttachedToWindow() {
    if (isInEditMode) {
      throw IllegalStateException("D'oh, isInEditMode == true")
    }
    super.onAttachedToWindow()
  }
}