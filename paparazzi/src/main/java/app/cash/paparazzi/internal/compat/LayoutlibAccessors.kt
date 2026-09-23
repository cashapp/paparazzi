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

import android.view.Choreographer
import android.view.Choreographer_Delegate
import android.view.ViewRootImpl
import android.view.ViewRootImpl_Accessor
import com.android.ide.common.rendering.api.RenderSession
import com.android.ide.common.rendering.api.Result
import com.android.layoutlib.bridge.Bridge
import com.android.layoutlib.bridge.BridgeRenderSession
import com.android.layoutlib.bridge.impl.RenderSessionImpl
import java.awt.image.BufferedImage
import java.lang.reflect.Constructor
import java.lang.reflect.Method

/*
 * Typed facades over layoutlib internals whose signature differs between supported versions.
 *
 * Each accessor resolves its member once and exposes [available] so compat hooks can gate on it.
 * Everything reflective lives here; hooks in LayoutlibCompat only compose these typed calls.
 */

/** A resolved static method, or `null` target when absent in the running layoutlib. */
internal class StaticMethod(private val method: Method?) {
  val available: Boolean get() = method != null

  fun call(vararg args: Any?): Any? = checkNotNull(method) { "layoutlib member unavailable" }.invoke(null, *args)

  companion object {
    fun of(owner: Class<*>, name: String, vararg parameterTypes: Class<*>): StaticMethod =
      StaticMethod(owner.methodOrNull(name, *parameterTypes))
  }
}

internal fun Class<*>.methodOrNull(name: String, vararg parameterTypes: Class<*>): Method? =
  try {
    getMethod(name, *parameterTypes)
  } catch (_: NoSuchMethodException) {
    null
  }

private val INT = Int::class.javaPrimitiveType!!
private val LONG = Long::class.javaPrimitiveType!!

/** `Bridge.prepareThread()` / `cleanupThread()`, removed in 16.2.4. */
internal object BridgeThreadAccessor {
  private val prepare = StaticMethod.of(Bridge::class.java, "prepareThread")
  private val cleanup = StaticMethod.of(Bridge::class.java, "cleanupThread")

  val available: Boolean get() = prepare.available && cleanup.available

  fun prepareThread() {
    prepare.call()
  }

  fun cleanupThread() {
    cleanup.call()
  }
}

/**
 * `Choreographer_Delegate.doCallbacks`: `(Choreographer, int, long)` up to 16.x, `(Choreographer, int)`
 * from 17.0.
 */
internal object ChoreographerDelegateAccessor {
  private val withTime = StaticMethod.of(
    Choreographer_Delegate::class.java,
    "doCallbacks",
    Choreographer::class.java,
    INT,
    LONG
  )
  private val withoutTime = StaticMethod.of(
    Choreographer_Delegate::class.java,
    "doCallbacks",
    Choreographer::class.java,
    INT
  )

  val hasTimedDoCallbacks: Boolean get() = withTime.available
  val hasUntimedDoCallbacks: Boolean get() = withoutTime.available

  fun doCallbacks(choreographer: Choreographer, callbackType: Int, frameTimeNanos: Long) {
    withTime.call(choreographer, callbackType, frameTimeNanos)
  }

  fun doCallbacks(choreographer: Choreographer, callbackType: Int) {
    withoutTime.call(choreographer, callbackType)
  }
}

/** `ViewRootImpl_Accessor.updateFrame(ViewRootImpl, int, int)`, added in 16.2.3. */
internal object ViewRootImplAccessorCompat {
  private val updateFrame = StaticMethod.of(
    ViewRootImpl_Accessor::class.java,
    "updateFrame",
    ViewRootImpl::class.java,
    INT,
    INT
  )

  val available: Boolean get() = updateFrame.available

  fun updateFrame(viewRootImpl: ViewRootImpl, width: Int, height: Int) {
    updateFrame.call(viewRootImpl, width, height)
  }
}

/**
 * `RenderSession.getRecyclableImage()` + `RecyclableImage`, which replace `getImage()` in 17.0.3.
 * Paparazzi's pinned layoutlib-api predates both, so they're resolved by name.
 */
internal object RecyclableImageAccessor {
  private val recyclableImageClass: Class<*>? =
    try {
      Class.forName("com.android.ide.common.rendering.api.RecyclableImage")
    } catch (_: ClassNotFoundException) {
      null
    }
  private val getImage: Method? = recyclableImageClass?.methodOrNull("getImage")

  fun supports(session: RenderSession): Boolean =
    getImage != null && session.javaClass.methodOrNull("getRecyclableImage") != null

  /** Copies the recyclable frame (layoutlib may reuse the buffer once closed), then closes it. */
  fun copyImage(session: RenderSession): BufferedImage? {
    val recyclable = session.javaClass.methodOrNull("getRecyclableImage")?.invoke(session) ?: return null
    return try {
      (checkNotNull(getImage).invoke(recyclable) as BufferedImage).copy()
    } finally {
      (recyclable as AutoCloseable).close()
    }
  }

  private fun BufferedImage.copy(): BufferedImage {
    val copyType = if (type == BufferedImage.TYPE_CUSTOM) BufferedImage.TYPE_INT_ARGB else type
    val copy = BufferedImage(width, height, copyType)
    val g = copy.createGraphics()
    try {
      g.drawImage(this, 0, 0, null)
    } finally {
      g.dispose()
    }
    return copy
  }
}

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
