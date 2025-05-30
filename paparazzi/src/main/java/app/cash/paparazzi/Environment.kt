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
import java.nio.file.Paths

@Poko
public class Environment(
  public val appTestDir: String,
  public val packageName: String,
  public val compileSdkVersion: Int,
  public val localResourceDirs: List<String>,
  public val moduleResourceDirs: List<String>,
  public val libraryResourceDirs: List<String>,
  public val allModuleAssetDirs: List<String>,
  public val libraryAssetDirs: List<String>
) {
  public fun copy(
    appTestDir: String = this.appTestDir,
    packageName: String = this.packageName,
    compileSdkVersion: Int = this.compileSdkVersion,
    localResourceDirs: List<String> = this.localResourceDirs,
    moduleResourceDirs: List<String> = this.moduleResourceDirs,
    libraryResourceDirs: List<String> = this.libraryResourceDirs,
    allModuleAssetDirs: List<String> = this.allModuleAssetDirs,
    libraryAssetDirs: List<String> = this.libraryAssetDirs
  ): Environment =
    Environment(
      appTestDir,
      packageName,
      compileSdkVersion,
      localResourceDirs,
      moduleResourceDirs,
      libraryResourceDirs,
      allModuleAssetDirs,
      libraryAssetDirs
    )
}

public fun detectEnvironment(): Environment {
  checkInstalledJvm()

  val projectDir = Paths.get(System.getProperty("paparazzi.project.dir"))
  val appTestDir = Paths.get(System.getProperty("paparazzi.build.dir"))
  val artifactsCacheDir = Paths.get(System.getProperty("paparazzi.artifacts.cache.dir"))

  val resourcesFile = File(System.getProperty("paparazzi.test.resources"))
  val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()!!
  val config =
    resourcesFile.source().buffer().use { moshi.adapter(Config::class.java).fromJson(it)!! }

  return Environment(
    appTestDir = appTestDir.toString(),
    packageName = config.mainPackage,
    compileSdkVersion = config.targetSdkVersion.toInt(),
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
  val projectResourceDirs: List<String>,
  val moduleResourceDirs: List<String>,
  val aarExplodedDirs: List<String>,
  val projectAssetDirs: List<String>,
  val aarAssetDirs: List<String>
)

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
