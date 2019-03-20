/*
 * Copyright (C) 2019 Square, Inc.
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
package com.squareup.cash.screenshot.jvm

import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.awt.image.BufferedImage
import java.io.File
import java.time.Instant
import java.util.Date

class RunWriterTest {
  @Rule
  @JvmField
  var temporaryFolder: TemporaryFolder = TemporaryFolder()

  private val anyImage = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)

  @Test
  fun happyPath() {
    val runWriter = RunWriter("run_one", temporaryFolder.root)
    runWriter.use {
      runWriter.add(
          Shot(
              name = "loading",
              testName = TestName("com.squareup.paparazzi", "CelebrityTest", "testSettings"),
              timestamp = Instant.parse("2019-03-20T10:27:43Z").toDate(),
              tags = listOf("redesign")
          ),
          anyImage)
    }

    assertThat(File("${temporaryFolder.root}/index.js")).hasContent("""
        |window.all_runs = [
        |  "run_one"
        |];
        |""".trimMargin())

    assertThat(File("${temporaryFolder.root}/run_one/run.js")).hasContent("""
        |window.runs["run_one"] = [
        |  {
        |    "file": "loading.png",
        |    "name": "loading",
        |    "tags": [
        |      "redesign"
        |    ],
        |    "testName": "com.squareup.paparazzi.CelebrityTest#testSettings",
        |    "timestamp": "2019-03-20T10:27:43.000Z"
        |  }
        |];
        |""".trimMargin())
  }

  private fun Instant.toDate() = Date(toEpochMilli())
}