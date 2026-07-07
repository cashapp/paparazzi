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
import org.gradle.api.tasks.Optional
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
 * Phase 3c: index page with summary + per-class detail pages with each
 * method's failure message and a thumbnail of the snapshot diff (when present
 * in `paparazzi.failures.dir`). stdout/stderr surfacing and visual polish
 * arrive in subsequent phases.
 */
public abstract class PaparazziCustomReportTask : DefaultTask() {

  @get:InputDirectory
  @get:PathSensitive(PathSensitivity.RELATIVE)
  public abstract val binaryResultsDirectory: DirectoryProperty

  @get:InputDirectory
  @get:Optional
  @get:PathSensitive(PathSensitivity.RELATIVE)
  public abstract val failuresDirectory: DirectoryProperty

  @get:OutputDirectory
  public abstract val reportDir: DirectoryProperty

  @TaskAction
  public fun generate() {
    val outputDir = reportDir.get().asFile.apply { mkdirs() }
    val classesDir = outputDir.resolve("classes").apply { mkdirs() }
    val deltasDir = failuresDirectory.orNull?.asFile

    val results = loadResults()
    val tests = results.filter { it.className != null }
    val byClass = tests.groupBy { it.className!! }

    val passed = tests.count { it.resultType == TestResult.ResultType.SUCCESS }
    val failed = tests.count { it.resultType == TestResult.ResultType.FAILURE }
    val skipped = tests.count { it.resultType == TestResult.ResultType.SKIPPED }

    outputDir.resolve("index.html").writeText(
      renderIndex(byClass, tests.size, passed, failed, skipped)
    )
    byClass.forEach { (className, classTests) ->
      classesDir.resolve("$className.html").writeText(
        renderClassPage(className, classTests, deltasDir)
      )
    }
  }

  private fun loadResults(): List<SerializableTestResult> {
    val store = SerializableTestResultStore(binaryResultsDirectory.get().asFile.toPath())
    val results = mutableListOf<SerializableTestResult>()
    store.forEachResult { _, _, result, _ -> results += result }
    return results
  }

  private fun renderIndex(
    byClass: Map<String, List<SerializableTestResult>>,
    totalCount: Int,
    passed: Int,
    failed: Int,
    skipped: Int
  ): String = buildString {
    appendStart("Paparazzi report")
    append("<h1>Paparazzi test report</h1>")
    appendSummary(totalCount, passed, failed, skipped)

    if (byClass.isNotEmpty()) {
      append("<table><thead><tr>")
      append("<th>Class</th><th>Tests</th><th>Failed</th><th>Skipped</th>")
      append("</tr></thead><tbody>")
      byClass.toSortedMap().forEach { (className, classTests) ->
        val classFailed = classTests.count { it.resultType == TestResult.ResultType.FAILURE }
        val classSkipped = classTests.count { it.resultType == TestResult.ResultType.SKIPPED }
        val rowClass = when {
          classFailed > 0 -> "fail"
          classSkipped > 0 -> "skip"
          else -> "pass"
        }
        append("<tr>")
        append("<td><a href=\"classes/${escape(className)}.html\">${escape(className)}</a></td>")
        append("<td>${classTests.size}</td>")
        append("<td class=\"$rowClass\">$classFailed</td>")
        append("<td>$classSkipped</td>")
        append("</tr>")
      }
      append("</tbody></table>")
    }
    appendEnd()
  }

  private fun renderClassPage(
    className: String,
    tests: List<SerializableTestResult>,
    deltasDir: java.io.File?
  ): String = buildString {
    appendStart(className)
    append("<p><a href=\"../index.html\">← All classes</a></p>")
    append("<h1>${escape(className)}</h1>")

    val passed = tests.count { it.resultType == TestResult.ResultType.SUCCESS }
    val failed = tests.count { it.resultType == TestResult.ResultType.FAILURE }
    val skipped = tests.count { it.resultType == TestResult.ResultType.SKIPPED }
    appendSummary(tests.size, passed, failed, skipped)

    tests.sortedBy { it.name }.forEach { t ->
      val cssClass = when (t.resultType) {
        TestResult.ResultType.SUCCESS -> "pass"
        TestResult.ResultType.FAILURE -> "fail"
        TestResult.ResultType.SKIPPED -> "skip"
        else -> ""
      }
      append("<section class=\"test\">")
      append("<h2><span class=\"$cssClass\">${t.resultType.name}</span> ")
      append("${escape(t.displayName ?: t.name)} <small>(${t.duration} ms)</small></h2>")

      if (t.resultType == TestResult.ResultType.FAILURE) {
        appendFailures(t)
        appendDeltaThumbnail(deltasDir, className, t)
      }
      append("</section>")
    }
    appendEnd()
  }

