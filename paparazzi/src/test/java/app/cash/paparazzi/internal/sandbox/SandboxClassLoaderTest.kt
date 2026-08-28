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
import org.junit.After
import org.junit.Test

class SandboxClassLoaderTest {
  private val sandboxes = mutableListOf<Sandbox>()
  private val stagingRoot = NativeLibraryStaging.defaultStagingRoot()

  private fun newSandbox(configuration: SandboxConfiguration = SandboxConfiguration.default()): Sandbox =
    Sandbox.create(configuration = configuration, stagingRoot = stagingRoot)
      .also { sandboxes += it }

  @After
  fun tearDown() {
    sandboxes.forEach(Sandbox::close)
  }

  @Test
  fun `acquired classes are distinct from the host copy`() {
    // initialize = false matters: nearly every framework class runs JNI in <clinit>, which is
    // unavailable until Bridge.init. Class identity does not require initialisation.
    val name = "android.os._Original_Build"
    val sandboxed = newSandbox().loadClass(name)
    val host = Class.forName(name, false, javaClass.classLoader)

    assertThat(sandboxed).isNotSameInstanceAs(host)
    assertThat(sandboxed.name).isEqualTo(name)
  }

  @Test
  fun `two sandboxes get distinct copies of the same class`() {
    val first = newSandbox().loadClass("android.view.View")
    val second = newSandbox().loadClass("android.view.View")

    assertThat(first).isNotSameInstanceAs(second)
    assertThat(first.classLoader).isNotSameInstanceAs(second.classLoader)
  }

  @Test
  fun `delegated classes are shared with the host`() {
    val sandbox = newSandbox()

    assertThat(sandbox.loadClass("java.lang.String")).isSameInstanceAs(String::class.java)
    assertThat(sandbox.loadClass("com.android.ide.common.rendering.api.ILayoutLog"))
      .isSameInstanceAs(com.android.ide.common.rendering.api.ILayoutLog::class.java)
  }

  @Test
  fun `acquired class is loaded by the sandbox loader`() {
    val sandbox = newSandbox()

    assertThat(sandbox.loadClass("android.view.Choreographer").classLoader)
      .isSameInstanceAs(sandbox.classLoader)
  }

  @Test
  fun `repeated loads return the same class within one sandbox`() {
    val sandbox = newSandbox()
    val name = "android.view.Choreographer"

    assertThat(sandbox.loadClass(name)).isSameInstanceAs(sandbox.loadClass(name))
  }

  @Test
  fun `acquired classes get independent static fields`() {
    val first = newSandbox()
    val second = newSandbox()

    // Reading the value would run <clinit>, which needs JNI. Class identity is enough to show the
    // statics cannot alias; LayoutlibSandboxTest proves the behavioural half.
    fun densityField(sandbox: Sandbox) =
      sandbox.loadClass("android.graphics.Bitmap").getDeclaredField("sDefaultDensity")

    assertThat(densityField(first)).isNotEqualTo(densityField(second))
    assertThat(densityField(first).declaringClass)
      .isNotSameInstanceAs(densityField(second).declaringClass)
  }

  @Test
  fun `loading is lazy - constructing a sandbox acquires nothing`() {
    val sandbox = newSandbox()

    assertThat(sandbox.classLoader.acquiredClassNames()).isEmpty()

    sandbox.loadClass("android.view.Choreographer")

    assertThat(sandbox.classLoader.acquiredClassNames()).contains("android.view.Choreographer")
  }

  @Test
  fun `delegated class loads do not count as acquired`() {
    val sandbox = newSandbox()

    sandbox.loadClass("java.lang.String")

    assertThat(sandbox.classLoader.acquiredClassNames()).doesNotContain("java.lang.String")
  }

  @Test
  fun `runOnSandbox swaps and restores the context class loader`() {
    val sandbox = newSandbox()
    val before = Thread.currentThread().contextClassLoader

    val inside = sandbox.runOnSandbox { Thread.currentThread().contextClassLoader }

    assertThat(inside).isSameInstanceAs(sandbox.classLoader)
    assertThat(Thread.currentThread().contextClassLoader).isSameInstanceAs(before)
  }

  @Test
  fun `runOnSandbox restores the context class loader after a failure`() {
    val sandbox = newSandbox()
    val before = Thread.currentThread().contextClassLoader

    try {
      sandbox.runOnSandbox { throw IllegalStateException("boom") }
    } catch (ignored: IllegalStateException) {
    }

    assertThat(Thread.currentThread().contextClassLoader).isSameInstanceAs(before)
  }

  @Test
  fun `closed sandbox rejects further loads`() {
    val sandbox = newSandbox()
    sandbox.close()

    try {
      sandbox.loadClass("android.view.Choreographer")
      throw AssertionError("Expected IllegalStateException")
    } catch (expected: IllegalStateException) {
      assertThat(expected).hasMessageThat().contains("closed")
    }
  }

  @Test
  fun `unknown class still fails`() {
    try {
      newSandbox().loadClass("android.does.not.Exist")
      throw AssertionError("Expected ClassNotFoundException")
    } catch (expected: ClassNotFoundException) {
      assertThat(expected).hasMessageThat().contains("android.does.not.Exist")
    }
  }
}
