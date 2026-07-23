package app.cash.paparazzi.plugin.test

import android.os.SystemClock
import androidx.compose.material.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ComposeView
import app.cash.paparazzi.Paparazzi
import org.junit.Assert.assertEquals
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runners.MethodSorters
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.resetMain

@OptIn(ExperimentalCoroutinesApi::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class CoroutineDelayMainTest {
  @get:Rule
  val paparazzi = Paparazzi()

  init {
    // coroutines-test installs a TestMainDispatcher which is incompatible with ComposeUi
    Dispatchers.resetMain()
  }

  @Test fun aDirectCompositionInitializesMainDispatcher() {
    val executions = AtomicInteger()

    paparazzi.snapshot {
      DispatcherBackedContent(executions)
    }

    assertEquals(1, executions.get())
  }

  @Test fun bScaffoldCompositionUsesMainDispatcher() {
    val executions = AtomicInteger()

    paparazzi.snapshot {
      Scaffold {
        DispatcherBackedContent(executions)
      }
    }

    assertEquals(1, executions.get())
  }

  @Test fun delayUsesMainDispatcher() {
    var start = 0L
    var end = 0L
    paparazzi.gif(
      ComposeView(paparazzi.context).apply {
        setContent {
          start = SystemClock.uptimeMillis()
          LaunchedEffect(Unit) {
            delay(250)
            end = SystemClock.uptimeMillis()
          }
        }
      },
      end = 1000,
      fps = 4
    )

    assertEquals(250L, end - start)
  }
}

@Composable
private fun DispatcherBackedContent(executions: AtomicInteger) {
  remember {
    object : RememberObserver {
      override fun onRemembered() {
        CoroutineScope(Dispatchers.Main.immediate).launch {
          executions.incrementAndGet()
        }
      }

      override fun onForgotten() = Unit
      override fun onAbandoned() = Unit
    }
  }
}
