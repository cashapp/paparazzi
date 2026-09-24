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
package app.cash.paparazzi.layoutlib.shim

import android.view.Choreographer
import android.view.Choreographer_Delegate
import com.android.layoutlib.bridge.Bridge
import com.android.layoutlib.bridge.impl.RenderSessionImpl
import java.awt.image.BufferedImage

/** layoutlib [16.0.0, 16.2.3): Paparazzi owns Looper setup; no inflate-time window sizing. */
internal class LayoutlibShim16Legacy : LayoutlibShim {
  override fun prepareThread() = Bridge.prepareThread()

  override fun cleanupThread() = Bridge.cleanupThread()

  override fun dispatchAnimationCallbacks(choreographer: Choreographer, frameTimeNanos: Long) =
    Choreographer_Delegate.doCallbacks(choreographer, Choreographer.CALLBACK_ANIMATION, frameTimeNanos)

  override fun renderedImage(renderSession: RenderSessionImpl): BufferedImage? = renderSession.image
}

public class LayoutlibShim16LegacyProvider : LayoutlibShimProvider {
  override val name: String = "layoutlib-shim-16.0"
  override val since: String = "16.0.0"
  override val until: String = "16.2.3"

  override fun isCompatible(): Boolean = hasMethod("com.android.layoutlib.bridge.Bridge", "prepareThread")

  override fun create(): LayoutlibShim = LayoutlibShim16Legacy()
}
