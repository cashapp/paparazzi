package app.cash.paparazzi.internal.layoutlib

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.FieldVisitor
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import java.lang.instrument.ClassFileTransformer
import java.lang.instrument.Instrumentation
import java.security.ProtectionDomain

/**
 * ASM patches for layoutlib's edit-mode and canvas-sizing paths.
 *
 * Existing stack map frames are retained. The traversal guard adds one explicit frame with the
 * method's original entry locals; the exit hooks add straight-line code with no new locals.
 * Computing frames here would load Android classes from inside a class-loading callback.
 */
internal object LayoutlibPatch {
  private enum class Patch { EDIT_MODE, TRAVERSAL, MEASURE_LAYOUT, RELAYOUT }

  private class Target(
    val binaryName: String,
    val methodName: String,
    val descriptor: String,
    val patch: Patch
  ) {
    val internalName = binaryName.replace('.', '/')

    @Volatile var status = "class never loaded"
  }

  private const val APPLIED = "applied"
  private const val STATE = "app/cash/paparazzi/internal/layoutlib/RenderSizingState"
  private const val SUPPORT = "app/cash/paparazzi/internal/layoutlib/WindowSizingSupport"
  private val targets = listOf(
    Target("android.view.View", "isInEditMode", "()Z", Patch.EDIT_MODE),
    Target("android.view.ViewRootImpl", "performTraversals", "()V", Patch.TRAVERSAL),
    Target(
      "com.android.layoutlib.bridge.impl.RenderSessionImpl",
      "measureLayout",
      "(Lcom/android/ide/common/rendering/api/SessionParams;)V",
      Patch.MEASURE_LAYOUT
    ),
    Target(
      "com.android.layoutlib.bridge.impl.BridgeWindowSession",
      "relayout",
      "(Landroid/view/IWindow;Landroid/view/WindowManager\$LayoutParams;IIIIII" +
        "Landroid/view/WindowRelayoutResult;Landroid/view/SurfaceControl;)I",
      Patch.RELAYOUT
    )
  )

  // Keep these in argument order for WindowSizingSupport.finishMeasureLayout.
  private val measureFields = mapOf(
    "mViewRoot" to "Landroid/view/ViewGroup;",
    "mMeasuredScreenWidth" to "I",
    "mMeasuredScreenHeight" to "I",
    "mNewRenderSize" to "Z"
  )
  private var installed = false

  @Synchronized
  fun install(instrumentation: Instrumentation) {
    if (installed) return
    val loader = LayoutlibPatch::class.java.classLoader
    val transformer = Transformer(loader)
    instrumentation.addTransformer(transformer, true)
    try {
      instrumentation.allLoadedClasses
        .filter { type -> type.classLoader === loader && targets.any { it.binaryName == type.name } }
        .forEach { instrumentation.retransformClasses(it) }
      installed = true
    } catch (t: Throwable) {
      instrumentation.removeTransformer(transformer)
      throw t
    }
  }

  /** The JVM swallows transformer exceptions, so explicitly verify the loaded target shapes. */
  fun verifyApplied() {
    val broken = targets.filter { it.status != APPLIED }
    check(broken.isEmpty()) {
      buildString {
        append("Paparazzi could not patch layoutlib's edit-mode/canvas-sizing paths. ")
        append("This layoutlib is not the shape Paparazzi was built against. Unpatched targets:")
        broken.forEach { append("\n  - ${it.binaryName}#${it.methodName}${it.descriptor}: ${it.status}") }
      }
    }
  }

  private class Transformer(private val targetLoader: ClassLoader) : ClassFileTransformer {
    override fun transform(
      loader: ClassLoader?,
      className: String?,
      classBeingRedefined: Class<*>?,
      protectionDomain: ProtectionDomain?,
      classfileBuffer: ByteArray
    ): ByteArray? {
      if (loader !== targetLoader) return null
      val target = targets.firstOrNull { it.internalName == className } ?: return null
      return try {
        val reader = ClassReader(classfileBuffer)
        val writer = ClassWriter(reader, 0)
        val overloads = mutableListOf<String>()
        val fields = mutableMapOf<String, String>()
        var matches = 0
        reader.accept(
          object : ClassVisitor(Opcodes.ASM9, writer) {
            override fun visitField(
              access: Int,
              name: String,
              descriptor: String,
              signature: String?,
              value: Any?
            ): FieldVisitor? {
              fields[name] = descriptor
              return super.visitField(access, name, descriptor, signature, value)
            }

            override fun visitMethod(
              access: Int,
              name: String,
              descriptor: String,
              signature: String?,
              exceptions: Array<String>?
            ): MethodVisitor {
              val delegate = super.visitMethod(access, name, descriptor, signature, exceptions)
              if (name != target.methodName) return delegate
              overloads += descriptor
              if (descriptor != target.descriptor || access and Opcodes.ACC_STATIC != 0) return delegate
              matches++
              return when (target.patch) {
                Patch.EDIT_MODE -> editModeVisitor(delegate)
                Patch.TRAVERSAL -> traversalVisitor(delegate, target.internalName)
                Patch.MEASURE_LAYOUT -> measureLayoutVisitor(delegate, target.internalName)
                Patch.RELAYOUT -> relayoutVisitor(delegate)
              }
            }
          },
          ClassReader.EXPAND_FRAMES
        )
        when {
          matches != 1 -> {
            target.status = "expected one instance method with descriptor ${target.descriptor}; " +
              "found $matches; declared overloads: ${overloads.joinToString().ifEmpty { "<none>" }}"
            null
          }
          target.patch == Patch.MEASURE_LAYOUT && measureFields.any { (name, type) -> fields[name] != type } -> {
            target.status = "unexpected measureLayout fields: $fields"
            null
          }
          else -> writer.toByteArray().also { target.status = APPLIED }
        }
      } catch (t: Throwable) {
        target.status = "transform threw ${t::class.java.name}: ${t.message}"
        null
      }
    }
  }

