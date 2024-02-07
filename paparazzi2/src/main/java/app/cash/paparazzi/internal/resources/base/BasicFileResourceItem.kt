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
package app.cash.paparazzi.internal.resources.base

import app.cash.paparazzi.internal.resources.RepositoryConfiguration
import com.android.ide.common.rendering.api.ResourceNamespace.Resolver
import com.android.ide.common.rendering.api.ResourceReference
import com.android.ide.common.util.PathString
import com.android.resources.Density
import com.android.resources.ResourceType
import com.android.resources.ResourceVisibility
import com.android.utils.Base128InputStream
import com.android.utils.Base128InputStream.StreamFormatException
import com.android.utils.HashCodes
import java.io.IOException

/**
 * Ported from: [BasicFileResourceItem.java](https://cs.android.com/android-studio/platform/tools/base/+/18047faf69512736b8ddb1f6a6785f58d47c893f:resource-repository/main/java/com/android/resources/base/BasicFileResourceItem.java)
 *
 * Resource item representing a file resource, e.g. a drawable or a layout.
 */
open class BasicFileResourceItem(
  type: ResourceType,
  name: String,
  override val repositoryConfiguration: RepositoryConfiguration,
  visibility: ResourceVisibility,
  private val relativePath: String
) : BasicResourceItem(type, name, visibility) {
  override fun isFileBased(): Boolean = true

  override fun getReference(): ResourceReference? = null

  override fun getNamespaceResolver(): Resolver = Resolver.EMPTY_RESOLVER

  override fun getValue(): String = repository.getResourceUrl(relativePath)

  /**
   * The returned PathString points either to a file on disk, or to a ZIP entry inside a res.apk file.
   * In the latter case the filesystem URI part points to res.apk itself, e.g. `"zip:///foo/bar/res.apk"`.
   * The path part is the path of the ZIP entry containing the resource.
   */
  override fun getSource(): PathString = repository.getSourceFile(relativePath, true)

  override fun getOriginalSource(): PathString? =
    repository.getOriginalSourceFile(relativePath, true)

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (!super.equals(other)) return false
    val that = other as BasicFileResourceItem
    return repositoryConfiguration == that.repositoryConfiguration && relativePath == that.relativePath
  }

  override fun hashCode(): Int {
    return HashCodes.mix(super.hashCode(), relativePath.hashCode())
  }

  companion object {
    /**
     * Creates a [BasicFileResourceItem] by reading its contents from the given stream.
     */
    @Throws(IOException::class)
    fun deserialize(
      stream: Base128InputStream,
      resourceType: ResourceType,
      name: String,
      visibility: ResourceVisibility,
      configurations: List<RepositoryConfiguration>
    ): BasicFileResourceItem {
      val relativePath = stream.readString() ?: throw StreamFormatException.invalidFormat()
      val configuration = configurations[stream.readInt()]
      val encodedDensity = stream.readInt()
      if (encodedDensity == 0) {
        return BasicFileResourceItem(resourceType, name, configuration, visibility, relativePath)
      }
      val density = Density.values()[encodedDensity - 1]
      return BasicDensityBasedFileResourceItem(
        resourceType, name, configuration, visibility, relativePath, density
      )
    }
  }
}
