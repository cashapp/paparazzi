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

import com.android.ide.common.rendering.api.Result
import com.android.layoutlib.bridge.BridgeRenderSession
import com.android.layoutlib.bridge.impl.RenderSessionImpl
import java.lang.reflect.Constructor

/*
 * Reflective access to layoutlib internals that is stable across versions but not public. Anything
 * that *differs* between versions belongs in a paparazzi-layoutlib-shim-* module instead.
 */

/** `BridgeRenderSession(RenderSessionImpl, Result)` is package-private. */
internal object BridgeRenderSessionAccessor {
  private val constructor: Constructor<*> by lazy {
    BridgeRenderSession::class.java
      .getDeclaredConstructor(RenderSessionImpl::class.java, Result::class.java)
      .apply { isAccessible = true }
  }

  fun create(renderSession: RenderSessionImpl, result: Result): BridgeRenderSession =
    try {
      constructor.newInstance(renderSession, result) as BridgeRenderSession
    } catch (e: Exception) {
      throw RuntimeException(e)
    }
}
