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
package app.cash.paparazzi.vintage

import app.cash.paparazzi.PaparazziReportingBridge
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.io.File

class PaparazziReportingBridgeTest {
  @AfterEach
  fun clearPublisher() {
    PaparazziReportingBridge.currentPublisher.remove()
  }

  @Test
  fun currentPublisher_isNullByDefault() {
    assertThat(PaparazziReportingBridge.currentPublisher.get()).isNull()
  }

  @Test
  fun publishFile_isNoOp_whenNoPublisherSet() {
    val file = File.createTempFile("snapshot", ".png")
    // Should not throw.
    PaparazziReportingBridge.publishFile(file)
  }

  @Test
  fun publishFile_invokesCurrentPublisher() {
    val published = mutableListOf<File>()
    PaparazziReportingBridge.currentPublisher.set { file -> published += file }

    val file = File.createTempFile("snapshot", ".png")
    PaparazziReportingBridge.publishFile(file)

    assertThat(published).containsExactly(file)
  }

  @Test
  fun publishFile_invokesCurrentPublisher_multipleTimesForMultipleFiles() {
    val published = mutableListOf<File>()
    PaparazziReportingBridge.currentPublisher.set { file -> published += file }

    val file1 = File.createTempFile("snapshot1", ".png")
    val file2 = File.createTempFile("snapshot2", ".png")
    PaparazziReportingBridge.publishFile(file1)
    PaparazziReportingBridge.publishFile(file2)

    assertThat(published).containsExactly(file1, file2).inOrder()
  }

  @Test
  fun publishFile_isNoOp_afterPublisherCleared() {
    val published = mutableListOf<File>()
    PaparazziReportingBridge.currentPublisher.set { file -> published += file }
    PaparazziReportingBridge.currentPublisher.remove()

    PaparazziReportingBridge.publishFile(File.createTempFile("snapshot", ".png"))

    assertThat(published).isEmpty()
  }
}
