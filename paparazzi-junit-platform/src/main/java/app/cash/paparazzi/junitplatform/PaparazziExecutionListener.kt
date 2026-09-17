/*
 * Copyright (C) 2026 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package app.cash.paparazzi.junitplatform

import org.junit.platform.engine.EngineExecutionListener
import org.junit.platform.engine.TestDescriptor
import org.junit.platform.engine.TestExecutionResult
import org.junit.platform.engine.reporting.FileEntry
import org.junit.platform.engine.reporting.ReportEntry
import org.junit.platform.engine.support.descriptor.MethodSource
import java.io.File

/**
 * Wraps the [EngineExecutionListener] passed in by JUnit Platform.
 *
 * On `executionFinished` for leaf test descriptors, scans Paparazzi's failure
 * directory for any diff images produced during the test method and emits each
 * one as a [FileEntry] attachment on that test descriptor. Diff files are
 * discovered by filesystem convention: Paparazzi writes them to
 * `paparazzi.failures.dir` with names of the form
 * `delta-<packageName>_<simpleClassName>_<methodName>[_<label>].png`. The class comes from the
 * descriptor's [MethodSource], the method name from [methodNameOf].
 *
 * All other events are forwarded to the delegate unchanged.
 */
internal class PaparazziExecutionListener(
  private val delegate: EngineExecutionListener,
  private val methodNameOf: (TestDescriptor, MethodSource) -> String
) : EngineExecutionListener {

  override fun dynamicTestRegistered(testDescriptor: TestDescriptor) {
    delegate.dynamicTestRegistered(testDescriptor)
  }

  override fun executionSkipped(testDescriptor: TestDescriptor, reason: String) {
    delegate.executionSkipped(testDescriptor, reason)
  }

  override fun executionStarted(testDescriptor: TestDescriptor) {
    delegate.executionStarted(testDescriptor)
  }

  override fun executionFinished(testDescriptor: TestDescriptor, testExecutionResult: TestExecutionResult) {
    // Emit attachments before forwarding the finished event, because Gradle's binary result
    // store ties published events to whichever test is still current.
    if (testDescriptor.isTest) {
      emitDiffAttachments(testDescriptor)
    }
    delegate.executionFinished(testDescriptor, testExecutionResult)
  }

  override fun reportingEntryPublished(testDescriptor: TestDescriptor, entry: ReportEntry) {
    delegate.reportingEntryPublished(testDescriptor, entry)
  }

  override fun fileEntryPublished(testDescriptor: TestDescriptor, file: FileEntry) {
    delegate.fileEntryPublished(testDescriptor, file)
  }

  private fun emitDiffAttachments(testDescriptor: TestDescriptor) {
    val failureDirPath = System.getProperty(SYSTEM_PROPERTY_FAILURES_DIR) ?: return
    val failureDir = File(failureDirPath)
    if (!failureDir.isDirectory) return

    val source = testDescriptor.source.orElse(null) as? MethodSource ?: return
    val packageName = source.className.substringBeforeLast('.', missingDelimiterValue = "")
    val simpleClassName = source.className.substringAfterLast('.')
    val methodName = methodNameOf(testDescriptor, source).replace(WHITESPACE, "_")

    val prefix = "delta-${packageName}_${simpleClassName}_$methodName"

    failureDir.listFiles()
      ?.filter { file ->
        val name = file.name
        if (!name.startsWith(prefix) || !name.endsWith(".png")) return@filter false
        // '.' when unlabelled, '_' before a label; a '_' inside the method name over-matches.
        val boundary = name.getOrNull(prefix.length)
        boundary == '.' || boundary == '_'
      }
      ?.forEach { file ->
        delegate.fileEntryPublished(
          testDescriptor,
          FileEntry.from(file.toPath(), "image/png")
        )
      }
  }

  private companion object {
    const val SYSTEM_PROPERTY_FAILURES_DIR: String = "paparazzi.failures.dir"
    val WHITESPACE = Regex("\\s")
  }
}
