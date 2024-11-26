package app.cash.paparazzi.gradle.instrumentation

import com.android.build.api.instrumentation.AsmClassVisitorFactory
import com.android.build.api.instrumentation.ClassContext
import com.android.build.api.instrumentation.ClassData
import com.android.build.api.instrumentation.InstrumentationParameters
import groovy.transform.Internal
import org.objectweb.asm.ClassVisitor

internal abstract class CoreVisitorFactory: AsmClassVisitorFactory<InstrumentationParameters.None> {

  override fun createClassVisitor(classContext: ClassContext, nextClassVisitor: ClassVisitor): ClassVisitor {
    return factories.first {
      it.isInstrumentable(classContext.currentClassData)
    }.createClassVisitor(classContext, nextClassVisitor)
  }

  override fun isInstrumentable(classData: ClassData): Boolean = factories.any {
    it.isInstrumentable(classData)
  }

  companion object {
    private val factories = listOf(
      ResourcesCompatVisitorFactory(),
      InstrumentVisitorFactory()
    )
  }
}
