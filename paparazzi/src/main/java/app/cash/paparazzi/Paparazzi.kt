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

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.util.AttributeSet
import android.util.DisplayMetrics
import android.view.BridgeInflater
import android.view.Choreographer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import app.cash.paparazzi.agent.AgentTestRule
import app.cash.paparazzi.agent.InterceptorRegistrar
import app.cash.paparazzi.internal.EditModeInterceptor
import app.cash.paparazzi.internal.ImageUtils
import app.cash.paparazzi.internal.MatrixMatrixMultiplicationInterceptor
import app.cash.paparazzi.internal.MatrixVectorMultiplicationInterceptor
import app.cash.paparazzi.internal.parsers.LayoutPullParser
import app.cash.paparazzi.internal.PaparazziCallback
import app.cash.paparazzi.internal.PaparazziLogger
import app.cash.paparazzi.internal.Renderer
import app.cash.paparazzi.internal.ResourcesInterceptor
import app.cash.paparazzi.internal.SessionParamsBuilder
import com.android.ide.common.rendering.api.RenderSession
import com.android.ide.common.rendering.api.Result
import com.android.ide.common.rendering.api.Result.Status.ERROR_UNKNOWN
import com.android.ide.common.rendering.api.SessionParams
import com.android.layoutlib.bridge.Bridge.cleanupThread
import com.android.layoutlib.bridge.Bridge.prepareThread
import com.android.layoutlib.bridge.BridgeRenderSession
import com.android.layoutlib.bridge.impl.RenderAction
import com.android.layoutlib.bridge.impl.RenderSessionImpl
import com.android.tools.layoutlib.java.System_Delegate
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.awt.image.BufferedImage
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.util.Date
import java.util.concurrent.TimeUnit

