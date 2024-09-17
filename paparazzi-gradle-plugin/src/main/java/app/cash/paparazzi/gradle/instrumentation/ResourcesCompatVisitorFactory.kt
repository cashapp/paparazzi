package app.cash.paparazzi.gradle.instrumentation

import com.android.build.api.instrumentation.AsmClassVisitorFactory
import com.android.build.api.instrumentation.ClassContext
import com.android.build.api.instrumentation.ClassData
import com.android.build.api.instrumentation.InstrumentationParameters
import org.objectweb.asm.ClassVisitor

internal abstract class ResourcesCompatVisitorFactory : AsmClassVisitorFactory<InstrumentationParameters.None> {
  override fun createClassVisitor(
    classContext: ClassContext,
    nextClassVisitor: ClassVisitor
  ): ClassVisitor {
    return ResourcesCompatTransform(instrumentationContext.apiVersion.get(), nextClassVisitor)
  }

  override fun isInstrumentable(classData: ClassData): Boolean = true
}
