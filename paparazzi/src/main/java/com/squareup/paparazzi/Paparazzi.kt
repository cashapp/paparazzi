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
package com.squareup.paparazzi

import android.content.res.Resources
import android.view.BridgeInflater
import android.view.LayoutInflater
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
import com.squareup.paparazzi.internal.RenderTestBase
import com.squareup.paparazzi.internal.LayoutLibTestCallback
import com.squareup.paparazzi.internal.LayoutPullParser
import com.squareup.paparazzi.internal.ImageUtils
import com.squareup.paparazzi.internal.ModuleClassLoader
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.awt.image.BufferedImage
import java.util.Date

class Paparazzi(
    environment: Environment = detectEnvironment(),
    private val snapshotHandler: SnapshotHandler = RunWriter()
) : TestRule {
  private val THUMBNAIL_SIZE = 1000

  private val renderTestBase = RenderTestBase(environment)
  private val logger = PaparazziLogger()
  private val defaultClassLoader: ModuleClassLoader =
    ModuleClassLoader(
        environment.appClassesLocation, javaClass.classLoader
    )

  private lateinit var session: RenderSession
  private lateinit var scene: RenderSessionImpl
  private var testName: TestName? = null
  private var snapshotCount = 0

  val layoutInflater: LayoutInflater
    get() = RenderAction.getCurrentContext().getSystemService("layout_inflater") as BridgeInflater

  val resources: Resources
    get() = RenderAction.getCurrentContext().resources

  override fun apply(
    base: Statement,
    description: Description
  ) = object : Statement() {
    override fun evaluate() {
      prepare(description)
      try {
        base.evaluate()
      } finally {
        close()
      }
    }
  }

  fun prepare(description: Description) {
    testName = description.toTestName()
    renderTestBase.beforeClass()

    val layoutLibTestCallback =
      LayoutLibTestCallback(logger, defaultClassLoader)
    layoutLibTestCallback.initResources()

    val frameLayout = """
        |<?xml version="1.0" encoding="utf-8"?>
        |<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
        |              android:layout_width="match_parent"
        |              android:layout_height="match_parent"/>
        """.trimMargin()

    val sessionParams = renderTestBase.sessionParamsBuilder
        .setParser(LayoutPullParser.createFromString(frameLayout))
        .setCallback(layoutLibTestCallback)
        .setTheme("Theme.Material.NoActionBar.Fullscreen", false)
        .setRenderingMode(SessionParams.RenderingMode.V_SCROLL)
        .build()

    scene = RenderSessionImpl(sessionParams)
    prepareThread()
    scene.init(sessionParams.timeout)
    session = createBridgeSession(scene, scene.inflate())
  }

  fun close() {
    testName = null
    session.dispose()
    scene.release()
    cleanupThread()
    snapshotHandler.close()
  }

  fun <V : View> inflate(@LayoutRes layoutId: Int): V {
    return layoutInflater.inflate(layoutId, null) as V
  }

  fun snapshot(
    view: View,
    name: String? = null
  ) {
    snapshotCount++

    val viewGroup = session.rootViews[0].viewObject as ViewGroup
    viewGroup.addView(view)
    try {
      scene.render(true)
      saveImage(name ?: snapshotCount.toString(), session.image)
    } finally {
      viewGroup.removeView(view)
    }
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

  private fun saveImage(
    name: String,
    image: BufferedImage
  ) {
    val maxDimension = Math.max(image.width, image.height)
    val scale = THUMBNAIL_SIZE / maxDimension.toDouble()
    val copy = ImageUtils.scale(image, scale, scale)

    val shot = Snapshot(name, testName!!, Date())
    snapshotHandler.handle(shot, copy)
  }

  private fun Description.toTestName(): TestName {
    val packageName = testClass.`package`.name
    val className = testClass.name.substring(packageName.length + 1)
    return TestName(packageName, className, methodName)
  }
}
