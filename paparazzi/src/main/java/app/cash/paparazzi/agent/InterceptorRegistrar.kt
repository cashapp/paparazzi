package app.cash.paparazzi.agent

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import java.io.InputStream
import java.lang.instrument.ClassFileTransformer
import java.lang.instrument.Instrumentation
import java.security.ProtectionDomain
import java.util.concurrent.ConcurrentHashMap

internal object InterceptorRegistrar {
  private data class MethodInterceptor(
    val methodName: String,
    val interceptorInternalName: String,
    val interceptorMethodName: String,
    val interceptorMethodDesc: String
  )

  private val interceptorsByClass = ConcurrentHashMap<String, MutableList<MethodInterceptor>>()
  private val classVisitorsByClass = ConcurrentHashMap<String, (ClassVisitor) -> ClassVisitor>()

  @Volatile
  private var installedClassLoader: ClassLoader? = null

  @Volatile
  private var transformerInstalled: Boolean = false

  @Volatile
  private var transformer: ClassFileTransformer? = null

  fun addMethodInterceptor(
    receiverClass: String,
    methodName: String,
    interceptor: Class<*>,
    interceptedMethodName: String = "intercept"
  ) = addMethodInterceptors(
    receiverClass = receiverClass,
    methodNamesToInterceptors = setOf(methodName to interceptor),
    interceptedMethodName = interceptedMethodName
  )

  fun addMethodInterceptors(
    receiverClass: String,
    methodNamesToInterceptors: Set<Pair<String, Class<*>>>,
    interceptedMethodName: String = "intercept"
  ) {
    val list = interceptorsByClass.getOrPut(receiverClass) { mutableListOf() }
    methodNamesToInterceptors.forEach { (methodName, interceptorClass) ->
      val interceptMethod = interceptorClass.methods.firstOrNull {
        it.name == interceptedMethodName && it.parameterTypes.isEmpty()
      } ?: throw IllegalArgumentException(
        "Interceptor $interceptorClass must declare a public static $interceptedMethodName() method with no parameters."
      )

      list += MethodInterceptor(
        methodName = methodName,
        interceptorInternalName = Type.getInternalName(interceptorClass),
        interceptorMethodName = interceptMethod.name,
        interceptorMethodDesc = Type.getMethodDescriptor(interceptMethod)
      )
    }
  }

  fun addClassVisitorOverride(receiverClass: String, classVisitorFactory: (ClassVisitor) -> ClassVisitor) {
    classVisitorsByClass[receiverClass] = classVisitorFactory
  }

  /**
   * Installs an ASM-based classloader wrapper and sets it as the thread context classloader.
   *
   * Note: classloader-based rewriting only affects classes *loaded after* installation.
   */
  fun registerInstrumentation() {
    val inst = runCatching { PaparazziAsmAgent.install() }.getOrElse { e ->
      throw IllegalStateException(
        "Failed to install Paparazzi ASM agent. " +
          "This is required for intercepting Android framework classes like android.view.View.",
        e
      )
    }

    ensureTransformerInstalled(inst)
    retransformLoadedTargets(inst)
    return
  }

  fun clearInstrumentation() {
    interceptorsByClass.clear()
    classVisitorsByClass.clear()
    val installed = installedClassLoader
    val current = Thread.currentThread().contextClassLoader
    if (installed != null && current === installed) {
      Thread.currentThread().contextClassLoader = installed.parent
    }
    installedClassLoader = null
  }

  private fun ensureTransformerInstalled(inst: Instrumentation) {
    if (transformerInstalled) return
    if (!inst.isRetransformClassesSupported) return

    val t = object : ClassFileTransformer {
      override fun transform(
        loader: ClassLoader?,
        className: String?,
        classBeingRedefined: Class<*>?,
        protectionDomain: ProtectionDomain?,
        classfileBuffer: ByteArray?
      ): ByteArray? {
        if (className == null || classfileBuffer == null) return null
        val dotName = className.replace('/', '.')
        if (!interceptorsByClass.containsKey(dotName) && !classVisitorsByClass.containsKey(dotName)) {
          return null
        }
        return transformIfNeeded(dotName, classfileBuffer)
      }
    }

    inst.addTransformer(t, true)
    transformer = t
    transformerInstalled = true
  }

