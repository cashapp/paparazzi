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
import com.android.ide.common.rendering.api.RenderSession
import java.awt.image.BufferedImage

/**
 * layoutlib 17.0.0+: `doCallbacks` drops the frame-time argument; 17.0.3 drops
 * `BridgeRenderSession.getImage()` for `getRecyclableImage()`.
 */
internal class LayoutlibShim17 : LayoutlibShim {
  override fun dispatchAnimationCallbacks(choreographer: Choreographer, frameTimeNanos: Long) =
    Choreographer_Delegate.doCallbacks(choreographer, Choreographer.CALLBACK_ANIMATION)

  override fun resetWindowFrame(viewRootImpl: ViewRootImpl, width: Int, height: Int) =
    ViewRootImpl_Accessor.updateFrame(viewRootImpl, width, height)

  // 17.0.0-17.0.2 still implement getImage(). RecyclableImage (layoutlib-api 32.3.0+) is only
  // touched once getImage() comes back null, so older layoutlib-api jars never load it.
  override fun renderedImage(session: RenderSession): BufferedImage? = session.image ?: RecyclableFrames.copy(session)
}

private object RecyclableFrames {
  /** The recyclable buffer may be reused by layoutlib once closed, so copy it first. */
  fun copy(session: RenderSession): BufferedImage? =
    session.recyclableImage?.use { recyclable ->
      val image = recyclable.image
      val copyType = if (image.type == BufferedImage.TYPE_CUSTOM) BufferedImage.TYPE_INT_ARGB else image.type
      BufferedImage(image.width, image.height, copyType).also { copy ->
        val g = copy.createGraphics()
        try {
          g.drawImage(image, 0, 0, null)
        } finally {
          g.dispose()
        }
      }
    }
}

public class LayoutlibShim17Provider : LayoutlibShimProvider {
  override val name: String = "layoutlib-shim-17"
  override val since: String = "17.0.0"
  override val until: String? = null

  override fun isCompatible(): Boolean =
    hasMethod(
      "android.view.Choreographer_Delegate",
      "doCallbacks",
      Choreographer::class.java,
      Int::class.javaPrimitiveType!!
    )

  override fun create(): LayoutlibShim = LayoutlibShim17()
}
