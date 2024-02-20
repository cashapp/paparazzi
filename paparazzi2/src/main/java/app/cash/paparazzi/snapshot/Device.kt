package app.cash.paparazzi.snapshot

import android.animation.AnimationHandler
import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.os.Handler_Delegate
import android.os.SystemClock_Delegate
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
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import app.cash.paparazzi.snapshot.internal.PaparazziCallback
import app.cash.paparazzi.snapshot.internal.PaparazziLifecycleOwner
import app.cash.paparazzi.snapshot.internal.PaparazziLogger
import app.cash.paparazzi.snapshot.internal.PaparazziOnBackPressedDispatcherOwner
import app.cash.paparazzi.snapshot.internal.PaparazziSavedStateRegistryOwner
import app.cash.paparazzi.snapshot.internal.Renderer
import app.cash.paparazzi.snapshot.internal.SessionParamsBuilder
import app.cash.paparazzi.snapshot.internal.interceptors.ChoreographerDelegateInterceptor
import app.cash.paparazzi.snapshot.internal.interceptors.EditModeInterceptor
import app.cash.paparazzi.snapshot.internal.interceptors.IInputMethodManagerInterceptor
import app.cash.paparazzi.snapshot.internal.interceptors.MatrixMatrixMultiplicationInterceptor
import app.cash.paparazzi.snapshot.internal.interceptors.MatrixVectorMultiplicationInterceptor
import app.cash.paparazzi.snapshot.internal.interceptors.ResourcesInterceptor
import app.cash.paparazzi.snapshot.internal.interceptors.ServiceManagerInterceptor
import app.cash.paparazzi.snapshot.internal.parsers.LayoutPullParser
import com.android.ide.common.rendering.api.RenderSession
import com.android.ide.common.rendering.api.Result
import com.android.ide.common.rendering.api.SessionParams
import com.android.internal.lang.System_Delegate
import com.android.layoutlib.bridge.Bridge
import com.android.layoutlib.bridge.BridgeRenderSession
import com.android.layoutlib.bridge.impl.RenderAction
import com.android.layoutlib.bridge.impl.RenderSessionImpl
import com.android.tools.idea.validator.LayoutValidator
import com.android.tools.idea.validator.ValidatorData.Level
import com.android.tools.idea.validator.ValidatorData.Policy
import com.android.tools.idea.validator.ValidatorData.Type
import net.bytebuddy.agent.ByteBuddyAgent
import java.awt.image.BufferedImage
import java.util.EnumSet
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.flow

