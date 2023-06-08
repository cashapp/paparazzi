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

import app.cash.paparazzi.internal.resources.base.BasicResourceItem
import com.android.ide.common.resources.configuration.FolderConfiguration
import java.io.File

/**
 * Ported from: [ResourceItemSources.kt](https://cs.android.com/android-studio/platform/tools/adt/idea/+/1c8e6b0a85b2dc96826c185854504f7d476868c8:android/src/com/android/tools/idea/res/ResourceItemSources.kt)
 *
 * Represents a resource file from which [com.android.ide.common.resources.ResourceItem]s are
 * created by [ResourceFolderRepository]. An [Iterable] of [BasicResourceItem]s.
 */
class ResourceFile(
  val file: File?,
  override val configuration: RepositoryConfiguration
) : ResourceSourceFile, Iterable<BasicResourceItem> {
  private val items = mutableListOf<BasicResourceItem>()
  override val repository: ResourceFolderRepository
    get() = configuration.repository as ResourceFolderRepository
  val folderConfiguration: FolderConfiguration
    get() = configuration.folderConfiguration

  override fun iterator(): Iterator<BasicResourceItem> = items.iterator()
  fun addItem(item: BasicResourceItem) {
    items += item
  }

  override val relativePath: String?
    get() = file?.let { repository.resourceDir.toRelativeString(it) }

  fun isValid(): Boolean = file != null
}
