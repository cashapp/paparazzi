/*
 * Copyright (C) 2019 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package app.cash.paparazzi

import android.animation.AnimationHandler
import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.os.Handler
import android.os.Handler_Delegate
import android.os.Looper_Accessor
import android.util.AttributeSet
import android.util.DisplayMetrics
import android.view.BridgeInflater
import android.view.Choreographer
import android.view.Choreographer_Delegate
import android.view.LayoutInflater
import android.view.View
import android.view.View.NO_ID
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams
import androidx.activity.setViewTreeOnBackPressedDispatcherOwner
import androidx.annotation.LayoutRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Recomposer
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.WindowRecomposerPolicy
import androidx.compose.ui.platform.createLifecycleAwareWindowRecomposer
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import app.cash.paparazzi.accessibility.AccessibilityRenderExtension
import app.cash.paparazzi.agent.InterceptorRegistrar
import app.cash.paparazzi.internal.AnimationSeeker
import app.cash.paparazzi.internal.ImageUtils
import app.cash.paparazzi.internal.PaparazziCallback
import app.cash.paparazzi.internal.PaparazziLifecycleOwner
import app.cash.paparazzi.internal.PaparazziLogger
import app.cash.paparazzi.internal.PaparazziOnBackPressedDispatcherOwner
import app.cash.paparazzi.internal.PaparazziSavedStateRegistryOwner
import app.cash.paparazzi.internal.Renderer
import app.cash.paparazzi.internal.SessionParamsBuilder
import app.cash.paparazzi.internal.interceptors.EditModeInterceptor
import app.cash.paparazzi.internal.layoutlib.LayoutlibPatch
import app.cash.paparazzi.internal.layoutlib.RenderSizingState
import app.cash.paparazzi.internal.parsers.LayoutPullParser
import com.android.ide.common.rendering.api.RenderSession
import com.android.ide.common.rendering.api.Result
import com.android.ide.common.rendering.api.Result.Status.ERROR_UNKNOWN
import com.android.ide.common.rendering.api.SessionParams
import com.android.ide.common.rendering.api.SessionParams.RenderingMode
import com.android.ide.common.rendering.api.SessionParams.RenderingMode.SizeAction
import com.android.internal.lang.System_Delegate
import com.android.layoutlib.bridge.Bridge
import com.android.layoutlib.bridge.BridgeRenderSession
import com.android.layoutlib.bridge.impl.RenderAction
import com.android.layoutlib.bridge.impl.RenderSessionImpl
import com.android.resources.ScreenOrientation
import com.android.resources.ScreenRound
import com.android.tools.idea.validator.LayoutValidator
import com.android.tools.idea.validator.ValidatorData.Level
import com.android.tools.idea.validator.ValidatorData.Policy
import com.android.tools.idea.validator.ValidatorData.Type
import net.bytebuddy.agent.ByteBuddyAgent
import java.awt.geom.Ellipse2D
import java.awt.image.BufferedImage
import java.util.EnumSet
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.android.asCoroutineDispatcher

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
public class PaparazziSdk @JvmOverloads constructor(
  private val environment: Environment = detectEnvironment(),
  private val deviceConfig: DeviceConfig = DeviceConfig.NEXUS_5,
  private val theme: String = "android:Theme.Material.NoActionBar.Fullscreen",
  private val renderingMode: RenderingMode = RenderingMode.NORMAL,
  private val appCompatEnabled: Boolean = true,
  private val renderExtensions: Set<RenderExtension> = setOf(),
  private val supportsRtl: Boolean = false,
  private val showSystemUi: Boolean = false,
  private val useDeviceResolution: Boolean = false,
  private val onNewFrame: (BufferedImage) -> Unit
) {
  private var validateAccessibility = false

  @Deprecated(
    "validateAccessibility is deprecated. " +
      "Use the AccessibilityRenderExtension for accessibility testing instead."
  )
  public constructor(
    environment: Environment = detectEnvironment(),
    deviceConfig: DeviceConfig = DeviceConfig.NEXUS_5,
    theme: String = "android:Theme.Material.NoActionBar.Fullscreen",
    renderingMode: RenderingMode = RenderingMode.NORMAL,
    appCompatEnabled: Boolean = true,
    renderExtensions: Set<RenderExtension> = setOf(),
    supportsRtl: Boolean = false,
    showSystemUi: Boolean = false,
    validateAccessibility: Boolean = false,
    useDeviceResolution: Boolean = false,
    onNewFrame: (BufferedImage) -> Unit
  ) : this(
    environment,
    deviceConfig,
    theme,
    renderingMode,
    appCompatEnabled,
    renderExtensions,
    supportsRtl,
    showSystemUi,
    useDeviceResolution,
    onNewFrame
  ) {
    this.validateAccessibility = validateAccessibility
  }

  private val logger = PaparazziLogger()

  /** The time of the last frame [withTime] ran, so it can tell when a new snapshot rewinds the clock. */
  private var lastFrameNanos = 0L
  private lateinit var sessionParams: SessionParams
  private lateinit var renderSession: RenderSessionImpl
  private lateinit var bridgeRenderSession: RenderSession

  public val layoutInflater: LayoutInflater
    get() = RenderAction.getCurrentContext().getSystemService("layout_inflater") as BridgeInflater

  public val resources: Resources
    get() = RenderAction.getCurrentContext().resources

  public val context: Context
    get() = RenderAction.getCurrentContext()

  public fun setup() {
    if (!isInitialized) {
      registerViewEditModeInterception()

      LayoutlibPatch.install(ByteBuddyAgent.install())
      InterceptorRegistrar.registerMethodInterceptors()
    }
  }

  public fun prepare() {
    RenderSizingState.reset()

    val layoutlibCallback =
      PaparazziCallback(logger, environment.packageName, environment.resourcePackageNames)
    layoutlibCallback.initResources()

    if (!isInitialized) {
      renderer = Renderer(environment, layoutlibCallback, logger)
      sessionParamsBuilder = renderer.prepare()
    }
    forcePlatformSdkVersion(environment.compileSdkVersion)

    sessionParamsBuilder = sessionParamsBuilder
      .copy(
        layoutPullParser = LayoutPullParser.createFromString(contentRoot(renderingMode)),
        deviceConfig = deviceConfig.updateIfAccessibilityTest(),
        renderingMode = renderingMode,
        supportsRtl = supportsRtl,
        decor = showSystemUi,
        logger = logger
      )
      .withTheme(theme)

    sessionParams = sessionParamsBuilder.build()
    renderSession = createRenderSession(sessionParams)
    renderSession.init(sessionParams.timeout)
    Bitmap.setDefaultDensity(DisplayMetrics.DENSITY_DEVICE_STABLE)

    // requires LayoutInflater to be created, which is a side-effect of RenderSessionImpl.init()
    if (appCompatEnabled) {
      initializeAppCompatIfPresent()
    }

    bridgeRenderSession = createBridgeSession(renderSession, renderSession.inflate())
    // inflate() has now loaded every class the patch targets.
    LayoutlibPatch.verifyApplied()
    // inflate() runs a real ViewRootImpl traversal, which instantiates the AnimationHandler
    // before any test code runs. Keep the "no handler outside a snapshot" invariant.
    AnimationHandler.sAnimatorHandler.set(null)
  }

  public fun teardown() {
    renderSession.release()
    bridgeRenderSession.dispose()
    Looper_Accessor.cleanupThread()

    renderer.dumpDelegates()
    logger.assertNoErrors()
  }

  public fun <V : View> inflate(@LayoutRes layoutId: Int): V = layoutInflater.inflate(layoutId, null) as V

  public fun snapshot(composable: @Composable () -> Unit) {
    val hostView = ComposeView(context).apply {
      layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
    }
    hostView.setContent(composable)

    snapshot(hostView)
  }

  /**
   * Snapshots [composable] at [offsetMillis].
   *
   * With [seekAnimations], supported Compose animations are seeked to [offsetMillis] the way Android
   * Studio's Animation Preview does, instead of being advanced by the frame clock. This requires
   * `androidx.compose.ui:ui-tooling` and `androidx.compose.animation:animation-tooling-internal` on
   * the test classpath, and composes with `LocalInspectionMode` set to true. Animations that cannot
   * be seeked, such as `Animatable`, stay at time 0.
   */
  @JvmOverloads
  public fun snapshot(offsetMillis: Long, seekAnimations: Boolean = false, composable: @Composable () -> Unit) {
    val nanos = TimeUnit.MILLISECONDS.toNanos(offsetMillis)
    withComposeHost(seekAnimations, composable) { hostView, seeker ->
      takeSnapshots(hostView, nanos, -1, 1, seeker)
    }
  }

  /** Records [composable] from [start] to [end]. See [snapshot] for [seekAnimations]. */
  @JvmOverloads
  public fun gif(
    start: Long = 0L,
    end: Long = 500L,
    fps: Int = 30,
    seekAnimations: Boolean = false,
    composable: @Composable () -> Unit
  ) {
    withComposeHost(seekAnimations, composable) { hostView, seeker ->
      takeSnapshots(hostView, TimeUnit.MILLISECONDS.toNanos(start), fps, frameCount(start, end, fps), seeker)
    }
  }

  private fun withComposeHost(
    seekAnimations: Boolean,
    composable: @Composable () -> Unit,
    block: (View, AnimationSeeker?) -> Unit
  ) {
    val seeker = if (seekAnimations) AnimationSeeker() else null
    val hostView = ComposeView(context).apply {
      layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
      if (seeker != null) setContent { seeker.Content(composable) } else setContent(composable)
    }
    try {
      block(hostView, seeker)
    } finally {
      seeker?.dispose()
    }
  }

  @JvmOverloads
  public fun snapshot(view: View, offsetMillis: Long = 0L) {
    takeSnapshots(view, TimeUnit.MILLISECONDS.toNanos(offsetMillis), -1, 1)
  }

  @JvmOverloads
  public fun gif(view: View, start: Long = 0L, end: Long = 500L, fps: Int = 30) {
    takeSnapshots(view, TimeUnit.MILLISECONDS.toNanos(start), fps, frameCount(start, end, fps))
  }

  // Add one to the frame count so we get the last frame. Otherwise a 1 second, 60 FPS animation
  // our 60th frame will be at time 983 ms, and we want our last frame to be 1,000 ms. This gets
  // us 61 frames for a 1 second animation, 121 frames for a 2 second animation, etc.
  private fun frameCount(start: Long, end: Long, fps: Int): Int = ((end - start).toInt() * fps) / 1000 + 1

  public fun unsafeUpdateConfig(
    deviceConfig: DeviceConfig? = null,
    theme: String? = null,
    renderingMode: RenderingMode? = null
  ) {
    require(deviceConfig != null || theme != null || renderingMode != null) {
      "Calling unsafeUpdateConfig requires at least one non-null argument."
    }

    logger.flushErrors()
    renderSession.release()
    bridgeRenderSession.dispose()
    Looper_Accessor.cleanupThread()

    sessionParamsBuilder = sessionParamsBuilder
      .copy(
        // Required to reset underlying parser stream
        layoutPullParser = LayoutPullParser.createFromString(contentRoot(renderingMode ?: this.renderingMode))
      )

    if (deviceConfig != null) {
      sessionParamsBuilder = sessionParamsBuilder.copy(
        deviceConfig = deviceConfig.updateIfAccessibilityTest()
      )
    }

    if (theme != null) {
      sessionParamsBuilder = sessionParamsBuilder.withTheme(theme)
    }

    if (renderingMode != null) {
      sessionParamsBuilder = sessionParamsBuilder.copy(renderingMode = renderingMode)
    }

    sessionParams = sessionParamsBuilder.build()
    renderSession = createRenderSession(sessionParams)
    renderSession.init(sessionParams.timeout)
    Bitmap.setDefaultDensity(DisplayMetrics.DENSITY_DEVICE_STABLE)
    bridgeRenderSession = createBridgeSession(renderSession, renderSession.inflate())
    // inflate() runs a real ViewRootImpl traversal, which instantiates the AnimationHandler
    // before any test code runs. Keep the "no handler outside a snapshot" invariant.
    AnimationHandler.sAnimatorHandler.set(null)
  }

  private fun takeSnapshots(view: View, startNanos: Long, fps: Int, frameCount: Int, seeker: AnimationSeeker? = null) {
    val viewGroup = bridgeRenderSession.rootViews[0].viewObject as ViewGroup
    val modifiedView = renderExtensions.fold(view) { currentView, renderExtension ->
      val currentSessionRenderingMode = sessionParams.renderingMode
      if (currentSessionRenderingMode == RenderingMode.SHRINK && renderExtension is AccessibilityRenderExtension) {
        throw IllegalStateException(
          "AccessibilityRenderExtension cannot be used with the SHRINK rendering mode. " +
            "See https://github.com/cashapp/paparazzi/issues/1350 for more context."
        )
      } else {
        renderExtension.renderView(currentView)
      }
    }

    System_Delegate.setNanosTime(0L)
    System_Delegate.setBootTimeNanos(0L)

    // Set up an UncaughtExceptionHandler to ensure that uncaught exceptions are propagated to the
    // test framework rather than being silently swallowed. See https://github.com/cashapp/paparazzi/issues/2127
    val previousUncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
      logger.error(throwable, throwable.message)
      previousUncaughtExceptionHandler?.uncaughtException(thread, throwable)
    }

    lateinit var lifecycleOwner: PaparazziLifecycleOwner

    try {
      AnimationHandler.getInstance().setProvider(SingleDispatchFrameCallbackProvider)
      withTime(0L) {
        // Initialize the choreographer at time=0.
      }

      // The consumer may not have compose runtime on the classpath, so we don't reference the type.
      var recomposer: Any? = null

      if (hasComposeRuntime) {
        // During onAttachedToWindow, AbstractComposeView will attempt to resolve its parent's
        // CompositionContext, which requires first finding the "content view", then using that
        // to find a root view with a ViewTreeLifecycleOwner
        viewGroup.id = android.R.id.content

        // By default, Compose UI uses its own implementation of CoroutineDispatcher, `AndroidUiDispatcher`.
        // Since this dispatcher does not provide its own implementation of Delay, it will default to using DefaultDelay which runs
        // async to our test Handler. By initializing Recomposer with Dispatchers.Main, Delay will now be backed by our test Handler,
        // synchronizing expected behavior.
        WindowRecomposerPolicy.setFactory {
          val windowRecomposer = it.createLifecycleAwareWindowRecomposer(MAIN_DISPATCHER)
          recomposer = windowRecomposer
          return@setFactory windowRecomposer
        }
      }

      if (hasLifecycleOwnerRuntime) {
        lifecycleOwner = PaparazziLifecycleOwner()
        modifiedView.setViewTreeLifecycleOwner(lifecycleOwner)

        if (hasSavedStateRegistryOwnerRuntime) {
          modifiedView.setViewTreeSavedStateRegistryOwner(PaparazziSavedStateRegistryOwner(lifecycleOwner))
        }
        if (hasAndroidxActivityRuntime) {
          modifiedView.setViewTreeOnBackPressedDispatcherOwner(PaparazziOnBackPressedDispatcherOwner(lifecycleOwner))
        }
        // Must be changed after the SavedStateRegistryOwner above has finished restoring its state.
        lifecycleOwner.registry.currentState = Lifecycle.State.RESUMED
      }

      viewGroup.addView(modifiedView)
      // Only the non-NORMAL modes derive the canvas from the content, so only those can present a
      // stale canvas to a traversal. Suppressing in NORMAL would skip a measure pass that content
      // subcomposed during measure, such as a Dialog inside a Scaffold, depends on to exist at all.
      RenderSizingState.canvasSizedForContent =
        sessionParams.renderingMode == RenderingMode.NORMAL
      // Only Compose content needs this: Views start animations from the frame they are drawn in.
      if (recomposer != null && (startNanos > 0L || seeker != null)) {
        settleAtTimeZero(recomposer)
      }

      for (frame in 0 until frameCount) {
        val nowNanos = (startNanos + (frame * 1_000_000_000.0 / fps)).toLong()
        // When seeking, animations are positioned by the seeker and the frame clock stays at the
        // time every animation started at.
        val frameTimeNanos = if (seeker != null) 0L else nowNanos

        if (recomposer != null && seeker == null) advanceFramesTo(frameTimeNanos)

        // If we have pendingTasks run recomposer to ensure we get the correct frame.
        var hasPendingWork = false
        withTime(frameTimeNanos) {
          seeker?.seek(modifiedView, TimeUnit.NANOSECONDS.toMillis(nowNanos))
          resetExpandBaseline()
          renderForResult()
          // A seek lands in a snapshot apply, so like Studio it always needs a second render to
          // show the recomposed state. Otherwise, pending tasks only need it on the first frame.
          hasPendingWork = seeker != null || (frame == 0 && recomposer.hasPendingWork())
        }

        if (hasPendingWork) {
          withTime(frameTimeNanos) {
            resetExpandBaseline()
            renderForResult()
          }

          if (recomposer.hasPendingWork()) {
            logger.warning(
              "Pending work detected. This may cause unexpected results in your generated snapshots. ${(recomposer as Recomposer).changeCount}"
            )
          }
        }

        val image = bridgeRenderSession.image
        if (validateAccessibility) {
          require(renderExtensions.isEmpty()) {
            "Running accessibility validation and render extensions simultaneously is not supported."
          }
          validateLayoutAccessibility(modifiedView, image)
        }
        onNewFrame(scaleImage(frameImage(image)))
      }
    } finally {
      if (hasLifecycleOwnerRuntime) {
        lifecycleOwner.registry.currentState = Lifecycle.State.DESTROYED
      }
      viewGroup.removeAllViews()

      // Remove any applied render extensions
      if (modifiedView !== view) {
        (view.parent as ViewGroup).removeView(view)
      }
      AnimationHandler.sAnimatorHandler.set(null)
      if (hasComposeRuntime) {
        forceReleaseComposeReferenceLeaks()
      }

      // Reset the choreographer to its initial state for last for future test runs as it is a singleton.
      val choreographer = Choreographer.getInstance()
      val mLastFrameTimeNanos = choreographer::class.java.getDeclaredField("mLastFrameTimeNanos")
      mLastFrameTimeNanos.isAccessible = true
      mLastFrameTimeNanos.set(choreographer, 0L)

      Thread.setDefaultUncaughtExceptionHandler(previousUncaughtExceptionHandler)
    }
  }

  /**
   * Compose animations measure play time from the frame they first receive. Rendering at t=0 until
   * the composition is idle lets effects launch and each animation receive its first frame at 0, so
   * a later offset is measured from 0 rather than from whenever the first render happened. Two
   * renders are always needed: one to compose and launch effects, one to deliver the first frame.
   */
  private fun settleAtTimeZero(recomposer: Any?) {
    for (render in 0 until MAX_SETTLE_RENDERS) {
      withTime(0L) {
        resetExpandBaseline()
        renderForResult()
      }
      if (render >= 1 && !recomposer.hasPendingWork()) return
    }
  }

  /**
   * Ticks the clock through every frame between the last frame and [targetNanos], without drawing.
   * Handler messages (such as a coroutine `delay`) and the state changes they make then run at the
   * frame they are due in, so animations they start measure their play time from then rather than
   * from [targetNanos]. Jumping straight to [targetNanos] would start them all at play time 0.
   */
  private fun advanceFramesTo(targetNanos: Long) {
    var nanos = lastFrameNanos + FRAME_INTERVAL_NANOS
    while (nanos < targetNanos) {
      withTime(nanos) {}
      nanos += FRAME_INTERVAL_NANOS
    }
  }

  private fun Any?.hasPendingWork(): Boolean = hasComposeRuntime && this != null && (this as Recomposer).hasPendingWork

  private fun renderForResult() {
    val result = renderSession.render(true)
    if (result.status == ERROR_UNKNOWN) {
      throw result.exception
    }
  }

  private fun withTime(timeNanos: Long, block: () -> Unit) {
    val frameNanos = timeNanos
    if (frameNanos < lastFrameNanos) {
      runStaleFrameCallbacks()
    }
    lastFrameNanos = frameNanos

    // Execute the block at the requested time.
    System_Delegate.setNanosTime(0L)
    Choreographer_Delegate.sChoreographerTime = frameNanos
    // layoutlib divides this into AnimatedVectorDrawable's native animator clock, so without a
    // per-frame value every animated vector renders at t=0 regardless of the snapshot offset.
    renderSession.setElapsedFrameTimeNanos(frameNanos)

    try {
      executeHandlerCallbacks()
      val currentTimeNanos = uptimeNanos()
      /**
       * The choreographer needs to be manually ticked in order for the frame time to become visible to the native layer
       * which is necessary in order for ripples to work is compose, as well as view animation classes.
       *
       * After frame is run, we have to reset sChoreographerTime since [com.android.layoutlib.bridge.SessionInteractiveData.getNanosTime]
       * uses sChoreographerTime to calculate nanoTime via [System_Delegate.nanoTime].
       */
      Choreographer_Delegate.doFrame(currentTimeNanos)

      return block()
    } catch (e: Throwable) {
      Bridge.getLog().error("broken", "Failed executing Choreographer#doFrame", e, null, null)
      throw e
    }
  }

  /**
   * Each snapshot rewinds the clock to 0, but layoutlib queues Choreographer callbacks with a due
   * time on that clock. A callback left over from the previous snapshot would not be due again until
   * this snapshot reached the same time. Compose's frame dispatcher is shared by every snapshot on the
   * thread and keeps at most one callback queued, holding every later frame request behind it, so
   * this snapshot's animations would get no frames before then. Running the leftovers at the time
   * they were queued for, before the clock moves back, clears them out.
   */
  private fun runStaleFrameCallbacks() {
    RenderAction.getCurrentContext()?.sessionInteractiveData?.choreographerCallbacks
      ?.execute(lastFrameNanos, Bridge.getLog())
  }

  private fun createRenderSession(sessionParams: SessionParams): RenderSessionImpl {
    val renderSession = RenderSessionImpl(sessionParams)
    renderSession.setElapsedFrameTimeNanos(0L)
    return renderSession
  }

  private fun createBridgeSession(renderSession: RenderSessionImpl, result: Result): BridgeRenderSession {
    try {
      val bridgeSessionClass = Class.forName("com.android.layoutlib.bridge.BridgeRenderSession")
      val constructor =
        bridgeSessionClass.getDeclaredConstructor(RenderSessionImpl::class.java, Result::class.java)
      constructor.isAccessible = true
      val bridgeSession = constructor.newInstance(renderSession, result) as BridgeRenderSession
      return bridgeSession
    } catch (e: Exception) {
      throw RuntimeException(e)
    }
  }

  private fun frameImage(image: BufferedImage): BufferedImage {
    // On device sized screenshot, we should apply any device specific shapes.
    if (renderingMode == RenderingMode.NORMAL && deviceConfig.screenRound == ScreenRound.ROUND) {
      val newImage = BufferedImage(image.width, image.height, image.type)
      val g = newImage.createGraphics()
      g.clip = Ellipse2D.Float(0f, 0f, image.height.toFloat(), image.width.toFloat())
      g.drawImage(image, 0, 0, image.width, image.height, null)
      return newImage
    }

    return image
  }

  private fun scaleImage(image: BufferedImage): BufferedImage {
    val scale = ImageUtils.getThumbnailScale(image)
    // Only scale images down, so we don't waste storage space enlarging smaller layouts.
    return if (scale < 1f && !useDeviceResolution) ImageUtils.scale(image, scale, scale) else image
  }

  private fun validateLayoutAccessibility(view: View, image: BufferedImage? = null) {
    LayoutValidator.updatePolicy(
      Policy(
        EnumSet.of(Type.ACCESSIBILITY, Type.RENDER, Type.INTERNAL_ERROR),
        EnumSet.of(Level.ERROR, Level.WARNING)
      )
    )

    val validationResults = LayoutValidator.validate(view, image, 1f, 1f)
    validationResults.issues.forEach { issue ->
      val issueViewId = validationResults.srcMap[issue.mSrcId]?.id ?: NO_ID
      val issueViewName = if (issueViewId != NO_ID) {
        view.resources.getResourceName(issueViewId)
      } else {
        "no-id"
      }

      logger.warning(
        format = "\u001B[33mAccessibility issue of type {0} on {1}:\u001B[0m {2} \nSee: {3}",
        issue.mCategory,
        issueViewName,
        issue.mMsg,
        issue.mHelpfulUrl
      )
    }
  }

  private fun forcePlatformSdkVersion(compileSdkVersion: Int) {
    val buildVersionClass = try {
      PaparazziSdk::class.java.classLoader.loadClass("android.os.Build\$VERSION")
    } catch (e: ClassNotFoundException) {
      // Project unit tests don't load Android platform code
      return
    }
    buildVersionClass
      .getFieldReflectively("SDK_INT")
      .setStaticValue(compileSdkVersion)
  }

  private fun initializeAppCompatIfPresent() {
    lateinit var appCompatDelegateClass: Class<*>
    try {
      // See androidx.appcompat.widget.AppCompatDrawableManager#preload()
      val appCompatDrawableManagerClass =
        Class.forName("androidx.appcompat.widget.AppCompatDrawableManager")
      val preloadMethod = appCompatDrawableManagerClass.getMethod("preload")
      preloadMethod.invoke(null)

      appCompatDelegateClass = Class.forName("androidx.appcompat.app.AppCompatDelegate")
    } catch (e: ClassNotFoundException) {
      logger.verbose("AppCompat not found on classpath")
      return
    }

    // See androidx.appcompat.app.AppCompatDelegateImpl#installViewFactory()
    if (layoutInflater.factory == null) {
      layoutInflater.factory2 = object : LayoutInflater.Factory2 {
        override fun onCreateView(parent: View?, name: String, context: Context, attrs: AttributeSet): View? {
          val appCompatViewInflaterClass =
            Class.forName("androidx.appcompat.app.AppCompatViewInflater")

          val createViewMethod = appCompatViewInflaterClass
            .getDeclaredMethod(
              "createView",
              View::class.java,
              String::class.java,
              Context::class.java,
              AttributeSet::class.java,
              Boolean::class.javaPrimitiveType,
              Boolean::class.javaPrimitiveType,
              Boolean::class.javaPrimitiveType,
              Boolean::class.javaPrimitiveType
            )
            .apply { isAccessible = true }

          val inheritContext = true
          val readAndroidTheme = true
          val readAppTheme = true
          val wrapContext = true

          val newAppCompatViewInflaterInstance = appCompatViewInflaterClass
            .getConstructor()
            .newInstance()

          return createViewMethod.invoke(
            newAppCompatViewInflaterInstance, parent, name, context, attrs,
            inheritContext, readAndroidTheme, readAppTheme, wrapContext
          ) as View?
        }

        override fun onCreateView(name: String, context: Context, attrs: AttributeSet): View? =
          onCreateView(null, name, context, attrs)
      }
    } else {
      if (!appCompatDelegateClass.isAssignableFrom(layoutInflater.factory2::class.java)) {
        throw IllegalStateException(
          "The LayoutInflater already has a Factory installed so we can not install AppCompat's"
        )
      }
    }
  }

  private fun registerViewEditModeInterception() {
    InterceptorRegistrar.addMethodInterceptor(
      "android.view.View",
      "isInEditMode",
      EditModeInterceptor::class.java
    )
  }

  private fun forceReleaseComposeReferenceLeaks() {
    // AndroidUiDispatcher is backed by a Handler, by executing one last time
    // we give the dispatcher the ability to clean-up / release its callbacks.
    executeHandlerCallbacks()
  }

  /**
   * An expanding axis grows the canvas by the difference between the content's natural size and its
   * measured size, added to the size the canvas already had. That is only correct once. layoutlib
   * keeps the measured size across renders and Paparazzi renders repeatedly, so the difference is
   * added again on top of an already-expanded canvas and it outgrows the content. Clearing the
   * measured size gives every render the same device-sized starting point.
   */
  private fun resetExpandBaseline() {
    val renderingMode = sessionParams.renderingMode
    if (renderingMode.horizAction == SizeAction.EXPAND ||
      renderingMode.vertAction == SizeAction.EXPAND
    ) {
      renderSession.invalidateRenderingSize()
    }
  }

  private fun executeHandlerCallbacks() {
    // Avoid ConcurrentModificationException in
    // RenderAction.currentContext.sessionInteractiveData.handlerMessageQueue.runnablesMap which is a WeakHashMap
    // https://android.googlesource.com/platform/tools/adt/idea/+/c331c9b2f4334748c55c29adec3ad1cd67e45df2/designer/src/com/android/tools/idea/uibuilder/scene/LayoutlibSceneManager.java#1558
    synchronized(this) {
      // https://android.googlesource.com/platform/frameworks/layoutlib/+/ebdd83e4be7e8d89a38e3f316b2e15112f61ca30%5E%21/#F1
      val uptimeNanos = uptimeNanos()

      // https://android.googlesource.com/platform/frameworks/layoutlib/+/d58aa4703369e109b24419548f38b422d5a44738/bridge/src/com/android/layoutlib/bridge/BridgeRenderSession.java#171
      // BridgeRenderSession.executeCallbacks aggressively tears down the main Looper and BridgeContext, so we call the static delegates ourselves.
      Handler_Delegate.executeCallbacks(uptimeNanos)
    }
  }

  // This is necessary, because SystemClock_Delegate#uptimeNanos() is package-private.
  // https://android.googlesource.com/platform/frameworks/layoutlib/+/refs/tags/studio-2023.2.1-rc1/bridge/src/android/os/SystemClock_Delegate.java#56
  private fun uptimeNanos() = System_Delegate.nanoTime() - System_Delegate.bootTime()

  private fun DeviceConfig.updateIfAccessibilityTest(): DeviceConfig =
    if (renderExtensions.any { it is AccessibilityRenderExtension }) {
      val newWidth = screenWidth * 2
      val newOrientation = if (newWidth > screenHeight) ScreenOrientation.LANDSCAPE else ScreenOrientation.PORTRAIT
      copy(
        screenWidth = screenWidth * 2,
        softButtons = false,
        orientation = newOrientation
      )
    } else {
      this
    }

  /**
   * layoutlib drains a single type-blind Choreographer queue twice per `doFrame`
   * (CALLBACK_ANIMATION then CALLBACK_TRAVERSAL) against one frozen time threshold, so a callback
   * re-posted with zero delay is already due in the second drain and runs twice in one frame.
   * [AnimationHandler]'s frame callback re-posts itself with zero delay, so it is dispatched twice.
   * A 1ms delay makes the re-post due only on the following frame, restoring one dispatch per frame
   * without touching the clock or deferring any other queued work.
   */
  private object SingleDispatchFrameCallbackProvider :
    AnimationHandler.AnimationFrameCallbackProvider {
    override fun postFrameCallback(callback: Choreographer.FrameCallback) {
      Choreographer.getInstance().postFrameCallbackDelayed(callback, 1L)
    }

    override fun postCommitCallback(runnable: Runnable) {
      Choreographer.getInstance().postCallback(Choreographer.CALLBACK_COMMIT, runnable, null)
    }

    override fun getFrameTime(): Long = Choreographer.getInstance().frameTime

    override fun getFrameDelay(): Long = 1L

    override fun setFrameDelay(delay: Long) = Unit
  }

  internal companion object {
    internal lateinit var renderer: Renderer
    internal val isInitialized get() = ::renderer.isInitialized

    private const val MAX_SETTLE_RENDERS = 5

    /** A 60Hz frame, the cadence frames are stepped at between snapshot times. */
    private const val FRAME_INTERVAL_NANOS = 1_000_000_000L / 60

    internal lateinit var sessionParamsBuilder: SessionParamsBuilder

    private val MAIN_DISPATCHER by lazy {
      Handler.getMain().asCoroutineDispatcher("Paparazzi-Main")
    }

    private val hasComposeRuntime: Boolean = isPresentInClasspath(
      "androidx.compose.runtime.snapshots.SnapshotKt",
      "androidx.compose.ui.platform.AndroidUiDispatcher"
    )
    private val hasLifecycleOwnerRuntime = isPresentInClasspath(
      "androidx.lifecycle.ViewTreeLifecycleOwner"
    )
    private val hasSavedStateRegistryOwnerRuntime = isPresentInClasspath(
      "androidx.savedstate.SavedStateRegistryController\$Companion"
    )
    private val hasAndroidxActivityRuntime = isPresentInClasspath(
      "androidx.activity.ViewTreeOnBackPressedDispatcherOwner"
    )

    private fun contentRoot(renderingMode: RenderingMode) =
      """
        |<?xml version="1.0" encoding="utf-8"?>
        |<${if (hasComposeRuntime) "app.cash.paparazzi.internal.ComposeViewAdapter" else "FrameLayout"}
        |     xmlns:android="http://schemas.android.com/apk/res/android"
        |              android:layout_width="${if (renderingMode.horizAction == RenderingMode.SizeAction.SHRINK) "wrap_content" else "match_parent"}"
        |              android:layout_height="${if (renderingMode.vertAction == RenderingMode.SizeAction.SHRINK) "wrap_content" else "match_parent"}"/>
      """.trimMargin()

    private fun isPresentInClasspath(vararg classNames: String): Boolean {
      return try {
        for (className in classNames) {
          Class.forName(className)
        }
        true
      } catch (e: ClassNotFoundException) {
        false
      }
    }
  }
}
