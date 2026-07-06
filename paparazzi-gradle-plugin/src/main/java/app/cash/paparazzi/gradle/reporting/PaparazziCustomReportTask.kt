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
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.TaskAction

/**
 * Stub task for the `app.cash.paparazzi.reportType=custom` path (Option C).
 *
 * The end state for this task is a Paparazzi-owned HTML report generated
 * directly from the Gradle binary results store (`SerializableTestResultStore` /
 * `TestTreeModel` / `TestOutputReader`), bypassing both the deprecated
 * `TestReporter` interface (legacy mode) and Gradle's bundled
 * `GenericHtmlTestReportGenerator` (native mode).
 *
 * Today this is plumbing only — emits a placeholder `index.html` so the rest of
 * the build wiring can be validated independently. Subsequent phases will
 * replace the stub body with real `TestTreeModel` traversal.
 */
public abstract class PaparazziCustomReportTask : DefaultTask() {

  @get:InputDirectory
  @get:PathSensitive(PathSensitivity.RELATIVE)
  public abstract val binaryResultsDirectory: DirectoryProperty

  @get:OutputDirectory
  public abstract val reportDir: DirectoryProperty

  @TaskAction
  public fun generate() {
    val dir = reportDir.get().asFile.apply { mkdirs() }
    dir.resolve("index.html").writeText(
      """
      <!DOCTYPE html>
      <html>
        <head><meta charset="utf-8"><title>Paparazzi custom report (stub)</title></head>
        <body>
          <h1>Paparazzi custom report</h1>
          <p>Placeholder — Option C renderer not yet implemented.</p>
          <p>Binary results: ${binaryResultsDirectory.get().asFile.absolutePath}</p>
        </body>
      </html>
      """.trimIndent()
    )
  }
}
