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
import android.view.ViewRootImpl
import android.view.ViewRootImpl_Accessor
import com.android.layoutlib.bridge.impl.RenderSessionImpl
import java.awt.image.BufferedImage

/**
 * layoutlib [17.0.0, 17.0.1): 17.x `doCallbacks` (no frame time), but frames still come from
 * `RenderSessionImpl.getImage()`; `getRecyclableImage()` only arrives in 17.0.1.
 */
internal class LayoutlibShim170 : LayoutlibShim {
  override fun dispatchAnimationCallbacks(choreographer: Choreographer, frameTimeNanos: Long) =
    Choreographer_Delegate.doCallbacks(choreographer, Choreographer.CALLBACK_ANIMATION)

  override fun resetWindowFrame(viewRootImpl: ViewRootImpl, width: Int, height: Int) =
    ViewRootImpl_Accessor.updateFrame(viewRootImpl, width, height)

  override fun renderedImage(renderSession: RenderSessionImpl): BufferedImage? = renderSession.image
}

public class LayoutlibShim170Provider : LayoutlibShimProvider {
  override val name: String = "layoutlib-shim-17.0"
  override val since: String = "17.0.0"
  override val until: String = "17.0.1"

  override fun isCompatible(): Boolean =
    hasMethod(
      "android.view.Choreographer_Delegate",
      "doCallbacks",
      Choreographer::class.java,
      Int::class.javaPrimitiveType!!
    ) &&
      hasMethod("com.android.layoutlib.bridge.impl.RenderSessionImpl", "getImage")

  override fun create(): LayoutlibShim = LayoutlibShim170()
}
