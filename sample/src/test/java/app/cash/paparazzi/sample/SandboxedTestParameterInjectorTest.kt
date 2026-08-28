package app.cash.paparazzi.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.cash.paparazzi.PaparazziRunner
import app.cash.paparazzi.SandboxedRunWith
import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterInjector
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/**
 * A third-party parameterized runner composed with the sandbox.
 *
 * `@RunWith` can only name one runner, so the inner one is declared with
 * [PaparazziRunner.SandboxedRunWith]. `TestParameterInjector` is then loaded and constructed inside
 * the sandbox, which means its reflection over constructor and method parameters, its enum
 * resolution, and the resulting test instances all stay on the sandbox side of the boundary.
 */
@RunWith(PaparazziRunner::class)
@SandboxedRunWith(TestParameterInjector::class)
class SandboxedTestParameterInjectorTest(
  @TestParameter private val config: Config
) {
  enum class Config(val deviceConfig: DeviceConfig) {
    NEXUS_4(DeviceConfig.NEXUS_4),
    NEXUS_5(DeviceConfig.NEXUS_5)
  }

  @get:Rule
  val paparazzi = Paparazzi(deviceConfig = config.deviceConfig)

  @Before
  fun assumeSandboxIsSupported() {
    // Windows resolves DLL imports by base name, so one JVM cannot hold both a sandboxed layoutlib
    // and the unsandboxed one this module's other tests load.
    assumeFalse(
      "layoutlib cannot be sandboxed alongside unsandboxed Paparazzi in one JVM on Windows",
      System.getProperty("os.name").lowercase(Locale.US).startsWith("windows")
    )
  }

  @Test
  fun rendersEachConfigInsideTheSandbox() {
    val sandboxLoader = "app.cash.paparazzi.internal.sandbox.SandboxClassLoader"

    // The enum reached the constructor as a sandbox type, not a host one.
    assertEquals(sandboxLoader, config.javaClass.classLoader!!.javaClass.name)
    assertEquals(sandboxLoader, javaClass.classLoader!!.javaClass.name)

    paparazzi.snapshot {
      Column(
        Modifier
          .background(Color.White)
          .fillMaxSize()
          .wrapContentSize()
      ) {
        Text("Sandboxed ${config.name}")
      }
    }
  }
}
