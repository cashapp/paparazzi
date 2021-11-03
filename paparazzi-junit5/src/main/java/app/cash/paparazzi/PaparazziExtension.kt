package app.cash.paparazzi

import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ParameterContext
import org.junit.jupiter.api.extension.ParameterResolver
import org.junit.jupiter.api.extension.TestInstancePostProcessor

/**
 * Usage:
 * ```
 * @ExtendWith(PaparazziExtension::class)
 * class MyClassTest {
 *     @Test
 *     fun test(paparazzi: Paparazzi) {
 *         ...
 *     }
 * }
 * ```
 * or
 * ```
 * @ExtendWith(PaparazziExtension::class)
 * class MyClassTest {
 *     private lateinit var paparazzi: Paparazzi
 *
 *     @Test
 *     fun test() {
 *         ...
 *     }
 * }
 * ```
 * or
 * ```
 * class MyClassTest {
 *     @RegisterExtension
 *     @JvmField
 *     val paparazzi = PaparazziExtension.from(Paparazzi(...))
 * }
 * ```
 *
 * @see org.junit.jupiter.api.extension.ExtendWith
 * @see org.junit.jupiter.api.extension.RegisterExtension
 */
class PaparazziExtension(
  /**
   * By default it creates a clean instance for every test.
   */
  private val creator: () -> Paparazzi = { Paparazzi() }
) : BeforeEachCallback, AfterEachCallback, ParameterResolver, TestInstancePostProcessor {

  override fun postProcessTestInstance(testInstance: Any, context: ExtensionContext) {
    val paparazzi = creator()
    context.store.put(KEY, paparazzi)

    testInstance
      .javaClass
      .declaredFields
      .filter { it.type === Paparazzi::class.java }
      .onEach { it.isAccessible = true }
      .forEach { field -> field.set(testInstance, paparazzi) }
  }

  override fun beforeEach(context: ExtensionContext) {
    val paparazzi = context.store.get(KEY, Paparazzi::class.java)
    paparazzi.prepare(context.toTestName())
    context.store.put(KEY, paparazzi)
  }

  override fun afterEach(context: ExtensionContext) {
    val paparazzi = context.store.remove(KEY, Paparazzi::class.java)
    paparazzi.close()
  }

  override fun supportsParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Boolean =
    parameterContext.parameter.type == Paparazzi::class.java

  override fun resolveParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Any? =
    extensionContext.store.get(KEY, Paparazzi::class.java)

  companion object {
    private val NAMESPACE = ExtensionContext.Namespace.create("paparazzi")
    private val KEY  = Paparazzi::class
    private val ExtensionContext.store: ExtensionContext.Store
      get() = this.getStore(NAMESPACE)

    /**
     * Use a shared static instance.
     */
    fun from(paparazzi: Paparazzi): PaparazziExtension =
      PaparazziExtension { paparazzi }
  }
}

private fun ExtensionContext.toTestName(): TestName {
  val testClass = testClass.get()
  val testMethod = testMethod.get()

  return TestName(
    packageName = testClass.packageName,
    className = testClass.canonicalName ?: testClass.name,
    methodName = testMethod.name
  )
}
