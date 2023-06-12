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
import com.android.ide.common.rendering.api.TextResourceValue
import com.android.resources.ResourceType
import com.android.resources.ResourceVisibility
import com.android.utils.HashCodes

/**
 * Ported from: [BasicTextValueResourceItem.java](https://cs.android.com/android-studio/platform/tools/base/+/47d204001bf0cb6273d8b135c7eece3a982cf0e0:resource-repository/main/java/com/android/resources/base/BasicTextValueResourceItem.java)
 *
 * Resource item representing a value resource, e.g. a string or a color.
 */
class BasicTextValueResourceItem(
  type: ResourceType,
  name: String,
  sourceFile: ResourceSourceFile,
  visibility: ResourceVisibility,
  textValue: String?,
  private val rawXmlValue: String?
) : BasicValueResourceItem(type, name, sourceFile, visibility, textValue), TextResourceValue {
  override fun getRawXmlValue(): String? = rawXmlValue ?: value

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (!super.equals(other)) return false
    val that = other as BasicTextValueResourceItem
    return rawXmlValue == that.rawXmlValue
  }

  override fun hashCode(): Int {
    return HashCodes.mix(super.hashCode(), rawXmlValue.hashCode())
  }
}
