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
import android.view.LayoutInflater
import android.view.View
import androidx.annotation.LayoutRes
import androidx.compose.runtime.Composable
import app.cash.paparazzi.internal.Differ
import app.cash.paparazzi.internal.OffByTwo
import app.cash.paparazzi.internal.PixelPerfect
import com.android.ide.common.rendering.api.SessionParams.RenderingMode
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.util.Date

public class Paparazzi @JvmOverloads constructor(
  private val environment: Environment = detectEnvironment(),
  private val deviceConfig: DeviceConfig = DeviceConfig.NEXUS_5,
  private val theme: String = "android:Theme.Material.NoActionBar.Fullscreen",
  private val renderingMode: RenderingMode = RenderingMode.NORMAL,
  private val appCompatEnabled: Boolean = true,
  private val maxPercentDifference: Double = detectMaxPercentDifferenceDefault(),
  private val withExpectedActualLabels: Boolean = true,
  private val snapshotHandler: SnapshotHandler = determineHandler(maxPercentDifference, differ, withExpectedActualLabels),
  private val renderExtensions: Set<RenderExtension> = setOf(),
  private val supportsRtl: Boolean = false,
  private val showSystemUi: Boolean = false,
  private val useDeviceResolution: Boolean = false
) : TestRule {
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
    maxPercentDifference: Double = detectMaxPercentDifferenceDefault(),
    snapshotHandler: SnapshotHandler = determineHandler(maxPercentDifference, differ),
    renderExtensions: Set<RenderExtension> = setOf(),
    supportsRtl: Boolean = false,
    showSystemUi: Boolean = false,
    useDeviceResolution: Boolean = false,
    validateAccessibility: Boolean = false
  ) : this(
    environment,
    deviceConfig,
    theme,
    renderingMode,
    appCompatEnabled,
    maxPercentDifference,
    snapshotHandler,
    renderExtensions,
    supportsRtl,
    showSystemUi,
    useDeviceResolution
  ) {
    this.validateAccessibility = validateAccessibility
  }
  private lateinit var sdk: PaparazziSdk
  private lateinit var frameHandler: SnapshotHandler.FrameHandler
  private var testName: TestName? = null

  public val layoutInflater: LayoutInflater
    get() = sdk.layoutInflater

  public val resources: Resources
    get() = sdk.resources

  public val context: Context
    get() = sdk.context

  override fun apply(base: Statement, description: Description): Statement {
    return object : Statement() {
      override fun evaluate() {
        sdk = PaparazziSdk(
          environment = environment,
          deviceConfig = deviceConfig,
          theme = theme,
          renderingMode = renderingMode,
          appCompatEnabled = appCompatEnabled,
          renderExtensions = renderExtensions,
          supportsRtl = supportsRtl,
          showSystemUi = showSystemUi,
          validateAccessibility = validateAccessibility,
          onNewFrame = { frameHandler.handle(it) },
          useDeviceResolution = useDeviceResolution
        )
        sdk.setup()
        prepare(description)
        try {
          base.evaluate()
        } finally {
          close()
        }
      }
    }
  }

  public fun prepare(description: Description) {
    testName = description.toTestName()
    sdk.prepare()
  }

  public fun close() {
    testName = null
    sdk.teardown()
    snapshotHandler.close()
  }

  public fun <V : View> inflate(@LayoutRes layoutId: Int): V = sdk.inflate(layoutId)

  public fun snapshot(name: String? = null, composable: @Composable () -> Unit) {
    createFrameHandler(name).use { handler ->
      frameHandler = handler
      sdk.snapshot(composable)
    }
  }

  @JvmOverloads
  public fun snapshot(view: View, name: String? = null, offsetMillis: Long = 0L) {
    createFrameHandler(name).use { handler ->
      frameHandler = handler
      sdk.snapshot(view, offsetMillis)
    }
  }

  @JvmOverloads
  public fun gif(view: View, name: String? = null, start: Long = 0L, end: Long = 500L, fps: Int = 30) {
    // Add one to the frame count so we get the last frame. Otherwise a 1 second, 60 FPS animation
    // our 60th frame will be at time 983 ms, and we want our last frame to be 1,000 ms. This gets
    // us 61 frames for a 1 second animation, 121 frames for a 2 second animation, etc.
    val durationMillis = (end - start).toInt()
    val frameCount = (durationMillis * fps) / 1000 + 1
    createFrameHandler(name, frameCount, fps).use { handler ->
      frameHandler = handler
      sdk.gif(view, start, end, fps)
    }
  }

  public fun unsafeUpdateConfig(
    deviceConfig: DeviceConfig? = null,
    theme: String? = null,
    renderingMode: RenderingMode? = null
  ): Unit = sdk.unsafeUpdateConfig(deviceConfig, theme, renderingMode)

  private fun createFrameHandler(
    name: String? = null,
    frameCount: Int = 1,
    fps: Int = -1
  ): SnapshotHandler.FrameHandler {
    val snapshot = Snapshot(name, testName!!, Date())
    return snapshotHandler.newFrameHandler(snapshot, frameCount, fps)
  }

  private fun Description.toTestName(): TestName {
    val fullQualifiedName = className
    val packageName = fullQualifiedName.substringBeforeLast('.', missingDelimiterValue = "")
    val className = fullQualifiedName.substringAfterLast('.')
    return TestName(packageName, className, methodName)
  }

  private companion object {
    private val isVerifying: Boolean =
      System.getProperty("paparazzi.test.verify")?.toBoolean() == true

    private val differ: Differ =
      System.getProperty("app.cash.paparazzi.differ")?.lowercase().let { differ ->
        when (differ) {
          "offbytwo" -> OffByTwo
          "pixelperfect" -> PixelPerfect
          null, "", "default" -> OffByTwo
          else -> error("Unknown differ type '$differ'.")
        }
      }

    private fun determineHandler(maxPercentDifference: Double, differ: Differ, withExpectedActualLabels: Boolean): SnapshotHandler =
      if (isVerifying) {
        SnapshotVerifier(maxPercentDifference, differ = differ, withExpectedActualLabels)
      } else {
        HtmlReportWriter(maxPercentDifference = maxPercentDifference, differ = differ)
      }
  }
}
