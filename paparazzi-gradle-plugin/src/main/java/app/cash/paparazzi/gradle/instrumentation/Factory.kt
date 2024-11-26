package app.cash.paparazzi.gradle.instrumentation

import com.android.build.api.instrumentation.ClassContext
import com.android.build.api.instrumentation.ClassData
import org.objectweb.asm.ClassVisitor

internal interface Factory {
  fun createClassVisitor(classContext: ClassContext, nextClassVisitor: ClassVisitor): ClassVisitor

  fun isInstrumentable(classData: ClassData): Boolean
}
