/*
 * Copyright (C) 2019 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package app.cash.paparazzi

import java.io.BufferedWriter
import java.io.File

const val PAPARAZZI_RESOURCES_DETAILS_FILE_KEY = "PAPARAZZI_RESOURCES_DETAILS_FILE_KEY"

enum class PaparazziRenderer {
    Library,
    Application
}

enum class VerifyMode {
    VerifyAgainstGolden,
    GenerateToGolden
}

data class Environment(
  val renderer: PaparazziRenderer,
  val verifyMode: VerifyMode,
  val reportDir: String,
  val platformDir: String,
  val goldenImagesFolder: String,
  val resDir: String,
  val packageName: String,
  val compileSdkVersion: Int,
  val mergedResourceValueDir: String,
  val apkPath: String,
  val assetsDir: String)

fun dumpEnvironment(environment: Environment, writer: BufferedWriter) {
    writer.write(environment.renderer.name)
    writer.newLine()
    writer.write(environment.verifyMode.name)
    writer.newLine()
    writer.write(environment.packageName)
    writer.newLine()
    writer.write(environment.resDir)
    writer.newLine()
    writer.write(environment.assetsDir)
    writer.newLine()
    writer.write(environment.compileSdkVersion.toString())
    writer.newLine()
    writer.write(environment.platformDir)
    writer.newLine()
    writer.write(environment.reportDir)
    writer.newLine()
    writer.write(environment.goldenImagesFolder)
    writer.newLine()
    writer.write(environment.apkPath)
    writer.newLine()
    writer.write(environment.mergedResourceValueDir)
    writer.newLine()
}

@OptIn(ExperimentalStdlibApi::class)
fun detectEnvironment(paparazziResourcesDetailsFile: String = System.getProperty(PAPARAZZI_RESOURCES_DETAILS_FILE_KEY)): Environment {
  checkInstalledJvm()

  File(paparazziResourcesDetailsFile).readLines()
          .toMutableList()
          .run {
            val renderer = PaparazziRenderer.valueOf(removeFirst())
            val verifyMode = VerifyMode.valueOf(removeFirst())
            val packageName = removeFirst()
            val resDir = removeFirst()
            val assetsDir = removeFirst()
            val compileSdkVersion = removeFirst().toInt()
            val platformDir = removeFirst()
            val reportDir = removeFirst()
            val goldenImagesFolder = removeFirst()
            val apkPath = removeFirst()
            val mergedResourceValueDir = removeFirst()

            return Environment(renderer = renderer,
                    verifyMode = verifyMode,
                    reportDir = reportDir,
                    goldenImagesFolder = goldenImagesFolder,
                    platformDir = platformDir,
                    resDir = resDir,
                    assetsDir = assetsDir,
                    packageName = packageName,
                    compileSdkVersion = compileSdkVersion,
                    mergedResourceValueDir = mergedResourceValueDir,
                    apkPath = apkPath
            )
          }
}

private fun checkInstalledJvm() {
  val jvmVendor = System.getProperty("java.vendor")
  val jvmVersion = System.getProperty("java.version")
  if (jvmVendor == null || jvmVersion == null) return // we tried...

  val (major, minor) = jvmVersion.split(".")

  if (jvmVendor.startsWith("Oracle") && major.toInt() == 1 && minor.toInt() <= 8) {
    println(
        """
          |Unsupported JRE detected!!!
          |
          |Some custom fonts may not render correctly.  To avoid this, please install and run 
          |Paparazzi test suites on OpenJDK version 8 or greater.
          |See https://github.com/cashapp/paparazzi/issues/33 for additional context.
          |""".trimMargin()
    )
  }
}
