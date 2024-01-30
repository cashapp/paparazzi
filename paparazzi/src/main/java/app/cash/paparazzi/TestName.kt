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

import dev.drewhamilton.poko.Poko

@Poko
public class TestName(
  public val packageName: String,
  public val className: String,
  public val methodName: String
) {
  public fun copy(
    packageName: String = this.packageName,
    className: String = this.className,
    methodName: String = this.methodName
  ): TestName = TestName(packageName, className, methodName)
}
