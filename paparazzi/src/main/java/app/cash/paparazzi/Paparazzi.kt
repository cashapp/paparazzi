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
import com.android.ide.common.rendering.api.SessionParams.RenderingMode
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.util.Date

public class Paparazzi @JvmOverloads constructor(
  environment: Environment = detectEnvironment(),
  deviceConfig: DeviceConfig = DeviceConfig.NEXUS_5,
  theme: String = "android:Theme.Material.NoActionBar.Fullscreen",
  renderingMode: RenderingMode = RenderingMode.NORMAL,
  appCompatEnabled: Boolean = true,
  renderExtensions: Set<RenderExtension> = setOf(),
  supportsRtl: Boolean = false,
  showSystemUi: Boolean = false,
  validateAccessibility: Boolean = false,
  private val maxPercentDifference: Double = 0.1,
  private val snapshotHandler: SnapshotHandler = determineHandler(maxPercentDifference)
) : TestRule {
  private val paparazzi = PaparazziSdk(
    environment,
    deviceConfig,
    theme,
    renderingMode,
    appCompatEnabled,
    renderExtensions,
    supportsRtl,
    showSystemUi,
    validateAccessibility
  )
  private var testName: TestName? = null

  public val layoutInflater: LayoutInflater
    get() = paparazzi.layoutInflater

  public val resources: Resources
    get() = paparazzi.resources

  public val context: Context
    get() = paparazzi.context

  override fun apply(
    base: Statement,
    description: Description
  ): Statement {
    return object : Statement() {
      override fun evaluate() {
        prepare(description)
        try {
          base.evaluate()
        } finally {
          close()
        }
      }
    }
  }

  public fun <V : View> inflate(@LayoutRes layoutId: Int): V = paparazzi.inflate(layoutId)

  public fun unsafeUpdateConfig(
    deviceConfig: DeviceConfig? = null,
    theme: String? = null,
    renderingMode: RenderingMode? = null
  ): Unit = paparazzi.unsafeUpdateConfig(deviceConfig, theme, renderingMode)

  public fun snapshot(name: String? = null, composable: @Composable () -> Unit) {
    setupFrameHandler(name, -1, 1)
    paparazzi.snapshot(composable)
  }

  @JvmOverloads
  public fun snapshot(view: View, name: String? = null, offsetMillis: Long = 0L) {
    setupFrameHandler(name, -1, 1)
    paparazzi.snapshot(view, offsetMillis)
  }

  @JvmOverloads
  public fun gif(view: View, name: String? = null, start: Long = 0L, end: Long = 500L, fps: Int = 30) {
    // Add one to the frame count so we get the last frame. Otherwise a 1 second, 60 FPS animation
    // our 60th frame will be at time 983 ms, and we want our last frame to be 1,000 ms. This gets
    // us 61 frames for a 1 second animation, 121 frames for a 2 second animation, etc.
    val durationMillis = (end - start).toInt()
    val frameCount = (durationMillis * fps) / 1000 + 1
    setupFrameHandler(name, frameCount, fps)
    paparazzi.gif(view, start, end, fps)
  }

  private fun prepare(description: Description) {
    testName = description.toTestName()
    paparazzi.prepare()
  }

  private fun close() {
    testName = null
    paparazzi.close()
  }

  private fun setupFrameHandler(name: String? = null, frameCount: Int, fps: Int) {
    val snapshot = Snapshot(name, testName!!, Date())
    val frameHandler = snapshotHandler.newFrameHandler(snapshot, frameCount, fps)
    paparazzi.setFrameHandler(frameHandler)
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

    private fun determineHandler(maxPercentDifference: Double): SnapshotHandler =
      if (isVerifying) {
        SnapshotVerifier(maxPercentDifference)
      } else {
        HtmlReportWriter()
      }
  }
}
