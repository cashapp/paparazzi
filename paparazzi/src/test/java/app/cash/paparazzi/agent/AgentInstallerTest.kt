package app.cash.paparazzi.agent

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import java.lang.instrument.ClassFileTransformer
import java.net.URLClassLoader
import java.security.ProtectionDomain

class AgentInstallerTest {
  @Test
  fun runtimeDoesNotRequireByteBuddy() {
    for (name in listOf("net.bytebuddy.ByteBuddy", "net.bytebuddy.agent.ByteBuddyAgent")) {
      assertThrows(ClassNotFoundException::class.java) {
        Class.forName(name, false, javaClass.classLoader)
      }
    }
  }

  @Test
  fun installationIsIdempotent() {
    val instrumentation = AgentInstaller.install()
    assertThat(AgentInstaller.install()).isSameInstanceAs(instrumentation)
    assertThat(instrumentation.isRedefineClassesSupported).isTrue()
    assertThat(instrumentation.isRetransformClassesSupported).isTrue()
  }

  @Test
  fun installationIsSharedAcrossTestClassLoaders() {
    val instrumentation = AgentInstaller.install()
    val type = AgentInstaller::class.java
    val loader = object : URLClassLoader(arrayOf(type.protectionDomain.codeSource.location), type.classLoader) {
      override fun loadClass(name: String, resolve: Boolean): Class<*> {
        if (name != type.name) return super.loadClass(name, resolve)
        return findLoadedClass(name) ?: findClass(name).also { if (resolve) resolveClass(it) }
      }
    }
    loader.use {
      val isolated = Class.forName(type.name, true, loader)
      assertThat(isolated).isNotSameInstanceAs(type)
      val instance = isolated.getField("INSTANCE").get(null)
      assertThat(isolated.getMethod("install").invoke(instance)).isSameInstanceAs(instrumentation)
    }
  }

  @Test
  fun agentCanRetransformAnAlreadyLoadedClass() {
    val target = Target()
    assertThat(target.value()).isEqualTo(1)
    val instrumentation = AgentInstaller.install()
    val transformer = object : ClassFileTransformer {
      override fun transform(
        loader: ClassLoader?,
        className: String?,
        classBeingRedefined: Class<*>?,
        protectionDomain: ProtectionDomain?,
        classfileBuffer: ByteArray
      ): ByteArray? {
        if (classBeingRedefined != Target::class.java) return null
        val reader = ClassReader(classfileBuffer)
        val writer = ClassWriter(reader, 0)
        reader.accept(
          object : ClassVisitor(Opcodes.ASM9, writer) {
            override fun visitMethod(
              access: Int,
              name: String,
              descriptor: String,
              signature: String?,
              exceptions: Array<String>?
            ): MethodVisitor {
              val visitor = super.visitMethod(access, name, descriptor, signature, exceptions)
              if (name != "value" || descriptor != "()I") return visitor
              return object : MethodVisitor(Opcodes.ASM9, visitor) {
                override fun visitInsn(opcode: Int) {
                  super.visitInsn(if (opcode == Opcodes.ICONST_1) Opcodes.ICONST_2 else opcode)
                }
              }
            }
          },
          0
        )
        return writer.toByteArray()
      }
    }
    instrumentation.addTransformer(transformer, true)
    try {
      instrumentation.retransformClasses(Target::class.java)
      assertThat(target.value()).isEqualTo(2)
    } finally {
      instrumentation.removeTransformer(transformer)
      instrumentation.retransformClasses(Target::class.java)
    }
    assertThat(target.value()).isEqualTo(1)
  }

  class Target {
    fun value(): Int = 1
  }
}