public class Device(
  private val environment: Environment = detectEnvironment(),
  private val validateAccessibility: Boolean = false,
  private val frameSpec: FrameSpec,
  private val renderExtensions: Set<RenderExtension> = setOf()
) : Snapshotter {

  private val logger = PaparazziLogger()
  private lateinit var renderSession: RenderSessionImpl
  private lateinit var bridgeRenderSession: RenderSession
  private lateinit var renderer: Renderer
  private lateinit var sessionParamsBuilder: SessionParamsBuilder

  public val layoutInflater: LayoutInflater
    get() = RenderAction.getCurrentContext().getSystemService("layout_inflater") as BridgeInflater

  public val resources: Resources
    get() = RenderAction.getCurrentContext().resources

  public val context: Context
    get() = RenderAction.getCurrentContext()

  private val contentRoot = """
        |<?xml version="1.0" encoding="utf-8"?>
        |<${if (hasComposeRuntime) "app.cash.paparazzi.internal.ComposeViewAdapter" else "FrameLayout"}
        |     xmlns:android="http://schemas.android.com/apk/res/android"
        |              android:layout_width="${if (frameSpec.renderingMode.horizAction == SessionParams.RenderingMode.SizeAction.SHRINK) "wrap_content" else "match_parent"}"
        |              android:layout_height="${if (frameSpec.renderingMode.vertAction == SessionParams.RenderingMode.SizeAction.SHRINK) "wrap_content" else "match_parent"}"/>
  """.trimMargin()

  init {
    registerFontLookupInterceptionIfResourceCompatDetected()
    registerViewEditModeInterception()
    registerMatrixMultiplyInterception()
    registerChoreographerDelegateInterception()
    registerServiceManagerInterception()
    registerIInputMethodManagerInterception()

    ByteBuddyAgent.install()
    InterceptorRegistrar.registerMethodInterceptors()

    prepare()
  }

  override fun snapshot(composable: @Composable () -> Unit, timestampNanos: Long): Snapshot {
    val hostView = ComposeView(context)
    hostView.setContent(composable)

    return snapshot(hostView, timestampNanos)
  }

  override fun snapshot(view: View, timestampNanos: Long): Snapshot {
    val viewGroup = bridgeRenderSession.rootViews[0].viewObject as ViewGroup
    val modifiedView = prepareSnapshot(view, viewGroup)

    var snapshotImage: BufferedImage? = null
    withTime(timestampNanos) {
      val result = renderSession.render(true)
      if (result.status == Result.Status.ERROR_UNKNOWN) {
        throw result.exception
      }

      snapshotImage = bridgeRenderSession.image
      if (validateAccessibility) {
        require(renderExtensions.isEmpty()) {
          "Running accessibility validation and render extensions simultaneously is not supported."
        }
        validateLayoutAccessibility(modifiedView, snapshotImage)
      }
    }
    viewGroup.removeView(modifiedView)
    AnimationHandler.sAnimatorHandler.set(null)
    if (hasComposeRuntime) {
      forceReleaseComposeReferenceLeaks()
    }
    return Snapshot(frameSpec, snapshotImage!!)
  }

  public override fun clip(
    view: View,
    clipSpec: ClipSpec
  ): Clip = Clip(
    spec = clipSpec,
    images = flow {
      val viewGroup = bridgeRenderSession.rootViews[0].viewObject as ViewGroup
      val modifiedView = prepareSnapshot(view, viewGroup)
      var snapshotImage: BufferedImage? = null
      clipSpec.frameTimeNanos.forEach { frameTimeNanos ->
        withTime(frameTimeNanos) {
          val result = renderSession.render(true)
          if (result.status == Result.Status.ERROR_UNKNOWN) {
            throw result.exception
          }
          snapshotImage = bridgeRenderSession.image
          if (validateAccessibility) {
            require(renderExtensions.isEmpty()) {
              "Running accessibility validation and render extensions simultaneously is not supported."
            }
            validateLayoutAccessibility(modifiedView, snapshotImage)
          }
        }
        emit(snapshotImage!!)
      }
      viewGroup.removeView(modifiedView)
      AnimationHandler.sAnimatorHandler.set(null)
      if (hasComposeRuntime) {
        forceReleaseComposeReferenceLeaks()
      }
    }
  )

  override fun clip(
    composable: @Composable () -> Unit,
    clipSpec: ClipSpec
  ): Clip {
    val hostView = ComposeView(context)
    hostView.setContent(composable)

    return clip(hostView, clipSpec)
  }

  // This function prepares the supplied view by applying any provided render extensions, applying
  // lifecycle owner dependencies if required, and adding it to the view group.
  private fun prepareSnapshot(
    view: View,
    viewGroup: ViewGroup
  ): View {
    val modifiedView = renderExtensions.fold(view) { view, renderExtension ->
      renderExtension.renderView(view)
    }

    System_Delegate.setBootTimeNanos(0)
    withTime(0L) {
      // Initialize the choreographer at time=0.
    }

    if (hasComposeRuntime) {
      // During onAttachedToWindow, AbstractComposeView will attempt to resolve its parent's
      // CompositionContext, which requires first finding the "content view", then using that
      // to find a root view with a ViewTreeLifecycleOwner
      viewGroup.id = android.R.id.content
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
    return modifiedView
  }

  public fun close() {
    renderSession.release()
    bridgeRenderSession.dispose()
    Bridge.cleanupThread()

    renderer.dumpDelegates()
    logger.assertNoErrors()
  }

  public fun <V : View> inflate(@LayoutRes layoutId: Int): V = layoutInflater.inflate(layoutId, null) as V

  private fun prepare() {
    val layoutlibCallback =
      PaparazziCallback(logger, environment.packageName, environment.resourcePackageNames)
    layoutlibCallback.initResources()

    renderer = Renderer(environment, layoutlibCallback, logger)
    sessionParamsBuilder = renderer.prepare()
    forcePlatformSdkVersion(environment.compileSdkVersion)

    sessionParamsBuilder = sessionParamsBuilder
      .copy(
        layoutPullParser = LayoutPullParser.createFromString(contentRoot),
        deviceConfig = frameSpec.deviceConfig,
        renderingMode = frameSpec.renderingMode,
        supportsRtl = frameSpec.supportsRtl,
        decor = frameSpec.showSystemUi
      )
      .withTheme(frameSpec.theme)

    val sessionParams = sessionParamsBuilder.build()
    renderSession = createRenderSession(sessionParams)
    Bridge.prepareThread()
    renderSession.init(sessionParams.timeout)
    Bitmap.setDefaultDensity(DisplayMetrics.DENSITY_DEVICE_STABLE)

    // requires LayoutInflater to be created, which is a side-effect of RenderSessionImpl.init()
    if (frameSpec.appCompatEnabled) {
      initializeAppCompatIfPresent()
    }

    bridgeRenderSession = createBridgeSession(renderSession, renderSession.inflate())
  }

  private fun withTime(
    timeNanos: Long,
    block: () -> Unit
  ) {
    val frameNanos = TIME_OFFSET_NANOS + timeNanos

    // Execute the block at the requested time.
    System_Delegate.setNanosTime(frameNanos)

    val choreographer = Choreographer.getInstance()
    val areCallbacksRunningField = choreographer::class.java.getDeclaredField("mCallbacksRunning")
    areCallbacksRunningField.isAccessible = true

    try {
      areCallbacksRunningField.setBoolean(choreographer, true)

      executeHandlerCallbacks()
      val currentTimeMs = SystemClock_Delegate.uptimeMillis()
      val choreographerCallbacks =
        RenderAction.getCurrentContext().sessionInteractiveData.choreographerCallbacks
      choreographerCallbacks.execute(currentTimeMs, Bridge.getLog())

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
      Device::class.java.classLoader.loadClass("android.os.Build\$VERSION")
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
    } catch (e: NoClassDefFoundError) {
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
      val resourcesCompatClass = Class.forName("androidx.core.content.res.ResourcesCompat")
      InterceptorRegistrar.addMethodInterceptor(
        resourcesCompatClass,
        "getFont",
        ResourcesInterceptor::class.java
      )
    } catch (e: ClassNotFoundException) {
      logger.verbose("ResourceCompat not found on classpath")
    }
  }

  private fun registerServiceManagerInterception() {
    val serviceManager = Class.forName("android.os.ServiceManager")
    InterceptorRegistrar.addMethodInterceptor(
      serviceManager,
      "getServiceOrThrow",
      ServiceManagerInterceptor::class.java
    )
  }

  private fun registerIInputMethodManagerInterception() {
    val iimm = Class.forName("com.android.internal.view.IInputMethodManager\$Stub")
    InterceptorRegistrar.addMethodInterceptor(
      iimm,
      "asInterface",
      IInputMethodManagerInterceptor::class.java
    )
  }

  private fun registerViewEditModeInterception() {
    val viewClass = Class.forName("android.view.View")
    InterceptorRegistrar.addMethodInterceptor(
      viewClass,
      "isInEditMode",
      EditModeInterceptor::class.java
    )
  }

  private fun registerMatrixMultiplyInterception() {
    val matrixClass = Class.forName("android.opengl.Matrix")
    InterceptorRegistrar.addMethodInterceptors(
      matrixClass,
      setOf(
        "multiplyMM" to MatrixMatrixMultiplicationInterceptor::class.java,
        "multiplyMV" to MatrixVectorMultiplicationInterceptor::class.java
      )
    )
  }

  private fun registerChoreographerDelegateInterception() {
    val choreographerDelegateClass = Class.forName("android.view.Choreographer_Delegate")
    InterceptorRegistrar.addMethodInterceptor(
      choreographerDelegateClass,
      "getFrameTimeNanos",
      ChoreographerDelegateInterceptor::class.java
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
      // https://android.googlesource.com/platform/frameworks/layoutlib/+/d58aa4703369e109b24419548f38b422d5a44738/bridge/src/com/android/layoutlib/bridge/BridgeRenderSession.java#171
      // BridgeRenderSession.executeCallbacks aggressively tears down the main Looper and BridgeContext, so we call the static delegates ourselves.
      Handler_Delegate.executeCallbacks()
    }
  }

  private companion object {
    /** The choreographer doesn't like 0 as a frame time, so start an hour later. */
    internal val TIME_OFFSET_NANOS = TimeUnit.HOURS.toNanos(1L)

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
