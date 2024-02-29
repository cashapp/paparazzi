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

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dev.drewhamilton.poko.Poko
import okio.buffer
import okio.source
import java.io.File
import java.io.FileNotFoundException
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Locale
import kotlin.io.path.exists

@Poko
public class Environment(
  public val platformDir: String,
  public val appTestDir: String,
  public val packageName: String,
  public val compileSdkVersion: Int,
  public val resourcePackageNames: List<String>,
  public val localResourceDirs: List<String>,
  public val moduleResourceDirs: List<String>,
  public val libraryResourceDirs: List<String>,
  public val allModuleAssetDirs: List<String>,
  public val libraryAssetDirs: List<String>
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

  public fun copy(
    platformDir: String = this.platformDir,
    appTestDir: String = this.appTestDir,
    packageName: String = this.packageName,
    compileSdkVersion: Int = this.compileSdkVersion,
    resourcePackageNames: List<String> = this.resourcePackageNames,
    localResourceDirs: List<String> = this.localResourceDirs,
    moduleResourceDirs: List<String> = this.moduleResourceDirs,
    libraryResourceDirs: List<String> = this.libraryResourceDirs,
    allModuleAssetDirs: List<String> = this.allModuleAssetDirs,
    libraryAssetDirs: List<String> = this.libraryAssetDirs
  ): Environment =
    Environment(
      platformDir,
      appTestDir,
      packageName,
      compileSdkVersion,
      resourcePackageNames,
      localResourceDirs,
      moduleResourceDirs,
      libraryResourceDirs,
      allModuleAssetDirs,
      libraryAssetDirs
    )
}

@Suppress("unused")
public fun androidHome(): String = System.getenv("ANDROID_SDK_ROOT")
  ?: System.getenv("ANDROID_HOME")
  ?: androidSdkPath()

public fun detectEnvironment(): Environment {
  checkInstalledJvm()

  val projectDir = Paths.get(System.getProperty("paparazzi.project.dir"))
  val appTestDir = Paths.get(System.getProperty("paparazzi.build.dir"))
  val artifactsCacheDir = Paths.get(System.getProperty("paparazzi.artifacts.cache.dir"))
  val androidHome = Paths.get(androidHome())

  val resourcesFile = File(System.getProperty("paparazzi.test.resources"))
  val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()!!
  val config =
    resourcesFile.source().buffer().use { moshi.adapter(Config::class.java).fromJson(it)!! }

  return Environment(
    platformDir = androidHome.resolve(config.platformDir).toString(),
    appTestDir = appTestDir.toString(),
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

internal data class Config(
  val mainPackage: String,
  val targetSdkVersion: String,
  val platformDir: String,
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
  if (jvmVersion < 11) {
    throw IllegalStateException(
      "Unsupported JRE detected! Please install and run Paparazzi test suites on JDK 11+."
    )
  }

  if (osArch != null && osArch != jreArch) {
    throw IllegalStateException(
      "Mismatched architectures detected, OS = $osArch, JRE = $jreArch. Please install and configure the correct JRE for your system's architecture."
    )
  }
}

private val osArch: String? by lazy {
  if (!osName.startsWith("mac")) {
    osName
  } else {
    // System.getProperty("os.arch") returns the OS of the JRE, not necessarily of the platform
    // we are running on.  Try /usr/bin/arch to get the actual architecture.
    ProcessBuilder("/usr/bin/arch")
      .start()
      .inputStream
      .bufferedReader()
      .readLine()
      ?.lowercase(Locale.US)
      ?.let {
        if (it.startsWith("i386")) "mac" else "mac-arm"
      }
  }
}

private val jreArch: String by lazy {
  if (!osName.startsWith("mac")) {
    osName
  } else {
    System.getProperty("os.arch").lowercase(Locale.US).let {
      if (it.startsWith("x86")) "mac" else "mac-arm"
    }
  }
}

private val osName: String by lazy { System.getProperty("os.name").lowercase(Locale.US) }

private val jvmVersion: Int by lazy {
  try {
    // Runtime#version() only available as of Java 9.
    val version = Runtime::class.java.getMethod("version").invoke(null)
    // Runtime.Version#feature() only available as of Java 10.
    version.javaClass.getMethod("feature").invoke(version) as Int
  } catch (e: NoSuchMethodException) {
    -1
  }
}
