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
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

/**
 * Transforms `android.os.SystemProperties` from layoutlib to be compatible with both
 * Robolectric's native runtime and plain JVM tests where no native runtime is loaded.
 *
 * Layoutlib's `SystemProperties` differs from the standard Android SDK in that
 * `native_get(String)` is a non-native Java wrapper. Robolectric's native runtime calls
 * `RegisterNatives` expecting ALL native methods from the standard SDK to exist. The missing
 * binding causes `RegisterNatives` to fail atomically, leaving all native bindings unregistered.
 *
 * This transform:
 * 1. Makes `native_get(String)` native, matching the standard SDK so RegisterNatives succeeds.
 * 2. Rewrites `get(String)` to call `native_get(String, "")`, since the single-arg overload
 *    has no standard native binding.
 * 3. Wraps public methods with `UnsatisfiedLinkError` catch blocks so non-Paparazzi tests
 *    (which don't load any native runtime) get safe defaults instead of crashes.
 */
internal abstract class SystemPropertiesVisitorFactory :
  AsmClassVisitorFactory<InstrumentationParameters.None> {

  override fun createClassVisitor(classContext: ClassContext, nextClassVisitor: ClassVisitor): ClassVisitor =
    SystemPropertiesTransform(nextClassVisitor)

  override fun isInstrumentable(classData: ClassData): Boolean = classData.className == "android.os.SystemProperties"

  internal class SystemPropertiesTransform(delegate: ClassVisitor) :
    ClassVisitor(Opcodes.ASM9, delegate) {

    override fun visitMethod(
      access: Int,
      name: String,
      descriptor: String,
      signature: String?,
      exceptions: Array<String>?
    ): MethodVisitor {
      // Make native_get(String) truly native — drop the Java body so it matches the SDK.
      if (name == "native_get" && descriptor == "(Ljava/lang/String;)Ljava/lang/String;") {
        val mv = super.visitMethod(
          access or Opcodes.ACC_NATIVE, name, descriptor, signature, exceptions
        )
        mv.visitEnd()
        return object : MethodVisitor(api, null) {}
      }

      // Replace get(String) body to call native_get(String, "") with UnsatisfiedLinkError guard.
      if (name == "get" && descriptor == "(Ljava/lang/String;)Ljava/lang/String;") {
        val mv = super.visitMethod(access, name, descriptor, signature, exceptions)
        emitGetStringBody(mv)
        return object : MethodVisitor(api, null) {}
      }

      // Wrap remaining public methods with UnsatisfiedLinkError guard returning defaults.
      if (name == "get" && descriptor == GET_WITH_DEFAULT_DESC) {
        return guardMethod(access, name, descriptor, signature, exceptions, 1, Opcodes.ALOAD, Opcodes.ARETURN)
      }
      if (name == "set" && descriptor == "(Ljava/lang/String;Ljava/lang/String;)V") {
        return guardMethod(access, name, descriptor, signature, exceptions, -1, -1, Opcodes.RETURN)
      }
      if (name == "getInt" && descriptor == "(Ljava/lang/String;I)I") {
        return guardMethod(access, name, descriptor, signature, exceptions, 1, Opcodes.ILOAD, Opcodes.IRETURN)
      }
      if (name == "getLong" && descriptor == "(Ljava/lang/String;J)J") {
        return guardMethod(access, name, descriptor, signature, exceptions, 1, Opcodes.LLOAD, Opcodes.LRETURN)
      }
      if (name == "getBoolean" && descriptor == "(Ljava/lang/String;Z)Z") {
        return guardMethod(access, name, descriptor, signature, exceptions, 1, Opcodes.ILOAD, Opcodes.IRETURN)
      }

      return super.visitMethod(access, name, descriptor, signature, exceptions)
    }

    private fun guardMethod(
      access: Int,
      name: String,
      descriptor: String,
      signature: String?,
      exceptions: Array<String>?,
      defaultSlot: Int,
      loadOpcode: Int,
      returnOpcode: Int
    ): MethodVisitor {
      val mv = super.visitMethod(access, name, descriptor, signature, exceptions)
      return UnsatisfiedLinkErrorGuard(api, mv, defaultSlot, loadOpcode, returnOpcode)
    }

    /**
     * Emits: try { return native_get(key, ""); } catch (UnsatisfiedLinkError) { return ""; }
     */
    private fun emitGetStringBody(mv: MethodVisitor) {
      val tryStart = Label()
      val tryEnd = Label()
      val handler = Label()

      mv.visitCode()
      mv.visitTryCatchBlock(tryStart, tryEnd, handler, UNSATISFIED_LINK_ERROR)
      mv.visitLabel(tryStart)
      mv.visitVarInsn(Opcodes.ALOAD, 0)
      mv.visitLdcInsn("")
      mv.visitMethodInsn(
        Opcodes.INVOKESTATIC,
        SYSTEM_PROPERTIES_INTERNAL,
        "native_get",
        GET_WITH_DEFAULT_DESC,
        false
      )
      mv.visitLabel(tryEnd)
      mv.visitInsn(Opcodes.ARETURN)
      mv.visitLabel(handler)
      mv.visitInsn(Opcodes.POP)
      mv.visitLdcInsn("")
      mv.visitInsn(Opcodes.ARETURN)
      mv.visitMaxs(2, 1)
      mv.visitEnd()
    }

    /**
     * Wraps the original method body in try { ... } catch (UnsatisfiedLinkError) { return default; }
     * by passing all original instructions through to the delegate and only injecting the try-start
     * in [visitCode] and the catch handler in [visitMaxs].
     */
    private class UnsatisfiedLinkErrorGuard(
      api: Int,
      delegate: MethodVisitor,
      private val defaultSlot: Int,
      private val loadOpcode: Int,
      private val returnOpcode: Int
    ) : MethodVisitor(api, delegate) {
      private val tryStart = Label()
      private val tryEnd = Label()
      private val handler = Label()

      override fun visitCode() {
        super.visitCode()
        super.visitTryCatchBlock(tryStart, tryEnd, handler, UNSATISFIED_LINK_ERROR)
        super.visitLabel(tryStart)
      }

      override fun visitMaxs(maxStack: Int, maxLocals: Int) {
        super.visitLabel(tryEnd)
        super.visitLabel(handler)
        super.visitInsn(Opcodes.POP) // pop exception
        if (defaultSlot == -1) {
          super.visitInsn(returnOpcode)
        } else {
          super.visitVarInsn(loadOpcode, defaultSlot)
          super.visitInsn(returnOpcode)
        }
        super.visitMaxs(maxStack.coerceAtLeast(2), maxLocals)
      }
    }
  }

  internal companion object {
    private const val SYSTEM_PROPERTIES_INTERNAL = "android/os/SystemProperties"
    private const val UNSATISFIED_LINK_ERROR = "java/lang/UnsatisfiedLinkError"
    private const val GET_WITH_DEFAULT_DESC =
      "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;"
  }
}
