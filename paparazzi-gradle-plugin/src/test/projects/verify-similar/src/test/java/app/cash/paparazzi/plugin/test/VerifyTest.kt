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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test

class VerifyTest {
  @get:Rule
  val paparazzi = Paparazzi()

  @Test
  fun verify() {
    paparazzi.snapshot { SimilarDeltaBoxes() }
  }
}

@Suppress("TestFunctionName")
@Composable
fun SimilarDeltaBoxes() {
  Column(
    Modifier
      .background(Color.White)
      .fillMaxSize()
      .wrapContentSize()
  ) {
    Box(
      Modifier
        .background(Color(0xFFFFFD00))
        .size(100.dp),
      contentAlignment = Alignment.Center
    ) {
      Text("Similar")
    }

    Box(
      Modifier
        .background(Color.Red)
        .size(100.dp),
      contentAlignment = Alignment.Center
    ) {
      Text("Different")
    }
  }
}
