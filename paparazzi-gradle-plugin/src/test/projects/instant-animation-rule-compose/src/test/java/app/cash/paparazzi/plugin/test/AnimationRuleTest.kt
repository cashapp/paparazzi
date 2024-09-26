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

import androidx.compose.ui.platform.ComposeView
import app.cash.paparazzi.InstantAnimationsRule
import app.cash.paparazzi.Paparazzi
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test

class AnimationRuleTest {
  @get:Rule
  val paparazzi = Paparazzi()

  @get:Rule
  val instantAnimationRule = InstantAnimationsRule()

  @Test
  fun test() {
    val log = mutableListOf<String>()

    val view = ComposeView(context = paparazzi.context).apply {
      setContent {
        Animations { log += it }
      }
    }

    paparazzi.snapshot(view)
    assertThat(log)
      .containsExactly("onDraw anim=50.0", "onDraw anim=50.0", "onDraw anim=100.0")
      .inOrder()
    log.clear()

    paparazzi.gif(view, end = 30_000, fps = 30)
    assertThat(log).containsExactly("onDraw anim=50.0", "onDraw anim=50.0", "onDraw anim=100.0", "finished").inOrder()
  }
}
