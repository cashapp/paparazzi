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
import android.util.AttributeSet
import android.util.DisplayMetrics
import android.view.BridgeInflater
import android.view.Choreographer
import android.view.LayoutInflater
import android.view.View
import android.view.View.NO_ID
import android.view.ViewGroup
import androidx.activity.setViewTreeOnBackPressedDispatcherOwner
import androidx.annotation.LayoutRes
import androidx.compose.runtime.Composable
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
import app.cash.paparazzi.internal.ImageUtils
import app.cash.paparazzi.internal.PaparazziCallback
import app.cash.paparazzi.internal.PaparazziLifecycleOwner
import app.cash.paparazzi.internal.PaparazziLogger
import app.cash.paparazzi.internal.PaparazziOnBackPressedDispatcherOwner
import app.cash.paparazzi.internal.PaparazziSavedStateRegistryOwner
import app.cash.paparazzi.internal.Renderer
import app.cash.paparazzi.internal.SessionParamsBuilder
import app.cash.paparazzi.internal.interceptors.EditModeInterceptor
import app.cash.paparazzi.internal.interceptors.IInputMethodManagerInterceptor
import app.cash.paparazzi.internal.interceptors.MatrixMatrixMultiplicationInterceptor
import app.cash.paparazzi.internal.interceptors.MatrixVectorMultiplicationInterceptor
import app.cash.paparazzi.internal.interceptors.ResourcesInterceptor
import app.cash.paparazzi.internal.interceptors.ServiceManagerInterceptor
import app.cash.paparazzi.internal.parsers.LayoutPullParser
import com.android.ide.common.rendering.api.RenderSession
import com.android.ide.common.rendering.api.Result
import com.android.ide.common.rendering.api.Result.Status.ERROR_UNKNOWN
import com.android.ide.common.rendering.api.SessionParams
import com.android.ide.common.rendering.api.SessionParams.RenderingMode
import com.android.internal.lang.System_Delegate
import com.android.layoutlib.bridge.Bridge
import com.android.layoutlib.bridge.Bridge.cleanupThread
import com.android.layoutlib.bridge.Bridge.prepareThread
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
  private val validateAccessibility: Boolean = false,
  private val onNewFrame: (BufferedImage) -> Unit
) {
  private val logger = PaparazziLogger()
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
      registerFontLookupInterceptionIfResourceCompatDetected()
      registerViewEditModeInterception()
      registerMatrixMultiplyInterception()
      registerServiceManagerInterception()
      registerIInputMethodManagerInterception()

      ByteBuddyAgent.install()
      InterceptorRegistrar.registerMethodInterceptors()
    }
  }

  public fun prepare() {
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

    val sessionParams = sessionParamsBuilder.build()
    renderSession = createRenderSession(sessionParams)
    prepareThread()
    renderSession.init(sessionParams.timeout)
    Bitmap.setDefaultDensity(DisplayMetrics.DENSITY_DEVICE_STABLE)

    // requires LayoutInflater to be created, which is a side-effect of RenderSessionImpl.init()
    if (appCompatEnabled) {
      initializeAppCompatIfPresent()
    }

    bridgeRenderSession = createBridgeSession(renderSession, renderSession.inflate())
  }

  public fun teardown() {
    renderSession.release()
    bridgeRenderSession.dispose()
    cleanupThread()

    renderer.dumpDelegates()
    logger.assertNoErrors()
  }

  public fun <V : View> inflate(@LayoutRes layoutId: Int): V =
    layoutInflater.inflate(layoutId, null) as V

  public fun snapshot(composable: @Composable () -> Unit) {
    val hostView = ComposeView(context)
    hostView.setContent(composable)

    snapshot(hostView)
  }

  @JvmOverloads
  public fun snapshot(view: View, offsetMillis: Long = 0L) {
    takeSnapshots(view, TimeUnit.MILLISECONDS.toNanos(offsetMillis), -1, 1)
  }

  @JvmOverloads
  public fun gif(
    view: View,
    start: Long = 0L,
    end: Long = 500L,
    fps: Int = 30
  ) {
    // Add one to the frame count so we get the last frame. Otherwise a 1 second, 60 FPS animation
    // our 60th frame will be at time 983 ms, and we want our last frame to be 1,000 ms. This gets
    // us 61 frames for a 1 second animation, 121 frames for a 2 second animation, etc.
    val durationMillis = (end - start).toInt()
    val frameCount = (durationMillis * fps) / 1000 + 1
    val startNanos = TimeUnit.MILLISECONDS.toNanos(start)
    takeSnapshots(view, startNanos, fps, frameCount)
  }

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
    cleanupThread()

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

    val sessionParams = sessionParamsBuilder.build()
    renderSession = createRenderSession(sessionParams)
    prepareThread()
    renderSession.init(sessionParams.timeout)
    Bitmap.setDefaultDensity(DisplayMetrics.DENSITY_DEVICE_STABLE)
    bridgeRenderSession = createBridgeSession(renderSession, renderSession.inflate())
  }

  private fun takeSnapshots(
    view: View,
    startNanos: Long,
    fps: Int,
    frameCount: Int
  ) {
    val viewGroup = bridgeRenderSession.rootViews[0].viewObject as ViewGroup
    val modifiedView = renderExtensions.fold(view) { view, renderExtension ->
      renderExtension.renderView(view)
    }

    System_Delegate.setNanosTime(0L)
    try {
      withTime(0L) {
        // Initialize the choreographer at time=0.
      }

      if (hasComposeRuntime) {
        // During onAttachedToWindow, AbstractComposeView will attempt to resolve its parent's
        // CompositionContext, which requires first finding the "content view", then using that
        // to find a root view with a ViewTreeLifecycleOwner
        viewGroup.id = android.R.id.content

        // By default, Compose UI uses its own implementation of CoroutineDispatcher, `AndroidUiDispatcher`.
        // Since this dispatcher does not provide its own implementation of Delay, it will default to using DefaultDelay which runs
        // async to our test Handler. By initializing Recomposer with Dispatchers.Main, Delay will now be backed by our test Handler,
        // synchronizing expected behavior.
        WindowRecomposerPolicy.setFactory { it.createLifecycleAwareWindowRecomposer(MAIN_DISPATCHER) }
      }

      if (hasLifecycleOwnerRuntime) {
        val lifecycleOwner = PaparazziLifecycleOwner()
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
      for (frame in 0 until frameCount) {
        val nowNanos = (startNanos + (frame * 1_000_000_000.0 / fps)).toLong()
        withTime(nowNanos) {
          val result = renderSession.render(true)
          if (result.status == ERROR_UNKNOWN) {
            throw result.exception
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
      }
    } finally {
      viewGroup.removeView(modifiedView)
      AnimationHandler.sAnimatorHandler.set(null)
      if (hasComposeRuntime) {
        forceReleaseComposeReferenceLeaks()
      }
    }
  }

  private fun withTime(
    timeNanos: Long,
    block: () -> Unit
  ) {
    val frameNanos = timeNanos

    // Execute the block at the requested time.
    System_Delegate.setNanosTime(0L)
    System_Delegate.setNanosTime(frameNanos)

    val choreographer = Choreographer.getInstance()
    val areCallbacksRunningField = choreographer::class.java.getDeclaredField("mCallbacksRunning")
    areCallbacksRunningField.isAccessible = true

    try {
      areCallbacksRunningField.setBoolean(choreographer, true)

      executeHandlerCallbacks()
      val currentTimeNanos = uptimeNanos()
      val choreographerCallbacks =
        RenderAction.getCurrentContext().sessionInteractiveData.choreographerCallbacks
      choreographerCallbacks.execute(currentTimeNanos, Bridge.getLog())

      block()
    } catch (e: Throwable) {
      Bridge.getLog().error("broken", "Failed executing Choreographer#doFrame", e, null, null)
      throw e
    } finally {
      areCallbacksRunningField.setBoolean(choreographer, false)
    }
  }

  private fun createRenderSession(sessionParams: SessionParams): RenderSessionImpl {
    val renderSession = RenderSessionImpl(sessionParams)
    renderSession.setElapsedFrameTimeNanos(0L)
    RenderSessionImpl::class.java
      .getDeclaredField("mFirstFrameExecuted")
      .apply {
        isAccessible = true
        set(renderSession, true)
      }
    return renderSession
  }

  private fun createBridgeSession(
    renderSession: RenderSessionImpl,
    result: Result
  ): BridgeRenderSession {
    try {
      val bridgeSessionClass = Class.forName("com.android.layoutlib.bridge.BridgeRenderSession")
      val constructor =
        bridgeSessionClass.getDeclaredConstructor(RenderSessionImpl::class.java, Result::class.java)
      constructor.isAccessible = true
      return constructor.newInstance(renderSession, result) as BridgeRenderSession
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
    // Only scale images down so we don't waste storage space enlarging smaller layouts.
    return if (scale < 1f) ImageUtils.scale(image, scale, scale) else image
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
        override fun onCreateView(
          parent: View?,
          name: String,
          context: Context,
          attrs: AttributeSet
        ): View? {
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

        override fun onCreateView(
          name: String,
          context: Context,
          attrs: AttributeSet
        ): View? = onCreateView(null, name, context, attrs)
      }
    } else {
      if (!appCompatDelegateClass.isAssignableFrom(layoutInflater.factory2::class.java)) {
        throw IllegalStateException(
          "The LayoutInflater already has a Factory installed so we can not install AppCompat's"
        )
      }
    }
  }

  /**
   * Current workaround for supporting custom fonts when constructing views in code. This check
   * may be used or expanded to support other cases requiring similar method interception
   * techniques.
   *
   * See:
   * https://github.com/cashapp/paparazzi/issues/119
   * https://issuetracker.google.com/issues/156065472
   */
  private fun registerFontLookupInterceptionIfResourceCompatDetected() {
    try {
      InterceptorRegistrar.addMethodInterceptor(
        "androidx.core.content.res.ResourcesCompat",
        "getFont",
        ResourcesInterceptor::class.java
      )
    } catch (e: ClassNotFoundException) {
      logger.verbose("ResourceCompat not found on classpath")
    }
  }

  private fun registerServiceManagerInterception() {
    InterceptorRegistrar.addMethodInterceptor(
      "android.os.ServiceManager",
      "getServiceOrThrow",
      ServiceManagerInterceptor::class.java
    )
  }

  private fun registerIInputMethodManagerInterception() {
    InterceptorRegistrar.addMethodInterceptor(
      "com.android.internal.view.IInputMethodManager\$Stub",
      "asInterface",
      IInputMethodManagerInterceptor::class.java
    )
  }

  private fun registerViewEditModeInterception() {
    InterceptorRegistrar.addMethodInterceptor(
      "android.view.View",
      "isInEditMode",
      EditModeInterceptor::class.java
    )
  }

  private fun registerMatrixMultiplyInterception() {
    InterceptorRegistrar.addMethodInterceptors(
      "android.opengl.Matrix",
      setOf(
        "multiplyMM" to MatrixMatrixMultiplicationInterceptor::class.java,
        "multiplyMV" to MatrixVectorMultiplicationInterceptor::class.java
      )
    )
  }

  private fun forceReleaseComposeReferenceLeaks() {
    // AndroidUiDispatcher is backed by a Handler, by executing one last time
    // we give the dispatcher the ability to clean-up / release its callbacks.
    executeHandlerCallbacks()
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

  internal companion object {
    internal lateinit var renderer: Renderer
    internal val isInitialized get() = ::renderer.isInitialized

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

    private fun contentRoot(renderingMode: RenderingMode) = """
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
