/*
 * Copyright (C) 2026 Square, Inc.
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

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test

/**
 * Pixel-level verification for the Compose timing path that spike 001 must not disturb.
 *
 * A box slides across the screen over one second via [animateDpAsState]. Compose animations are
 * driven by the frame clock that Paparazzi advances each frame; capturing a GIF proves the box
 * occupies a different position per frame, confirming the Choreographer/Compose clock path continues
 * to advance alongside the new per-frame layoutlib elapsed-frame time.
 */
class ComposeAnimationTest {
  @get:Rule
  val paparazzi = Paparazzi()

  @Test
  fun slidesAcrossFrames() {
    paparazzi.gif(name = "slide", start = 0, end = 1000, fps = 8) { SlidingBox() }
  }
}

@Suppress("TestFunctionName")
@Composable
private fun SlidingBox() {
  var target by remember { mutableStateOf(false) }
  LaunchedEffect(Unit) { target = true }

  val offsetX by animateDpAsState(
    targetValue = if (target) 240.dp else 0.dp,
    animationSpec = tween(durationMillis = 1000, easing = LinearEasing),
    label = "offsetX"
  )

  Box(Modifier.fillMaxSize().background(Color.White)) {
    Box(
      Modifier
        .offset(x = offsetX)
        .size(48.dp)
        .background(Color(0xFF3DDC84))
    )
  }
}
