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
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.logging.Logging
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.gradle.work.DisableCachingByDefault
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.gradle.workers.WorkerExecutor
import javax.inject.Inject

/**
 * Statically verifies a layoutlib release against Paparazzi and optionally records it:
 *
 * ```
 * ./gradlew verifyLayoutlibVersion --layoutlib-version=17.0.3 [--write]
 * ```
 *
 * Without `--write`, an already-recorded version must match what's computed (so CI catches drift).
 * Then render against it with `LayoutlibCompatibilityTest` (see `LAYOUTLIB.md`).
 */
@DisableCachingByDefault(because = "Depends on remote Maven state")
public abstract class VerifyLayoutlibVersionTask @Inject constructor(
  private val workers: WorkerExecutor
) : DefaultTask() {
  @get:Input
  @get:Option(option = "layoutlib-version", description = "layoutlib version to verify, e.g. 17.0.3")
  public abstract val layoutlibVersion: Property<String>

  @get:Input
  @get:Option(option = "write", description = "Record the result in gradle/layoutlib-compat.properties")
  public abstract val write: Property<Boolean>

  @get:Input
  public abstract val defaultLayoutlibVersion: Property<String>

  @get:Input
  public abstract val pinnedLayoutlibApiVersion: Property<String>

  /** Oldest layoutlib-api release considered when searching for the minimum. */
  @get:Input
  public abstract val layoutlibApiFloor: Property<String>

  @get:InputFile
  @get:PathSensitive(PathSensitivity.NONE)
  public abstract val paparazziJar: RegularFileProperty

  /** Read, and written with `--write`; not declared as an input/output since it's both. */
  @get:Internal
  public abstract val compatFile: RegularFileProperty

  @get:Internal
  public abstract val cacheDir: DirectoryProperty

  init {
    outputs.upToDateWhen { false }
  }

  @TaskAction
  public fun verify() {
    workers.noIsolation().submit(VerifyLayoutlibWork::class.java) { params ->
      params.layoutlibVersion.set(layoutlibVersion)
      params.write.set(write)
      params.defaultLayoutlibVersion.set(defaultLayoutlibVersion)
      params.pinnedLayoutlibApiVersion.set(pinnedLayoutlibApiVersion)
      params.layoutlibApiFloor.set(layoutlibApiFloor)
      params.paparazziJar.set(paparazziJar)
      params.compatFile.set(compatFile)
      params.cacheDir.set(cacheDir)
    }
  }
}

internal interface VerifyLayoutlibParameters : WorkParameters {
  val layoutlibVersion: Property<String>
  val write: Property<Boolean>
  val defaultLayoutlibVersion: Property<String>
  val pinnedLayoutlibApiVersion: Property<String>
  val layoutlibApiFloor: Property<String>
  val paparazziJar: RegularFileProperty
  val compatFile: RegularFileProperty
  val cacheDir: DirectoryProperty
}

internal abstract class VerifyLayoutlibWork : WorkAction<VerifyLayoutlibParameters> {
  private val logger = Logging.getLogger(VerifyLayoutlibWork::class.java)

  override fun execute() {
    val version = parameters.layoutlibVersion.get()
    val compatFile = parameters.compatFile.get().asFile
    val verifier = LayoutlibVerifier(
      maven = GoogleMaven(parameters.cacheDir.get().asFile),
      defaultVersion = parameters.defaultLayoutlibVersion.get(),
      pinnedApiVersion = parameters.pinnedLayoutlibApiVersion.get(),
      apiFloor = parameters.layoutlibApiFloor.get(),
      paparazziJar = parameters.paparazziJar.get().asFile,
      log = { step, message -> logger.lifecycle("[$step] $message") }
    )

    val entry = try {
      verifier.verify(version)
    } catch (e: VerificationException) {
      throw GradleException("layoutlib $version failed verification: ${e.message}", e)
    }

    val compat = CompatFile.read(compatFile)
    val recorded = compat.entries[version]
    when {
      parameters.write.get() -> {
        compat.with(version, entry).writeTo(compatFile)
        logger.lifecycle("[write] recorded $version in ${compatFile.name}: $entry")
      }
      recorded != null && recorded != entry -> throw GradleException(
        "layoutlib $version is recorded as $recorded but verifies as $entry. Re-run with --write."
      )
      recorded == null -> logger.lifecycle("[write] $version is not recorded; re-run with --write to add $entry")
    }
    logger.lifecycle(
      "layoutlib $version verified statically. Render with:\n" +
        "  ./gradlew :paparazzi-gradle-plugin:test --tests app.cash.paparazzi.gradle.LayoutlibCompatibilityTest " +
        "-Ppaparazzi.layoutlib.versionsUnderTest=$version"
    )
  }
}
