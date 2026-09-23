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
import android.view.WindowManagerGlobal
import app.cash.paparazzi.internal.compat.BridgeRenderSessionAccessor
import app.cash.paparazzi.internal.compat.CompatRegistry
import app.cash.paparazzi.internal.compat.compatHook
import app.cash.paparazzi.layoutlib.shim.LayoutlibShim
import app.cash.paparazzi.layoutlib.shim.LayoutlibShimProvider
import com.android.ide.common.rendering.api.RenderSession
import com.android.ide.common.rendering.api.Result
import com.android.ide.common.rendering.api.SessionParams.RenderingMode
import com.android.internal.lang.System_Delegate
import com.android.layoutlib.bridge.Bridge
import com.android.layoutlib.bridge.BridgeRenderSession
import com.android.layoutlib.bridge.impl.RenderSessionImpl
import java.awt.image.BufferedImage
import java.io.File
import java.util.Properties
import java.util.ServiceLoader

/**
 * Bridges layoutlib internal API and behavior differences so Paparazzi can run against layoutlib
 * versions other than the one it was compiled against (see `paparazzi { layoutlibVersion = ... }`).
 *
 * Any layoutlib internal whose signature or behavior varies between supported versions should be
 * accessed through here rather than referenced directly.
 */
internal object LayoutlibCompat {
  private val compat: Properties = Properties().apply {
    LayoutlibCompat::class.java.getResourceAsStream("layoutlib-compat.properties")?.use(::load)
  }

  /**
   * The layoutlib version in use, passed by the Gradle plugin as `paparazzi.layoutlib.version`.
   * Falls back to the default from `libs.versions.toml` Paparazzi was built with; `null` only if
   * neither is available, in which case version-gated hooks fall back to feature detection.
   */
  val versionName: String? =
    System.getProperty("paparazzi.layoutlib.version") ?: compat.getProperty("default")

  val version: LayoutlibVersion? = versionName?.let(LayoutlibVersion::parse)

  private val registry = CompatRegistry { version }

  /** Every [LayoutlibShimProvider] on the classpath, as ServiceLoader found them. */
  val shimProviders: List<LayoutlibShimProvider> =
    ServiceLoader.load(LayoutlibShimProvider::class.java, LayoutlibCompat::class.java.classLoader).toList()

  /**
   * The shim for the running layoutlib: newest range first, so an unknown version gets the newest
   * shim whose probe passes.
   */
  private val shim: Lazy<LayoutlibShim> = registry.register(
    compatHook<Lazy<LayoutlibShim>>("shim") {
      shimProviders
        .sortedByDescending { it.since?.let(LayoutlibVersion::parse) ?: LayoutlibVersion(0, 0, 0) }
        .forEach { provider ->
          variant(provider.name, provider.since, provider.until, provider::isCompatible, lazy(provider::create))
        }
    }
  ).let { lazy { it.value.value } }

  /** Chosen variant per compat hook, for diagnostics. */
  fun describe(): Map<String, String> = registry.describe()

  private val handlerLock = Any()
  private val ICU_DATA_FILE = Regex("""icudt\d+l\.dat""")

  fun prepareThread() = shim.value.prepareThread()

  fun cleanupThread() = shim.value.cleanupThread()

  /**
   * ICU data file shipped in `layoutlib-runtime/data/icu`, per verified layoutlib version (identical
   * across OS classifiers), from `gradle/layoutlib-compat.properties`.
   *
   * Framework resources (`layoutlib-resources`) need no per-version handling: every verified version
   * ships the same `res/` layout, `values/attrs.xml`, resource types and value tags, and loads
   * cleanly through `FrameworkResourceRepository`. `./gradlew verifyLayoutlibVersion` re-checks this.
   */
  val icuDataFiles: Map<String, String> =
    compat.stringPropertyNames()
      .filter { it.endsWith(".icu") }
      .associate { it.removeSuffix(".icu") to compat.getProperty(it) }

  /**
   * Resolves the ICU data file for the current layoutlib [version]. Unknown versions (or a table
   * entry whose file is absent) fall back to the single `icudt*l.dat` present in the runtime.
   */
  fun icuDataFile(platformDataDir: File, version: String? = versionName): File {
    val icuDir = File(platformDataDir, "icu")
    icuDataFiles[version]
      ?.let { File(icuDir, it) }
      ?.takeIf { it.isFile }
      ?.let { return it }

    val candidates = icuDir.listFiles { file -> ICU_DATA_FILE.matches(file.name) }.orEmpty()
    return candidates.maxByOrNull { it.name }
      ?: error("No ICU data file (icudt*l.dat) found in $icuDir for layoutlib ${version ?: "<unknown>"}")
  }

  /**
   * The last rendered frame. layoutlib 17.0.3 dropped `BridgeRenderSession.getImage()` in favor of
   * `getRecyclableImage()` (so the `RenderSession.getImage()` default returns `null`). The
   * recyclable buffer may be reused by layoutlib once closed, so copy it before closing.
   */
  fun renderedImage(session: RenderSession): BufferedImage =
    shim.value.renderedImage(session)
      ?: error("layoutlib ${versionName ?: "<unknown>"} produced no rendered image")

  /** Root views of every window currently attached (main content, dialogs, popups). */
  fun windowViews(): List<View> = WindowManagerGlobal.getInstance().windowViews

  /**
   * Hook invoked after the content is attached and before the first frame is rendered. Applies
   * per-version render-configuration fixes.
   */
  fun beforeRender(contentView: View, sessionParamsBuilder: SessionParamsBuilder) {
    val renderingMode = sessionParamsBuilder.build().renderingMode
    if (renderingMode == RenderingMode.SHRINK) {
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
      shim.value.dispatchAnimationCallbacks(Choreographer.getInstance(), currentTimeNanos)

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

  fun createBridgeRenderSession(renderSession: RenderSessionImpl, result: Result): BridgeRenderSession =
    BridgeRenderSessionAccessor.create(renderSession, result)

  // SystemClock_Delegate#uptimeNanos() is package-private.
  // https://android.googlesource.com/platform/frameworks/layoutlib/+/refs/tags/studio-2023.2.1-rc1/bridge/src/android/os/SystemClock_Delegate.java#56
  private fun uptimeNanos() = System_Delegate.nanoTime() - System_Delegate.bootTime()

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
   * attached to a `ViewRootImpl`, or the shim doesn't reset frames (pre-16.2.3).
   */
  private fun sizeShrinkWindowFrameToDevice(contentView: View) {
    val viewRootImpl = contentView.viewRootImpl ?: return
    val displayMetrics = contentView.context.resources.displayMetrics
    shim.value.resetWindowFrame(viewRootImpl, displayMetrics.widthPixels, displayMetrics.heightPixels)
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
