package com.squareup.cash.screenshot.jvm

import android.annotation.NonNull
import android.annotation.Nullable
import android.view.BridgeInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import com.android.ide.common.rendering.api.RenderSession
import com.android.ide.common.rendering.api.Result
import com.android.ide.common.rendering.api.SessionParams
import com.android.layoutlib.bridge.Bridge.cleanupThread
import com.android.layoutlib.bridge.Bridge.prepareThread
import com.android.layoutlib.bridge.BridgeRenderSession
import com.android.layoutlib.bridge.impl.RenderAction
import com.android.layoutlib.bridge.impl.RenderSessionImpl
import com.android.layoutlib.bridge.intensive.RenderResult
import com.android.layoutlib.bridge.intensive.RenderTestBase
import com.android.layoutlib.bridge.intensive.setup.LayoutLibTestCallback
import com.android.layoutlib.bridge.intensive.setup.LayoutPullParser
import com.android.layoutlib.bridge.intensive.util.ImageUtils
import com.android.layoutlib.bridge.intensive.util.ModuleClassLoader
import com.android.utils.ILogger
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.io.File
import java.io.IOException
import javax.imageio.ImageIO

class Paparazzi : TestRule {
  private val THUMBNAIL_SIZE = 1000
  private val APP_TEST_DIR = "/Users/jrod/Development/screenshot/android-lib"
  private val APP_CLASSES_LOCATION =
    "$APP_TEST_DIR/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes"

  private val renderTestBase = RenderTestBase()
  private val sRenderMessages = mutableListOf<String>()
  private val sLogger = object : ILogger {
    override fun error(
      t: Throwable, @Nullable msgFormat: String?,
      vararg args: Any
    ) {
      t.printStackTrace()
      failWithMsg(msgFormat ?: "", *args)
    }

    override fun warning(@NonNull msgFormat: String, vararg args: Any) {
      failWithMsg(msgFormat, *args)
    }

    override fun info(@NonNull msgFormat: String, vararg args: Any) {
      // pass.
    }

    override fun verbose(@NonNull msgFormat: String, vararg args: Any) {
      // pass.
    }
  }

  private lateinit var session: RenderSession
  private lateinit var defaultClassLoader: ModuleClassLoader

  init {
    setup()
  }

  override fun apply(
    base: Statement?,
    description: Description?
  ) = object : Statement() {
    @Throws(Throwable::class)
    override fun evaluate() {
      try {
        base?.evaluate() // This will run the test.
      } finally {
      }
    }
  }

  fun <V : View> inflate(
    @LayoutRes layoutId: Int
  ): V {
    val layoutLibCallback = LayoutLibTestCallback(sLogger, defaultClassLoader)
    layoutLibCallback.initResources()

    val parser = LayoutPullParser.createFromString(
        """
        |<?xml version="1.0" encoding="utf-8"?>
        |<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
        |              android:layout_width="match_parent"
        |              android:layout_height="match_parent"/>
        """.trimMargin()
    )

    val params = renderTestBase.sessionParamsBuilder
        .setParser(parser)
        .setCallback(layoutLibCallback)
        .setTheme("Theme.Material.NoActionBar.Fullscreen", false)
        .setRenderingMode(SessionParams.RenderingMode.V_SCROLL)
        .build()

    val scene = RenderSessionImpl(params)

    try {
      prepareThread()
      scene.init(params.timeout)

      val bridgeInflater =
        RenderAction.getCurrentContext().getSystemService("layout_inflater") as BridgeInflater
      val view = bridgeInflater.inflate(layoutId, null) as V

      val lastResult = scene.inflate()
      session = createBridgeSession(scene, lastResult)
      (session.rootViews.get(0).viewObject as ViewGroup).addView(view)

      return view
    } finally {
      scene.release()
      cleanupThread()
    }
  }

  fun snapshot() {
    session.render(50000)
    val fromSession = RenderResult.getFromSession(session)

    // hmm
    session.dispose()

    saveImage(fromSession)
  }

  private fun createBridgeSession(
    scene: RenderSessionImpl,
    lastResult: Result
  ): BridgeRenderSession {
    try {
      val bridgeSessionClass = Class.forName("com.android.layoutlib.bridge.BridgeRenderSession")
      val constructor =
        bridgeSessionClass.getDeclaredConstructor(RenderSessionImpl::class.java, Result::class.java)
      constructor.isAccessible = true
      return constructor.newInstance(scene, lastResult) as BridgeRenderSession
    } catch (e: Exception) {
      throw RuntimeException(e)
    }
  }

  private fun saveImage(result: RenderResult) {
    try {
      val image = result.image
      val maxDimension = Math.max(image.width, image.height)
      val scale = THUMBNAIL_SIZE / maxDimension.toDouble()
      val thumbnail = ImageUtils.scale(image, scale, scale)

      val output = File("out/failures", "output.jpg")
      if (output.exists()) {
        val deleted = output.delete()
        if (!deleted) {
          println("Error deleting previous file stored at " + output.path)
        }
      }
      ImageIO.write(thumbnail, "PNG", output)
      println("Thumbnail for current rendering stored at " + output.path)
    } catch (e: IOException) {
      println(e)
    }
  }

  private fun failWithMsg(
    msgFormat: String,
    vararg args: Any
  ) {
    sRenderMessages.add(if (args.isEmpty()) msgFormat else String.format(msgFormat, *args))
  }

  private fun setup() {
    defaultClassLoader = ModuleClassLoader(APP_CLASSES_LOCATION, javaClass.classLoader)
    sRenderMessages.clear()
  }
}
