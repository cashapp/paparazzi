package app.cash.paparazzi.internal.renderresources

import androidx.annotation.GuardedBy
import app.cash.paparazzi.internal.resources.ResourceNamespacing
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.internal.R
import com.android.resources.ResourceType
import java.lang.reflect.Field
import java.lang.reflect.Modifier

private const val FIRST_PACKAGE_ID: Byte = 0x02

// Comparator for fields, which makes them appear in the same order as in the R class source code.
// This means that in R.styleable,
// indices come after corresponding array and before other arrays, e.g. "ActionBar_logo" comes
// after "ActionBar" but before
// "ActionBar_LayoutParams". This allows the invariant that int fields are indices into the last
// seen array field.
private val fieldNameOrdering: Comparator<String> = Comparator { name1, name2 ->
  for (i in 0 until minOf(name1.length, name2.length)) {
    val c1 = name1[i]
    val c2 = name2[i]

    if (c1 != c2) {
      return@Comparator when {
        c1 == '_' -> -1
        c2 == '_' -> 1
        c1.isLowerCase() && c2.isUpperCase() -> -1
        c1.isUpperCase() && c2.isLowerCase() -> 1
        else -> c1 - c2
      }
    }
  }

  name1.length - name2.length
}

internal fun buildResourceId2(packageId: Byte, typeId: Byte, entryId: Short): Int =
  (packageId.toInt() shl 24) or
    (typeId.toInt() shl 16) or
    (entryId.toInt() and 0xffff)

/** Loads the given [Class] from disk if available. */
private fun Class<*>.loadClassBytes(fqcn: String = simpleName): ByteArray =
  try {
    getResource("$fqcn.class")?.openStream()?.readAllBytes() ?: ByteArray(0)
  } catch (_: Throwable) {
    ByteArray(0)
  }

/**
 * Reads numeric ids from the given R class (loading it from the bytecode without using reflection)
 * and stores them in the supplied [SingleNamespaceIdMapping].
 *
 * @param klass the R class to read ids from
 * @param into the result [SingleNamespaceIdMapping]
 * @param lookForAttrsInStyleables whether to get attr ids by looking at `R.styleable`. Aapt has a
 *   feature where an ignore list of all resources to be put in the R class can be supplied at build
 *   time (to reduce the size of the R class). In this case the numeric ids of attr resources can
 *   still "leak" into bytecode in the `styleable` class. If this argument is set to `true`, names
 *   of the attrs are inferred from corresponding fields in the `styleable` class and their numeric
 *   ids are saved. This is applicable mostly to the internal android R class.
 */
private fun loadIdsFromResourceClassFromBytecode(
  klassBytes: ByteArray,
  classLoader: (String) -> ByteArray,
  into: SingleNamespaceIdMapping,
  lookForAttrsInStyleables: Boolean = false,
) {

  val fieldOrdering: Comparator<ResourceClass.Field> = Comparator { f1, f2 ->
    fieldNameOrdering.compare(f1.name, f2.name)
  }

  val klass = resourceIdClassBinaryParser(klassBytes, classLoader)
  for (innerClass in klass.declaredClasses) {
    val type = ResourceType.fromClassName(innerClass.name) ?: continue
    when {
      type != ResourceType.STYLEABLE -> {
        val toIdMap = into.toIdMap.getOrPut(type) { mutableMapOf<String, Int>() }
        val fromIdMap = into.fromIdMap

        for (field in innerClass.declaredFields) {
          if (field !is ResourceClass.Field.Int || !field.isStatic) continue
          val id = field.value
          val name = field.name
          toIdMap.put(name, id)
          fromIdMap.put(id, Pair(type, name))
        }
      }

      type == ResourceType.STYLEABLE && lookForAttrsInStyleables -> {
        val toIdMap = into.toIdMap.getOrPut(ResourceType.ATTR) { mutableMapOf<String, Int>() }
        val fromIdMap = into.fromIdMap

        // We process fields by name, so that arrays come before indices into them. currentArray is
        // initialized to a dummy value.
        var currentArray = IntArray(0)
        var currentStyleable = ""

        val sortedFields = innerClass.declaredFields.sortedWith(fieldOrdering)
        for (field in sortedFields) {
          if (field is ResourceClass.Field.IntArray) {
            currentArray =
              field.value.filterIsInstance<ResourceClass.Field.Int>().map { it.value }.toIntArray()
            currentStyleable = field.name
          } else {
            val attrName: String = field.name.substring(currentStyleable.length + 1)
            val attrId = currentArray[(field as ResourceClass.Field.Int).value]
            toIdMap.put(attrName, attrId)
            fromIdMap.put(attrId, Pair(ResourceType.ATTR, attrName))
          }
        }
      }

      else -> {
        // No interesting information in the styleable class, if we're not trying to infer attr ids
        // from it.
      }
    }
  }
}