  private fun retransformLoadedTargets(inst: Instrumentation) {
    if (!transformerInstalled || !inst.isRetransformClassesSupported) return
    val targetNames = HashSet<String>().apply {
      addAll(interceptorsByClass.keys)
      addAll(classVisitorsByClass.keys)
    }
    if (targetNames.isEmpty()) return

    val toRetransform = inst.allLoadedClasses
      .asSequence()
      .filter { it.name in targetNames }
      .filter { inst.isModifiableClass(it) }
      .toList()

    if (toRetransform.isNotEmpty()) {
      inst.retransformClasses(*toRetransform.toTypedArray())
    }
  }

  internal fun transformIfNeeded(className: String, bytecode: ByteArray): ByteArray {
    val interceptors = interceptorsByClass[className]
    val classVisitor = classVisitorsByClass[className]
    if ((interceptors == null || interceptors.isEmpty()) && classVisitor == null) return bytecode

    val reader = ClassReader(bytecode)
    val writer = ClassWriter(reader, ClassWriter.COMPUTE_FRAMES or ClassWriter.COMPUTE_MAXS)

    // Use provided class visitor if available, otherwise create a one for method interception.
    val visitor = classVisitor?.invoke(writer) ?: object : ClassVisitor(Opcodes.ASM9, writer) {
      val interceptorsByName = requireNotNull(interceptors) {
        "No interceptors registered for class $className"
      }.groupBy { it.methodName }

      override fun visitMethod(
        access: Int,
        name: String,
        descriptor: String,
        signature: String?,
        exceptions: Array<out String>?
      ): MethodVisitor {
        val methodInterceptors =
          interceptorsByName[name] ?: return super.visitMethod(access, name, descriptor, signature, exceptions)

        // Only support interceptors which match the target method signature exactly.
        val matching = methodInterceptors.firstOrNull { it.interceptorMethodDesc == descriptor }
          ?: return super.visitMethod(access, name, descriptor, signature, exceptions)

        // Emit a brand new method body that delegates directly to interceptor.intercept().
        // Return a no-op visitor so the original method implementation isn't copied through.
        val out = super.visitMethod(access, name, descriptor, signature, exceptions)
        out.visitCode()
        out.visitMethodInsn(
          Opcodes.INVOKESTATIC,
          matching.interceptorInternalName,
          matching.interceptorMethodName,
          matching.interceptorMethodDesc,
          false
        )
        val returnType = Type.getReturnType(descriptor)
        out.visitInsn(returnType.getOpcode(Opcodes.IRETURN))
        out.visitMaxs(0, 0) // COMPUTE_MAXS
        out.visitEnd()

        return object : MethodVisitor(Opcodes.ASM9) {}
      }
    }

    reader.accept(visitor, 0)
    return writer.toByteArray()
  }

  private class InterceptingClassLoader(parent: ClassLoader) : ClassLoader(parent) {
    override fun loadClass(name: String, resolve: Boolean): Class<*> {
      // Child-first for classes we might transform, otherwise default parent-first.
      if (interceptorsByClass.containsKey(name) || classVisitorsByClass.containsKey(name)) {
        synchronized(getClassLoadingLock(name)) {
          findLoadedClass(name)?.let { already ->
            if (resolve) resolveClass(already)
            return already
          }

          val bytes = readClassBytesFromParent(name)
            ?: return super.loadClass(name, resolve)
          val transformed = transformIfNeeded(name, bytes)
          val clazz = defineClass(name, transformed, 0, transformed.size)
          if (resolve) resolveClass(clazz)
          return clazz
        }
      }
      return super.loadClass(name, resolve)
    }

    private fun readClassBytesFromParent(name: String): ByteArray? {
      val resourceName = name.replace('.', '/') + ".class"
      val stream: InputStream = parent.getResourceAsStream(resourceName) ?: return null
      return stream.use { it.readBytes() }
    }
  }
}
