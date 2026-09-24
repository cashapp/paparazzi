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
 * layoutlib [16.2.3, 17.0.0): RenderAction manages the Looper; `inflate()` sizes the window frame,
 * which collapses in SHRINK mode.
 */
internal class LayoutlibShim16 : LayoutlibShim {
  override fun dispatchAnimationCallbacks(choreographer: Choreographer, frameTimeNanos: Long) =
    Choreographer_Delegate.doCallbacks(choreographer, Choreographer.CALLBACK_ANIMATION, frameTimeNanos)

  override fun resetWindowFrame(viewRootImpl: ViewRootImpl, width: Int, height: Int) =
    ViewRootImpl_Accessor.updateFrame(viewRootImpl, width, height)

  override fun renderedImage(renderSession: RenderSessionImpl): BufferedImage? = renderSession.image
}

public class LayoutlibShim16Provider : LayoutlibShimProvider {
  override val name: String = "layoutlib-shim-16.2"
  override val since: String = "16.2.3"
  override val until: String = "17.0.0"

  override fun isCompatible(): Boolean =
    hasMethod(
      "android.view.ViewRootImpl_Accessor",
      "updateFrame",
      ViewRootImpl::class.java,
      Int::class.javaPrimitiveType!!,
      Int::class.javaPrimitiveType!!
    ) &&
      hasMethod(
        "android.view.Choreographer_Delegate",
        "doCallbacks",
        Choreographer::class.java,
        Int::class.javaPrimitiveType!!,
        Long::class.javaPrimitiveType!!
      )

  override fun create(): LayoutlibShim = LayoutlibShim16()
}
