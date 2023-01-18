package app.cash.paparazzi.gradle

import com.android.build.api.instrumentation.AsmClassVisitorFactory
import com.android.build.api.instrumentation.ClassContext
import com.android.build.api.instrumentation.ClassData
import com.android.build.api.instrumentation.InstrumentationParameters
import org.objectweb.asm.ClassVisitor

abstract class ResourcesCompatVisitorFactory : AsmClassVisitorFactory<InstrumentationParameters.None> {
  override fun createClassVisitor(
    classContext: ClassContext,
    nextClassVisitor: ClassVisitor
  ): ClassVisitor {
    return ResourcesCompatTransform(nextClassVisitor)
  }

  override fun isInstrumentable(classData: ClassData): Boolean = true
}
