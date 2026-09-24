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
import android.view.ViewRootImpl
import com.android.layoutlib.bridge.impl.RenderSessionImpl
import java.awt.image.BufferedImage

/**
 * Internal Paparazzi SPI: layoutlib operations whose signature or behavior differs between
 * versions. Each implementation lives in its own module compiled against a layoutlib inside its
 * supported range, so it calls version-specific internals directly instead of by reflection.
 *
 * Only types stable across every supported layoutlib may appear in this interface.
 */
public interface LayoutlibShim {
  /** Main-Looper setup before a render action; no-op where RenderAction does it itself. */
  public fun prepareThread() {}

  public fun cleanupThread() {}

  /** Runs one dispatch of `Choreographer.CALLBACK_ANIMATION` callbacks at [frameTimeNanos]. */
  public fun dispatchAnimationCallbacks(choreographer: Choreographer, frameTimeNanos: Long)

  /**
   * Resets the window frame before the first SHRINK render (see LayoutlibCompat.beforeRender).
   * No-op for versions whose `inflate()` doesn't size the frame.
   */
  public fun resetWindowFrame(viewRootImpl: ViewRootImpl, width: Int, height: Int) {}

  /**
   * The last frame rendered by [renderSession], owned by the caller (safe to keep after the next
   * render). `RenderSessionImpl.getImage()` up to 16.x; `getRecyclableImage()` from 17.0.
   */
  public fun renderedImage(renderSession: RenderSessionImpl): BufferedImage?
}

/**
 * Discovered with [java.util.ServiceLoader]. Loading a provider must not link any
 * version-specific layoutlib member; only [create] may do so.
 */
public interface LayoutlibShimProvider {
  public val name: String

  /** Inclusive lower bound (e.g. `"16.2.3"`), or `null` for none. */
  public val since: String?

  /** Exclusive upper bound, or `null` for none. */
  public val until: String?

  /**
   * Cheap reflective probe that the running layoutlib has what this shim links against. Guards
   * against a mislabelled version and decides for unknown versions.
   */
  public fun isCompatible(): Boolean

  public fun create(): LayoutlibShim
}

/** Helper for [LayoutlibShimProvider.isCompatible] probes. */
public fun hasMethod(className: String, name: String, vararg parameterTypes: Class<*>): Boolean =
  try {
    Class.forName(className).getMethod(name, *parameterTypes)
    true
  } catch (_: ReflectiveOperationException) {
    false
  } catch (_: LinkageError) {
    false
  }
