package app.cash.paparazzi.preview.processor

import com.tschuchort.compiletesting.KotlinCompilation
import java.io.File

data class KspCompileResult(
  val result: KotlinCompilation.Result,
  val generatedFiles: List<File>
)
