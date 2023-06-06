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
import com.android.ide.common.resources.ResourceItem
import com.android.ide.common.resources.ResourceRepository
import com.android.ide.common.resources.ResourceTable
import com.android.ide.common.resources.ResourceVisitor
import com.android.ide.common.resources.ResourceVisitor.VisitResult
import com.android.ide.common.resources.ResourceVisitor.VisitResult.ABORT
import com.android.ide.common.resources.ResourceVisitor.VisitResult.CONTINUE
import com.android.ide.common.resources.SingleNamespaceResourceRepository
import com.android.ide.common.resources.configuration.FolderConfiguration
import com.android.resources.ResourceType
import com.google.common.collect.ArrayListMultimap
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableListMultimap
import com.google.common.collect.ImmutableListMultimap.Builder
import com.google.common.collect.ListMultimap
import com.google.common.collect.Maps
import com.google.common.collect.Multimap
import com.google.common.collect.Multiset
import com.google.common.collect.Table
import com.google.common.collect.Tables
import java.util.function.Predicate
import kotlin.collections.Map.Entry

/**
 * Ported from: [MultiResourceRepository.java](https://cs.android.com/android-studio/platform/tools/adt/idea/+/55991b4380c1ac18e81151493f59228220c5b72a:android/src/com/android/tools/idea/res/MultiResourceRepository.java)
 *
 * A super class for several of the other repositories. Its only purpose is to be able to combine
 * multiple resource repositories and expose it as a single one, applying the “override” semantics
 * of resources: earlier children defining the same resource namespace/type/name combination will
 * replace/hide any subsequent definitions of the same resource.
 *
 * <p>In the resource repository hierarchy, MultiResourceRepository is an internal node, never a leaf.
 */
