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
import org.gradle.api.Project
import org.gradle.api.attributes.Usage

fun configureAarAsJarForConfiguration(project: Project, configuration: String) {
  val aarAsJar = project.configurations
    .create("${configuration}AarAsJar") {
      it.isCanBeResolved = true
      it.attributes.attribute(AndroidArtifacts.ARTIFACT_TYPE, "release")
      it.attributes.attribute(
        Usage.USAGE_ATTRIBUTE,
        project.objects.named(Usage::class.java, Usage.JAVA_API)
      )
    }
    .incoming
    .artifactView { viewConfiguration ->
      viewConfiguration.attributes.attribute(AndroidArtifacts.ARTIFACT_TYPE, "aarAsJar")
    }
    .files

  project.configurations
    .getByName(configuration)
    .dependencies
    .add(project.dependencies.create(aarAsJar))
}
