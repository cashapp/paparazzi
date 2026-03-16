package app.cash.paparazzi.gradle.instrumentation

import com.android.build.api.instrumentation.AsmClassVisitorFactory
import com.android.build.api.instrumentation.ClassContext
import com.android.build.api.instrumentation.ClassData
import com.android.build.api.instrumentation.InstrumentationParameters
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

/**
 * [AsmClassVisitorFactory] that patches [AndroidParagraphIntrinsics] to handle null Typeface
 * resolution gracefully.
 *
 * In Compose's text rendering pipeline, [AndroidParagraphIntrinsics] resolves font families to
 * [android.graphics.Typeface] instances via a lambda that performs a non-null cast:
 * `fontFamilyResolver.resolve(...).value as Typeface`. In layoutlib's test environment, font
 * resolution can return null for custom or unavailable fonts, causing a [NullPointerException].
 *
 * This transform wraps Typeface-returning methods with a try-catch that falls back to
 * [android.graphics.Typeface.DEFAULT] when a [NullPointerException] occurs during resolution.
 */
internal abstract class TypefaceResolutionVisitorFactory :
  AsmClassVisitorFactory<InstrumentationParameters.None> {
  override fun createClassVisitor(classContext: ClassContext, nextClassVisitor: ClassVisitor): ClassVisitor {
    return TypefaceResolutionTransform(nextClassVisitor)
  }

  override fun isInstrumentable(classData: ClassData): Boolean =
    classData.className == ANDROID_PARAGRAPH_INTRINSICS_CLASS_NAME

  internal class TypefaceResolutionTransform(delegate: ClassVisitor) :
    ClassVisitor(Opcodes.ASM9, delegate) {
    override fun visitMethod(
      access: Int,
      name: String,
      descriptor: String,
      signature: String?,
      exceptions: Array<String>?
    ): MethodVisitor {
      val methodVisitor = super.visitMethod(access, name, descriptor, signature, exceptions)
      if (descriptor.endsWith(")Landroid/graphics/Typeface;")) {
        return NullSafeTypefaceMethodVisitor(api, methodVisitor)
      }
      return methodVisitor
    }

    /**
     * Wraps the method body in a try-catch for [NullPointerException], returning
     * [android.graphics.Typeface.DEFAULT] in the catch handler.
     */
    private class NullSafeTypefaceMethodVisitor(api: Int, delegate: MethodVisitor) :
      MethodVisitor(api, delegate) {
      private lateinit var tryStart: Label
      private lateinit var tryEnd: Label
      private lateinit var catchHandler: Label

      override fun visitCode() {
        super.visitCode()
        tryStart = Label()
        tryEnd = Label()
        catchHandler = Label()
        super.visitTryCatchBlock(
          tryStart,
          tryEnd,
          catchHandler,
          "java/lang/NullPointerException"
        )
        super.visitLabel(tryStart)
      }

      override fun visitMaxs(maxStack: Int, maxLocals: Int) {
        super.visitLabel(tryEnd)
        super.visitLabel(catchHandler)
        // Pop the exception and return Typeface.DEFAULT as fallback
        super.visitInsn(Opcodes.POP)
        super.visitFieldInsn(
          Opcodes.GETSTATIC,
          "android/graphics/Typeface",
          "DEFAULT",
          "Landroid/graphics/Typeface;"
        )
        super.visitInsn(Opcodes.ARETURN)
        super.visitMaxs(maxStack, maxLocals)
      }
    }
  }

  internal companion object {
    const val ANDROID_PARAGRAPH_INTRINSICS_CLASS_NAME =
      "androidx.compose.ui.text.platform.AndroidParagraphIntrinsics"
  }
}
