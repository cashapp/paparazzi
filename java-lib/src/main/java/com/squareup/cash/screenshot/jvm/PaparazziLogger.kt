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
package com.squareup.cash.screenshot.jvm

import com.android.utils.ILogger

class PaparazziLogger : ILogger {
  private val renderMessages = mutableListOf<String>()

  override fun error(
    t: Throwable,
    format: String?,
    vararg args: Any
  ) {
    renderMessages += String.format(format ?: "", *args)
  }

  override fun warning(format: String, vararg args: Any) {
    renderMessages += String.format(format, *args)
  }

  override fun info(msgFormat: String, vararg args: Any) {
  }

  override fun verbose(msgFormat: String, vararg args: Any) {
  }
}