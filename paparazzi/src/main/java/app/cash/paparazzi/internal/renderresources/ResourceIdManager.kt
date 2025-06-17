package app.cash.paparazzi.internal.renderresources

import com.android.ide.common.rendering.api.ResourceReference

/**
 * Module service responsible for tracking the numeric resource ids we assign to resources, in an attempt to emulate aapt.
 */
internal interface ResourceIdManager : ResourceClassGenerator.NumericIdProvider {
  /**
   * Whether R classes with final ids are used for compiling custom views.
   */
  val finalIdsUsed: Boolean

  fun getCompiledId(resource: ResourceReference): Int?

  fun findById(id: Int): ResourceReference?
  /**
   * Resets the currently loaded compiled ids. Accepts a lambda that should call the passed
   * [RClassParser] on every class the ids should be extracted from.
   */
  fun resetCompiledIds(rClassProvider: (RClassParser) -> Unit)

  fun resetDynamicIds()

  interface RClassParser {
    fun parseUsingReflection(rClass: Class<*>)

    /**
     * Method called when an R class should be parsed from byte code.
     *
     * @param rClass contains the bytecode of the to R class.
     * @param rClassProvider will be called to resolve the different R type classes (e.g. R$string).
     */
    fun parseBytecode(rClass: ByteArray, rClassProvider: (String) -> ByteArray)
  }
}
