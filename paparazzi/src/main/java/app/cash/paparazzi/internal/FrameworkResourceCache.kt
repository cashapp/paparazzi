package app.cash.paparazzi.internal

import app.cash.paparazzi.internal.resources.pseudolocalizeIfNeeded
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.resources.ResourceRepository
import com.android.ide.common.resources.ResourceValueMap
import com.android.ide.common.resources.configuration.FolderConfiguration
import com.android.ide.common.resources.getConfiguredResources
import com.android.resources.ResourceType

/** Bounded cache of resource selection from the renderer's immutable framework repository. */
internal class FrameworkResourceCache {
  private var repository: ResourceRepository? = null
  private val configurations =
    object : LinkedHashMap<FolderConfiguration, Map<ResourceType, ResourceValueMap>>(8, 0.75f, true) {
      override fun removeEldestEntry(
        eldest: MutableMap.MutableEntry<FolderConfiguration, Map<ResourceType, ResourceValueMap>>
      ) = size > 8
    }

  @Synchronized
  fun get(resources: ResourceRepository, configuration: FolderConfiguration): Map<ResourceType, ResourceValueMap> {
    if (repository !== resources) {
      configurations.clear()
      repository = resources
    }
    return configurations.getOrPut(FolderConfiguration.copyOf(configuration)) {
      resources.getConfiguredResources(configuration)
        .pseudolocalizeIfNeeded(configuration.localeQualifier)
        .row(ResourceNamespace.ANDROID)
    }
  }
}
