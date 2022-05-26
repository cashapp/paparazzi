package app.cash.paparazzi.plugin.test

import leakcanary.GcTrigger
import leakcanary.ObjectWatcher
import shark.AndroidReferenceMatchers
import shark.AndroidReferenceMatchers.Companion.instanceFieldLeak
import shark.HeapAnalysisFailure
import shark.HeapAnalysisSuccess
import shark.HeapAnalyzer
import shark.KeyedWeakReferenceFinder
import shark.ObjectInspectors
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class JvmHeapAnalyzer(private val objectWatcher: ObjectWatcher) {
  private val fileNameFormat = SimpleDateFormat(DATE_PATTERN, Locale.US)

  fun assertNoLeaks() {
    GcTrigger.Default.runGc()
    if (objectWatcher.retainedObjectCount == 0) {
      println("No retained object found")
      return
    }
    val fileName = fileNameFormat.format(Date())
    val hprofFile = File(fileName)
    hprofFile.deleteOnExit()

    println("Dumping the heap to ${hprofFile.absolutePath}")
    HotSpotHeapDumper.dumpHeap(hprofFile.absolutePath)

    val analyzer = HeapAnalyzer { step ->
      println("Analysis in progress, working on: ${step.name}")
    }

    val heapDumpAnalysis = analyzer.analyze(
      heapDumpFile = hprofFile,
      leakingObjectFinder = KeyedWeakReferenceFinder,
      computeRetainedHeapSize = true,
      objectInspectors = ObjectInspectors.jdkDefaults,
      referenceMatchers = AndroidReferenceMatchers.ignoredReferencesOnly +
        instanceFieldLeak(
          "app.cash.paparazzi.plugin.test.LeakWatcherRule",
          "instance",
          "The rule reference itself should not count as a leak."
        )
    )

    when (heapDumpAnalysis) {
      is HeapAnalysisSuccess -> {
        if (heapDumpAnalysis.applicationLeaks.isNotEmpty()) {
          throw AssertionError(heapDumpAnalysis.applicationLeaks.joinToString("\n\n"))
        } else {
          println("Heap analyzed, but no application leaks found!")
        }
      }
      is HeapAnalysisFailure -> println("""====================================
        STACKTRACE

        ${heapDumpAnalysis.exception}"""
      )
    }
  }

  companion object {
    private const val DATE_PATTERN = "yyyy-MM-dd_HH-mm-ss_SSS'.hprof'"
  }
}
