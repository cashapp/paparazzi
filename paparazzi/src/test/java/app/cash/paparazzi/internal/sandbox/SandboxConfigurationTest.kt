/*
 * Copyright (C) 2024 Square, Inc.
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

package app.cash.paparazzi.internal.sandbox

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SandboxConfigurationTest {
  private val configuration = SandboxConfiguration.default()

  @Test
  fun `android framework classes are acquired`() {
    assertThat(configuration.shouldAcquire("android.view.View")).isTrue()
    assertThat(configuration.shouldAcquire("android.os.Build\$VERSION")).isTrue()
    assertThat(configuration.shouldAcquire("android.graphics.Bitmap")).isTrue()
  }

  @Test
  fun `layoutlib bridge is acquired`() {
    assertThat(configuration.shouldAcquire("com.android.layoutlib.bridge.Bridge")).isTrue()
    assertThat(configuration.shouldAcquire("com.android.internal.policy.PhoneWindow")).isTrue()
    assertThat(configuration.shouldAcquire("com.android.tools.layoutlib.create.NativeConfig")).isTrue()
  }

  @Test
  fun `androidx is acquired because it links against the framework`() {
    // Compose resolves android.view.View at link time. Sharing androidx with the host would bind
    // it to the host's framework copy.
    assertThat(configuration.shouldAcquire("androidx.compose.ui.platform.ComposeView")).isTrue()
  }

  @Test
  fun `jdk and kotlin are delegated`() {
    assertThat(configuration.shouldAcquire("java.lang.String")).isFalse()
    assertThat(configuration.shouldAcquire("javax.imageio.ImageIO")).isFalse()
    assertThat(configuration.shouldAcquire("kotlin.Unit")).isFalse()
  }

  @Test
  fun `layoutlib-api spi is delegated so it can cross the boundary`() {
    assertThat(configuration.shouldAcquire("com.android.ide.common.rendering.api.ILayoutLog")).isFalse()
    assertThat(configuration.shouldAcquire("com.android.ide.common.rendering.api.SessionParams")).isFalse()
    assertThat(configuration.shouldAcquire("com.android.resources.ResourceType")).isFalse()
  }

  @Test
  fun `junit is delegated so assertions propagate to the runner`() {
    assertThat(configuration.shouldAcquire("org.junit.Assert")).isFalse()
  }

  @Test
  fun `paparazzi host classes are delegated by default`() {
    assertThat(configuration.shouldAcquire("app.cash.paparazzi.Paparazzi")).isFalse()
    assertThat(configuration.shouldAcquire("app.cash.paparazzi.internal.PaparazziCallback")).isFalse()
  }

  @Test
  fun `explicit class rules beat package rules`() {
    val custom = SandboxConfiguration.Builder()
      .classpath(Classpath.current())
      .withDefaults()
      .delegateClasses("android.util.Log")
      .acquireClasses("app.cash.paparazzi.internal.Renderer")
      .build()

    assertThat(custom.shouldAcquire("android.util.Log")).isFalse()
    assertThat(custom.shouldAcquire("android.util.SparseArray")).isTrue()
    assertThat(custom.shouldAcquire("app.cash.paparazzi.internal.Renderer")).isTrue()
  }

  @Test
  fun `configurations with identical policy are equal so they share a cache slot`() {
    assertThat(SandboxConfiguration.default()).isEqualTo(SandboxConfiguration.default())
    assertThat(SandboxConfiguration.default().hashCode())
      .isEqualTo(SandboxConfiguration.default().hashCode())
  }

  @Test
  fun `differing policy produces unequal configurations`() {
    val other = SandboxConfiguration.Builder()
      .classpath(Classpath.current())
      .withDefaults()
      .acquirePackages("app.cash.paparazzi.")
      .build()

    assertThat(other).isNotEqualTo(SandboxConfiguration.default())
  }

  @Test
  fun `empty classpath is rejected`() {
    try {
      SandboxConfiguration.Builder().withDefaults().build()
      throw AssertionError("Expected IllegalArgumentException")
    } catch (expected: IllegalArgumentException) {
      assertThat(expected).hasMessageThat().contains("classpath")
    }
  }
}
