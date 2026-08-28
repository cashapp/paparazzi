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
  fun `unlisted packages are acquired, because acquiring is the default`() {
    // The bugs this prevents: com.android.adservices ships inside layoutlib.jar and calls
    // android.util.Log, and kotlinx.coroutines.android links android.os.Handler. Neither would
    // appear on a hand-written list of things to isolate.
    assertThat(configuration.shouldAcquire("com.android.adservices.LogUtil")).isTrue()
    assertThat(configuration.shouldAcquire("kotlinx.coroutines.android.HandlerContext")).isTrue()
    assertThat(configuration.shouldAcquire("com.example.totally.unknown.Thing")).isTrue()
  }

  @Test
  fun `androidx is acquired because it links against the framework`() {
    assertThat(configuration.shouldAcquire("androidx.compose.ui.platform.ComposeView")).isTrue()
  }

  @Test
  fun `jdk and kotlin stdlib are delegated`() {
    assertThat(configuration.shouldAcquire("java.lang.String")).isFalse()
    assertThat(configuration.shouldAcquire("javax.imageio.ImageIO")).isFalse()
    assertThat(configuration.shouldAcquire("kotlin.Unit")).isFalse()
  }

  @Test
  fun `layoutlib-api spi and its signature types are delegated`() {
    assertThat(configuration.shouldAcquire("com.android.ide.common.rendering.api.ILayoutLog")).isFalse()
    assertThat(configuration.shouldAcquire("com.android.ide.common.rendering.api.SessionParams")).isFalse()
    assertThat(configuration.shouldAcquire("com.android.resources.ResourceType")).isFalse()
    // Exposed by the types above, so they have to cross the boundary too.
    assertThat(configuration.shouldAcquire("com.google.common.collect.ListMultimap")).isFalse()
    assertThat(configuration.shouldAcquire("org.xmlpull.v1.XmlPullParser")).isFalse()
  }

  @Test
  fun `junit is delegated so assertions propagate to the runner`() {
    assertThat(configuration.shouldAcquire("org.junit.Assert")).isFalse()
  }

  @Test
  fun `longest matching prefix wins`() {
    // javax. is delegated, but layoutlib ships javax.microedition and must own it.
    assertThat(configuration.shouldAcquire("javax.imageio.ImageIO")).isFalse()
    assertThat(configuration.shouldAcquire("javax.microedition.khronos.egl.EGL")).isTrue()
  }

  @Test
  fun `default policy keeps paparazzi on the host side`() {
    assertThat(configuration.shouldAcquire("app.cash.paparazzi.Paparazzi")).isFalse()
    assertThat(configuration.shouldAcquire("app.cash.paparazzi.internal.PaparazziCallback")).isFalse()
  }

  @Test
  fun `runner policy moves paparazzi into the sandbox`() {
    val runner = SandboxConfiguration.forRunner()

    assertThat(runner.shouldAcquire("app.cash.paparazzi.Paparazzi")).isTrue()
    assertThat(runner.shouldAcquire("app.cash.paparazzi.PaparazziSdk")).isTrue()
    assertThat(runner.shouldAcquire("app.cash.paparazzi.internal.Renderer")).isTrue()
  }

  @Test
  fun `runner policy keeps the sandbox machinery on the host side`() {
    val runner = SandboxConfiguration.forRunner()

    assertThat(runner.shouldAcquire("app.cash.paparazzi.internal.sandbox.SandboxManager")).isFalse()
    assertThat(runner.shouldAcquire("app.cash.paparazzi.PaparazziRunner")).isFalse()
  }

  @Test
  fun `runner is delegated by exact name, not by prefix`() {
    // A prefix rule would also match test classes named after the runner and silently un-sandbox
    // them, which is a failure no assertion would catch.
    val runner = SandboxConfiguration.forRunner()

    assertThat(runner.shouldAcquire("app.cash.paparazzi.PaparazziRunnerTest")).isTrue()
  }

  @Test
  fun `explicit class rules beat package rules`() {
    val custom = SandboxConfiguration.Builder()
      .classpath(Classpath.current())
      .withDefaults()
      .delegateClasses("android.util.Log")
      .acquireClasses("java.util.ArrayList")
      .build()

    assertThat(custom.shouldAcquire("android.util.Log")).isFalse()
    assertThat(custom.shouldAcquire("android.util.SparseArray")).isTrue()
    assertThat(custom.shouldAcquire("java.util.ArrayList")).isTrue()
  }

  @Test
  fun `configurations with identical policy are equal so they share a cache slot`() {
    assertThat(SandboxConfiguration.default()).isEqualTo(SandboxConfiguration.default())
    assertThat(SandboxConfiguration.default().hashCode())
      .isEqualTo(SandboxConfiguration.default().hashCode())
  }

  @Test
  fun `differing policy produces unequal configurations`() {
    assertThat(SandboxConfiguration.forRunner()).isNotEqualTo(SandboxConfiguration.default())
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
