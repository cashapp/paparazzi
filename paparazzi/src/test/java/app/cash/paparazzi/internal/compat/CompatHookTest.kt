/*
 * Copyright (C) 2026 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package app.cash.paparazzi.internal.compat

import app.cash.paparazzi.internal.LayoutlibCompat
import app.cash.paparazzi.internal.LayoutlibVersion
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class CompatHookTest {
  private val hook = compatHook<String>("test") {
    variant("old", until = "16.2.4", impl = "old")
    variant("new", since = "17.0.0", impl = "new")
    variant("fallback", impl = "fallback")
  }

  @Test
  fun selectsByVersionRange() {
    assertThat(hook.select(v("16.2.3")).impl).isEqualTo("old")
    assertThat(hook.select(v("16.2.4")).impl).isEqualTo("fallback")
    assertThat(hook.select(v("17.0.3")).impl).isEqualTo("new")
  }

  @Test
  fun unknownVersionPassesSinceAndFailsUntil() {
    assertThat(hook.select(null).impl).isEqualTo("new")
  }

  @Test
  fun skipsUnavailableVariants() {
    val probed = compatHook<String>("probed") {
      variant("missing", available = { false }, impl = "missing")
      variant("present", impl = "present")
    }
    assertThat(probed.select(v("16.2.3")).label).isEqualTo("present")
  }

  @Test
  fun failsWhenNothingMatches() {
    val none = compatHook<String>("none") { variant("only", until = "16.0.0", impl = "x") }
    val e = assertThrows(IllegalStateException::class.java) { none.select(v("17.0.0")) }
    assertThat(e).hasMessageThat().contains("tried: only")
  }

  @Test
  fun registryResolvesLazilyAndDescribes() {
    var resolutions = 0
    val registry = CompatRegistry {
      resolutions++
      v("16.2.3")
    }
    val value = registry.register(hook)
    assertThat(resolutions).isEqualTo(0)
    assertThat(value.value).isEqualTo("old")
    assertThat(registry.describe()).containsExactly("test", "old")
    assertThat(resolutions).isEqualTo(1)
  }

  @Test
  fun discoversEveryShim() {
    assertThat(LayoutlibCompat.shimProviders.map { it.name }).containsExactly(
      "layoutlib-shim-16.0",
      "layoutlib-shim-16.2",
      "layoutlib-shim-17"
    )
  }

  @Test
  fun currentLayoutlibSelectsShim() {
    // Default build pins 16.2.3.
    assertThat(LayoutlibCompat.describe()).containsExactly("shim", "layoutlib-shim-16.2")
  }

  @Test
  fun providerRangesDoNotOverlap() {
    val ranges = LayoutlibCompat.shimProviders
      .map { (it.since?.let(LayoutlibVersion::parse) ?: v("0.0.0")) to it.until?.let(LayoutlibVersion::parse) }
      .sortedBy { it.first }
    ranges.zipWithNext().forEach { (a, b) -> assertThat(a.second).isEqualTo(b.first) }
    assertThat(ranges.last().second).isNull()
  }

  private fun v(value: String) = LayoutlibVersion.parse(value)!!
}