  private fun editModeVisitor(delegate: MethodVisitor) =
    object : MethodVisitor(Opcodes.ASM9, delegate) {
      override fun visitCode() {
        super.visitCode()
        super.visitInsn(Opcodes.ICONST_0)
        super.visitInsn(Opcodes.IRETURN)
        super.visitMaxs(1, 1)
        super.visitEnd()
        // Keep method annotations, but discard the original code, handlers, frames and debug data.
        mv = null
      }
    }

  private fun traversalVisitor(delegate: MethodVisitor, owner: String) =
    object : MethodVisitor(Opcodes.ASM9, delegate) {
      override fun visitCode() {
        super.visitCode()
        // Avoid a premature traversal before measureLayout establishes the content-sized canvas.
        val proceed = Label()
        super.visitFieldInsn(Opcodes.GETSTATIC, STATE, "canvasSizedForContent", "Z")
        super.visitJumpInsn(Opcodes.IFNE, proceed)
        super.visitInsn(Opcodes.RETURN)
        super.visitLabel(proceed)
        super.visitFrame(Opcodes.F_NEW, 1, arrayOf(owner), 0, emptyArray())
      }

      override fun visitMaxs(maxStack: Int, maxLocals: Int) {
        super.visitMaxs(maxOf(maxStack, 1), maxLocals)
      }
    }

  private fun measureLayoutVisitor(delegate: MethodVisitor, owner: String) =
    object : MethodVisitor(Opcodes.ASM9, delegate) {
      override fun visitInsn(opcode: Int) {
        if (opcode == Opcodes.RETURN) {
          super.visitVarInsn(Opcodes.ALOAD, 0) // receiver for mNewRenderSize
          super.visitVarInsn(Opcodes.ALOAD, 1) // SessionParams
          for ((name, type) in measureFields) {
            super.visitVarInsn(Opcodes.ALOAD, 0)
            super.visitFieldInsn(Opcodes.GETFIELD, owner, name, type)
          }
          super.visitMethodInsn(
            Opcodes.INVOKESTATIC, SUPPORT, "finishMeasureLayout",
            "(Lcom/android/ide/common/rendering/api/SessionParams;Landroid/view/ViewGroup;IIZ)Z", false
          )
          super.visitFieldInsn(Opcodes.PUTFIELD, owner, "mNewRenderSize", "Z")
          for ((field, state) in listOf(
            "mMeasuredScreenWidth" to "canvasWidth",
            "mMeasuredScreenHeight" to "canvasHeight"
          )) {
            super.visitVarInsn(Opcodes.ALOAD, 0)
            super.visitFieldInsn(Opcodes.GETSTATIC, STATE, state, "I")
            super.visitFieldInsn(Opcodes.PUTFIELD, owner, field, "I")
          }
        }
        super.visitInsn(opcode)
      }

      override fun visitMaxs(maxStack: Int, maxLocals: Int) {
        super.visitMaxs(maxOf(maxStack, 6), maxLocals)
      }
    }

  private fun relayoutVisitor(delegate: MethodVisitor) =
    object : MethodVisitor(Opcodes.ASM9, delegate) {
      override fun visitInsn(opcode: Int) {
        if (opcode == Opcodes.IRETURN) {
          // Leave the original integer result below the hook's arguments on the operand stack.
          super.visitVarInsn(Opcodes.ALOAD, 2)
          super.visitVarInsn(Opcodes.ILOAD, 3)
          super.visitVarInsn(Opcodes.ILOAD, 4)
          super.visitVarInsn(Opcodes.ALOAD, 9)
          super.visitMethodInsn(
            Opcodes.INVOKESTATIC, SUPPORT, "positionWindowInCanvas",
            "(Landroid/view/WindowManager\$LayoutParams;IILandroid/view/WindowRelayoutResult;)V", false
          )
        }
        super.visitInsn(opcode)
      }

      override fun visitMaxs(maxStack: Int, maxLocals: Int) {
        super.visitMaxs(maxOf(maxStack, 5), maxLocals)
      }
    }
}