/**
 * Reads numeric ids from the given R class (loading it from the bytecode without using reflection)
 * and stores them in the supplied [SingleNamespaceIdMapping].
 *
 * @param klass the R class to read ids from
 * @param into the result [SingleNamespaceIdMapping]
 * @param lookForAttrsInStyleables whether to get attr ids by looking at `R.styleable`. Aapt has a
 *   feature where an ignore list of all resources to be put in the R class can be supplied at build
 *   time (to reduce the size of the R class). In this case the numeric ids of attr resources can
 *   still "leak" into bytecode in the `styleable` class. If this argument is set to `true`, names
 *   of the attrs are inferred from corresponding fields in the `styleable` class and their numeric
 *   ids are saved. This is applicable mostly to the internal android R class.
 */
private fun loadIdsFromResourceClassFromBytecode(
  theKlass: Class<*>,
  into: SingleNamespaceIdMapping,
  lookForAttrsInStyleables: Boolean = false,
) {
  assert(theKlass.simpleName == "R") { "Numeric ids can only be loaded from top-level R classes." }
  loadIdsFromResourceClassFromBytecode(
    theKlass.loadClassBytes(),
    classLoader = { theKlass.loadClassBytes(it) },
    into,
    lookForAttrsInStyleables
  )
}

/**
 * Reads numeric ids from the given R class (using reflection) and stores them in the supplied
 * [SingleNamespaceIdMapping].
 *
 * @param klass the R class to read ids from
 * @param into the result [SingleNamespaceIdMapping]
 * @param lookForAttrsInStyleables whether to get attr ids by looking at `R.styleable`. Aapt has a
 *   feature where an ignore list of all resources to be put in the R class can be supplied at build
 *   time (to reduce the size of the R class). In this case the numeric ids of attr resources can
 *   still "leak" into bytecode in the `styleable` class. If this argument is set to `true`, names
 *   of the attrs are inferred from corresponding fields in the `styleable` class and their numeric
 *   ids are saved. This is applicable mostly to the internal android R class.
 */
private fun loadIdsFromResourceClass(
  klass: Class<*>,
  into: SingleNamespaceIdMapping,
  lookForAttrsInStyleables: Boolean = false,
) {
  assert(klass.simpleName == "R") { "Numeric ids can only be loaded from top-level R classes." }

  val fieldOrdering: Comparator<Field> = Comparator { f1, f2 ->
    fieldNameOrdering.compare(f1.name, f2.name)
  }

  for (innerClass in klass.declaredClasses) {
    val type = ResourceType.fromClassName(innerClass.simpleName) ?: continue
    when {
      type != ResourceType.STYLEABLE -> {
        val toIdMap = into.toIdMap.getOrPut(type) { mutableMapOf<String, Int>() }
        val fromIdMap = into.fromIdMap

        for (field in innerClass.declaredFields) {
          if (field.type != Int::class.java || !Modifier.isStatic(field.modifiers)) continue
          val id = field.getInt(null)
          val name = field.name
          toIdMap.put(name, id)
          fromIdMap.put(id, Pair(type, name))
        }
      }

      type == ResourceType.STYLEABLE && lookForAttrsInStyleables -> {
        val toIdMap = into.toIdMap.getOrPut(ResourceType.ATTR) { mutableMapOf<String, Int>() }
        val fromIdMap = into.fromIdMap

        // We process fields by name, so that arrays come before indices into them. currentArray is
        // initialized to a dummy value.
        var currentArray = IntArray(0)
        var currentStyleable = ""

        val sortedFields = innerClass.fields.sortedArrayWith(fieldOrdering)
        for (field in sortedFields) {
          if (field.type.isArray) {
            currentArray = field.get(null) as IntArray
            currentStyleable = field.name
          } else {
            val attrName: String = field.name.substring(currentStyleable.length + 1)
            val attrId = currentArray[field.getInt(null)]
            toIdMap.put(attrName, attrId)
            fromIdMap.put(attrId, Pair(ResourceType.ATTR, attrName))
          }
        }
      }

      else -> {
        // No interesting information in the styleable class, if we're not trying to infer attr ids
        // from it.
      }
    }
  }
}


