/*
 * Copyright (C) 2023 Square, Inc.
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
package app.cash.paparazzi.gradle.utils

import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.artifacts.ArtifactView
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.type.ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE

internal fun Configuration.artifactsFor(
  attrValue: String,
  componentFilter: (ComponentIdentifier) -> Boolean = { true }
): ArtifactCollection =
  artifactViewFor(attrValue, componentFilter).artifacts

internal fun Configuration.artifactViewFor(
  attrValue: String,
  componentFilter: (ComponentIdentifier) -> Boolean = { true }
): ArtifactView =
  incoming.artifactView { config ->
    config.attributes.attribute(ARTIFACT_TYPE_ATTRIBUTE, attrValue)
    config.componentFilter(componentFilter)
  }
