/*
 * Copyright (C) 2019 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package app.cash.paparazzi.gradle

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

@CacheableTask
public abstract class PrepareResourcesTask : DefaultTask() {
  @get:Input
  public abstract val packageName: Property<String>

  @get:Input
  public abstract val targetSdkVersion: Property<String>

  @get:Input
  public abstract val projectResourceDirs: ListProperty<String>

  @get:Input
  public abstract val moduleResourceDirs: ListProperty<String>

  @get:Input
  public abstract val aarExplodedDirs: ListProperty<String>

  @get:Input
  public abstract val projectAssetDirs: ListProperty<String>

  @get:Input
  public abstract val aarAssetDirs: ListProperty<String>

  @get:OutputFile
  public abstract val paparazziResources: RegularFileProperty

  @TaskAction
  public fun writeResourcesFile() {
    val out = paparazziResources.get().asFile
    out.delete()

    val config = Config(
      mainPackage = packageName.get(),
      targetSdkVersion = targetSdkVersion.get(),
      projectResourceDirs = projectResourceDirs.get(),
      moduleResourceDirs = moduleResourceDirs.get(),
      aarExplodedDirs = aarExplodedDirs.get(),
      projectAssetDirs = projectAssetDirs.get(),
      aarAssetDirs = aarAssetDirs.get()
    )
    val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()!!
    val json = moshi.adapter(Config::class.java).indent("  ").toJson(config)
    out.writeText(json)
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
}