/**
 * Provider class that loads the framework resources.
 */
internal interface FrameworkResourceIdsProvider {
  /**
   * Loads the framework resources. If [useRBytecodeParsing] is true, the framework R class will be parsed using bytecode parsing.
   * Otherwise, the framework R class will be parsed using reflection.
   */
  fun loadFrameworkIds(useRBytecodeParsing: Boolean = true): SingleNamespaceIdMapping

  companion object {
    private val _instance: FrameworkResourceIdsProvider = FrameworkResourceIds()

    fun getInstance(): FrameworkResourceIdsProvider = _instance
  }
}

/**
 * Singleton containing the resource ids for the current framework used in Layoutlib. There are
 * immutable and only change when a new layoutlib is added.
 */
private class FrameworkResourceIds : FrameworkResourceIdsProvider {
  private val loadFrameworkIdsLock = Any()
  private val frameworkIds: SingleNamespaceIdMapping = SingleNamespaceIdMapping(ResourceNamespace.ANDROID)

  override fun loadFrameworkIds(useRBytecodeParsing: Boolean): SingleNamespaceIdMapping =
    synchronized(loadFrameworkIdsLock) {
      val rClass = R::class.java

      // If the rClass or the useRBytecodeParsing value change, we will allow the class to be reloaded.
      // Otherwise, we only allow one initialization.
      frameworkIds.apply {
        // These are the counts around the S time frame, to allocate roughly the right amount of space upfront.
        toIdMap[ResourceType.ANIM] = HashMap<String, Int>(75)
        toIdMap[ResourceType.ATTR] = HashMap<String, Int>(1752)
        toIdMap[ResourceType.ARRAY] = HashMap<String, Int>(181)
        toIdMap[ResourceType.BOOL] = HashMap<String, Int>(382)
        toIdMap[ResourceType.COLOR] = HashMap<String, Int>(151)
        toIdMap[ResourceType.DIMEN] = HashMap<String, Int>(310)
        toIdMap[ResourceType.DRAWABLE] = HashMap<String, Int>(519)
        toIdMap[ResourceType.ID] = HashMap<String, Int>(526)
        toIdMap[ResourceType.INTEGER] = HashMap<String, Int>(226)
        toIdMap[ResourceType.LAYOUT] = HashMap<String, Int>(221)
        toIdMap[ResourceType.PLURALS] = HashMap<String, Int>(33)
        toIdMap[ResourceType.STRING] = HashMap<String, Int>(1585)
        toIdMap[ResourceType.STYLE] = HashMap<String, Int>(794)

        fromIdMap.clear()
      }


      if (useRBytecodeParsing) {
        loadIdsFromResourceClassFromBytecode(
          rClass,
          into = frameworkIds,
          lookForAttrsInStyleables = true,
        )
      } else {
        loadIdsFromResourceClass(rClass, into = frameworkIds, lookForAttrsInStyleables = true)
      }

      return frameworkIds
    }
}

