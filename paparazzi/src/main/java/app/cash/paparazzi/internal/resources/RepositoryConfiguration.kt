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

import com.android.ide.common.resources.configuration.FolderConfiguration
import com.android.utils.HashCodes

/**
 * A ([LoadableResourceRepository], [FolderConfiguration]) pair. Instances of [BasicResourceItem] contain
 * a reference to an [RepositoryConfiguration] instead of two separate references to [LoadableResourceRepository]
 * and [FolderConfiguration]. This indirection saves memory because the number of [RepositoryConfiguration]
 * instances is a tiny fraction of the number of [BasicResourceItem] instances.
 */
class RepositoryConfiguration(
  repository: LoadableResourceRepository,
  val folderConfiguration: FolderConfiguration
) {
  var repository = repository
    private set

  /**
   * Makes [repository] the owner of this [RepositoryConfiguration]. The new owner should be loaded from
   * the same file or directory as the previous one, which means that changing the owner does not
   * affect [equals] or [hashCode].
   */
  fun transferOwnershipTo(repository: LoadableResourceRepository) {
    assert(this.repository.origin == repository.origin)
    this.repository = repository
  }

  /**
   * Overridden to not distinguish between repositories loaded from the same file or folder.
   */
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as RepositoryConfiguration

    if (repository.origin != other.repository.origin) return false
    return folderConfiguration == other.folderConfiguration
  }

  /**
   * Overridden to not distinguish between repositories loaded from the same file or folder.
   */
  override fun hashCode(): Int {
    return HashCodes.mix(repository.origin.hashCode(), folderConfiguration.hashCode())
  }
}
