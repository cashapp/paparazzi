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
package app.cash.paparazzi.internal.resources.base

import app.cash.paparazzi.internal.resources.ResourceSourceFile
import com.android.ide.common.rendering.api.AttributeFormat
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.resources.ResourceVisibility

/**
 * Ported from: [BasicForeignAttrResourceItem.java](https://cs.android.com/android-studio/platform/tools/base/+/47d204001bf0cb6273d8b135c7eece3a982cf0e0:resource-repository/main/java/com/android/resources/base/BasicForeignAttrResourceItem.java)
 *
 * Resource item representing an attr resource that is defined in a namespace different from the namespace
 * of the owning AAR.
 */
class BasicForeignAttrResourceItem(
  private val namespace: ResourceNamespace,
  name: String,
  sourceFile: ResourceSourceFile,
  description: String?,
  groupName: String?,
  formats: Set<AttributeFormat>,
  valueMap: Map<String, Int>,
  valueDescriptionMap: Map<String, String>
) : BasicAttrResourceItem(
  name = name,
  sourceFile = sourceFile,
  visibility = ResourceVisibility.PUBLIC,
  description = description,
  groupName = groupName,
  formats = formats,
  valueMap = valueMap,
  valueDescriptionMap = valueDescriptionMap
) {
  override fun getNamespace(): ResourceNamespace = namespace
}
