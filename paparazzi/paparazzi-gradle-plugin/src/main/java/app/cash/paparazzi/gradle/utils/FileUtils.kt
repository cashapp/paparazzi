/*
 * Copyright (C) 2023 Square, Inc.
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
package app.cash.paparazzi.gradle.utils

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.Directory
import java.io.File

fun ConfigurableFileCollection.joinFiles(directory: Directory) = files.joinToString(",") { file ->
  directory.relativize(file)
}

fun Directory.relativize(child: File): String {
  return asFile.toPath().relativize(child.toPath()).toFile().invariantSeparatorsPath
}
