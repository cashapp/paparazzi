package app.cash.paparazzi.agent

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import java.lang.instrument.ClassFileTransformer
import java.security.ProtectionDomain

internal object InterceptorRegistrar {
  private val pendingInterceptors = mutableMapOf<String, MutableSet<Pair<String, Class<*>>>>()
  private val pendingTransforms = mutableMapOf<String, (ClassVisitor) -> ClassVisitor>()

  fun addMethodInterceptor(receiverClass: String, methodName: String, interceptor: Class<*>) =
    addMethodInterceptors(receiverClass, setOf(methodName to interceptor))

  fun addMethodInterceptors(receiverClass: String, methodNamesToInterceptors: Set<Pair<String, Class<*>>>) {
    pendingInterceptors.getOrPut(receiverClass) { mutableSetOf() } += methodNamesToInterceptors
  }

  fun addTransform(className: String, transform: (ClassVisitor) -> ClassVisitor) {
    pendingTransforms[className] = transform
  }

  fun registerMethodInterceptors() {
    val instrumentation = AgentInstaller.install()

    for (className in pendingInterceptors.keys + pendingTransforms.keys) {
      val methodNamesToInterceptors = pendingInterceptors[className].orEmpty()
      val interceptorMap = methodNamesToInterceptors.associate { (name, clazz) -> name to clazz }
      val transform = pendingTransforms[className]
      val transformer = object : ClassFileTransformer {
        override fun transform(
          loader: ClassLoader?,
          internalName: String?,
          classBeingRedefined: Class<*>?,
          protectionDomain: ProtectionDomain?,
          classfileBuffer: ByteArray
        ): ByteArray? {
          if (classBeingRedefined == null) return null
          return transformClass(classfileBuffer, interceptorMap, transform)
        }
      }

      instrumentation.addTransformer(transformer, true)
      try {
        val targetClass = try {
          ClassLoader.getSystemClassLoader().loadClass(className)
        } catch (_: ClassNotFoundException) {
          continue
        }
        instrumentation.retransformClasses(targetClass)
      } finally {
        instrumentation.removeTransformer(transformer)
      }
    }
  }

  fun clearMethodInterceptors() {
    pendingInterceptors.clear()
    pendingTransforms.clear()
  }

  private fun transformClass(
    classBytes: ByteArray,
    interceptorMap: Map<String, Class<*>>,
    transform: ((ClassVisitor) -> ClassVisitor)?
  ): ByteArray {
    val reader = ClassReader(classBytes)
    val writer = ClassWriter(reader, ClassWriter.COMPUTE_MAXS)

    val interceptorVisitor = object : ClassVisitor(Opcodes.ASM9, writer) {
      override fun visitMethod(
        access: Int,
        name: String,
        descriptor: String,
        signature: String?,
        exceptions: Array<String>?
      ): MethodVisitor {
        val mv = super.visitMethod(access, name, descriptor, signature, exceptions)
        val interceptor = interceptorMap[name] ?: return mv

        // Write replacement body that delegates to the interceptor's static intercept method
        val returnType = Type.getReturnType(descriptor)
        mv.visitCode()
        mv.visitMethodInsn(
          Opcodes.INVOKESTATIC,
          Type.getType(interceptor).internalName,
          "intercept",
          Type.getMethodDescriptor(returnType),
          false
        )
        mv.visitInsn(returnOpcode(returnType))
        mv.visitMaxs(0, 0) // COMPUTE_MAXS recalculates
        mv.visitEnd()

        // Return a no-op visitor to discard the original method bytecode
        return object : MethodVisitor(Opcodes.ASM9) {}
      }
    }
    reader.accept(transform?.invoke(interceptorVisitor) ?: interceptorVisitor, 0)

    return writer.toByteArray()
  }

  private fun returnOpcode(type: Type): Int =
    when (type.sort) {
      Type.VOID -> Opcodes.RETURN
      Type.BOOLEAN, Type.BYTE, Type.CHAR, Type.SHORT, Type.INT -> Opcodes.IRETURN
      Type.LONG -> Opcodes.LRETURN
      Type.FLOAT -> Opcodes.FRETURN
      Type.DOUBLE -> Opcodes.DRETURN
      else -> Opcodes.ARETURN
    }
}
