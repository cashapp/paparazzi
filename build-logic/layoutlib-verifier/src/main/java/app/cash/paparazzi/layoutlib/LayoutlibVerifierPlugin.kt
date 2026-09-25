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

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Registers `verifyLayoutlibVersion`, `listUnverifiedLayoutlibVersions` and `mergeLayoutlibEntries`. The applying build
 * wires [VerifyLayoutlibVersionTask.paparazziJar], [VerifyLayoutlibVersionTask.defaultLayoutlibVersion]
 * and [VerifyLayoutlibVersionTask.pinnedLayoutlibApiVersion].
 */
public class LayoutlibVerifierPlugin : Plugin<Project> {
  override fun apply(project: Project) {
    val compatFile = project.layout.projectDirectory.file("gradle/layoutlib-compat.properties")
    val cacheDir = project.layout.buildDirectory.dir("layoutlib-verifier/cache")

    project.tasks.register("verifyLayoutlibVersion", VerifyLayoutlibVersionTask::class.java) {
      it.group = "verification"
      it.description = "Statically verify a layoutlib release (--layoutlib-version=X [--write])"
      it.write.convention(false)
      it.layoutlibApiFloor.convention("31.0.0")
      it.compatFile.convention(compatFile)
      it.cacheDir.convention(cacheDir)
    }

    project.tasks.register("mergeLayoutlibEntries", MergeLayoutlibEntriesTask::class.java) {
      it.group = "verification"
      it.description = "Merge entry fragments (--entries-dir=DIR) into gradle/layoutlib-compat.properties"
      it.compatFile.convention(compatFile)
    }

    project.tasks.register("listUnverifiedLayoutlibVersions", ListUnverifiedLayoutlibVersionsTask::class.java) {
      it.group = "verification"
      it.description = "List layoutlib releases not yet recorded in gradle/layoutlib-compat.properties"
      it.floor.convention("16.0.1")
      it.includeQualified.convention(false)
      it.compatFile.convention(compatFile)
      it.cacheDir.convention(cacheDir)
      it.outputFile.convention(project.layout.buildDirectory.file("layoutlib-verifier/unverified.json"))
    }
  }
}
