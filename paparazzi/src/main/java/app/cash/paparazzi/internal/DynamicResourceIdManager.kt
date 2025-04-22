package app.cash.paparazzi.internal

import app.cash.paparazzi.internal.resources.ResourceNamespacing
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.resources.ResourceType

/**
 * Copied from https://cs.android.com/android-studio/platform/tools/adt/idea/+/mirror-goog-studio-main:android/src/com/android/tools/idea/res/ResourceIdManager.kt;l=100-117
 *
 * Responsible for tracking the numeric resource ids we assign to resources, in an attempt to emulate aapt.
 */
internal class DynamicResourceIdManager : ResourceClassGenerator.NumericIdProvider {
  private var generationCounter = 1L

  /**
   * Class for generating dynamic ids with the given byte as the "package id" part of the 32-bit resource id.
   *
   * The generated ids follow the aapt PPTTEEEE format: 1 byte for package, 1 byte for type, 2 bytes for entry id. The entry IDs are
   * assigned sequentially, starting with the highest possible value and going down. This should mean they won't conflict with
   * [compiledIds] assigned by real aapt in a normal-size project (although there is no mechanism to check that).
   */
  private class IdProvider(private val packageByte: Byte) {
    private val counters: ShortArray = ShortArray(ResourceType.entries.size) { 0xffff.toShort() }

    fun getNext(type: ResourceType): Int {
      return buildResourceId(packageByte, (type.ordinal + 1).toByte(), --counters[type.ordinal])
    }
  }

  override val generation: Long
    get() = generationCounter

  private var nextPackageId = FIRST_PACKAGE_ID

  private val perNamespaceProviders = hashMapOf<ResourceNamespace, IdProvider>()

  /**
   * Ids assigned by this class, on-the-fly. May not be the same as ids chosen by aapt.
   *
   * [compiledIds] take precedence over these, if known.
   */
  private val dynamicToIdMap = mutableMapOf<ResourceReference, Int>()
  private val dynamicFromIdMap = mutableMapOf<Int, ResourceReference>()

  /**
   * Ids read from the real `R.class` file saved to disk by aapt. They are used instead of dynamic
   * ids, to make sure numeric values compiled into custom views bytecode are consistent with the
   * resource-to-id mapping that this class maintains.
   *
   * These are only read when we know the custom views are compiled against an R class with fields
   * marked as final. See [finalIdsUsed].
   */
  private var compiledIds: SingleNamespaceIdMapping? = null

  /**
   * Ids from the framework `R.class`. It is initialized here so the loading of the resources
   * happens at a predictable point during the initialization of the class.
   */
  private val frameworkIds: SingleNamespaceIdMapping = FrameworkResourceIds.frameworkIds

  private val finalIdsUsed: Boolean
    get() {
      return module.isAppOrFeature && module.namespacing == ResourceNamespacing.DISABLED
    }

  init {
    perNamespaceProviders[ResourceNamespace.RES_AUTO] = IdProvider(0x7f)
    perNamespaceProviders[ResourceNamespace.ANDROID] = IdProvider(0x01)
  }

  fun findById(id: Int): ResourceReference? = compiledIds?.findById(id) ?: dynamicFromIdMap[id]

  fun getCompiledId(resource: ResourceReference): Int? {
    val knownIds = when(resource.namespace) {
      ResourceNamespace.ANDROID -> frameworkIds
      ResourceNamespace.RES_AUTO -> compiledIds
      else -> null
    }

    return knownIds?.getId(resource)?.let { id -> if (id == 0) null else id }
  }

  /**
   * Returns the compiled id if known, otherwise returns the dynamic id of the resource (which may need to be generated).
   *
   * See [getCompiledId] and [dynamicToIdMap] for an explanation of what this means.
   */
  override fun getOrGenerateId(resource: ResourceReference): Int {
    val compiledId = getCompiledId(resource)
    if (compiledId != null) {
      return compiledId
    }

    val dynamicId = dynamicToIdMap.getValue(resource)
    if (dynamicId != 0) {
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

  fun loadCompiledIds(klass: Class<*>) {
    if (compiledIds == null) {
      compiledIds = SingleNamespaceIdMapping(ResourceNamespace.RES_AUTO)
    }
    loadIdsFromResourceClass(klass, into = compiledIds!!)
  }
}

/**
 * Singleton containing the resource ids for the current framework used in Layoutlib.
 * There are immutable and only change when a new layoutlib is added.
 */
private object FrameworkResourceIds {
  val frameworkIds = loadFrameworkIds()

  private fun loadFrameworkIds(): SingleNamespaceIdMapping {
    val frameworkIds = SingleNamespaceIdMapping(ResourceNamespace.ANDROID).apply {
      // These are the counts around the S time frame, to allocate roughly the right amount of space upfront.
      toIdMap[ResourceType.ANIM] = HashMap(75)
      toIdMap[ResourceType.ATTR] = HashMap(1752)
      toIdMap[ResourceType.ARRAY] = HashMap(181)
      toIdMap[ResourceType.BOOL] = HashMap(382)
      toIdMap[ResourceType.COLOR] = HashMap(151)
      toIdMap[ResourceType.DIMEN] = HashMap(310)
      toIdMap[ResourceType.DRAWABLE] = HashMap(519)
      toIdMap[ResourceType.ID] = HashMap(526)
      toIdMap[ResourceType.INTEGER] = HashMap(226)
      toIdMap[ResourceType.LAYOUT] = HashMap(221)
      toIdMap[ResourceType.PLURALS] = HashMap(33)
      toIdMap[ResourceType.STRING] = HashMap(1585)
      toIdMap[ResourceType.STYLE] = HashMap(794)
    }

    loadIdsFromResourceClass(
      klass = com.android.internal.R::class.java,
      into = frameworkIds,
      lookForAttrsInStyleables = true
    )

    return frameworkIds
  }
}

private fun loadIdsFromResourceClass(
  klass: Class<*>,
  into: SingleNamespaceIdMapping,
  lookForAttrsInStyleables: Boolean = false
) {
  assert(klass.simpleName == "R") { "Numeric ids can only be loaded from top-level R classes." }

  TODO("Not yet implemented")
}

private const val FIRST_PACKAGE_ID = 0x02.toByte()

/**
 * Copied from https://cs.android.com/android-studio/platform/tools/adt/idea/+/mirror-goog-studio-main:android/src/com/android/tools/idea/res/IdeResourcesUtil.kt;l=1030-1031
 *
 * Build ids following the aapt PPTTEEEE format: 1 byte for package, 1 byte for type, 2 bytes for entry id.
 */
private fun buildResourceId(packageId: Byte, typeId: Byte, entryId: Short) =
  (packageId.toInt() shl 24) or (typeId.toInt() shl 16) or (entryId.toInt() and 0xffff)
