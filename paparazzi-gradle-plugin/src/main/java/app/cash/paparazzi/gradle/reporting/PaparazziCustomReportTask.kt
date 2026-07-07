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
import java.io.File

/**
 * Paparazzi-owned HTML report generated directly from Gradle's binary test
 * results store (`SerializableTestResultStore` / `SerializableTestResult`),
 * bypassing both the deprecated `TestReporter` shim (legacy mode) and Gradle's
 * bundled `GenericHtmlTestReportGenerator` (native mode).
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
    appendStart("Paparazzi report", crumbs = emptyList())
    append("<h1 class=\"page-title\">Paparazzi test report</h1>")
    appendSummary(totalCount, passed, failed, skipped)

    if (byClass.isNotEmpty()) {
      append("<table class=\"results\"><thead><tr>")
      append("<th>Class</th><th>Tests</th><th>Failed</th><th>Skipped</th>")
      append("</tr></thead><tbody>")
      byClass.toSortedMap().forEach { (className, classTests) ->
        val classFailed = classTests.count { it.resultType == TestResult.ResultType.FAILURE }
        val classSkipped = classTests.count { it.resultType == TestResult.ResultType.SKIPPED }
        append("<tr>")
        append("<td><a href=\"classes/${escape(className)}.html\">${escape(className)}</a></td>")
        append("<td>${classTests.size}</td>")
        append("<td>${countBadge(classFailed, "fail")}</td>")
        append("<td>${countBadge(classSkipped, "skip")}</td>")
        append("</tr>")
      }
      append("</tbody></table>")
    }
    appendEnd()
  }

  private fun renderClassPage(
    className: String,
    tests: List<SerializableTestResult>,
    deltasDir: File?
  ): String = buildString {
    appendStart(className, crumbs = listOf("../index.html" to "All classes"))
    append("<h1 class=\"page-title\">${escape(className)}</h1>")

    val passed = tests.count { it.resultType == TestResult.ResultType.SUCCESS }
    val failed = tests.count { it.resultType == TestResult.ResultType.FAILURE }
    val skipped = tests.count { it.resultType == TestResult.ResultType.SKIPPED }
    appendSummary(tests.size, passed, failed, skipped)

    tests.sortedBy { it.name }.forEach { t ->
      append("<section class=\"test-card\">")
      append("<h2>")
      append(statusBadge(t.resultType))
      append("<span>${escape(t.displayName ?: t.name)}</span>")
      append("<span class=\"duration\">${t.duration} ms</span>")
      append("</h2>")

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
    deltasDir: File?,
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
    append("<div class=\"delta\">")
    append("<a href=\"$src\"><img src=\"$src\" alt=\"snapshot delta\"/></a>")
    append("<div class=\"caption\">${escape(match.name)}</div>")
    append("</div>")
  }

  // SerializableTestResult.getFailures() returns Gradle's shaded ImmutableList
  // type at compile time but a different (un-shaded) type at runtime — direct
  // calls trigger NoSuchMethodError. Reflection sidesteps the binding,
  // returning a List<*> from which SerializableFailure.getMessage() (a plain
  // String accessor) is safe to call.
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

  private fun StringBuilder.appendStart(title: String, crumbs: List<Pair<String, String>>) {
    append("<!DOCTYPE html>")
    append("<html><head><meta charset=\"utf-8\"><title>${escape(title)}</title>")
    append("<style>$STYLES</style>")
    append("</head><body>")
    append("<header class=\"app\"><div class=\"container\">")
    append("<h1>Paparazzi</h1>")
    append("</div></header>")
    append("<main class=\"container\">")
    if (crumbs.isNotEmpty()) {
      append("<nav class=\"breadcrumb\">")
      crumbs.forEachIndexed { i, (href, label) ->
        if (i > 0) append(" / ")
        append("<a href=\"$href\">← ${escape(label)}</a>")
      }
      append("</nav>")
    }
  }

  private fun StringBuilder.appendSummary(total: Int, passed: Int, failed: Int, skipped: Int) {
    append("<div class=\"summary-cards\">")
    append(summaryCard("Total", total, ""))
    append(summaryCard("Passed", passed, "pass"))
    append(summaryCard("Failed", failed, "fail"))
    append(summaryCard("Skipped", skipped, "skip"))
    append("</div>")
  }

  private fun summaryCard(label: String, count: Int, cls: String): String =
    "<div class=\"summary-card $cls\"><span class=\"count\">$count</span>" +
      "<span class=\"label\">${escape(label)}</span></div>"

  private fun countBadge(count: Int, cls: String): String =
    if (count == 0) "$count" else "<span class=\"badge $cls\">$count</span>"

  private fun statusBadge(result: TestResult.ResultType): String = when (result) {
    TestResult.ResultType.SUCCESS -> "<span class=\"badge pass\">SUCCESS</span>"
    TestResult.ResultType.FAILURE -> "<span class=\"badge fail\">FAILURE</span>"
    TestResult.ResultType.SKIPPED -> "<span class=\"badge skip\">SKIPPED</span>"
  }

  private fun StringBuilder.appendEnd() {
    append("</main></body></html>")
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

  private companion object {
    private val STYLES = """
      * { box-sizing: border-box; }
      body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
        margin: 0; background: #fafafa; color: #222; }
      .container { max-width: 1200px; margin: 0 auto; padding: 24px; }
      header.app { background: #1a5fb4; color: white; }
      header.app h1 { font-size: 1.05em; margin: 0; padding: 14px 0; letter-spacing: 0.3px; }
      .breadcrumb { font-size: 0.9em; color: #666; margin-bottom: 12px; }
      .breadcrumb a { color: #1a5fb4; text-decoration: none; }
      .breadcrumb a:hover { text-decoration: underline; }
      h1.page-title { margin: 0 0 16px; font-size: 1.6em; }
      .summary-cards { display: grid; grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
        gap: 12px; margin: 16px 0 24px; }
      .summary-card { background: white; border: 1px solid #e4e4e7; border-radius: 8px;
        padding: 14px 16px; box-shadow: 0 1px 2px rgba(0,0,0,0.04); }
      .summary-card .count { font-size: 1.8em; font-weight: 700; display: block; line-height: 1; }
      .summary-card .label { color: #666; font-size: 0.85em; display: block; margin-top: 6px; }
      .summary-card.pass .count { color: #1a7f1a; }
      .summary-card.fail .count { color: #b00020; }
      .summary-card.skip .count { color: #777; }
      table.results { width: 100%; background: white; border: 1px solid #e4e4e7;
        border-radius: 8px; border-collapse: separate; border-spacing: 0; overflow: hidden;
        box-shadow: 0 1px 2px rgba(0,0,0,0.04); }
      table.results th, table.results td { text-align: left; padding: 10px 14px;
        border-bottom: 1px solid #eee; }
      table.results th { background: #f8f8f8; font-weight: 600; color: #555;
        font-size: 0.85em; text-transform: uppercase; letter-spacing: 0.3px; }
      table.results tr:last-child td { border-bottom: none; }
      table.results a { color: #1a5fb4; text-decoration: none; }
      table.results a:hover { text-decoration: underline; }
      .badge { display: inline-block; padding: 2px 10px; border-radius: 12px;
        font-size: 0.78em; font-weight: 600; letter-spacing: 0.3px; }
      .badge.pass { background: #d1fadc; color: #0e5a16; }
      .badge.fail { background: #fce0e3; color: #93001a; }
      .badge.skip { background: #eaeaea; color: #555; }
      .test-card { background: white; border: 1px solid #e4e4e7; border-radius: 8px;
        padding: 14px 18px; margin: 14px 0; box-shadow: 0 1px 2px rgba(0,0,0,0.04); }
      .test-card h2 { display: flex; align-items: center; gap: 12px; margin: 0 0 8px;
        font-size: 1em; font-weight: 600; }
      .test-card .duration { color: #888; font-weight: normal; font-size: 0.85em;
        margin-left: auto; }
      .test-card pre { background: #f8f8f8; border: 1px solid #eee; border-radius: 4px;
        padding: 12px; overflow: auto; white-space: pre-wrap; word-break: break-word;
        font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: 0.85em; margin: 8px 0; }
      .test-card details summary { cursor: pointer; font-weight: 600; color: #444;
        user-select: none; margin: 8px 0; }
      .test-card .delta { margin-top: 12px; }
      .test-card .delta img { max-width: 100%; border: 1px solid #ddd; border-radius: 4px; }
      .test-card .delta .caption { font-size: 0.8em; color: #888; margin-top: 6px;
        font-family: ui-monospace, SFMono-Regular, Menlo, monospace; }
    """.trimIndent()
  }
}