internal open class TempIdManager internal constructor(
  private val module: ResourceIdManagerModelModule,
  private val searchFrameworkIds: Boolean,
  frameworkResourceIdsProvider: FrameworkResourceIdsProvider
): ResourceIdManager {
  constructor(
    module: ResourceIdManagerModelModule,
    searchFrameworkIds: Boolean = false
  ): this(module, searchFrameworkIds, FrameworkResourceIdsProvider.getInstance())

  private var generationCounter = 1L

  /**
   * Class for generating dynamic ids with the given byte as the "package id" part of the 32-bit
   * resource id.
   *
   * The generated ids follow the aapt PPTTEEEE format: 1 byte for package, 1 byte for type, 2 bytes
   * for entry id. The entry IDs are assigned sequentially, starting with the highest possible value
   * and going down. This should mean they won't conflict with [compiledIds] assigned by real aapt
   * in a normal-size project (although there is no mechanism to check that).
   */
  private class IdProvider(private val packageByte: Byte) {
    private val counters: ShortArray = ShortArray(ResourceType.entries.size) { 0xffff.toShort() }

    fun getNext(type: ResourceType): Int {
      return buildResourceId2(packageByte, (type.ordinal + 1).toByte(), --counters[type.ordinal])
    }

    override fun toString(): String {
      return counters.contentToString()
    }
  }

  @GuardedBy("this")
  private var nextPackageId: Byte = FIRST_PACKAGE_ID
  @GuardedBy("this")
  private val perNamespaceProviders = mutableMapOf<ResourceNamespace, IdProvider>()

  @Synchronized
  private fun resetProviders() {
    nextPackageId = FIRST_PACKAGE_ID
    perNamespaceProviders.clear()
    perNamespaceProviders[ResourceNamespace.RES_AUTO] = IdProvider(0x7f)
    perNamespaceProviders[ResourceNamespace.ANDROID] = IdProvider(0x01)
  }

  init {
    resetProviders()
  }

  /**
   * Ids assigned by this class, on-the-fly. May not be the same as ids chosen by aapt.
   *
   * [compiledIds] take precedence over these, if known.
   */
  @GuardedBy("this")
  private val dynamicToIdMap = mutableMapOf<ResourceReference, Int>()

  /** Inverse of [dynamicToIdMap]. */
  @GuardedBy("this")
  private val dynamicFromIdMap = mutableMapOf<Int, ResourceReference>()

  /**
   * Ids read from the real `R.class` file saved to disk by aapt. They are used instead of dynamic
   * ids, to make sure numeric values compiled into custom views bytecode are consistent with the
   * resource-to-id mapping that this class maintains.
   *
   * These are only read when we know the custom views are compiled against an R class with fields
   * marked as final. See [finalIdsUsed].
   */
  @GuardedBy("this")
  private var compiledIds: SingleNamespaceIdMapping? = null

  /**
   * Ids from the framework `R.class`. It is initialized here so the loading of the resources
   * happens at a predictable point during the initialization of the class.
   */
  private val frameworkIds: SingleNamespaceIdMapping =
    frameworkResourceIdsProvider.loadFrameworkIds(module.useRBytecodeParsing)

  override val finalIdsUsed: Boolean
    get() {
      return module.isAppOrFeature && module.namespacing == ResourceNamespacing.DISABLED
    }

  @Synchronized
  override fun findById(id: Int): ResourceReference? {
    val ref = compiledIds?.findById(id) ?: dynamicFromIdMap[id]
    if (ref == null && searchFrameworkIds) {
      return frameworkIds.findById(id)
    }
    return ref
  }

  /**
   * Returns the compiled id of the given resource, if known.
   *
   * See [compiledIds] for an explanation of what this means for project resources. For framework
   * resources, this will return the value read from [R].
   */
  @Synchronized
  override fun getCompiledId(resource: ResourceReference): Int? {
    val knownIds =
      when (resource.namespace) {
        ResourceNamespace.ANDROID -> frameworkIds
        ResourceNamespace.RES_AUTO -> compiledIds
        else -> null
      }

    return knownIds?.getId(resource)?.let { id -> if (id == 0) null else id }
  }

  /**
   * Returns the compiled id if known, otherwise returns the dynamic id of the resource (which may
   * need to be generated).
   *
   * See [getCompiledId] and [dynamicToIdMap] for an explanation of what this means.
   */
  @Synchronized
  override fun getOrGenerateId(resource: ResourceReference): Int {
    val compiledId = getCompiledId(resource)
    if (compiledId != null) {
      return compiledId
    }

    val dynamicId = dynamicToIdMap[resource]
    if (dynamicId != null) {
      return dynamicId
    }

    val provider =
      perNamespaceProviders.getOrPut(resource.namespace) { IdProvider(nextPackageId++) }
    val newId = provider.getNext(resource.resourceType)

    dynamicToIdMap.put(resource, newId)
    dynamicFromIdMap.put(newId, resource)

    return newId
  }

  @Synchronized
  override fun resetDynamicIds() {
    generationCounter++
    resetProviders()
    dynamicToIdMap.clear()
    dynamicFromIdMap.clear()
  }

  override val generation: Long
    get() = generationCounter

  override fun resetCompiledIds(rClassProvider: (ResourceIdManager.RClassParser) -> Unit) {
    val temporaryCompileIds = SingleNamespaceIdMapping(ResourceNamespace.RES_AUTO)
    try {
      rClassProvider.invoke(object : ResourceIdManager.RClassParser {
        override fun parseUsingReflection(rClass: Class<*>) =
          loadIdsFromResourceClass(rClass, into = temporaryCompileIds)

        override fun parseBytecode(rClass: ByteArray, rClassProvider: (String) -> ByteArray) =
          loadIdsFromResourceClassFromBytecode(rClass, classLoader = rClassProvider, into = temporaryCompileIds)
      })
    } finally {
      synchronized(this) { compiledIds = temporaryCompileIds }
    }
  }
}
