package app.cash.paparazzi.gradle.instrumentation

import com.android.build.api.instrumentation.ClassContext
import com.android.build.api.instrumentation.ClassData
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

internal class InstrumentVisitorFactory : Factory {
  override fun createClassVisitor(classContext: ClassContext, nextClassVisitor: ClassVisitor): ClassVisitor {
    return ImageDecoderTransform(nextClassVisitor)
  }

  override fun isInstrumentable(classData: ClassData): Boolean = classData.className == IMAGE_DECODER_CLASS_NAME

  private class ImageDecoderTransform(delegate: ClassVisitor) : ClassVisitor(Opcodes.ASM9, delegate) {
    override fun visitMethod(
      access: Int,
      name: String,
      descriptor: String,
      signature: String?,
      exceptions: Array<String>?
    ): MethodVisitor {
      println("visitMethod: $name $descriptor $signature ${exceptions?.joinToString()}")
      val methodVisitor = super.visitMethod(access, name, descriptor, signature, exceptions)
      if (name == CREATE_FROM_ASSET_METHOD_NAME && descriptor == CREATE_FROM_ASSET_METHOD_DESCRIPTOR) {
        return super.visitMethod(access, "createFromStream", CREATE_FROM_STREAM_METHOD_DESCRIPTOR, signature, arrayOf("java/io/IOException"))
      }
      return methodVisitor
    }
  }

  internal companion object {
    const val IMAGE_DECODER_CLASS_NAME = "android.graphics.ImageDecoder"
    const val CREATE_FROM_ASSET_METHOD_NAME = "createFromAsset"
    const val CREATE_FROM_ASSET_METHOD_DESCRIPTOR =
      "(Landroid/content/res/AssetManager\$AssetInputStream;ZLandroid/graphics/ImageDecoder\$Source;)Landroid/graphics/ImageDecoder;"
    const val CREATE_FROM_STREAM_METHOD_DESCRIPTOR =
      "(Ljava/io/InputStream;ZZLandroid/graphics/ImageDecoder\$Source;)Landroid/graphics/ImageDecoder;"
  }
}
