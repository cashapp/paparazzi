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
}
