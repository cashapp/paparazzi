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
package app.cash.paparazzi

import app.cash.paparazzi.internal.PaparazziCallback
import app.cash.paparazzi.internal.PaparazziLogger
import app.cash.paparazzi.internal.SessionParamsBuilder
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

/**
 * Locking contract tests for the fix to
 * https://github.com/cashapp/paparazzi/issues/2547.
 *
 * `PaparazziSdk.prepare()` used to check `isInitialized` and then assign the companion-level
 * `renderer`/`sessionParamsBuilder` pair without any lock. Two tests running concurrently on
 * one JVM (JUnit 5 parallel execution) could both observe `!isInitialized`, which either
 * double-initialized the layoutlib `Bridge` ("Failed to init Bridge") or tore the property pair
 * (`UninitializedPropertyAccessException: lateinit property sessionParamsBuilder has not been
 * initialized"). With the lock removed again, a racing first-time init reproduces that exact
 * exception (verified while developing this fix).
 *
 * `initializeRendererOnce` now performs the check-and-assign atomically under
 * [PaparazziSdk.Companion.initializationLock].
 *
 * These tests deliberately do NOT reset the companion state or trigger a real first-time
 * initialization on a worker thread: layoutlib's `RenderAction` pins per-thread scene state,
 * so initializing from a non-main thread would poison every other test in this module's JVM.
 * Instead they verify the locking contract end-to-end with a real
 * `Paparazzi.prepare()`-driven initialization on the main thread, and a blocked-second-caller
 * scenario that fails unless the lock is actually held.
 */
class PaparazziSdkConcurrentInitTest {
  /**
   * A real `Paparazzi` prepare() drives `initializeRendererOnce` through the production path.
   * Running it twice proves the second caller reuses (does not re-create) the shared renderer
   * and builder — the exact behavior that used to race when two Paparazzi instances prepared
   * concurrently.
   */
  @Test
  fun prepareReusesSharedRendererAcrossInstances() {
    // layoutlib's RenderAction pins one scene per thread, so the two Paparazzi lifecycles
    // must not overlap — exactly like two sequential tests in the same JVM.
    val first = Paparazzi()
    // setup(TestName) drives the full production path: sdk.setup() + sdk.prepare(), which
    // routes through initializeRendererOnce and initializes the shared renderer.
    first.setup(TestName("app.cash.paparazzi", "PaparazziSdkConcurrentInitTest", "first"))
    assertThat(PaparazziSdk.Companion.isInitialized).isTrue()
    val rendererBefore = PaparazziSdk.renderer
    first.teardown()

    val second = Paparazzi()
    second.setup(TestName("app.cash.paparazzi", "PaparazziSdkConcurrentInitTest", "second"))

    // The second Paparazzi instance must not have re-created the shared Renderer; only
    // per-instance SessionParamsBuilder copies are allowed to change.
    assertThat(PaparazziSdk.renderer).isSameInstanceAs(rendererBefore)
    second.teardown()
  }

  /**
   * While one thread holds [initializationLock] inside `initializeRendererOnce`, a second
   * caller on a *different* thread must block instead of barreling through an unlocked
   * `!isInitialized` check. The production method must serialize on exactly this lock: the
   * first thread grabs the lock via a scaffold that mimics the synchronized body, and the
   * second thread (on its own executor, so pool serialization cannot mask the result) calls
   * the production `initializeRendererOnce`. If the production method did not synchronize on
   * the same lock, the second call would complete before the first thread released.
   */
  @Test
  fun secondCallerBlocksWhileInitializationIsInProgress() {
    // Ensure the shared renderer exists so the second caller cannot sneak a real
    // initialization in before the scaffold acquires the lock.
    if (!PaparazziSdk.Companion.isInitialized) {
      val bootstrap = Paparazzi()
      bootstrap.setup(TestName("app.cash.paparazzi", "PaparazziSdkConcurrentInitTest", "bootstrap"))
      bootstrap.teardown()
    }
    assertThat(PaparazziSdk.Companion.isInitialized).isTrue()

    val environment = Environment(
      appTestDir = System.getProperty("paparazzi.build.dir"),
      packageName = "app.cash.paparazzi",
      compileSdkVersion = 36,
      resourcePackageNames = listOf("app.cash.paparazzi"),
      localResourceDirs = emptyList(),
      moduleResourceDirs = emptyList(),
      libraryResourceDirs = emptyList(),
      allModuleAssetDirs = emptyList(),
      libraryAssetDirs = emptyList()
    )
    val callback = PaparazziCallback(
      PaparazziLogger(),
      environment.packageName,
      environment.resourcePackageNames
    )
    val sdk = PaparazziSdk(environment = environment, onNewFrame = { })

    val insideLock = CountDownLatch(1)
    val releaseLock = CountDownLatch(1)
    val holderExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    val callerExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    try {
      val holder: Future<*> = holderExecutor.submit {
        synchronized(PaparazziSdk.initializationLock) {
          insideLock.countDown()
          // Hold the initialization lock as a slow Renderer.prepare() would.
          assertThat(releaseLock.await(20, TimeUnit.SECONDS)).isTrue()
        }
      }
      assertThat(insideLock.await(20, TimeUnit.SECONDS)).isTrue()

      val caller = callerExecutor.submit<SessionParamsBuilder> {
        sdk.initializeRendererOnce(callback)
      }

      // The second caller cannot finish while the lock is held. If initializeRendererOnce
      // were unsynchronized (the pre-fix code), this would complete immediately.
      assertThat(isDoneWithin(caller, 300, TimeUnit.MILLISECONDS)).isFalse()

      releaseLock.countDown()
      holder.get(20, TimeUnit.SECONDS)
      // After release, the caller completes and observes fully-initialized state.
      val builder = caller.get(20, TimeUnit.SECONDS)
      assertThat(builder).isSameInstanceAs(PaparazziSdk.sessionParamsBuilder)
    } finally {
      releaseLock.countDown()
      holderExecutor.shutdownNow()
      callerExecutor.shutdownNow()
    }
  }

  private fun isDoneWithin(
    future: Future<*>,
    timeout: Long,
    unit: TimeUnit
  ): Boolean {
    val deadline = System.nanoTime() + unit.toNanos(timeout)
    while (System.nanoTime() < deadline) {
      if (future.isDone) return true
      Thread.sleep(10)
    }
    return future.isDone
  }
}
