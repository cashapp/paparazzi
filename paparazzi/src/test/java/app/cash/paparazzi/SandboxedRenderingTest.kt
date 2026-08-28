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
package app.cash.paparazzi

import android.graphics.Canvas
import android.view.View
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Renders a real snapshot through [PaparazziRunner], and asserts it happened inside a sandbox.
 *
 * Deliberately named so it does not begin with "PaparazziRunner": the runner's configuration
 * delegates that exact class name, and a prefix match would quietly un-sandbox this test.
 */
@RunWith(PaparazziRunner::class)
class SandboxedRenderingTest {
  @get:Rule
  val testRule = PaparazziTestRule()

  private val paparazzi get() = testRule.paparazzi

  @Test
  fun testClassItselfIsLoadedBySandbox() {
    assertThat(javaClass.classLoader!!.javaClass.name)
      .isEqualTo("app.cash.paparazzi.internal.sandbox.SandboxClassLoader")
  }

  @Test
  fun androidFrameworkIsSandboxed() {
    // If View came from the application loader, the runner failed to bootstrap the test class and
    // everything below would be testing the old shared-namespace behaviour.
    assertThat(View::class.java.classLoader!!.javaClass.name)
      .isEqualTo("app.cash.paparazzi.internal.sandbox.SandboxClassLoader")
  }

  @Test
  fun paparazziItselfIsSandboxed() {
    // Acquired, so the sandbox has its own PaparazziSdk statics rather than the host's.
    assertThat(Paparazzi::class.java.classLoader!!.javaClass.name)
      .isEqualTo("app.cash.paparazzi.internal.sandbox.SandboxClassLoader")
  }

  @Test
  fun junitIsSharedWithTheHost() {
    // Delegated, which is what lets Statement/Description cross the boundary and reporting work.
    assertThat(Test::class.java.classLoader!!.javaClass.name)
      .isNotEqualTo("app.cash.paparazzi.internal.sandbox.SandboxClassLoader")
  }

  @Test
  fun rendersInsideTheSandbox() {
    val log = mutableListOf<String>()

    val view = object : View(paparazzi.context) {
      override fun onDraw(canvas: Canvas) {
        log += "onDraw"
      }
    }

    paparazzi.snapshot(view)

    assertThat(log).isNotEmpty()
  }

  @Test
  fun rendersAViewHierarchy() {
    val textView = TextView(paparazzi.context).apply { text = "sandboxed" }

    paparazzi.snapshot(textView)

    assertThat(textView.text.toString()).isEqualTo("sandboxed")
  }

  @Test
  fun editModeInterceptorAppliesToTheSandboxCopyOfView() {
    // InterceptorRegistrar redefines View#isInEditMode. If it had targeted the system loader it
    // would have instrumented the host's View and left this one returning true.
    val view = View(paparazzi.context)

    assertThat(view.isInEditMode).isFalse()
  }

  @Test
  fun composableLambdaIsDefinedInsideTheSandbox() {
    // This is the case a TestRule cannot handle, and the whole reason PaparazziRunner exists.
    // A lambda compiled into a host-loaded test class captures the host's Compose classes; handing
    // it to a sandbox holding its own copy fails with ClassCastException. Because the runner
    // reloaded this test class, the lambda below is itself a sandbox class.
    //
    // Actually rendering Compose needs AGP-generated R classes, which this plain JVM module has
    // no way to produce; see :sample for the end-to-end Compose render.
    val content: @Composable () -> Unit = { }

    assertThat(content.javaClass.classLoader!!.javaClass.name)
      .isEqualTo("app.cash.paparazzi.internal.sandbox.SandboxClassLoader")
  }

  @Test
  fun composeViewResolvesTheSandboxFramework() {
    // ComposeView extends android.view.ViewGroup. If androidx were shared with the host it would
    // link against the host's framework and this would not even load.
    assertThat(ComposeView::class.java.classLoader!!.javaClass.name)
      .isEqualTo("app.cash.paparazzi.internal.sandbox.SandboxClassLoader")
    assertThat(View::class.java.isAssignableFrom(ComposeView::class.java)).isTrue()
  }
}
