package app.cash.paparazzi.internal

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.resources.ResourceType

/**
 * Copied from https://cs.android.com/android-studio/platform/tools/adt/idea/+/mirror-goog-studio-main:android/src/com/android/tools/idea/res/ResourceIdManager.kt;l=100-117
 *
 * Generates and keep track of dynamic resource ids, emulating aapt.
 */
internal class DynamicResourceIdManager {
  private class IdProvider(private val packageByte: Byte) {
    private val counters: ShortArray = ShortArray(ResourceType.values().size) { 0xffff.toShort() }

    fun getNext(type: ResourceType): Int {
      return buildResourceId(packageByte, (type.ordinal + 1).toByte(), --counters[type.ordinal])
    }
  }

  private var nextPackageId = FIRST_PACKAGE_ID

  private val perNamespaceProviders = hashMapOf<ResourceNamespace, IdProvider>()
  private val dynamicToIdMap = hashMapOf<ResourceReference, Int>()
  private val dynamicFromIdMap = hashMapOf<Int, ResourceReference>()

  init {
    perNamespaceProviders[ResourceNamespace.RES_AUTO] = IdProvider(0x7f)
    perNamespaceProviders[ResourceNamespace.ANDROID] = IdProvider(0x01)
  }

  fun findById(id: Int): ResourceReference? = dynamicFromIdMap[id]

  fun getOrGenerateId(resource: ResourceReference): Int {
    val dynamicId = dynamicToIdMap[resource]
    if (dynamicId != null) {
      return dynamicId
    }

    val provider = perNamespaceProviders.getOrPut(resource.namespace) {
      IdProvider(nextPackageId++)
    }
    val newId = provider.getNext(resource.resourceType)

    dynamicToIdMap[resource] = newId
    dynamicFromIdMap[newId] = resource

    return newId
  }
}

private const val FIRST_PACKAGE_ID = 0x02.toByte()

/**
 * Copied from https://cs.android.com/android-studio/platform/tools/adt/idea/+/mirror-goog-studio-main:android/src/com/android/tools/idea/res/IdeResourcesUtil.kt;l=1030-1031
 *
 * Build ids following the aapt PPTTEEEE format: 1 byte for package, 1 byte for type, 2 bytes for entry id.
 */
private fun buildResourceId(packageId: Byte, typeId: Byte, entryId: Short) =
  (packageId.toInt() shl 24) or (typeId.toInt() shl 16) or (entryId.toInt() and 0xffff)
