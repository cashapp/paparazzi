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
  private val manager = SandboxManager(maxSize = 2, stagingRoot = stagingRoot)

  private val defaultConfiguration = SandboxConfiguration.default()
  private val acquiresPaparazzi = SandboxConfiguration.Builder()
    .classpath(Classpath.current())
    .withDefaults()
    .acquirePackages("app.cash.paparazzi.")
    .build()
  private val acquiresOkio = SandboxConfiguration.Builder()
    .classpath(Classpath.current())
    .withDefaults()
    .acquirePackages("okio.")
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
    val second = manager.getSandbox(acquiresPaparazzi)

    assertThat(first).isNotSameInstanceAs(second)
    assertThat(manager.size).isEqualTo(2)
  }

  @Test
  fun `cache is bounded and evicts least recently used`() {
    val first = manager.getSandbox(defaultConfiguration)
    manager.getSandbox(acquiresPaparazzi)
    manager.getSandbox(acquiresOkio)

    assertThat(manager.size).isEqualTo(2)

    // `first` was the least recently used, so it was evicted and closed.
    try {
      first.loadClass("android.view.Choreographer")
      throw AssertionError("Expected the evicted sandbox to be closed")
    } catch (expected: IllegalStateException) {
      assertThat(expected).hasMessageThat().contains("closed")
    }
  }

  @Test
  fun `access refreshes recency`() {
    val first = manager.getSandbox(defaultConfiguration)
    manager.getSandbox(acquiresPaparazzi)

    // Touch `first` so `acquiresPaparazzi` becomes the eviction candidate instead.
    manager.getSandbox(defaultConfiguration)
    manager.getSandbox(acquiresOkio)

    assertThat(first.loadClass("android.view.Choreographer").name).isEqualTo("android.view.Choreographer")
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
