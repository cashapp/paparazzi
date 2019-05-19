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
import com.android.internal.R.attr.height
import com.android.internal.R.attr.width
import com.android.layoutlib.bridge.Bridge.cleanupThread
import com.android.layoutlib.bridge.Bridge.prepareThread
import com.android.layoutlib.bridge.BridgeRenderSession
import com.android.layoutlib.bridge.impl.RenderAction
import com.android.layoutlib.bridge.impl.RenderSessionImpl
import com.android.tools.layoutlib.java.System_Delegate
import com.squareup.gifencoder.GifEncoder
import com.squareup.gifencoder.ImageOptions
import com.squareup.paparazzi.internal.ImageUtils
import com.squareup.paparazzi.internal.LayoutPullParser
import com.squareup.paparazzi.internal.PaparazziCallback
import com.squareup.paparazzi.internal.Renderer
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.awt.image.BufferedImage
import java.io.FileOutputStream
import java.util.*

class Paparazzi(
  private val packageName: String,
  private val environment: Environment = detectEnvironment(),
  private val snapshotHandler: SnapshotHandler = RunWriter()
) : TestRule {
  private val THUMBNAIL_SIZE = 1000

  private val logger = PaparazziLogger()
  private lateinit var renderer: Renderer
  private lateinit var renderSession: RenderSessionImpl
  private lateinit var bridgeRenderSession: RenderSession
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
    val layoutlibCallback = PaparazziCallback(logger, packageName)
    layoutlibCallback.initResources()

    testName = description.toTestName()

    renderer = Renderer(environment, layoutlibCallback, logger)
    val sessionParamsBuilder = renderer.prepare()

    val frameLayout = """
        |<?xml version="1.0" encoding="utf-8"?>
        |<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
        |              android:layout_width="match_parent"
        |              android:layout_height="match_parent"/>
        """.trimMargin()

    val sessionParams = sessionParamsBuilder.copy(
        layoutPullParser = LayoutPullParser.createFromString(frameLayout),
        renderingMode = SessionParams.RenderingMode.V_SCROLL
    )
        .withTheme("Theme.Material.NoActionBar.Fullscreen", false)
        .build()

    renderSession = RenderSessionImpl(sessionParams)
    prepareThread()
    renderSession.init(sessionParams.timeout)
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
    name: String? = null
  ) {
    snapshotCount++

    val viewGroup = bridgeRenderSession.rootViews[0].viewObject as ViewGroup
    viewGroup.addView(view)
    try {
      renderSession.render(true)
      saveImage(name ?: snapshotCount.toString(), bridgeRenderSession.image)
    } finally {
      viewGroup.removeView(view)
    }
  }

  fun gif(
    view: View,
    name: String? = null,
    start: Long = 0,
    end: Long = 500,
    fps: Int = 30
  ) {

    val millisPerFrame = 1000f / fps
    val duration = end - start
    val frameCount = (((duration + millisPerFrame - 1) / millisPerFrame)).toInt()

    val viewGroup = bridgeRenderSession.rootViews[0].viewObject as ViewGroup
    viewGroup.addView(view)
    try {
      val outputStream = FileOutputStream("test.gif")
      val options = ImageOptions()
      val gifEncoder = GifEncoder(outputStream, width, height, 0)
      renderSession.setElapsedFrameTimeNanos(0L)
      for (frame in 0 until frameCount) {
        val timestamp = start + (frame * duration) / frameCount
        System_Delegate.setNanosTime(timestamp * 1_000_000)
        renderSession.render(true)
        gifEncoder.addImage(toInts(bridgeRenderSession.image), options)
      }
      gifEncoder.finishEncoding()
      outputStream.close()
    } finally {
      viewGroup.removeView(view)
      System_Delegate.setNanosTime(0L)
    }
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

  private fun saveImage(
    name: String,
    image: BufferedImage
  ) {
    val maxDimension = Math.max(image.width, image.height)
    val scale = THUMBNAIL_SIZE / maxDimension.toDouble()
    val copy = ImageUtils.scale(image, scale, scale)
    val snapshot = Snapshot(name, testName!!, Date())
    snapshotHandler.handle(snapshot, copy)
  }

  private fun Description.toTestName(): TestName {
    val packageName = testClass.`package`.name
    val className = testClass.name.substring(packageName.length + 1)
    return TestName(packageName, className, methodName)
  }
}

private fun toInts(bufferedImage: BufferedImage): Array<out IntArray> {
  return Array(bufferedImage.height) { y ->
    IntArray(bufferedImage.width) { x ->
      bufferedImage.getRGB(x, y)
    }
  }
}