class Paparazzi(
  private val environment: Environment = detectEnvironment(),
  private val deviceConfig: DeviceConfig = DeviceConfig.NEXUS_5,
  private val theme: String = "android:Theme.Material.NoActionBar.Fullscreen",
  private val appCompatEnabled: Boolean = true,
  private val maxPercentDifference: Double = 0.1,
  private val snapshotHandler: SnapshotHandler = determineHandler(maxPercentDifference)
) : TestRule {
  private val THUMBNAIL_SIZE = 1000

  private val logger = PaparazziLogger()
  private lateinit var sessionParamsBuilder: SessionParamsBuilder
  private lateinit var renderer: Renderer
  private lateinit var renderSession: RenderSessionImpl
  private lateinit var bridgeRenderSession: RenderSession
  private var testName: TestName? = null
  private val renderExtensions = LinkedHashSet<RenderExtension>()

  val layoutInflater: LayoutInflater
    get() = RenderAction.getCurrentContext().getSystemService("layout_inflater") as BridgeInflater

  val resources: Resources
    get() = RenderAction.getCurrentContext().resources

  val context: Context
    get() = RenderAction.getCurrentContext()

  val contentRoot = """
        |<?xml version="1.0" encoding="utf-8"?>
        |<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
        |              android:layout_width="match_parent"
        |              android:layout_height="match_parent"/>
        """.trimMargin()

  override fun apply(
    base: Statement,
    description: Description
  ): Statement {
    val statement = object : Statement() {
      override fun evaluate() {
        prepare(description)
        try {
          base.evaluate()
        } finally {
          close()
        }
      }
    }

    registerFontLookupInterceptionIfResourceCompatDetected()
    registerViewEditModeInterception()
    registerMatrixMultiplyInterception()

    val outerRule = AgentTestRule()
    return outerRule.apply(statement, description)
  }

  fun prepare(description: Description) {
    forcePlatformSdkVersion(environment.compileSdkVersion)

    val layoutlibCallback = PaparazziCallback(logger, environment.packageName)
    layoutlibCallback.initResources()

    testName = description.toTestName()

    renderer = Renderer(environment, layoutlibCallback, logger, maxPercentDifference)
    sessionParamsBuilder = renderer.prepare()

    sessionParamsBuilder = sessionParamsBuilder
        .copy(
            layoutPullParser = LayoutPullParser.createFromString(contentRoot),
            deviceConfig = deviceConfig,
            renderingMode = SessionParams.RenderingMode.V_SCROLL
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

  fun close() {
    testName = null
    renderer.close()
    renderSession.release()
    bridgeRenderSession.dispose()
    cleanupThread()
    snapshotHandler.close()
  }

  fun <V : View> inflate(@LayoutRes layoutId: Int): V = layoutInflater.inflate(layoutId, null) as V

  fun snapshot(
    view: View,
    name: String? = null,
    deviceConfig: DeviceConfig? = null,
    theme: String? = null
  ) {
    takeSnapshots(view, name, deviceConfig, theme, 0, -1, 1)
  }

  fun gif(
    view: View,
    name: String? = null,
    deviceConfig: DeviceConfig? = null,
    theme: String? = null,
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
    takeSnapshots(view, name, deviceConfig, theme, startNanos, fps, frameCount)
  }

  fun addRenderExtension(renderExtension: RenderExtension) {
    renderExtensions.add(renderExtension)
  }

  fun removeRenderExtension(renderExtension: RenderExtension) {
    renderExtensions.remove(renderExtension)
  }

  private fun takeSnapshots(
    view: View,
    name: String?,
    deviceConfig: DeviceConfig? = null,
    theme: String? = null,
    startNanos: Long,
    fps: Int,
    frameCount: Int
  ) {
    if (deviceConfig != null || theme != null) {
      renderSession.release()
      bridgeRenderSession.dispose()

      sessionParamsBuilder = sessionParamsBuilder
          .copy(
              // Required to reset underlying parser stream
              layoutPullParser = LayoutPullParser.createFromString(contentRoot)
          )

      if (deviceConfig != null) {
        sessionParamsBuilder = sessionParamsBuilder.copy(deviceConfig = deviceConfig)
      }

      if (theme != null) {
        sessionParamsBuilder = sessionParamsBuilder.withTheme(theme)
      }

      val sessionParams = sessionParamsBuilder.build()
      renderSession = createRenderSession(sessionParams)
      renderSession.init(sessionParams.timeout)
      Bitmap.setDefaultDensity(DisplayMetrics.DENSITY_DEVICE_STABLE)
      bridgeRenderSession = createBridgeSession(renderSession, renderSession.inflate())
    }

    val snapshot = Snapshot(name, testName!!, Date())

    val frameHandler = snapshotHandler.newFrameHandler(snapshot, frameCount, fps)
    frameHandler.use {
      val viewGroup = bridgeRenderSession.rootViews[0].viewObject as ViewGroup
      try {
        withTime(0L) {
          // Initialize the choreographer at time=0.
        }

        viewGroup.addView(view)
        for (frame in 0 until frameCount) {
          val nowNanos = (startNanos + (frame * 1_000_000_000.0 / fps)).toLong()
          withTime(nowNanos) {
            val result = renderSession.render(true)
            if (result.status == ERROR_UNKNOWN) {
              throw result.exception
            }

            var image = bridgeRenderSession.image
            renderExtensions.forEach {
              image = it.render(snapshot = snapshot, view = view, image = image)
            }
            frameHandler.handle(scaleImage(image))
          }
        }
      } finally {
        viewGroup.removeView(view)
      }
    }
  }

  private fun withTime(
    timeNanos: Long,
    block: () -> Unit
  ) {
    val frameNanos = TIME_OFFSET_NANOS + timeNanos

    // Execute the block at the requested time.
    System_Delegate.setBootTimeNanos(frameNanos)
    System_Delegate.setNanosTime(frameNanos)
    Choreographer.getInstance().doFrame(frameNanos, 0)
    try {
      block()
    } finally {
      System_Delegate.setNanosTime(0L)
      System_Delegate.setBootTimeNanos(0L)
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

  private fun scaleImage(image: BufferedImage): BufferedImage {
    val maxDimension = Math.max(image.width, image.height)
    val scale = THUMBNAIL_SIZE / maxDimension.toDouble()
    return ImageUtils.scale(image, scale, scale)
  }

  private fun Description.toTestName(): TestName {
    val fullQualifiedName = className
    val packageName = fullQualifiedName.substringBeforeLast('.', missingDelimiterValue = "")
    val className = fullQualifiedName.substringAfterLast('.')
    return TestName(packageName, className, methodName)
  }

  private fun forcePlatformSdkVersion(compileSdkVersion: Int) {
    val modifiersField =
      try {
        Field::class.java.getDeclaredField("modifiers")
      } catch (e: NoSuchFieldException) {
        // Hack for Java 12+ access
        // https://stackoverflow.com/q/56039341
        // https://github.com/powermock/powermock/commit/66ce9f77215bae68b45f35481abc8b52a5d5b6ae#diff-21c1fc51058efd316026f11f34f51c5c
        try {
          val getDeclaredFields0 =
            Class::class.java.getDeclaredMethod(
                "getDeclaredFields0", Boolean::class.javaPrimitiveType
            )
          getDeclaredFields0.isAccessible = true
          val fields = getDeclaredFields0.invoke(Field::class.java, false) as Array<Field>
          fields.find { it.name == "modifiers" } ?: throw e
        } catch (ex: Exception) {
          e.addSuppressed(ex)
          throw e
        }
      }
    modifiersField.isAccessible = true

    val versionClass = try {
      Paparazzi::class.java.classLoader.loadClass("android.os.Build\$VERSION")
    } catch (e: ClassNotFoundException) {
      return
    }

    versionClass
        .getDeclaredField("SDK_INT")
        .apply {
          isAccessible = true
          modifiersField.setInt(this, modifiers and Modifier.FINAL.inv())
          setInt(null, compileSdkVersion)
        }
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
      logger.info("AppCompat not found on classpath, exiting...")
      return
    }

    // See androidx.appcompat.app.AppCompatDelegateImpl#installViewFactory()
    if (layoutInflater.factory == null) {
      layoutInflater.factory2 = object : LayoutInflater.Factory2 {
        private val appCompatViewInflaterClass =
          Class.forName("androidx.appcompat.app.AppCompatViewInflater")

        private val viewInflaterClass = try {
          Class.forName("com.google.android.material.theme.MaterialComponentsViewInflater")
        } catch (e: ClassNotFoundException) {
          logger.info("MaterialComponents not found on classpath...")
          appCompatViewInflaterClass
        }

        override fun onCreateView(
          parent: View?,
          name: String,
          context: Context,
          attrs: AttributeSet
        ): View? {
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

          val newAppCompatViewInflaterInstance = viewInflaterClass
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
          resourcesCompatClass, "getFont", ResourcesInterceptor::class.java
      )
    } catch (e: ClassNotFoundException) {
      logger.info("ResourceCompat not found on classpath...")
    }
  }

  private fun registerViewEditModeInterception() {
    val viewClass = Class.forName("android.view.View")
    InterceptorRegistrar.addMethodInterceptor(
        viewClass, "isInEditMode", EditModeInterceptor::class.java
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

  companion object {
    /** The choreographer doesn't like 0 as a frame time, so start an hour later. */
    internal val TIME_OFFSET_NANOS = TimeUnit.HOURS.toNanos(1L)

    private val isVerifying: Boolean =
      System.getProperty("paparazzi.test.verify")?.toBoolean() == true

    private fun determineHandler(maxPercentDifference: Double): SnapshotHandler =
      if (isVerifying) {
        SnapshotVerifier(maxPercentDifference)
      } else {
        HtmlReportWriter()
      }
  }
}
