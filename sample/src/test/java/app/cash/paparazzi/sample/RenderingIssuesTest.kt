package app.cash.paparazzi.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import app.cash.paparazzi.Paparazzi
import com.android.ide.common.rendering.api.SessionParams
import org.junit.Rule
import org.junit.Test

class RenderingIssuesTest {
  @get:Rule
  val paparazzi = Paparazzi(
    renderingMode = SessionParams.RenderingMode.SHRINK,
  )

  @Test
  fun example() {
    paparazzi.snapshot("${osName()}-${gitShortSha()}") {
      Box(
        modifier = Modifier.background(Color(0xFF000033))
      ) {
        Text("ExampleText", color = Color.White)
      }
    }
  }

  @Test
  fun simpleBoxAlpha() {
    paparazzi.snapshot("${osName()}-${gitShortSha()}") { SimpleBoxAlphaRepro() }
  }

  @Test
  fun simpleBoxAlpha2() {
    paparazzi.snapshot("${osName()}-${gitShortSha()}") { SimpleBoxAlphaRepro2() }
  }

  fun osName(): String {
    val osName = System.getProperty("os.name")!!.lowercase()
    return when {
      osName.startsWith("windows") -> "win"
      osName.startsWith("mac") -> {
        val osArch = System.getProperty("os.arch")!!.lowercase()
        if (osArch.startsWith("x86")) "mac-x86" else "mac-arm"
      }

      else -> "linux"
    }
  }

  fun gitShortSha(): String {
    val command = "git rev-parse --short HEAD~"
    val p = ProcessBuilder().command(*command.split(" ").toTypedArray()).start()
    val exit = p.waitFor()
    if (exit == 0) {
      return p.inputStream.bufferedReader().readText()
    } else {
      throw Exception(p.errorStream.bufferedReader().readText())
    }
  }
}
