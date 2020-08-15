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

import java.io.File

const val PAPARAZZI_RESOURCES_DETAILS_FILE_KEY = "PAPARAZZI_RESOURCES_DETAILS_FILE_KEY"

enum class PaparazziRenderer {
    Library,
    Application
}

data class Environment(
  val renderer: PaparazziRenderer,
  val reportDir: String,
  val platformDir: String,
  val appTestDir: String,
  val resDir: String,
  val packageName: String,
  val compileSdkVersion: Int,
  val mergedResourceValueDir: String,
  val apkPath: String,
  val assetsDir: String = "$appTestDir/src/main/assets/")

fun detectEnvironment(paparazziResourcesDetailsFile: String = System.getProperty(PAPARAZZI_RESOURCES_DETAILS_FILE_KEY)): Environment {
  checkInstalledJvm()

  val userDir = System.getProperty("user.dir")

  File(paparazziResourcesDetailsFile).readLines()
          .run {
            val renderer = PaparazziRenderer.valueOf(this[0])
            val packageName = this[1]
            val resDir = this[2]
            val compileSdkVersion = this[3].toInt()
            val platformDir = this[4]
            val reportDir = this[5]
            val apkPath = this.getOrElse(6) { "" }
            val mergedResourceValueDir = this.getOrElse(7) { "" }

            return Environment(renderer, reportDir, platformDir, userDir, resDir, packageName, compileSdkVersion, mergedResourceValueDir, apkPath)
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