internal abstract class MultiResourceRepository internal constructor(displayName: String) :
  LocalResourceRepository(displayName) {
  private var localResources = listOf<LocalResourceRepository>()

  private var libraryResources = listOf<AarSourceResourceRepository>()

  /** A concatenation of [localResources] and [libraryResources].  */
  private var children = listOf<ResourceRepository>()

  /** Leaf resource repositories keyed by namespace.  */
  private var leafsByNamespace =
    ImmutableListMultimap.of<ResourceNamespace, SingleNamespaceResourceRepository>()

  /** Contained single-namespace resource repositories keyed by namespace.  */
  private var repositoriesByNamespace =
    ImmutableListMultimap.of<ResourceNamespace, SingleNamespaceResourceRepository>()

  private var resourceComparator =
    ResourceItemComparator(ResourcePriorityComparator(ImmutableList.of()))

  private val cachedMaps = ResourceTable()

  /** Names of resources from local leaf repositories.  */
  private val resourceNames: Table<SingleNamespaceResourceRepository, ResourceType, Set<String>> =
    Tables.newCustomTable(HashMap()) { Maps.newEnumMap(ResourceType::class.java) }

  fun setChildren(
    localResources: List<LocalResourceRepository>,
    libraryResources: Collection<AarSourceResourceRepository>
  ) {
    this.localResources = localResources.toList()
    this.libraryResources = libraryResources.toList()
    this.children = buildList(this.localResources.size + this.libraryResources.size) {
      addAll(this@MultiResourceRepository.localResources)
      addAll(this@MultiResourceRepository.libraryResources)
    }
    leafsByNamespace =
      ImmutableListMultimap.builder<ResourceNamespace, SingleNamespaceResourceRepository>()
        .apply { computeLeafs(this@MultiResourceRepository, this) }
        .build()
    repositoriesByNamespace =
      ImmutableListMultimap.builder<ResourceNamespace, SingleNamespaceResourceRepository>()
        .apply { computeNamespaceMap(this@MultiResourceRepository, this) }
        .build()
    resourceComparator =
      ResourceItemComparator(ResourcePriorityComparator(leafsByNamespace.values()))
    cachedMaps.clear()
  }

  override fun getNamespaces(): Set<ResourceNamespace> = repositoriesByNamespace.keySet()

  override fun accept(visitor: ResourceVisitor): VisitResult {
    for (namespace in namespaces) {
      if (visitor.shouldVisitNamespace(namespace)) {
        for (type in ResourceType.values()) {
          if (visitor.shouldVisitResourceType(type)) {
            val map = getMap(namespace, type)
            if (map != null) {
              for (item in map.values()) {
                if (visitor.visit(item) == ABORT) {
                  return ABORT
                }
              }
            }
          }
        }
      }
    }
    return CONTINUE
  }

  override fun getMap(
    namespace: ResourceNamespace,
    type: ResourceType
  ): ListMultimap<String, ResourceItem>? {
    val repositoriesForNamespace = leafsByNamespace[namespace]
    if (repositoriesForNamespace.size == 1) {
      val repository = repositoriesForNamespace[0]
      return getResources(repository, namespace, type)
    }

    var map = cachedMaps[namespace, type]
    if (map != null) {
      return map
    }

    // Merge all items of the given type.
    for (repository in repositoriesForNamespace) {
      val items = getResources(repository, namespace, type)
      if (!items.isEmpty) {
        if (map == null) {
          // Create a new map.
          // We only add a duplicate item if there isn't an item with the same qualifiers, and it
          // is not a styleable or an id. Styleables and ids are allowed to be defined in multiple
          // places even with the same qualifiers.
          map =
            if (type === ResourceType.STYLEABLE || type === ResourceType.ID) {
              ArrayListMultimap.create<String, ResourceItem>()
            } else {
              PerConfigResourceMap(resourceComparator)
            }
          cachedMaps.put(namespace, type, map)
        }
        map!!.putAll(items)
        if (repository is LocalResourceRepository) {
          resourceNames.put(repository, type, items.keySet().toSet())
        }
      }
    }
    return map
  }

  override fun getLeafResourceRepositories(): Collection<SingleNamespaceResourceRepository> =
    leafsByNamespace.values()

  private class ResourcePriorityComparator(repositories: Collection<SingleNamespaceResourceRepository>) :
    Comparator<ResourceItem> {
    private val repositoryOrdering: MutableMap<SingleNamespaceResourceRepository, Int>

    init {
      repositoryOrdering = HashMap(repositories.size)
      var i = 0
      for (repository in repositories) {
        repositoryOrdering[repository] = i++
      }
    }

    override fun compare(item1: ResourceItem, item2: ResourceItem): Int {
      return getOrdering(item1).compareTo(getOrdering(item2))
    }

    private fun getOrdering(item: ResourceItem): Int {
      val ordering: Int = repositoryOrdering[item.repository] ?: 0
      assert(ordering >= 0)
      return ordering
    }
  }

  /**
   * Custom implementation of [ListMultimap] that may store multiple resource items for
   * the same folder configuration, but for readers exposes ot most one resource item per folder
   * configuration.
   *
   *
   * This ListMultimap implementation is not as robust as Guava multimaps but is sufficient
   * for MultiResourceRepository because the latter always copies data to immutable containers
   * before exposing it to callers.
   */
  private class PerConfigResourceMap(private val comparator: ResourceItemComparator) :
    ListMultimap<String, ResourceItem> {
    private val map: MutableMap<String, MutableList<ResourceItem>> = HashMap()
    private var size = 0

    private var values: Values? = null

    override fun get(key: String?): List<ResourceItem> {
      val items: List<ResourceItem>? = key?.let { map[key] }
      return items ?: ImmutableList.of()
    }

    override fun keySet(): Set<String> = map.keys

    override fun keys(): Multiset<String> = throw UnsupportedOperationException()

    override fun values(): Collection<ResourceItem> {
      var values = this.values
      if (values == null) {
        values = Values(size)
        this.values = values
      }
      return values
    }

    override fun entries(): Collection<Entry<String, ResourceItem>> =
      throw UnsupportedOperationException()

    override fun removeAll(key: Any?): List<ResourceItem> {
      val removed: List<ResourceItem>? = key?.let { map.remove(it) }
      if (removed != null) {
        size -= removed.size
      }
      return removed ?: ImmutableList.of()
    }

    fun removeIf(key: String, filter: Predicate<in ResourceItem>): Boolean {
      val list: MutableList<ResourceItem> = map[key] ?: return false
      val oldSize = list.size
      val removed = list.removeIf(filter)
      size += list.size - oldSize
      if (list.isEmpty()) {
        map.remove(key)
      }
      return removed
    }

    override fun clear() {
      map.clear()
      size = 0
    }

    override fun size() = size

    override fun isEmpty() = size == 0

    override fun containsKey(key: Any?) = key?.let { map.containsKey(it) } ?: false

    override fun containsValue(value: Any?): Boolean = throw UnsupportedOperationException()

    override fun containsEntry(key: Any?, value: Any?): Boolean =
      throw UnsupportedOperationException()

    override fun put(key: String, item: ResourceItem): Boolean {
      val list = map.computeIfAbsent(key) { _ -> PerConfigResourceList() }
      val oldSize = list.size
      list += item
      size += list.size - oldSize
      return true
    }

    override fun remove(key: Any?, value: Any?): Boolean = throw UnsupportedOperationException()

    override fun putAll(key: String, items: Iterable<ResourceItem>): Boolean {
      if (items is Collection<*>) {
        if (items.isEmpty()) {
          return false
        }
        val list = map.computeIfAbsent(key) { _ -> PerConfigResourceList() }
        val oldSize = list.size
        val added = list.addAll(items as Collection<ResourceItem>)
        size += list.size - oldSize
        return added
      }
      var added = false
      var list: MutableList<ResourceItem>? = null
      var oldSize = 0
      for (item in items) {
        if (list == null) {
          list = map.computeIfAbsent(key) { _ -> PerConfigResourceList() }
          oldSize = list.size
        }
        added = list.add(item)
      }
      if (list != null) {
        size += list.size - oldSize
      }
      return added
    }

    override fun putAll(multimap: Multimap<out String, out ResourceItem>): Boolean {
      for ((key, items) in multimap.asMap().entries) {
        if (!items.isEmpty()) {
          val list = map.computeIfAbsent(key) { _ -> PerConfigResourceList() }
          val oldSize = list.size
          list += items
          size += list.size - oldSize
        }
      }
      return !multimap.isEmpty
    }

    override fun replaceValues(
      key: String?,
      values: Iterable<ResourceItem>
    ): List<ResourceItem> = throw UnsupportedOperationException()

    override fun asMap(): Map<String, Collection<ResourceItem>> = map

    /**
     * This class has a split personality. The class may store multiple resource items for the same
     * folder configuration, but for callers of non-mutating methods ([.get],
     * [.size], [Iterator.next], etc) it exposes at most one resource item per
     * folder configuration. Which of the resource items with the same folder configuration is
     * visible to non-mutating methods is determined by [ResourcePriorityComparator].
     */
    private inner class PerConfigResourceList : java.util.AbstractList<ResourceItem>() {

      /** Resource items sorted by folder configurations. Nested lists are sorted by repository priority.  */
      private val resourceItems: ArrayList<MutableList<ResourceItem>> = ArrayList()
      override val size: Int
        get() = resourceItems.size

      override fun get(index: Int): ResourceItem = resourceItems[index][0]

      override fun add(item: ResourceItem): Boolean {
        add(item, 0)
        return true
      }

      override fun addAll(items: Collection<ResourceItem>): Boolean {
        if (items.isEmpty()) {
          return false
        }
        if (items.size == 1) {
          return add(items.iterator().next())
        }
        val sortedItems: List<ResourceItem> = sortedItems(items)
        var start = 0
        for (item in sortedItems) {
          start = add(item, start)
        }
        return true
      }

      private fun add(item: ResourceItem, start: Int): Int {
        var index = findConfigIndex(item, start, resourceItems.size)
        if (index < 0) {
          index = index.inv()
          resourceItems.add(index, mutableListOf(item))
        } else {
          val nested = resourceItems[index]
          // Iterate backwards since it is likely to require fewer iterations.
          var i = nested.size
          while (--i >= 0) {
            if (comparator.priorityComparator.compare(item, nested[i]) > 0) {
              break
            }
          }
          nested.add(i + 1, item)
        }
        return index
      }

      private fun sortedItems(items: Collection<ResourceItem>): List<ResourceItem> =
        items.sortedWith(comparator)

      /**
       * Returns index in [.resourceItems] of the existing resource item with the same
       * configuration as the `item` parameter. If [.resourceItems] doesn't contains
       * resources with the same configuration, returns binary complement of the insertion point.
       */
      private fun findConfigIndex(item: ResourceItem, start: Int, end: Int): Int {
        val config: FolderConfiguration = item.configuration
        var low = start
        var high = end
        while (low < high) {
          val mid = low + high ushr 1
          val value: FolderConfiguration = resourceItems[mid][0].configuration
          val c = value.compareTo(config)
          if (c < 0) {
            low = mid + 1
          } else if (c > 0) {
            high = mid
          } else {
            return mid
          }
        }
        return low.inv() // Not found.
      }
    }

    private inner class Values(override val size: Int) : AbstractCollection<ResourceItem>() {
      override fun iterator(): Iterator<ResourceItem> {
        return ValuesIterator()
      }

      private inner class ValuesIterator : MutableIterator<ResourceItem> {
        private val outerCursor: Iterator<List<ResourceItem>> = map.values.iterator()
        private var currentList: List<ResourceItem>? = null
        private var innerCursor = 0
        override fun hasNext(): Boolean = currentList != null || outerCursor.hasNext()

        override fun next(): ResourceItem {
          if (currentList == null) {
            currentList = outerCursor.next()
            innerCursor = 0
          }
          return try {
            val item: ResourceItem = currentList!![innerCursor]
            if (++innerCursor >= currentList!!.size) {
              currentList = null
            }
            item
          } catch (e: IndexOutOfBoundsException) {
            throw NoSuchElementException()
          }
        }

        override fun remove() = throw UnsupportedOperationException()
      }
    }
  }

  private class ResourceItemComparator(val priorityComparator: Comparator<ResourceItem>) :
    Comparator<ResourceItem> {
    override fun compare(item1: ResourceItem, item2: ResourceItem): Int {
      val c: Int = item1.configuration.compareTo(item2.configuration)
      return if (c != 0) {
        c
      } else priorityComparator.compare(item1, item2)
    }
  }

  companion object {
    private fun computeLeafs(
      repository: ResourceRepository,
      result: Builder<ResourceNamespace, SingleNamespaceResourceRepository>
    ) {
      if (repository is MultiResourceRepository) {
        for (child in repository.children) {
          computeLeafs(child, result)
        }
      } else {
        for (resourceRepository in repository.leafResourceRepositories) {
          result.put(resourceRepository.namespace, resourceRepository)
        }
      }
    }

    private fun computeNamespaceMap(
      repository: ResourceRepository,
      result: Builder<ResourceNamespace, SingleNamespaceResourceRepository>
    ) {
      if (repository is SingleNamespaceResourceRepository) {
        result.put(repository.namespace, repository)
      } else if (repository is MultiResourceRepository) {
        for (child in (repository).children) {
          computeNamespaceMap(child, result)
        }
      }
    }

    private fun getResources(
      repository: SingleNamespaceResourceRepository,
      namespace: ResourceNamespace,
      type: ResourceType
    ): ListMultimap<String, ResourceItem> {
      if (repository is LocalResourceRepository) {
        val map = repository.getMapPackageAccessible(namespace, type)
        return map ?: ImmutableListMultimap.of()
      }
      return repository.getResources(namespace, type)
    }
  }
}
