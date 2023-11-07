package app.cash.paparazzi

import android.view.View
import androidx.annotation.LayoutRes
import androidx.compose.runtime.Composable
import app.cash.paparazzi.snapshotter.ClipSpec
import app.cash.paparazzi.snapshotter.Device
import app.cash.paparazzi.snapshotter.FrameSpec
import app.cash.paparazzi.test.HtmlReportWriter
import app.cash.paparazzi.test.SnapshotVerifier
import app.cash.paparazzi.test.TestName
import app.cash.paparazzi.test.TestRecord
import app.cash.paparazzi.test.TestSnapshotHandler
import com.android.ide.common.rendering.api.SessionParams
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.util.Date

class Paparazzi @JvmOverloads constructor(
  private val environment: Environment = detectEnvironment(),
  deviceConfig: DeviceConfig = DeviceConfig.NEXUS_5,
  theme: String = "android:Theme.Material.NoActionBar.Fullscreen",
  renderingMode: SessionParams.RenderingMode = SessionParams.RenderingMode.NORMAL,
  appCompatEnabled: Boolean = true,
  private val renderExtensions: Set<RenderExtension> = setOf(),
  supportsRtl: Boolean = false,
  showSystemUi: Boolean = false,
  private val validateAccessibility: Boolean = false,
  private val maxPercentDifference: Double = 0.1,
  private val testSnapshotHandler: TestSnapshotHandler = determineHandler(maxPercentDifference)
) : TestRule {
  private var frameSpec = FrameSpec(
    deviceConfig,
    renderingMode,
    theme,
    supportsRtl,
    showSystemUi,
    appCompatEnabled
  )
  private var device = createDevice()

  private var testName: TestName? = null

  override fun apply(base: Statement, description: Description): Statement {
    val statement = object : Statement() {
      override fun evaluate() {
        testName = description.toTestName()
        try {
          base.evaluate()
        } finally {
          close()
        }
      }
    }
    return statement
  }

  val context
    get() = device.context
  val resources
    get() = device.resources
  val layoutInflater
    get() = device.layoutInflater

  fun <V : View> inflate(@LayoutRes layoutId: Int): V = device.inflate(layoutId)

  fun snapshot(name: String? = null, composable: @Composable () -> Unit) {
    val image = device.snapshot(composable, 0L)
    val testRecord = createTestRecord(name)
    testSnapshotHandler.handleSnapshot(image, testRecord)
  }

  @JvmOverloads
  fun snapshot(view: View, name: String? = null, offsetMillis: Long = 0L) {
    val image = device.snapshot(view, offsetMillis)
    val testRecord = createTestRecord(name)
    testSnapshotHandler.handleSnapshot(image, testRecord)
  }

  @JvmOverloads
  fun gif(view: View, name: String? = null, start: Long = 0L, end: Long = 500L, fps: Int = 30) {
    val clipSpec = ClipSpec(frameSpec, start, end, fps)
    val clip = device.clip(view, clipSpec)
    val testRecord = createTestRecord(name)
    testSnapshotHandler.handleClip(clip, testRecord)
  }

  private fun createTestRecord(name: String?) = TestRecord(
    name = name,
    testName = testName!!,
    timestamp = Date()
  )

  private fun close() {
    device.close()
  }

  private fun Description.toTestName(): TestName {
    val fullQualifiedName = className
    val packageName = fullQualifiedName.substringBeforeLast('.', missingDelimiterValue = "")
    val className = fullQualifiedName.substringAfterLast('.')
    return TestName(packageName, className, methodName)
  }

  fun unsafeUpdateConfig(
    deviceConfig: DeviceConfig? = null,
    theme: String? = null,
    renderingMode: SessionParams.RenderingMode? = null
  ) {
    if (deviceConfig == null && theme == null && renderingMode == null) return
    val newDeviceConfig = deviceConfig ?: frameSpec.deviceConfig
    val newTheme = theme ?: frameSpec.theme
    val newRenderingMode = renderingMode ?: frameSpec.renderingMode
    frameSpec = frameSpec.copy(
      deviceConfig = newDeviceConfig,
      theme = newTheme,
      renderingMode = newRenderingMode
    )
    device.close()
    device = createDevice()
  }

  private fun createDevice() = Device(environment, validateAccessibility, frameSpec, renderExtensions)

  companion object {
    private val isVerifying: Boolean =
      System.getProperty("paparazzi.test.verify")?.toBoolean() == true

    private fun determineHandler(maxPercentDifference: Double): TestSnapshotHandler =
      if (isVerifying) {
        SnapshotVerifier(maxPercentDifference)
      } else {
        HtmlReportWriter()
      }
  }
}
