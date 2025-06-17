package app.cash.paparazzi.internal.renderresources

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.resources.ResourceType
import java.util.EnumMap

/**
 * Keeps a bidirectional mapping between type+name and a numeric id, for a known namespace.
 */
internal class SingleNamespaceIdMapping(private val namespace: ResourceNamespace) {
  var toIdMap = EnumMap<ResourceType, MutableMap<String, Int>>(ResourceType::class.java)
  var fromIdMap = mutableMapOf<Int, Pair<ResourceType, String>>()

  /**
   * Returns the id of the given resource or 0 if not known.
   */
  fun getId(resourceReference: ResourceReference): Int = toIdMap[resourceReference.resourceType]?.get(resourceReference.name) ?: 0

  /**
   * Returns the [ResourceReference] for the given id, if known.
   */
  fun findById(id: Int): ResourceReference? = fromIdMap[id]?.let { (type, name) -> ResourceReference(namespace, type, name) }
}
