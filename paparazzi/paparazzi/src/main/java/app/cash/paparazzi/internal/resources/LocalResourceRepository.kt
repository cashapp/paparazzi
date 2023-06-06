package app.cash.paparazzi.internal.resources

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.resources.AbstractResourceRepository
import com.android.ide.common.resources.ResourceItem
import com.android.resources.ResourceType
import com.google.common.collect.ImmutableListMultimap
import com.google.common.collect.ListMultimap

/**
 * Repository for Android application resources, e.g. those that show up in {@code R},
 * not {@code android.R} (which are referred to as framework resources.).
 */
abstract class LocalResourceRepository protected constructor(
  val displayName: String
) : AbstractResourceRepository() {
  protected abstract fun getMap(
    namespace: ResourceNamespace,
    resourceType: ResourceType
  ): ListMultimap<String, ResourceItem>?

  override fun getResourcesInternal(
    namespace: ResourceNamespace,
    resourceType: ResourceType
  ): ListMultimap<String, ResourceItem> {
    val map = getMap(namespace, resourceType)
    return map ?: ImmutableListMultimap.of()
  }

  // TODO(namespaces): Implement.
  override fun getPublicResources(
    namespace: ResourceNamespace,
    type: ResourceType
  ): Collection<ResourceItem> = throw UnsupportedOperationException("Not implemented yet")

  /**
   * Package accessible version of [getMap].
   * Do not call outside of [MultiResourceRepository].
   */
  open fun getMapPackageAccessible(
    namespace: ResourceNamespace,
    type: ResourceType
  ): ListMultimap<String, ResourceItem>? = getMap(namespace, type)
}
