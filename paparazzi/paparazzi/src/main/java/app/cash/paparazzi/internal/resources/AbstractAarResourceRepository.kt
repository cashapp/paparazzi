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
import com.android.ide.common.resources.AbstractResourceRepository
import com.android.ide.common.resources.ResourceItem
import com.android.ide.common.resources.ResourceItemWithVisibility
import com.android.ide.common.resources.ResourceVisitor
import com.android.ide.common.resources.ResourceVisitor.VisitResult
import com.android.ide.common.resources.ResourceVisitor.VisitResult.ABORT
import com.android.ide.common.resources.ResourceVisitor.VisitResult.CONTINUE
import com.android.resources.ResourceType
import com.android.resources.ResourceVisibility.PUBLIC
import com.google.common.collect.ArrayListMultimap
import com.google.common.collect.ImmutableListMultimap
import com.google.common.collect.ListMultimap
import java.util.EnumMap

/**
 * Ported from: [AbstractAarResourceRepository.java](https://cs.android.com/android-studio/platform/tools/base/+/47d204001bf0cb6273d8b135c7eece3a982cf0e0:resource-repository/main/java/com/android/resources/aar/AbstractAarResourceRepository.java)
 */
abstract class AbstractAarResourceRepository internal constructor(
  private val namespace: ResourceNamespace,
  override val libraryName: String?
) : AbstractResourceRepository(), LoadableResourceRepository {
  protected val resources =
    EnumMap<ResourceType, ListMultimap<String, ResourceItem>>(ResourceType::class.java)
  private val publicResources = EnumMap<ResourceType, Set<ResourceItem>>(ResourceType::class.java)

  override fun getResourcesInternal(
    namespace: ResourceNamespace,
    resourceType: ResourceType
  ): ListMultimap<String, ResourceItem> =
    if (namespace != this.namespace) {
      ImmutableListMultimap.of()
    } else {
      resources.getOrDefault(resourceType, ImmutableListMultimap.of())
    }

  private fun getOrCreateMap(resourceType: ResourceType): ListMultimap<String, ResourceItem> =
    resources.computeIfAbsent(resourceType) { ArrayListMultimap.create<String, ResourceItem>() }

  protected fun addResourceItem(item: ResourceItem) {
    val multimap = getOrCreateMap(item.type)
    multimap.put(item.name, item)
  }

  /**
   * Populates the [publicResources] map. Has to be called after [resources] has been populated.
   */
  protected fun populatePublicResourcesMap() {
    resources.entries.forEach { (resourceType, items) ->
      publicResources[resourceType] = items.values()
        .filterIsInstance<ResourceItemWithVisibility>()
        .filter { it.visibility == PUBLIC }
        .toSet()
    }
  }

  /**
   * Makes resource maps immutable.
   */
  protected fun freezeResources() {
    for ((key, value) in resources) {
      resources[key] = ImmutableListMultimap.copyOf(value)
    }
  }

  override fun accept(visitor: ResourceVisitor): VisitResult {
    if (visitor.shouldVisitNamespace(namespace)) {
      if (acceptByResources(resources, visitor) == ABORT) {
        return ABORT
      }
    }
    return CONTINUE
  }

  override fun getResources(
    namespace: ResourceNamespace,
    resourceType: ResourceType,
    resourceName: String
  ): List<ResourceItem> {
    val map = getResourcesInternal(namespace, resourceType)
    return map[resourceName] ?: emptyList()
  }

  override fun getResources(
    namespace: ResourceNamespace,
    resourceType: ResourceType
  ): ListMultimap<String, ResourceItem> = getResourcesInternal(namespace, resourceType)

  override fun getPublicResources(
    namespace: ResourceNamespace,
    type: ResourceType
  ): Collection<ResourceItem> {
    if (namespace != this.namespace) return emptySet()
    return publicResources[type] ?: emptySet()
  }

  override fun getNamespace(): ResourceNamespace = namespace

  override val displayName: String
    get() = if (libraryName == null) "Android Framework" else libraryName!!

  override fun containsUserDefinedResources(): Boolean = false
}
