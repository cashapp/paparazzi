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

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import okio.buffer
import okio.source
import java.io.File
import java.io.FileNotFoundException
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Locale
import kotlin.io.path.exists

data class Environment(
  val platformDir: String,
  val appTestDir: String,
  val resDir: String,
  val assetsDir: String,
  val packageName: String,
  val compileSdkVersion: Int,
  val resourcePackageNames: List<String>,
  val localResourceDirs: List<String>,
  val moduleResourceDirs: List<String>,
  val libraryResourceDirs: List<String>,
  val allModuleAssetDirs: List<String>,
  val libraryAssetDirs: List<String>
) {
  init {
    val platformDirPath = Path.of(platformDir)
    if (!platformDirPath.exists()) {
      val elements = platformDirPath.nameCount
      val platform = platformDirPath.subpath(elements - 1, elements)
      val platformVersion = platform.toString().split("-").last()
      throw FileNotFoundException("Missing platform version $platformVersion. Install with sdkmanager --install \"platforms;$platform\"")
    }
  }
}

@Suppress("unused")
fun androidHome() = System.getenv("ANDROID_SDK_ROOT")
  ?: System.getenv("ANDROID_HOME")
  ?: androidSdkPath()

fun detectEnvironment(): Environment {
  checkInstalledJvm()

  val projectDir = Paths.get(System.getProperty("paparazzi.project.dir"))
  val appTestDir = Paths.get(System.getProperty("paparazzi.build.dir"))
  val artifactsCacheDir = Paths.get(System.getProperty("paparazzi.artifacts.cache.dir"))
  val androidHome = Paths.get(androidHome())

  val resourcesFile = File(System.getProperty("paparazzi.test.resources"))
  val moshi = Moshi.Builder().build()!!
  val config = moshi.adapter(Config::class.java).fromJson(resourcesFile.source().buffer())!!

  return Environment(
    platformDir = androidHome.resolve(config.platformDir).toString(),
    appTestDir = appTestDir.toString(),
    resDir = appTestDir.resolve(config.mergeResourcesOutputDir).toString(),
    assetsDir = appTestDir.resolve(config.mergeAssetsOutputDir).toString(),
    packageName = config.mainPackage,
    compileSdkVersion = config.targetSdkVersion.toInt(),
    resourcePackageNames = config.resourcePackageNames,
    localResourceDirs = config.projectResourceDirs.map { projectDir.resolve(it).toString() },
    moduleResourceDirs = config.moduleResourceDirs.map { projectDir.resolve(it).toString() },
    libraryResourceDirs = config.aarExplodedDirs.map { artifactsCacheDir.resolve(it).toString() },
    allModuleAssetDirs = config.projectAssetDirs.map { projectDir.resolve(it).toString() },
    libraryAssetDirs = config.aarAssetDirs.map { artifactsCacheDir.resolve(it).toString() }
  )
}

@JsonClass(generateAdapter = true)
data class Config(
  val mainPackage: String,
  val mergeResourcesOutputDir: String,
  val targetSdkVersion: String,
  val platformDir: String,
  val mergeAssetsOutputDir: String,
  val resourcePackageNames: List<String>,
  val projectResourceDirs: List<String>,
  val moduleResourceDirs: List<String>,
  val aarExplodedDirs: List<String>,
  val projectAssetDirs: List<String>,
  val aarAssetDirs: List<String>
)

private fun androidSdkPath(): String {
  val osName = System.getProperty("os.name").lowercase(Locale.US)
  val sdkPathDir = if (osName.startsWith("windows")) {
    "\\AppData\\Local\\Android\\Sdk"
  } else if (osName.startsWith("mac")) {
    "/Library/Android/sdk"
  } else {
    "/Android/Sdk"
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
  } catch (e: NoSuchMethodException) {
    -1
  }

  if (feature < 11) {
    throw IllegalStateException(
      "Unsupported JRE detected! Please install and run Paparazzi test suites on JDK 11+."
    )
  }
}
