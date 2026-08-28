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

class SandboxManagerTest {
  private val stagingRoot = NativeLibraryStaging.defaultStagingRoot()
  private val manager = SandboxManager(limit = 2, stagingRoot = stagingRoot)

  private val defaultConfiguration = SandboxConfiguration.default()
  private val runnerConfiguration = SandboxConfiguration.forRunner()
  private val delegatesOkio = SandboxConfiguration.Builder()
    .classpath(Classpath.current())
    .withDefaults()
    .delegatePackages("okio.")
    .build()

  @After
  fun tearDown() {
    manager.close()
  }

  @Test
  fun `same configuration reuses the sandbox`() {
    val first = manager.getSandbox(defaultConfiguration)
    val second = manager.getSandbox(SandboxConfiguration.default())

    assertThat(first).isSameInstanceAs(second)
    assertThat(manager.size).isEqualTo(1)
  }

  @Test
  fun `different configurations get different sandboxes`() {
    val first = manager.getSandbox(defaultConfiguration)
    val second = manager.getSandbox(runnerConfiguration)

    assertThat(first).isNotSameInstanceAs(second)
    assertThat(manager.size).isEqualTo(2)
  }

  @Test
  fun `sandboxes are never evicted, because eviction would free nothing`() {
    val first = manager.getSandbox(defaultConfiguration)
    manager.getSandbox(runnerConfiguration)

    // With an LRU cache `first` would have been evicted here. Its memory would not have come back,
    // so evicting would only have forced a second allocation later.
    assertThat(manager.size).isEqualTo(2)
    assertThat(first.loadClass("android.view.Choreographer").name)
      .isEqualTo("android.view.Choreographer")
  }

  @Test
  fun `exceeding the limit fails loudly instead of degrading`() {
    manager.getSandbox(defaultConfiguration)
    manager.getSandbox(runnerConfiguration)

    try {
      manager.getSandbox(delegatesOkio)
      throw AssertionError("Expected IllegalStateException")
    } catch (expected: IllegalStateException) {
      assertThat(expected).hasMessageThat().contains("Refusing to create more than 2")
      assertThat(expected).hasMessageThat().contains("forkEvery")
    }
  }

  @Test
  fun `close releases every sandbox`() {
    val sandbox = manager.getSandbox(defaultConfiguration)

    manager.close()

    assertThat(manager.size).isEqualTo(0)
    try {
      sandbox.loadClass("android.view.Choreographer")
      throw AssertionError("Expected the sandbox to be closed")
    } catch (expected: IllegalStateException) {
      assertThat(expected).hasMessageThat().contains("closed")
    }
  }
}
