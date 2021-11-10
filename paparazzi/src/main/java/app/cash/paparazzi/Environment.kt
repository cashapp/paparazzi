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
import java.nio.file.Paths
import java.util.Locale

data class Environment(
  val platformDir: String,
  val appTestDir: String,
  val resDir: String,
  val assetsDir: String,
  val packageName: String,
  val compileSdkVersion: Int,
  val platformDataDir: String,
  val resourcePackageNames: List<String>,
)

@Suppress("unused")
fun androidHome() = System.getenv("ANDROID_SDK_ROOT")
    ?: System.getenv("ANDROID_HOME")
    ?: androidSdkPath()

fun detectEnvironment(): Environment {
  checkInstalledJvm()

  val resourcesFile = File(System.getProperty("paparazzi.test.resources"))
  val configLines = resourcesFile.readLines()

  val appTestDir = Paths.get(System.getProperty("user.dir"))
  val androidHome = Paths.get(androidHome())
  return Environment(
    platformDir = androidHome.resolve(configLines[3]).toString(),
    appTestDir = appTestDir.toString(),
    resDir = appTestDir.resolve(configLines[1]).toString(),
    assetsDir = appTestDir.resolve(configLines[4]).toString(),
    packageName = configLines[0],
    compileSdkVersion = configLines[2].toInt(),
    platformDataDir = configLines[5],
    resourcePackageNames = configLines[6].split(",")
  )
}

private fun androidSdkPath(): String {
  val osName = System.getProperty("os.name").lowercase(Locale.US)
  val sdkPathDir = if (osName.startsWith("windows")) {
    "\\AppData\\Local\\Android\\Sdk"
  } else {
    "/Library/Android/sdk"
  }
  val homeDir = System.getProperty("user.home")
  return homeDir + sdkPathDir
}

private fun checkInstalledJvm() {
  val feature = try {
    // Runtime#version() only available as of Java 9.
    val version = Runtime::class.java.getMethod("version").invoke(null)
    // Runtime.Version#feature() only available as of Java 10.
    version.javaClass.getMethod("feature").invoke(version) as Int
  } catch(e: NoSuchMethodException) {
    -1
  }

  if (feature < 11) {
    throw IllegalStateException(
        "Unsupported JRE detected! Please install and run Paparazzi test suites on JDK 11+."
    )
  }
}
