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
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Wraps the [EngineExecutionListener] passed in by JUnit Platform.
 *
 * Tracks diff images recorded by Paparazzi during each leaf test execution and emits them as
 * [FileEntry] attachments on that test descriptor before forwarding `executionFinished`. Each
 * recorded diff is immediately copied into a per-test-execution directory so later tests with
 * colliding filenames cannot overwrite an earlier test's attachment.
 *
 * All other events are forwarded to the delegate unchanged.
 */
internal class PaparazziExecutionListener(
  private val delegate: EngineExecutionListener
) : EngineExecutionListener {

  override fun dynamicTestRegistered(testDescriptor: TestDescriptor) {
    delegate.dynamicTestRegistered(testDescriptor)
  }

  override fun executionSkipped(testDescriptor: TestDescriptor, reason: String) {
    delegate.executionSkipped(testDescriptor, reason)
  }

  override fun executionStarted(testDescriptor: TestDescriptor) {
    if (testDescriptor.isTest) {
      activeScope.set(AttachmentScope(id = nextScopeId.incrementAndGet(), parent = activeScope.get()))
    }
    delegate.executionStarted(testDescriptor)
  }

  override fun executionFinished(testDescriptor: TestDescriptor, testExecutionResult: TestExecutionResult) {
    // Emit attachments before forwarding the finished event, because Gradle's binary result
    // store ties published events to whichever test is still current.
    if (testDescriptor.isTest) {
      val scope = activeScope.get()
      if (scope != null) {
        if (scope.parent != null) {
          activeScope.set(scope.parent)
        } else {
          activeScope.remove()
        }
        scope.attachments.forEach { file ->
          delegate.fileEntryPublished(
            testDescriptor,
            FileEntry.from(file, "image/png")
          )
        }
      }
    }
    delegate.executionFinished(testDescriptor, testExecutionResult)
  }

  override fun reportingEntryPublished(testDescriptor: TestDescriptor, entry: ReportEntry) {
    delegate.reportingEntryPublished(testDescriptor, entry)
  }

  override fun fileEntryPublished(testDescriptor: TestDescriptor, file: FileEntry) {
    delegate.fileEntryPublished(testDescriptor, file)
  }

  private class AttachmentScope(
    val id: Long,
    val parent: AttachmentScope?
  ) {
    private val nextIndex = AtomicInteger(0)
    val attachments: MutableList<Path> = Collections.synchronizedList(mutableListOf())

    fun nextAttachmentIndex(): Int = nextIndex.getAndIncrement()
  }

  internal companion object {
    private val nextScopeId = AtomicLong(0)
    private val activeScope = InheritableThreadLocal<AttachmentScope?>()

    fun recordAttachment(deltaFile: File) {
      val scope = activeScope.get() ?: return
      if (!deltaFile.isFile) return

      val parentDir = deltaFile.absoluteFile.parentFile ?: return
      val isolatedDir = File(parentDir, ".attachments/${scope.id}/${scope.nextAttachmentIndex()}")
      isolatedDir.mkdirs()
      val isolatedCopy = File(isolatedDir, deltaFile.name).toPath()
      Files.copy(deltaFile.toPath(), isolatedCopy, StandardCopyOption.REPLACE_EXISTING)
      scope.attachments.add(isolatedCopy)
    }
  }
}
