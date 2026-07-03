package app.cash.paparazzi.junitplatform

import org.junit.platform.engine.EngineExecutionListener
import org.junit.platform.engine.TestDescriptor

/**
 * Thread-local registry that bridges Paparazzi's runtime to JUnit Platform's
 * [EngineExecutionListener] during test execution.
 *
 * The engine wrapper sets the active listener and tracks the current
 * [TestDescriptor] around each test method. The Paparazzi SDK reads this state
 * when it has artifacts to emit (e.g. a snapshot diff image), so the artifact
 * can be attached to the right test in Gradle's binary test result store.
 *
 * When `paparazzi.reportType` is anything other than `native`, the engine
 * wrapper does not populate this registry and [listener] / [currentTest] return
 * `null`. Callers must check for `null` and no-op when the bridge is inactive.
 */
internal object PaparazziSnapshotReporter {
  private val activeListener = ThreadLocal<EngineExecutionListener?>()
  private val currentTest = ThreadLocal<TestDescriptor?>()

  fun setActive(listener: EngineExecutionListener?) {
    if (listener == null) activeListener.remove() else activeListener.set(listener)
  }

  fun setCurrentTest(descriptor: TestDescriptor?) {
    if (descriptor == null) currentTest.remove() else currentTest.set(descriptor)
  }

  fun listener(): EngineExecutionListener? = activeListener.get()

  fun currentTest(): TestDescriptor? = currentTest.get()
}
