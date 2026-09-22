package app.cash.paparazzi.agent

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

class ResourcesCompatTransformTest {
  @Test
  fun replacesStartsWithInLoadFont() {
    val instructions = mutableListOf<String>()
    val visitor = ResourcesCompatTransform(recordingVisitor(instructions))

    visitor.visitMethod(0, "loadFont", LOAD_FONT_METHOD_DESCRIPTOR, null, null)
      .visitMethodInsn(
        Opcodes.INVOKEVIRTUAL,
        "java/lang/String",
        "startsWith",
        "(Ljava/lang/String;)Z",
        false
      )

    assertThat(instructions).containsExactly("POP", "POP", "ICONST_1").inOrder()
  }

  @Test
  fun preservesCallsOutsideLoadFont() {
    val instructions = mutableListOf<String>()
    val visitor = ResourcesCompatTransform(recordingVisitor(instructions))

    visitor.visitMethod(0, "getFont", LOAD_FONT_METHOD_DESCRIPTOR, null, null)
      .visitMethodInsn(
        Opcodes.INVOKEVIRTUAL,
        "java/lang/String",
        "startsWith",
        "(Ljava/lang/String;)Z",
        false
      )

    assertThat(instructions).containsExactly("java/lang/String.startsWith(Ljava/lang/String;)Z")
  }

  @Test
  fun preservesOtherCallsInLoadFont() {
    val instructions = mutableListOf<String>()
    val visitor = ResourcesCompatTransform(recordingVisitor(instructions))

    visitor.visitMethod(0, "loadFont", LOAD_FONT_METHOD_DESCRIPTOR, null, null)
      .visitMethodInsn(
        Opcodes.INVOKEVIRTUAL,
        "java/lang/String",
        "contains",
        "(Ljava/lang/CharSequence;)Z",
        false
      )

    assertThat(instructions).containsExactly("java/lang/String.contains(Ljava/lang/CharSequence;)Z")
  }

  private fun recordingVisitor(instructions: MutableList<String>): ClassVisitor =
    object : ClassVisitor(Opcodes.ASM9) {
      override fun visitMethod(
        access: Int,
        name: String,
        descriptor: String,
        signature: String?,
        exceptions: Array<String>?
      ): MethodVisitor =
        object : MethodVisitor(Opcodes.ASM9) {
          override fun visitInsn(opcode: Int) {
            instructions += when (opcode) {
              Opcodes.POP -> "POP"
              Opcodes.ICONST_1 -> "ICONST_1"
              else -> opcode.toString()
            }
          }

          override fun visitMethodInsn(
            opcode: Int,
            owner: String,
            name: String,
            descriptor: String,
            isInterface: Boolean
          ) {
            instructions += "$owner.$name$descriptor"
          }
        }
    }

  private companion object {
    const val LOAD_FONT_METHOD_DESCRIPTOR =
      "(Landroid/content/Context;Landroid/content/res/Resources;Landroid/util/TypedValue;IILandroidx/core/content/res/ResourcesCompat\$FontCallback;Landroid/os/Handler;ZZ)Landroid/graphics/Typeface;"
  }
}
