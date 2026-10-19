/*
 * Copyright (C) 2026 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package app.cash.paparazzi.internal

import android.os.Handler
import android.os.Looper
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Delay
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.MainCoroutineDispatcher
import kotlinx.coroutines.android.HandlerDispatcher
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.internal.MainDispatcherFactory

/**
 * A main dispatcher that always targets the *current* layoutlib main [Looper].
 *
 * Layoutlib creates a new main Looper for every render session, but kotlinx-coroutines resolves
 * `Dispatchers.Main` once per process and binds it to whichever Looper existed at that time.
 * After the first Paparazzi test, the stock dispatcher would post work to a torn-down Looper and
 * `Dispatchers.Main.immediate` would no longer recognise the main thread.
 *
 * https://github.com/cashapp/paparazzi/issues/2382
 */
@OptIn(InternalCoroutinesApi::class)
internal class PaparazziMainDispatcher private constructor(
  private val isImmediate: Boolean
) : MainCoroutineDispatcher(), Delay {
  private var cachedLooper: Looper? = null
  private var cachedDispatcher: HandlerDispatcher? = null

  private val current: MainCoroutineDispatcher
    @Synchronized get() {
      val looper = Looper.getMainLooper()
      val dispatcher = cachedDispatcher.takeIf { looper === cachedLooper }
        ?: Handler(looper).asCoroutineDispatcher(NAME).also {
          cachedLooper = looper
          cachedDispatcher = it
        }
      return if (isImmediate) dispatcher.immediate else dispatcher
    }

  override val immediate: MainCoroutineDispatcher
    get() = if (isImmediate) this else IMMEDIATE

  override fun isDispatchNeeded(context: CoroutineContext): Boolean = current.isDispatchNeeded(context)

  override fun dispatch(context: CoroutineContext, block: Runnable) {
    current.dispatch(context, block)
  }

  override fun scheduleResumeAfterDelay(timeMillis: Long, continuation: CancellableContinuation<Unit>) {
    (current as Delay).scheduleResumeAfterDelay(timeMillis, continuation)
  }

  override fun invokeOnTimeout(timeMillis: Long, block: Runnable, context: CoroutineContext): DisposableHandle =
    (current as Delay).invokeOnTimeout(timeMillis, block, context)

  override fun toString(): String = toStringInternalImpl() ?: NAME

  internal companion object {
    private const val NAME = "Paparazzi-Main"

    val INSTANCE: PaparazziMainDispatcher = PaparazziMainDispatcher(isImmediate = false)
    private val IMMEDIATE = PaparazziMainDispatcher(isImmediate = true)
  }
}

/**
 * Installs [PaparazziMainDispatcher] as `Dispatchers.Main` via kotlinx-coroutines' ServiceLoader.
 *
 * The priority is above kotlinx-coroutines-android's factory but below kotlinx-coroutines-test's,
 * so `Dispatchers.setMain` still works and its default delegate is this dispatcher.
 */
@OptIn(InternalCoroutinesApi::class)
internal class PaparazziMainDispatcherFactory : MainDispatcherFactory {
  override val loadPriority: Int = Int.MAX_VALUE - 1

  override fun createDispatcher(allFactories: List<MainDispatcherFactory>): MainCoroutineDispatcher =
    PaparazziMainDispatcher.INSTANCE

  override fun hintOnError(): String = "Paparazzi must be on the test classpath"
}
