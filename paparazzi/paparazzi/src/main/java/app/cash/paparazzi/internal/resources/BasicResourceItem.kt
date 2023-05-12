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

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceNamespace.Resolver
import com.android.ide.common.rendering.api.ResourceReference
import com.android.ide.common.rendering.api.ResourceValue
import com.android.ide.common.resources.ResourceItemWithVisibility
import com.android.ide.common.resources.SingleNamespaceResourceRepository
import com.android.ide.common.resources.configuration.FolderConfiguration
import com.android.resources.ResourceType
import com.android.resources.ResourceVisibility

class BasicResourceItem(
  type: ResourceType,
  private val name: String,
  visibility: ResourceVisibility,
  private val repositoryConfiguration: RepositoryConfiguration,
  private val resourceValue: ResourceValue
) : ResourceItemWithVisibility, ResourceValue {
  // Store enums as their ordinals in byte form to minimize memory footprint.
  private val typeOrdinal: Byte
  private val visibilityOrdinal: Byte

  init {
    typeOrdinal = type.ordinal.toByte()
    visibilityOrdinal = visibility.ordinal.toByte()
  }

  override fun getType(): ResourceType = resourceType

  override fun getNamespace(): ResourceNamespace = repository.namespace

  override fun getName(): String = name

  override fun getLibraryName(): String? = null

  override fun getResourceType() = ResourceType.values()[typeOrdinal.toInt()]

  override fun getVisibility() = ResourceVisibility.values()[visibilityOrdinal.toInt()]

  override fun getReferenceToSelf(): ResourceReference = asReference()

  override fun getResourceValue(): ResourceValue = resourceValue

  override fun isUserDefined(): Boolean = !isFramework

  override fun isFramework(): Boolean = namespace == ResourceNamespace.ANDROID

  override fun asReference(): ResourceReference = ResourceReference(namespace, type, name)

  override fun getRepository(): SingleNamespaceResourceRepository =
    repositoryConfiguration.repository

  override fun getConfiguration(): FolderConfiguration = repositoryConfiguration.folderConfiguration

  override fun getKey(): String {
    val qualifiers = configuration.qualifierString
    return if (qualifiers.isNotEmpty()) {
      "${type.getName()}-$qualifiers/$name"
    } else {
      "${type.getName()}/$name"
    }
  }

  override fun setValue(value: String?) = throw UnsupportedOperationException()

  override fun getValue(): String = throw UnsupportedOperationException()

  override fun getNamespaceResolver(): Resolver = TODO("Not yet implemented")

  override fun getSource() = throw UnsupportedOperationException()

  override fun isFileBased() = throw UnsupportedOperationException()
}
