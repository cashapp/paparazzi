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
package app.cash.paparazzi.vintage

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class PaparazziVintageTestEngineTest {
  private val engine = PaparazziVintageTestEngine()

  @Test
  fun engineId_isPaparazziVintage() {
    assertThat(engine.id).isEqualTo("paparazzi-vintage")
  }

  @Test
  fun groupId_isPaparazziGroup() {
    assertThat(engine.groupId.orElse(null)).isEqualTo("app.cash.paparazzi")
  }

  @Test
  fun artifactId_isPaparazziJunit4Reporting() {
    assertThat(engine.artifactId.orElse(null)).isEqualTo("paparazzi-junit4-reporting")
  }
}
