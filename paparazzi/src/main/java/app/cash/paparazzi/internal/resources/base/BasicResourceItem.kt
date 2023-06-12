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

import app.cash.paparazzi.internal.resources.LoadableResourceRepository
import app.cash.paparazzi.internal.resources.RepositoryConfiguration
import app.cash.paparazzi.internal.resources.ResourceSourceFile
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceNamespace.Resolver
import com.android.ide.common.rendering.api.ResourceReference
import com.android.ide.common.rendering.api.ResourceValue
import com.android.ide.common.resources.ResourceItemWithVisibility
import com.android.ide.common.resources.configuration.FolderConfiguration
import com.android.resources.ResourceType
import com.android.resources.ResourceVisibility
import com.android.utils.Base128InputStream
import com.android.utils.Base128InputStream.StreamFormatException
import com.android.utils.HashCodes
import com.google.common.base.MoreObjects
import java.io.IOException

/**
 * Ported from: [BasicResourceItemBase.java](https://cs.android.com/android-studio/platform/tools/base/+/18047faf69512736b8ddb1f6a6785f58d47c893f:resource-repository/main/java/com/android/resources/base/BasicResourceItemBase.java)
 *
 * Base class for [com.android.ide.common.resources.ResourceItem]s.
 *
 * A merger of [BasicResourceItemBase] and [BasicResourceItem] from AOSP, to simplify.
 */
abstract class BasicResourceItem(
  private val type: ResourceType,
  private val name: String,
  visibility: ResourceVisibility
) : ResourceItemWithVisibility, ResourceValue {
  // Store enums as their ordinals in byte form to minimize memory footprint.
  private val typeOrdinal: Byte = type.ordinal.toByte()
  private val visibilityOrdinal: Byte = visibility.ordinal.toByte()

  override fun getType(): ResourceType = resourceType

  override fun getNamespace(): ResourceNamespace = repository.namespace

  override fun getName(): String = name

  override fun getLibraryName(): String? = repository.libraryName

  override fun getResourceType() = ResourceType.values()[typeOrdinal.toInt()]

  override fun getVisibility() = ResourceVisibility.values()[visibilityOrdinal.toInt()]

  override fun getReferenceToSelf(): ResourceReference = asReference()

  override fun getResourceValue(): ResourceValue = this

  override fun isUserDefined(): Boolean = repository.containsUserDefinedResources()

  override fun isFramework(): Boolean = namespace == ResourceNamespace.ANDROID

  override fun asReference(): ResourceReference = ResourceReference(namespace, resourceType, name)

  /**
   * Returns the repository this resource belongs to.
   *
   * Framework resource items may move between repositories with the same origin.
   * @see RepositoryConfiguration.transferOwnershipTo
   */
  override fun getRepository(): LoadableResourceRepository = repositoryConfiguration.repository

  override fun getConfiguration(): FolderConfiguration = repositoryConfiguration.folderConfiguration

  abstract val repositoryConfiguration: RepositoryConfiguration

  override fun getKey(): String {
    val qualifiers = configuration.qualifierString
    return if (qualifiers.isNotEmpty()) {
      "${type.getName()}-$qualifiers/$name"
    } else {
      "${type.getName()}/$name"
    }
  }

  override fun setValue(value: String?): Unit = throw UnsupportedOperationException()

  override fun equals(other: Any?): Boolean {
    if (this === other) {
      return true
    }
    if (other == null || javaClass != other.javaClass) {
      return false
    }
    val that = other as BasicResourceItem
    return typeOrdinal == that.typeOrdinal &&
      name == that.name &&
      visibilityOrdinal == that.visibilityOrdinal
  }

  override fun hashCode(): Int {
    // The visibilityOrdinal field is intentionally not included in hash code because having two
    // resource items differing only by visibility in the same hash table is extremely unlikely.
    return HashCodes.mix(typeOrdinal.toInt(), name.hashCode())
  }

  override fun toString(): String {
    return MoreObjects.toStringHelper(this)
      .add("namespace", namespace)
      .add("type", resourceType)
      .add("name", name)
      .add("value", value)
      .toString()
  }

  companion object {
    /**
     * Creates a resource item by reading its contents from the given stream.
     */
    @Throws(IOException::class)
    fun deserialize(
      stream: Base128InputStream,
      configurations: List<RepositoryConfiguration>,
      sourceFiles: List<ResourceSourceFile>,
      namespaceResolvers: List<Resolver>
    ): BasicResourceItem {
      assert(configurations.isNotEmpty())

      val encodedType = stream.readInt()
      val isFileBased = encodedType and 0x1 != 0
      val resourceType = ResourceType.values()[encodedType ushr 1]
      val name = stream.readString() ?: throw StreamFormatException.invalidFormat()
      val visibility = ResourceVisibility.values()[stream.readInt()]

      if (isFileBased) {
        val repository = configurations[0].repository
        return repository.deserializeFileResourceItem(
          stream, resourceType, name, visibility, configurations
        )
      }

      return BasicValueResourceItemBase.deserialize(
        stream, resourceType, name, visibility, configurations, sourceFiles, namespaceResolvers
      )
    }
  }
}
