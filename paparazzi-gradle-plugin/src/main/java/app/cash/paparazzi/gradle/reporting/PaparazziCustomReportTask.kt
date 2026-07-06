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
package app.cash.paparazzi.gradle.reporting

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.internal.tasks.testing.results.serializable.SerializableTestResult
import org.gradle.api.internal.tasks.testing.results.serializable.SerializableTestResultStore
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.testing.TestResult

/**
 * Paparazzi-owned HTML report generated directly from Gradle's binary test
 * results store (`SerializableTestResultStore` / `SerializableTestResult`),
 * bypassing both the deprecated `TestReporter` shim (legacy mode) and Gradle's
 * bundled `GenericHtmlTestReportGenerator` (native mode).
 *
 * Phase 3b: a minimal index page listing every test with its class, method,
 * duration, and status. Failure detail pages, diff thumbnails, and an
 * Accessibility tab arrive in subsequent phases.
 */
public abstract class PaparazziCustomReportTask : DefaultTask() {

  @get:InputDirectory
  @get:PathSensitive(PathSensitivity.RELATIVE)
  public abstract val binaryResultsDirectory: DirectoryProperty

  @get:OutputDirectory
  public abstract val reportDir: DirectoryProperty

  @TaskAction
  public fun generate() {
    val outputDir = reportDir.get().asFile.apply { mkdirs() }
    val results = loadResults()

    val tests = results.filter { it.className != null }
    val passed = tests.count { it.resultType == TestResult.ResultType.SUCCESS }
    val failed = tests.count { it.resultType == TestResult.ResultType.FAILURE }
    val skipped = tests.count { it.resultType == TestResult.ResultType.SKIPPED }

    outputDir.resolve("index.html").writeText(renderIndex(tests, passed, failed, skipped))
  }

  private fun loadResults(): List<SerializableTestResult> {
    val store = SerializableTestResultStore(binaryResultsDirectory.get().asFile.toPath())
    val results = mutableListOf<SerializableTestResult>()
    store.forEachResult { _, _, result, _ -> results += result }
    return results
  }

  private fun renderIndex(
    tests: List<SerializableTestResult>,
    passed: Int,
    failed: Int,
    skipped: Int
  ): String = buildString {
    append("<!DOCTYPE html>")
    append("<html><head><meta charset=\"utf-8\"><title>Paparazzi report</title>")
    append("<style>")
    append("body{font-family:sans-serif;margin:24px;color:#222}")
    append("h1{margin-top:0}")
    append("table{border-collapse:collapse;width:100%;margin-top:16px}")
    append("th,td{text-align:left;padding:6px 10px;border-bottom:1px solid #eee}")
    append("th{background:#f5f5f5}")
    append(".pass{color:#1a7f1a}.fail{color:#b00020;font-weight:600}.skip{color:#888}")
    append(".summary{display:flex;gap:24px;margin-top:8px}")
    append("</style>")
    append("</head><body>")
    append("<h1>Paparazzi test report</h1>")
    append("<div class=\"summary\">")
    append("<span><strong>${tests.size}</strong> tests</span>")
    append("<span class=\"pass\"><strong>$passed</strong> passed</span>")
    append("<span class=\"fail\"><strong>$failed</strong> failed</span>")
    append("<span class=\"skip\"><strong>$skipped</strong> skipped</span>")
    append("</div>")

    if (tests.isNotEmpty()) {
      append("<table><thead><tr>")
      append("<th>Class</th><th>Test</th><th>Duration</th><th>Result</th>")
      append("</tr></thead><tbody>")
      tests.sortedWith(compareBy({ it.className.orEmpty() }, { it.name })).forEach { t ->
        val cssClass = when (t.resultType) {
          TestResult.ResultType.SUCCESS -> "pass"
          TestResult.ResultType.FAILURE -> "fail"
          TestResult.ResultType.SKIPPED -> "skip"
          else -> ""
        }
        append("<tr>")
        append("<td>${escape(t.className.orEmpty())}</td>")
        append("<td>${escape(t.displayName ?: t.name)}</td>")
        append("<td>${t.duration} ms</td>")
        append("<td class=\"$cssClass\">${t.resultType.name}</td>")
        append("</tr>")
      }
      append("</tbody></table>")
    }

    append("</body></html>")
  }

  private fun escape(text: String): String = buildString(text.length) {
    text.forEach { c ->
      when (c) {
        '&' -> append("&amp;")
        '<' -> append("&lt;")
        '>' -> append("&gt;")
        '"' -> append("&quot;")
        '\'' -> append("&apos;")
        else -> append(c)
      }
    }
  }
}
