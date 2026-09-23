/*
 * Copyright (C) 2026 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package app.cash.paparazzi.layoutlib

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.gradle.work.DisableCachingByDefault

/**
 * Merges entry fragments (e.g. CI artifacts from parallel `verifyLayoutlibVersion --write` jobs)
 * into the compat file:
 *
 * ```
 * ./gradlew mergeLayoutlibEntries --entries-dir=entries
 * ```
 */
@DisableCachingByDefault(because = "Edits a source file in place")
public abstract class MergeLayoutlibEntriesTask : DefaultTask() {
  @get:Internal
  public abstract val entriesDir: DirectoryProperty

  @Option(option = "entries-dir", description = "Directory searched recursively for *.properties entry fragments")
  public fun setEntriesDirPath(path: String) {
    entriesDir.set(project.layout.projectDirectory.dir(path))
  }

  @get:Internal
  public abstract val compatFile: RegularFileProperty

  @TaskAction
  public fun merge() {
    val fragments = entriesDir.get().asFile.walkTopDown().filter { it.isFile && it.extension == "properties" }.toList()
    var compat = CompatFile.read(compatFile.get().asFile)
    val merged = fragments.flatMap { CompatFile.read(it).entries.entries }.map { it.key to it.value }
    merged.forEach { (version, entry) -> compat = compat.with(version, entry) }
    compat.writeTo(compatFile.get().asFile)
    logger.lifecycle(
      "Merged ${merged.map {
        it.first
      }.sortedWith(VersionOrder).ifEmpty { listOf("nothing") }.joinToString()}"
    )
  }
}
