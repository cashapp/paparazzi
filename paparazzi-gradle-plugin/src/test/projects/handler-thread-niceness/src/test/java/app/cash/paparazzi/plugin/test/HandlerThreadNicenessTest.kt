/*
 * Copyright (C) 2026 Square, Inc.
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
package app.cash.paparazzi.plugin.test

import dalvik.system.VMRuntime
import org.junit.Test

class HandlerThreadNicenessTest {
  @Test
  fun setThreadNicenessDoesNotCrash() {
    // VMRuntime.setThreadNiceness calls Thread.setPosixNicenessInternal,
    // a Dalvik-specific method that doesn't exist on the JVM.
    // Without the ASM transform, this throws NoSuchMethodError.
    VMRuntime.getRuntime().setThreadNiceness(Thread.currentThread(), 10)
  }
}
