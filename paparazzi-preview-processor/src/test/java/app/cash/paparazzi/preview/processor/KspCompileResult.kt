package app.cash.paparazzi.preview.processor

import com.tschuchort.compiletesting.KotlinCompilation
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import java.io.File

@OptIn(ExperimentalCompilerApi::class)
data class KspCompileResult(
  val result: KotlinCompilation.Result,
  val generatedFiles: List<File>
)
