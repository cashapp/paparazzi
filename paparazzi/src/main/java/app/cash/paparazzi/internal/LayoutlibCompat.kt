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
package app.cash.paparazzi.internal

import android.os.Handler_Delegate
import android.view.Choreographer
import android.view.Choreographer_Delegate
import android.view.View
import android.view.ViewRootImpl
import android.view.ViewRootImpl_Accessor
import android.view.WindowManagerGlobal
import com.android.ide.common.rendering.api.Result
import com.android.ide.common.rendering.api.SessionParams.RenderingMode
import com.android.internal.lang.System_Delegate
import com.android.layoutlib.bridge.Bridge
import com.android.layoutlib.bridge.BridgeRenderSession
import com.android.layoutlib.bridge.impl.RenderSessionImpl
import java.io.File
import java.lang.reflect.Method

/**
 * Bridges layoutlib internal API and behavior differences so Paparazzi can run against layoutlib
 * versions other than the one it was compiled against (see `paparazzi { layoutlibVersion = ... }`).
 *
 * Any layoutlib internal whose signature or behavior varies between supported versions should be
 * accessed through here rather than referenced directly.
 */
internal object LayoutlibCompat {
  /**
   * The layoutlib version in use, passed by the Gradle plugin as `paparazzi.layoutlib.version`.
   * `null` when unknown (e.g. run outside the plugin); version-gated hooks then fall back to
   * feature detection.
   */
  val version: LayoutlibVersion? =
    System.getProperty("paparazzi.layoutlib.version")?.let(LayoutlibVersion::parse)

  // Removed in layoutlib 16.2.4; RenderAction now prepares/cleans up the main Looper itself.
  private val prepareThread: Method? = Bridge::class.java.methodOrNull("prepareThread")
  private val cleanupThread: Method? = Bridge::class.java.methodOrNull("cleanupThread")

  // (Choreographer, int, long) up to 16.x; (Choreographer, int) from 17.0.
  private val doCallbacksWithTime: Method? = Choreographer_Delegate::class.java.methodOrNull(
    "doCallbacks",
    Choreographer::class.java,
    Int::class.javaPrimitiveType!!,
    Long::class.javaPrimitiveType!!
  )
  private val doCallbacks: Method? = Choreographer_Delegate::class.java.methodOrNull(
    "doCallbacks",
    Choreographer::class.java,
    Int::class.javaPrimitiveType!!
  )

  // Added in layoutlib 16.2.3.
  private val updateFrame: Method? = ViewRootImpl_Accessor::class.java.methodOrNull(
    "updateFrame",
    ViewRootImpl::class.java,
    Int::class.javaPrimitiveType!!,
    Int::class.javaPrimitiveType!!
  )

  private val handlerLock = Any()
  private val ICU_DATA_FILE = Regex("""icudt\d+l\.dat""")

  fun prepareThread() {
    prepareThread?.invoke(null)
  }

  fun cleanupThread() {
    cleanupThread?.invoke(null)
  }

  /**
   * The ICU data file shipped in `layoutlib-runtime`. Its name encodes the ICU version
   * (`icudt76l.dat` through 16.x, `icudt78l.dat` in 17.x), so locate it rather than hardcoding.
   */
  fun icuDataFile(platformDataDir: File): File {
    val icuDir = File(platformDataDir, "icu")
    return icuDir.listFiles { file -> ICU_DATA_FILE.matches(file.name) }
      ?.maxByOrNull { it.name }
      ?: File(icuDir, "icudt76l.dat")
  }

  /** Root views of every window currently attached (main content, dialogs, popups). */
  fun windowViews(): List<View> = WindowManagerGlobal.getInstance().windowViews

  /**
   * Hook invoked after the content is attached and before the first frame is rendered. Applies
   * per-version render-configuration fixes.
   */
  fun beforeFirstFrame(contentView: View, renderingMode: RenderingMode) {
    if (renderingMode == RenderingMode.SHRINK && isAtLeast(16, 2, 3)) {
      sizeShrinkWindowFrameToDevice(contentView)
    }
  }

  /**
   * Advances layoutlib's clocks to [timeNanos], runs one frame of Handler/Choreographer work, then
   * executes [block].
   */
  fun withTime(renderSession: RenderSessionImpl, timeNanos: Long, block: () -> Unit) {
    System_Delegate.setNanosTime(0L)
    Choreographer_Delegate.sChoreographerTime = timeNanos

    // Drive Layoutlib's per-frame animation clock the way Google's deviceless harness
    // (RenderTestBase / standalone-render) does: set the render session's elapsed-frame time before
    // each render. RenderSessionImpl#render divides this by 1_000_000 into AnimatedVectorDrawable's
    // native animator (sFrameTime), so native animated-vector timing advances in lockstep with the
    // Choreographer clock for nonzero snapshot offsets.
    renderSession.setElapsedFrameTimeNanos(timeNanos)

    try {
      executeHandlerCallbacks()
      val currentTimeNanos = uptimeNanos()

      // layoutlib 16.2.3+'s Choreographer#doFrame dispatches the animation callbacks itself, but a
      // re-posted callback (dueTime = uptimeMillis()) becomes due again within the same frame and
      // fires a second time. To keep exactly one dispatch per frame (matching pre-16.2.3 behavior),
      // dispatch the animation callbacks once here (Choreographer_Delegate.doCallbacks guards
      // mCallbacksRunning) then tick doFrame with sChoreographerTime zeroed so its internal
      // dispatch finds the re-posted callbacks not-yet-due and skips them (while still signaling
      // the native HWUI layer so ripples and view animations work).
      dispatchAnimationCallbacks(currentTimeNanos)

      Choreographer_Delegate.sChoreographerTime = 0
      Choreographer_Delegate.doFrame(currentTimeNanos)

      block()
    } catch (e: Throwable) {
      Bridge.getLog().error("broken", "Failed executing Choreographer#doFrame", e, null, null)
      throw e
    }
  }

