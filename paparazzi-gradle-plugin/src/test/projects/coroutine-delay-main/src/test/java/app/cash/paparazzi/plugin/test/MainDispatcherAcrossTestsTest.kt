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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.resetMain

/**
 * Regression test for https://github.com/cashapp/paparazzi/issues/2382.
 *
 * Every test gets a fresh layoutlib main Looper. The ordering is intentional: by the time the
 * second test runs, `Dispatchers.Main` has been resolved during an earlier test, so it must follow
 * the current Looper rather than the torn-down one.
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
      // Scaffold composes its content during measure, after the frame's Handler callbacks have run.
      Scaffold {
        DispatcherBackedContent(executions)
      }
    }

    assertEquals(1, executions.get())
  }
}

@Composable
private fun DispatcherBackedContent(executions: AtomicInteger) {
  // On the main thread, Main.immediate must run inline. A dispatcher bound to an earlier test's
  // Looper reports that a dispatch is needed.
  assertFalse(Dispatchers.Main.immediate.isDispatchNeeded(EmptyCoroutineContext))

  remember {
    object : RememberObserver {
      private var scope: CoroutineScope? = null

      override fun onRemembered() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).apply {
          launch { executions.incrementAndGet() }
        }
      }

      // Cancel with the composition, so work that was dispatched instead of run inline is dropped
      // rather than running during teardown, after the snapshot has been captured.
      override fun onForgotten() {
        scope?.cancel()
      }

      override fun onAbandoned() = Unit
    }
  }
}
