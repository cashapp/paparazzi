/*
 * Copyright (C) 2024 Square, Inc.
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

import com.android.build.gradle.internal.publishing.AndroidArtifacts
import org.gradle.api.Plugin
import org.gradle.api.Project

@Suppress("unused")
class Aar2JarPlugin : Plugin<Project> {
  override fun apply(project: Project) {
    project.extensions.create("aar2jar", Aar2JarExtension::class.java, project)
    project.dependencies.registerTransform(IdentityTransform::class.java) {
      it.from.attribute(AndroidArtifacts.ARTIFACT_TYPE, "jar")
      it.to.attribute(AndroidArtifacts.ARTIFACT_TYPE, "aarAsJar")
    }
    project.dependencies.registerTransform(ExtractClassesJarTransform::class.java) {
      it.from.attribute(AndroidArtifacts.ARTIFACT_TYPE, "aar")
      it.to.attribute(AndroidArtifacts.ARTIFACT_TYPE, "aarAsJar")
    }
  }
}
