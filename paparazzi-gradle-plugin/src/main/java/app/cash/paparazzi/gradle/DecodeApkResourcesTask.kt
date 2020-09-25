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

import app.cash.paparazzi.binary.encodedValues
import app.cash.paparazzi.binary.parseResourcesArsc
import app.cash.paparazzi.binary.parseXmlFile
import app.cash.paparazzi.binary.physicalFilesPaths
import app.cash.paparazzi.binary.valuesDump
import org.gradle.api.DefaultTask
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.util.zip.ZipFile

@CacheableTask
open class DecodeApkResourcesTask : DefaultTask() {
  @get:InputDirectory
  @get:PathSensitive(PathSensitivity.RELATIVE)
  internal val apkDirectory: DirectoryProperty = project.objects.directoryProperty()

  private fun getOutputRootFolder() = project.layout.buildDirectory.dir("intermediates/paparazzi/${name}")

  @get:OutputDirectory
  internal val decodedResourcesDirectory: Provider<Directory> =
      project.layout.buildDirectory.dir("intermediates/paparazzi/${name}/res")

  @get:OutputDirectory
  internal val decodedAssetsDirectory: Provider<Directory> =
      project.layout.buildDirectory.dir("intermediates/paparazzi/${name}/assets")

  @TaskAction
  fun decodeApk() {
    val out = getOutputRootFolder().get().asFile
    out.deleteRecursively()

    decodedResourcesDirectory.get().asFile.run {
      if (!this.mkdirs()) error("Failed to create output folder ${this.absolutePath}.")
    }
    decodedAssetsDirectory.get().asFile.run {
      if (!this.mkdirs()) error("Failed to create output folder ${this.absolutePath}.")
    }

    val inputApk = apkDirectory.get().asFile.listFiles()?.asSequence()
        ?.filter { it.extension.equals("apk", true) }
        ?.first() ?: error("Could not find any APKs under ${apkDirectory.get().asFile.absolutePath}!")

    ZipFile(inputApk).use { apkAsZip ->
      val resourcesMap = apkAsZip.getEntry("resources.arsc")
              .let { apkAsZip.getInputStream(it) }
              .use { parseResourcesArsc(it) }

      //copying the compressed binaries from the APK
      physicalFilesPaths(resourcesMap)
          .onEach {
            val target = out.resolve(it.toFile())
            target.parentFile.mkdirs()
            apkAsZip.getInputStream(apkAsZip.getEntry(it.toString())).use { sourceStream ->
              target.outputStream().use { targetStream ->
                if (target.extension.equals("xml", true)) {
                  //XML files need some extra work (resources within need to be decoded)
                  val decodedXml = parseXmlFile(sourceStream, resourcesMap)
                  targetStream.write(decodedXml.toByteArray())
                } else {
                  //other files are just decompressed
                  sourceStream.copyTo(targetStream)
                }
              }
            }
          }

      //dumping values encoded in resources.arsc into values xml files.
      valuesDump(encodedValues(resourcesMap))
          .forEach { (filePath, content) -> out.resolve(filePath.toFile())
              .also { it.parentFile.mkdirs() }
              .writeText(content) }
    }
  }
}