  fun executeHandlerCallbacks() {
    // Avoid ConcurrentModificationException in
    // RenderAction.currentContext.sessionInteractiveData.handlerMessageQueue.runnablesMap which is a WeakHashMap
    // https://android.googlesource.com/platform/tools/adt/idea/+/c331c9b2f4334748c55c29adec3ad1cd67e45df2/designer/src/com/android/tools/idea/uibuilder/scene/LayoutlibSceneManager.java#1558
    synchronized(handlerLock) {
      // BridgeRenderSession.executeCallbacks aggressively tears down the main Looper and
      // BridgeContext, so we call the static delegate ourselves.
      // https://android.googlesource.com/platform/frameworks/layoutlib/+/d58aa4703369e109b24419548f38b422d5a44738/bridge/src/com/android/layoutlib/bridge/BridgeRenderSession.java#171
      Handler_Delegate.executeCallbacks(uptimeNanos())
    }
  }

  // SystemClock_Delegate#uptimeNanos() is package-private.
  // https://android.googlesource.com/platform/frameworks/layoutlib/+/refs/tags/studio-2023.2.1-rc1/bridge/src/android/os/SystemClock_Delegate.java#56
  private fun uptimeNanos() = System_Delegate.nanoTime() - System_Delegate.bootTime()

  private fun dispatchAnimationCallbacks(frameTimeNanos: Long) {
    val choreographer = Choreographer.getInstance()
    when {
      doCallbacksWithTime != null ->
        doCallbacksWithTime.invoke(null, choreographer, Choreographer.CALLBACK_ANIMATION, frameTimeNanos)
      doCallbacks != null ->
        doCallbacks.invoke(null, choreographer, Choreographer.CALLBACK_ANIMATION)
      else -> error("Unsupported layoutlib: no Choreographer_Delegate.doCallbacks")
    }
  }

  /**
   * layoutlib 16.2.3's `RenderSessionImpl.inflate()` eagerly measures the content and sizes the
   * `ViewRootImpl` window frame (`mWinFrame`) to the measured content size. Paparazzi attaches the
   * test content *after* `inflate()`, so in [RenderingMode.SHRINK] — where the window shrinks to the
   * content in both dimensions — the window frame collapses to `0x0` (the empty inflate-time size)
   * and stays that way until the first `render()` re-measures it. (Other rendering modes keep the
   * device size in at least one dimension, so they never fully collapse.)
   *
   * Compose reads that stale `0x0` window frame during the first frame-clock pass, so any state
   * derived from the measured size (e.g. `AnchoredDraggableState` anchors computed in
   * `onSizeChanged`) is first resolved at `0x0` and then settles on the wrong value when the real
   * measure arrives. Resetting the frame to the device size here lets the first frame-clock measure
   * observe a sane window, matching pre-16.2.3 behavior; the subsequent `render()` re-shrinks the
   * frame to the true content size for the captured image.
   *
   * We set the frame directly rather than calling [RenderSessionImpl.measure] because that would run
   * an extra traversal/scroll pass whose process-global side effects (shared `Looper`/animation
   * state) leak into later snapshots on the same thread. No-ops if the content view is not yet
   * attached to a `ViewRootImpl`, or `ViewRootImpl_Accessor.updateFrame` is unavailable.
   */
  private fun sizeShrinkWindowFrameToDevice(contentView: View) {
    val updateFrame = updateFrame ?: return
    val viewRootImpl = contentView.viewRootImpl ?: return
    val displayMetrics = contentView.context.resources.displayMetrics
    updateFrame.invoke(null, viewRootImpl, displayMetrics.widthPixels, displayMetrics.heightPixels)
  }

  /** Unknown versions are treated as the latest, i.e. feature detection decides. */
  private fun isAtLeast(major: Int, minor: Int, patch: Int): Boolean =
    version == null || version >= LayoutlibVersion(major, minor, patch)

  private fun Class<*>.methodOrNull(name: String, vararg parameterTypes: Class<*>): Method? =
    try {
      getMethod(name, *parameterTypes)
    } catch (_: NoSuchMethodException) {
      null
    }
}

internal data class LayoutlibVersion(
  val major: Int,
  val minor: Int,
  val patch: Int
) : Comparable<LayoutlibVersion> {
  override fun compareTo(other: LayoutlibVersion): Int =
    compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch })

  override fun toString(): String = "$major.$minor.$patch"

  companion object {
    fun parse(value: String): LayoutlibVersion? {
      val parts = value.substringBefore('-').split('.').map { it.toIntOrNull() ?: return null }
      return LayoutlibVersion(parts.getOrElse(0) { 0 }, parts.getOrElse(1) { 0 }, parts.getOrElse(2) { 0 })
    }
  }
}
