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
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.gradle.work.DisableCachingByDefault

/**
 * Lists stable layoutlib releases at or above [floor] that aren't recorded in the compat file, as
 * a JSON array in [outputFile] (for a CI matrix) and one per line on stdout.
 */
@DisableCachingByDefault(because = "Depends on remote Maven state")
public abstract class ListUnverifiedLayoutlibVersionsTask : DefaultTask() {
  @get:Input
  @get:Option(option = "floor", description = "Oldest layoutlib version to consider")
  public abstract val floor: Property<String>

  /** Include qualified releases like `16.1.0-jdk17`. */
  @get:Input
  public abstract val includeQualified: Property<Boolean>

  @get:InputFile
  @get:PathSensitive(PathSensitivity.NONE)
  public abstract val compatFile: RegularFileProperty

  @get:OutputFile
  public abstract val outputFile: RegularFileProperty

  @get:Internal
  public abstract val cacheDir: DirectoryProperty

  init {
    outputs.upToDateWhen { false }
  }

  @TaskAction
  public fun list() {
    val recorded = CompatFile.read(compatFile.get().asFile).entries.keys
    val unverified = GoogleMaven(cacheDir.get().asFile).versionsFresh("layoutlib")
      .filter { VersionOrder.compare(it, floor.get()) >= 0 }
      .filter { includeQualified.get() || VersionOrder.isStable(it) }
      .filter { it !in recorded }
    outputFile.get().asFile.writeText(unverified.joinToString(prefix = "[", postfix = "]") { "\"$it\"" })
    if (unverified.isEmpty()) {
      logger.lifecycle("All layoutlib releases >= ${floor.get()} are recorded.")
    } else {
      println(unverified.joinToString("\n"))
    }
  }
}
