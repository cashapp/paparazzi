package app.cash.paparazzi.internal.renderresources

import app.cash.paparazzi.internal.resources.ResourceNamespacing

/** Representation of an Android module required for a [ResourceIdManager]. */
internal interface ResourceIdManagerModelModule {
  val isAppOrFeature: Boolean

  val namespacing: ResourceNamespacing

  /**
   * When true, the R classes belonging to this Module will be loaded using bytecode parsing and not reflection.
   */
  val useRBytecodeParsing: Boolean

  companion object {
    fun noNamespacingApp(useRBytecodeParsing: Boolean = true) =
      object : ResourceIdManagerModelModule {
        override val isAppOrFeature: Boolean = true
        override val namespacing: ResourceNamespacing = ResourceNamespacing.DISABLED
        override val useRBytecodeParsing: Boolean = useRBytecodeParsing
      }
  }
}
