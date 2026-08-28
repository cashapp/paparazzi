package app.cash.paparazzi.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import app.cash.paparazzi.Paparazzi
import app.cash.paparazzi.PaparazziRunner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The end-to-end case for [PaparazziRunner]: a real Compose hierarchy rendered inside an isolated
 * layoutlib sandbox.
 *
 * A `@Composable` lambda written here is compiled into this test class. Under a plain `TestRule`
 * the class would be defined by the application class loader, so the lambda would close over the
 * host's `androidx.compose` classes and could not be handed to a sandbox owning its own copy. The
 * runner reloads this class first, which is what makes the whole thing coherent.
 */
@RunWith(PaparazziRunner::class)
class SandboxedComposeTest {
  @get:Rule
  val paparazzi = Paparazzi()

  @Test
  fun rendersComposeInsideSandbox() {
    val sandboxLoader = "app.cash.paparazzi.internal.sandbox.SandboxClassLoader"

    assertEquals(sandboxLoader, javaClass.classLoader!!.javaClass.name)
    assertEquals(sandboxLoader, Modifier::class.java.classLoader!!.javaClass.name)
    assertEquals(sandboxLoader, android.view.View::class.java.classLoader!!.javaClass.name)

    var composed = false
    paparazzi.snapshot {
      composed = true
      Column(
        Modifier
          .background(Color.White)
          .fillMaxSize()
          .wrapContentSize()
      ) {
        Text("Hello from the sandbox")
      }
    }

    assertTrue(composed)
  }
}
