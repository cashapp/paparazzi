package app.cash.paparazzi.internal

import app.cash.paparazzi.internal.resources.pseudolocalizeIfNeeded
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.resources.ResourceRepository
import com.android.ide.common.resources.ResourceValueMap
import com.android.ide.common.resources.configuration.FolderConfiguration
import com.android.ide.common.resources.getConfiguredResources
import com.android.resources.ResourceType

internal typealias ConfiguredResources = Map<ResourceNamespace, Map<ResourceType, ResourceValueMap>>

/**
 * Bounded cache of resource selection from the renderer's project repository.
 *
 * Selecting resources walks every project and dependency resource for the requested configuration. The repository
 * is created once per test JVM, so the result only depends on the configuration. Parameterized snapshot suites cycle
 * through a fixed set of device configurations, so the capacity is sized to hold a typical device matrix.
 */
internal class ProjectResourceCache(private val capacity: Int = DEFAULT_CAPACITY) {
  private var repository: ResourceRepository? = null
  private val configurations =
    object : LinkedHashMap<FolderConfiguration, ConfiguredResources>(capacity, 0.75f, true) {
      override fun removeEldestEntry(eldest: MutableMap.MutableEntry<FolderConfiguration, ConfiguredResources>) =
        size > capacity
    }

  @Synchronized
  fun get(resources: ResourceRepository, configuration: FolderConfiguration): ConfiguredResources {
    if (repository !== resources) {
      configurations.clear()
      repository = resources
    }
    return configurations.getOrPut(FolderConfiguration.copyOf(configuration)) {
      resources.getConfiguredResources(configuration)
        .pseudolocalizeIfNeeded(configuration.localeQualifier)
        .rowMap()
    }
  }

  internal companion object {
    const val DEFAULT_CAPACITY = 16
  }
}
