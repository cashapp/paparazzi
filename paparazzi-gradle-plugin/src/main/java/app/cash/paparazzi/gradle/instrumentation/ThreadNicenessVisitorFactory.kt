/*
 * Copyright (C) 2026 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package app.cash.paparazzi.gradle.instrumentation

import com.android.build.api.instrumentation.AsmClassVisitorFactory
import com.android.build.api.instrumentation.ClassContext
import com.android.build.api.instrumentation.ClassData
import com.android.build.api.instrumentation.InstrumentationParameters
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

/**
 * Transforms [dalvik.system.VMRuntime] to no-op [setThreadNiceness], which calls
 * [java.lang.Thread.setPosixNicenessInternal] — a method that does not exist on
 * the JVM and causes [NoSuchMethodError] at runtime.
 */
internal abstract class ThreadNicenessVisitorFactory : AsmClassVisitorFactory<InstrumentationParameters.None> {
  override fun createClassVisitor(classContext: ClassContext, nextClassVisitor: ClassVisitor): ClassVisitor {
    return ThreadNicenessTransform(nextClassVisitor)
  }

  override fun isInstrumentable(classData: ClassData): Boolean = classData.className == VMRUNTIME_CLASS_NAME

  internal class ThreadNicenessTransform(delegate: ClassVisitor) : ClassVisitor(Opcodes.ASM9, delegate) {
    override fun visitMethod(
      access: Int,
      name: String,
      descriptor: String,
      signature: String?,
      exceptions: Array<String>?
    ): MethodVisitor {
      val methodVisitor = super.visitMethod(access, name, descriptor, signature, exceptions)
      if (name == SET_THREAD_NICENESS_METHOD_NAME) {
        // Replace the method body with a no-op return.
        return NoOpMethodVisitor(api, methodVisitor, descriptor)
      }
      return methodVisitor
    }

    /**
     * Replaces the entire method body with a return of the appropriate default value.
     */
    private class NoOpMethodVisitor(
      api: Int,
      delegate: MethodVisitor,
      private val descriptor: String
    ) : MethodVisitor(api, delegate) {
      override fun visitCode() {
        super.visitCode()
        // Emit a return matching the method's return type.
        val returnType = descriptor.substringAfterLast(')')
        when (returnType) {
          "V" -> super.visitInsn(Opcodes.RETURN)
          "I", "Z", "B", "C", "S" -> {
            super.visitInsn(Opcodes.ICONST_0)
            super.visitInsn(Opcodes.IRETURN)
          }
          "J" -> {
            super.visitInsn(Opcodes.LCONST_0)
            super.visitInsn(Opcodes.LRETURN)
          }
          "F" -> {
            super.visitInsn(Opcodes.FCONST_0)
            super.visitInsn(Opcodes.FRETURN)
          }
          "D" -> {
            super.visitInsn(Opcodes.DCONST_0)
            super.visitInsn(Opcodes.DRETURN)
          }
          else -> {
            super.visitInsn(Opcodes.ACONST_NULL)
            super.visitInsn(Opcodes.ARETURN)
          }
        }
        super.visitMaxs(2, 2)
        super.visitEnd()
      }

      // Suppress original bytecode instructions.
      override fun visitInsn(opcode: Int) = Unit
      override fun visitIntInsn(opcode: Int, operand: Int) = Unit
      override fun visitVarInsn(opcode: Int, varIndex: Int) = Unit
      override fun visitTypeInsn(opcode: Int, type: String) = Unit
      override fun visitFieldInsn(opcode: Int, owner: String, name: String, descriptor: String) = Unit
      override fun visitMethodInsn(opcode: Int, owner: String, name: String, descriptor: String, isInterface: Boolean) =
        Unit
      override fun visitJumpInsn(opcode: Int, label: org.objectweb.asm.Label) = Unit
      override fun visitLabel(label: org.objectweb.asm.Label) = Unit
      override fun visitLdcInsn(value: Any) = Unit
      override fun visitIincInsn(varIndex: Int, increment: Int) = Unit
      override fun visitTableSwitchInsn(
        min: Int,
        max: Int,
        dflt: org.objectweb.asm.Label,
        vararg labels: org.objectweb.asm.Label
      ) = Unit
      override fun visitLookupSwitchInsn(
        dflt: org.objectweb.asm.Label,
        keys: IntArray,
        labels: Array<org.objectweb.asm.Label>
      ) = Unit
      override fun visitMultiANewArrayInsn(descriptor: String, numDimensions: Int) = Unit
      override fun visitTryCatchBlock(
        start: org.objectweb.asm.Label,
        end: org.objectweb.asm.Label,
        handler: org.objectweb.asm.Label,
        type: String?
      ) = Unit
      override fun visitMaxs(maxStack: Int, maxLocals: Int) = Unit
      override fun visitEnd() = Unit
      override fun visitLineNumber(line: Int, start: org.objectweb.asm.Label) = Unit
      override fun visitFrame(type: Int, numLocal: Int, local: Array<out Any>?, numStack: Int, stack: Array<out Any>?) =
        Unit
      override fun visitLocalVariable(
        name: String,
        descriptor: String,
        signature: String?,
        start: org.objectweb.asm.Label,
        end: org.objectweb.asm.Label,
        index: Int
      ) = Unit
    }
  }

  internal companion object {
    const val VMRUNTIME_CLASS_NAME = "dalvik.system.VMRuntime"
    const val SET_THREAD_NICENESS_METHOD_NAME = "setThreadNiceness"
  }
}
