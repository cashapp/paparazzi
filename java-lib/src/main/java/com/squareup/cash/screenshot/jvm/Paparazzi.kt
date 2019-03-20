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
package com.squareup.cash.screenshot.jvm

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
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.io.File
import java.io.IOException
import javax.imageio.ImageIO

class Paparazzi(
  environment: Environment = detectEnvironment()
) : TestRule {
  private val THUMBNAIL_SIZE = 1000

  private val renderTestBase = RenderTestBase(environment)
  private val logger = PaparazziLogger()

  private lateinit var session: RenderSession
  private val defaultClassLoader: ModuleClassLoader =
    ModuleClassLoader(environment.appClassesLocation, javaClass.classLoader)

  init {
    renderTestBase.beforeClass()
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
    val layoutLibCallback = LayoutLibTestCallback(logger, defaultClassLoader)
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
      output.parentFile.mkdirs()
      ImageIO.write(thumbnail, "PNG", output)
      println("Thumbnail for current rendering stored at " + output.path)
    } catch (e: IOException) {
      println(e)
    }
  }
}
