package app.cash.paparazzi.internal

import net.bytebuddy.ByteBuddy
import net.bytebuddy.asm.AsmVisitorWrapper
import net.bytebuddy.description.method.MethodDescription
import net.bytebuddy.dynamic.ClassFileLocator
import net.bytebuddy.dynamic.loading.ClassReloadingStrategy
import net.bytebuddy.jar.asm.MethodVisitor
import net.bytebuddy.jar.asm.Opcodes
import net.bytebuddy.matcher.ElementMatchers.hasDescriptor
import net.bytebuddy.matcher.ElementMatchers.named
import net.bytebuddy.pool.TypePool

/**
 * Allows ResourcesCompat to load fonts from layoutlib's absolute resource paths.
 *
 * Apply the font-path workaround in the test JVM. Registering an AGP ASM visitor with
 * InstrumentationScope.ALL for this single class also transforms and caches every dependency
 * of every test variant, even when those dependencies contain no classes that need patching.
 */
internal object ResourcesCompatPatch {
  fun install() {
    val classLoader = ResourcesCompatPatch::class.java.classLoader
    val locator = ClassFileLocator.ForClassLoader.of(classLoader)
    val resolution = TypePool.Default.of(locator).describe(RESOURCES_COMPAT_CLASS_NAME)
    // AndroidX Core is optional for consumers that only use framework views.
    if (!resolution.isResolved) return

    ByteBuddy()
      .redefine<Any>(resolution.resolve(), locator)
      .visit(
        AsmVisitorWrapper.ForDeclaredMethods().method(
          named<MethodDescription>("loadFont").and(hasDescriptor(LOAD_FONT_METHOD_DESCRIPTOR)),
          { _, _, methodVisitor, _, _, _, _ -> LoadFontVisitor(methodVisitor) }
        )
      )
      .make()
      // Redefinition also handles ResourcesCompat loaded by an earlier, non-Paparazzi test.
      .load(classLoader, ClassReloadingStrategy.fromInstalledAgent())
  }

  private class LoadFontVisitor(delegate: MethodVisitor) : MethodVisitor(Opcodes.ASM9, delegate) {
    override fun visitMethodInsn(opcode: Int, owner: String, name: String, descriptor: String, isInterface: Boolean) {
      if (
        opcode == Opcodes.INVOKEVIRTUAL && owner == "java/lang/String" &&
        name == "startsWith" && descriptor == "(Ljava/lang/String;)Z"
      ) {
        // Bypass the res/ prefix check, as font paths are absolute on the host.
        // Preserve the stack effect and existing frames; no new branches or locals are needed.
        super.visitInsn(Opcodes.POP)
        super.visitInsn(Opcodes.POP)
        super.visitInsn(Opcodes.ICONST_1)
      } else {
        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface)
      }
    }
  }

  private const val RESOURCES_COMPAT_CLASS_NAME = "androidx.core.content.res.ResourcesCompat"
  private const val LOAD_FONT_METHOD_DESCRIPTOR =
    "(Landroid/content/Context;Landroid/content/res/Resources;Landroid/util/TypedValue;IILandroidx/core/content/res/ResourcesCompat\$FontCallback;Landroid/os/Handler;ZZ)Landroid/graphics/Typeface;"
}
