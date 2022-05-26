package app.cash.paparazzi.plugin.test

import app.cash.paparazzi.Paparazzi
import leakcanary.ObjectWatcher
import org.junit.rules.TestWatcher
import org.junit.runner.Description

class LeakWatcherRule(val paparazzi: Paparazzi) : TestWatcher() {
  private val objectWatcher = ObjectWatcher(
    clock = { System.currentTimeMillis() },
    checkRetainedExecutor = Runnable::run
  )

  override fun succeeded(description: Description) {
    objectWatcher.expectWeaklyReachable(
      watchedObject = paparazzi,
      description = "$paparazzi is closed and should be garbage collected"
    )
    val heapAnalyzer = JvmHeapAnalyzer(objectWatcher)
    heapAnalyzer.assertNoLeaks()
  }

  override fun finished(description: Description?) {
    objectWatcher.clearWatchedObjects()
  }
}
