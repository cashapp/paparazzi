/*
 * Copyright (C) 2021 Square, Inc.
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
import android.view.Gravity
import androidx.appcompat.widget.AppCompatTextView

class AmountView(context: Context) : AppCompatTextView(context) {
  init {
    text = "5.00"
    textSize = 64f
    gravity = Gravity.CENTER
    setBackgroundColor(
      context.getColor(androidx.appcompat.R.color.accent_material_dark)
    )
  }
}