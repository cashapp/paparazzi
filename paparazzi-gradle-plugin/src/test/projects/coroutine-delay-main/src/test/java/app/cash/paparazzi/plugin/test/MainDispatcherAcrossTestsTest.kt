package app.cash.paparazzi.plugin.test

import androidx.compose.material.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.remember
import app.cash.paparazzi.Paparazzi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runners.MethodSorters
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.resetMain

/**
 * Regression test for https://github.com/cashapp/paparazzi/issues/2382.
 *
 * Each test gets a fresh layoutlib main Looper. The ordering is intentional: the first test
 * initializes the main dispatcher, and the second verifies it still dispatches to the current
 * Looper rather than the previous test's (torn-down) one.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class MainDispatcherAcrossTestsTest {
  @get:Rule
  val paparazzi = Paparazzi()

  init {
    Dispatchers.resetMain()
  }

  @Test fun a_directCompositionInitializesMainDispatcher() {
    val executions = AtomicInteger()

    paparazzi.snapshot {
      DispatcherBackedContent(executions)
    }

    assertEquals(1, executions.get())
  }

  @Test fun b_measureTimeCompositionUsesCurrentMainLooper() {
    val executions = AtomicInteger()

    paparazzi.snapshot {
      Scaffold {
        DispatcherBackedContent(executions)
      }
    }

    assertEquals(1, executions.get())
  }
}

@Composable
private fun DispatcherBackedContent(executions: AtomicInteger) {
  // On the main thread, Main.immediate must run inline. A dispatcher bound to a previous test's
  // Looper would report a dispatch is needed.
  assertFalse(Dispatchers.Main.immediate.isDispatchNeeded(EmptyCoroutineContext))

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
