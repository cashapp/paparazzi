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

import androidx.compose.foundation.layout.Box
import androidx.compose.material.Text
import androidx.compose.ui.graphics.Color
import app.cash.paparazzi.Paparazzi
import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterInjector
import com.google.testing.junit.testparameterinjector.TestParameterValuesProvider
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(TestParameterInjector::class)
class InvalidCharsTest {
  @get:Rule
  val paparazzi = Paparazzi()

  @Test
  fun badSnapshotName() {
    paparazzi.snapshot(name = "this is/my name") {
      Box {}
    }
  }

  @Test
  fun badValues(@TestParameter(valuesProvider = MathOperatorProvider::class) char: Char) {
    paparazzi.snapshot {
      Text(
        text = char.toString(),
        color = Color.Black
      )
    }
  }

  @Test
  fun goodValues(@TestParameter operation: MathOperation) {
    paparazzi.snapshot {
      Text(
        text = operation.operator.toString(),
        color = Color.Black
      )
    }
  }

  object MathOperatorProvider : TestParameterValuesProvider() {
    override fun provideValues(context: Context): List<Char> = listOf('+', '-', '*', '/')
  }

  enum class MathOperation(val operator: Char) {
    ADDITION(operator = '+'),
    SUBTRACTION(operator = '-'),
    MULTIPLICATION(operator = '*'),
    DIVISION(operator = '/')
  }
}
