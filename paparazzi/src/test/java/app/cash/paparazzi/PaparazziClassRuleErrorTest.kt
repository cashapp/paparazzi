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

import android.view.View
import org.assertj.core.api.Assertions.assertThat
import org.junit.Assert.fail
import org.junit.ClassRule
import org.junit.Test
import java.lang.IllegalStateException

class PaparazziClassRuleErrorTest {
  companion object {
    @get:ClassRule
    @JvmStatic
    val classRuleOnlyPaparazzi = Paparazzi()
  }

  @Test
  fun classRuleWithoutMethodRuleErrors() {
    val view = View(classRuleOnlyPaparazzi.context)
    try {
      classRuleOnlyPaparazzi.snapshot(view)
      fail()
    } catch (expected: IllegalStateException) {
      assertThat(expected).hasMessage("To use Paparazzi as a @ClassRule, it must also be a @Rule.")
    }
  }
}

