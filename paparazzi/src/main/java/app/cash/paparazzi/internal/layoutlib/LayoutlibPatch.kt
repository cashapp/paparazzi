package app.cash.paparazzi.internal.layoutlib

import net.bytebuddy.ByteBuddy
import net.bytebuddy.asm.Advice
import net.bytebuddy.description.method.MethodDescription
import net.bytebuddy.dynamic.ClassFileLocator
import net.bytebuddy.matcher.ElementMatchers.hasDescriptor
import net.bytebuddy.matcher.ElementMatchers.named
import net.bytebuddy.pool.TypePool
import java.lang.instrument.ClassFileTransformer
import java.lang.instrument.Instrumentation
import java.security.ProtectionDomain

/**
 * Load-time bytecode patch for layoutlib's canvas-sizing path.
 *
 * Two independent defects live here; both are described in full on the advice that fixes them.
 *
 * 1. A traversal can run before the canvas has been sized for the content
 *    ([RenderSizingAdvice.SuppressPrematureTraversal]).
 * 2. The canvas is sized from the base window alone, and windows are then positioned inside the
 *    device display rather than inside that canvas
 *    ([RenderSizingAdvice.MeasureLayoutComplete], [RenderSizingAdvice.WindowRelayout]).
 *
 * Every layoutlib class is offered to the transformer at first load through the ordinary
 * application class loader, so `Advice` can wrap existing methods without adding members.
 *
 * Do not replace `Advice` with hand-written ASM: conditionally skipping a method body introduces
 * branches, so stack map frames have to be recomputed, and ASM's `COMPUTE_FRAMES` would need a
 * `getCommonSuperClass` that loads layoutlib classes *from inside a `ClassFileTransformer`*, which
 * is a classloader reentrancy trap. `Advice` computes frames itself; in exchange it cannot add
 * fields, methods or signatures, and it only hooks entry and exit.
 */
internal object LayoutlibPatch {
  /** A single advised method, pinned by name *and* descriptor. */
  private class Target(
    val binaryName: String,
    val methodName: String,
    val descriptor: String,
    val advice: Class<*>
  ) {
    val internalName: String = binaryName.replace('.', '/')
    var status: String = "class never loaded"
    val applied: Boolean get() = status == APPLIED
  }

  private const val APPLIED = "applied"

  private val targets = listOf(
    Target(
      binaryName = "android.view.ViewRootImpl",
      methodName = "performTraversals",
      descriptor = "()V",
      advice = RenderSizingAdvice.SuppressPrematureTraversal::class.java
    ),

    Target(
      binaryName = "com.android.layoutlib.bridge.impl.RenderSessionImpl",
      methodName = "measureLayout",
      descriptor = "(Lcom/android/ide/common/rendering/api/SessionParams;)V",
      advice = RenderSizingAdvice.MeasureLayoutComplete::class.java
    )
  )

  private var installed = false

  fun install(instrumentation: Instrumentation) {
    if (installed) return
    installed = true
    instrumentation.addTransformer(Transformer(), false)
  }

  /**
   * Turns a transform that silently did not happen into a hard failure.
   *
   * The JVM swallows anything thrown out of [ClassFileTransformer.transform] and quietly keeps the
   * untransformed class, and `Advice` is a no-op when its method matcher matches nothing, so
   * neither a renamed target nor a re-signatured one fails on its own. Callers must invoke this
   * once the targets are known to have been loaded.
   */
  fun verifyApplied() {
    val broken = targets.filterNot { it.applied }
    if (broken.isEmpty()) return
    throw IllegalStateException(
      buildString {
        append("Paparazzi could not patch layoutlib's canvas-sizing path. ")
        append("This layoutlib is not the shape Paparazzi was built against, and ")
        append("RenderingMode.V_SCROLL/H_SCROLL/FULL_EXPAND would silently produce an unexpanded ")
        append("canvas. Unpatched targets:")
        broken.forEach {
          append("\n  - ${it.binaryName}#${it.methodName}${it.descriptor}: ${it.status}")
        }
      }
    )
  }

  private class Transformer : ClassFileTransformer {
    override fun transform(
      loader: ClassLoader?,
      className: String?,
      classBeingRedefined: Class<*>?,
      protectionDomain: ProtectionDomain?,
      classfileBuffer: ByteArray
    ): ByteArray? {
      val target = targets.firstOrNull { it.internalName == className } ?: return null
      return try {
        val locator = ClassFileLocator.Compound(
          ClassFileLocator.Simple.of(target.binaryName, classfileBuffer),
          if (loader == null) {
            ClassFileLocator.ForClassLoader.ofSystemLoader()
          } else {
            ClassFileLocator.ForClassLoader.of(loader)
          }
        )
        val typeDescription = TypePool.Default.of(locator).describe(target.binaryName).resolve()

        // Shape guard: `Advice` is silent when nothing matches, so assert the exact descriptor
        // *before* asking for the transform.
        val matched = typeDescription.declaredMethods.filter {
          it.name == target.methodName && it.descriptor == target.descriptor
        }
        if (matched.size != 1) {
          target.status = "expected exactly one method named '${target.methodName}' with " +
            "descriptor '${target.descriptor}', found ${matched.size}; declared overloads: " +
            typeDescription.declaredMethods
              .filter { it.name == target.methodName }
              .joinToString(", ") { it.descriptor }
              .ifEmpty { "<none>" }
          return null
        }

        val bytes = ByteBuddy()
          .redefine<Any>(typeDescription, locator)
          .visit(
            Advice.to(target.advice)
              .on(
                named<MethodDescription>(target.methodName)
                  .and(hasDescriptor(target.descriptor))
              )
          )
          .make()
          .bytes
        target.status = APPLIED
        bytes
      } catch (t: Throwable) {
        target.status = "transform threw ${t::class.java.name}: ${t.message}"
        null
      }
    }
  }
}
