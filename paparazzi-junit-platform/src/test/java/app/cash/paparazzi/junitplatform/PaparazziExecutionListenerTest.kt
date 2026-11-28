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

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.platform.engine.EngineExecutionListener
import org.junit.platform.engine.TestDescriptor
import org.junit.platform.engine.TestExecutionResult
import org.junit.platform.engine.UniqueId
import org.junit.platform.engine.reporting.FileEntry
import org.junit.platform.engine.reporting.ReportEntry
import org.junit.platform.engine.support.descriptor.AbstractTestDescriptor
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files

class PaparazziExecutionListenerTest {
  @get:Rule
  val tempFolder = TemporaryFolder()

  @Test
  fun failingLongerNameDoesNotAttachToPassingPrefixTest() {
    val published = mutableListOf<Pair<String, FileEntry>>()
    val listener = PaparazziExecutionListener(recordingDelegate(published))
    val failureDir = tempFolder.newFolder("failures")
    val sharedDelta = File(failureDir, "delta-app.example_NameCollisionTest_render_detail.png")

    val renderDetail = fakeTestDescriptor("render_detail")
    listener.executionStarted(renderDetail)
    sharedDelta.writeText("render_detail diff")
    recordPaparazziAttachment(sharedDelta)
    listener.executionFinished(renderDetail, TestExecutionResult.failed(AssertionError("diff")))

    val render = fakeTestDescriptor("render")
    listener.executionStarted(render)
    listener.executionFinished(render, TestExecutionResult.successful())

    assertThat(published.map { it.first }).containsExactly("render_detail")
    assertThat(published.single().second.path.fileName.toString()).isEqualTo(sharedDelta.name)
    assertThat(Files.readString(published.single().second.path)).isEqualTo("render_detail diff")
  }

  @Test
  fun collidingFilenamesPreserveEachTestsDiffImage() {
    val published = mutableListOf<Pair<String, FileEntry>>()
    val listener = PaparazziExecutionListener(recordingDelegate(published))
    val failureDir = tempFolder.newFolder("failures")
    val sharedDelta = File(failureDir, "delta-app.example_NameCollisionTest_render_detail.png")

    val renderDetail = fakeTestDescriptor("render_detail")
    listener.executionStarted(renderDetail)
    sharedDelta.writeText("first test diff")
    recordPaparazziAttachment(sharedDelta)
    listener.executionFinished(renderDetail, TestExecutionResult.failed(AssertionError("diff 1")))

    val renderWithLabel = fakeTestDescriptor("render")
    listener.executionStarted(renderWithLabel)
    sharedDelta.writeText("second test diff")
    recordPaparazziAttachment(sharedDelta)
    listener.executionFinished(renderWithLabel, TestExecutionResult.failed(AssertionError("diff 2")))

    assertThat(published).hasSize(2)
    val (firstTest, firstEntry) = published[0]
    val (secondTest, secondEntry) = published[1]
    assertThat(firstTest).isEqualTo("render_detail")
    assertThat(secondTest).isEqualTo("render")
    assertThat(firstEntry.path.fileName.toString()).isEqualTo(sharedDelta.name)
    assertThat(secondEntry.path.fileName.toString()).isEqualTo(sharedDelta.name)
    assertThat(Files.readString(firstEntry.path)).isEqualTo("first test diff")
    assertThat(Files.readString(secondEntry.path)).isEqualTo("second test diff")
  }

  @Test
  fun recordOutsideActiveTestIsNoOp() {
    val failureDir = tempFolder.newFolder("failures")
    val delta = File(failureDir, "delta-app.example_OutsideTest_verify.png").apply {
      writeText("diff")
    }

    recordPaparazziAttachment(delta)

    assertThat(File(failureDir, ".attachments").exists()).isFalse()
  }

  private fun recordingDelegate(published: MutableList<Pair<String, FileEntry>>): EngineExecutionListener =
    object : EngineExecutionListener {
      override fun dynamicTestRegistered(testDescriptor: TestDescriptor) = Unit
      override fun executionSkipped(testDescriptor: TestDescriptor, reason: String) = Unit
      override fun executionStarted(testDescriptor: TestDescriptor) = Unit
      override fun executionFinished(testDescriptor: TestDescriptor, testExecutionResult: TestExecutionResult) = Unit

      override fun reportingEntryPublished(testDescriptor: TestDescriptor, entry: ReportEntry) = Unit
      override fun fileEntryPublished(testDescriptor: TestDescriptor, file: FileEntry) {
        published += testDescriptor.displayName to file
      }
    }

  private fun fakeTestDescriptor(name: String): TestDescriptor =
    object : AbstractTestDescriptor(UniqueId.forEngine("test").append("test", name), name) {
      override fun getType(): TestDescriptor.Type = TestDescriptor.Type.TEST
    }
}
