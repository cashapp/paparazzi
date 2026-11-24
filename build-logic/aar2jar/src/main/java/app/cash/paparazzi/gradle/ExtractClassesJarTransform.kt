/*
 * Copyright (C) 2024 Square, Inc.
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
package app.cash.paparazzi.gradle

import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.work.DisableCachingByDefault
import java.nio.file.Files
import java.util.zip.ZipInputStream

@DisableCachingByDefault
abstract class ExtractClassesJarTransform : TransformAction<TransformParameters.None> {
  @get:PathSensitive(PathSensitivity.NAME_ONLY)
  @get:InputArtifact
  abstract val primaryInput: Provider<FileSystemLocation>

  override fun transform(outputs: TransformOutputs) {
    val inputFile = primaryInput.get().asFile
    val aarFileName = inputFile.nameWithoutExtension

    ZipInputStream(inputFile.inputStream().buffered()).use { input ->
      while(true) {
        val entry = input.nextEntry ?: break
        if (entry.name != "classes.jar") continue
        Files.copy(input, outputs.file("$aarFileName-${entry.name}").toPath())
        break
      }
    }
  }
}
