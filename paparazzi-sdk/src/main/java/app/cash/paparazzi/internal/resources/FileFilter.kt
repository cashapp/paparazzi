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
package app.cash.paparazzi.internal.resources

import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes

/**
 * Ported from: [FileFilter.java](https://cs.android.com/android-studio/platform/tools/base/+/18047faf69512736b8ddb1f6a6785f58d47c893f:resource-repository/main/java/com/android/resources/base/FileFilter.java)
 *
 * A filter used to select files when traversing the file system.
 */
internal interface FileFilter {
  /** Returns true to skip the file or directory, or false to accept it.  */
  fun isIgnored(fileOrDirectory: Path, attrs: BasicFileAttributes): Boolean
}