  private fun StringBuilder.appendFailures(t: SerializableTestResult) {
    val messages = failuresOf(t)
    if (messages.isEmpty()) return
    append("<details open><summary>Failure</summary>")
    messages.forEach { msg ->
      append("<pre>${escape(msg)}</pre>")
    }
    append("</details>")
  }

  private fun StringBuilder.appendDeltaThumbnail(
    deltasDir: java.io.File?,
    className: String,
    t: SerializableTestResult
  ) {
    if (deltasDir == null || !deltasDir.isDirectory) return
    // Snapshot.toFileName-style: delta-<package>_<class>_<method>.png
    val prefix = "delta-${className.replace('.', '_')}_${t.name}"
    val match = deltasDir.listFiles()?.firstOrNull {
      it.name.startsWith(prefix) && it.name.endsWith(".png")
    } ?: return
    val src = match.toURI().toString()
    append("<p><a href=\"$src\"><img src=\"$src\" alt=\"delta\" ")
    append("style=\"max-width:100%;border:1px solid #ddd\"/></a></p>")
  }

  // SerializableTestResult.getFailures() returns Gradle's shaded
  // ImmutableList type at compile time but a different (un-shaded) type at
  // runtime — direct calls trigger NoSuchMethodError. Reflection sidesteps the
  // binding, returning a List<*> from which SerializableFailure.getMessage()
  // (a plain String accessor) is safe to call.
  private fun failuresOf(t: SerializableTestResult): List<String> {
    return try {
      val method = SerializableTestResult::class.java.getMethod("getFailures")
      val raw = method.invoke(t) as List<*>
      raw.mapNotNull { failure ->
        if (failure == null) return@mapNotNull null
        val msgMethod = failure.javaClass.getMethod("getMessage")
        msgMethod.invoke(failure) as String?
      }
    } catch (e: ReflectiveOperationException) {
      emptyList()
    }
  }

  private fun StringBuilder.appendStart(title: String) {
    append("<!DOCTYPE html>")
    append("<html><head><meta charset=\"utf-8\"><title>${escape(title)}</title>")
    append("<style>")
    append("body{font-family:sans-serif;margin:24px;color:#222}")
    append("h1{margin-top:0}")
    append(".test{margin-top:24px;padding-top:8px;border-top:1px solid #eee}")
    append(".test h2{font-size:1.05em;margin:8px 0}")
    append(".test small{color:#888;font-weight:normal}")
    append("table{border-collapse:collapse;width:100%;margin-top:16px}")
    append("th,td{text-align:left;padding:6px 10px;border-bottom:1px solid #eee}")
    append("th{background:#f5f5f5}")
    append("a{color:#1a5fb4}")
    append("pre{background:#f8f8f8;padding:12px;overflow:auto;white-space:pre-wrap;word-break:break-word}")
    append("details summary{cursor:pointer}")
    append(".pass{color:#1a7f1a}.fail{color:#b00020;font-weight:600}.skip{color:#888}")
    append(".summary{display:flex;gap:24px;margin-top:8px}")
    append("</style></head><body>")
  }

  private fun StringBuilder.appendSummary(total: Int, passed: Int, failed: Int, skipped: Int) {
    append("<div class=\"summary\">")
    append("<span><strong>$total</strong> tests</span>")
    append("<span class=\"pass\"><strong>$passed</strong> passed</span>")
    append("<span class=\"fail\"><strong>$failed</strong> failed</span>")
    append("<span class=\"skip\"><strong>$skipped</strong> skipped</span>")
    append("</div>")
  }

  private fun StringBuilder.appendEnd() {
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
