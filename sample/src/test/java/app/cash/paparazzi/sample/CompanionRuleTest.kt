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
package app.cash.paparazzi.sample

import android.widget.TextView
import app.cash.paparazzi.DeviceConfig.Companion.PIXEL_3
import app.cash.paparazzi.Paparazzi
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test

class CompanionRuleTest {
  companion object {
    @get:ClassRule @get:Rule @JvmStatic
    val paparazzi = Paparazzi(deviceConfig = PIXEL_3)
  }

//  @get:Rule val paparazzi = Paparazzi(deviceConfig = PIXEL_3)

  private fun test() {
    paparazzi.snapshot(TextView(paparazzi.context).apply { text = "Hello world!" })
  }

  @Test fun test1() = test()
  @Test fun test2() = test()
  @Test fun test3() = test()
  @Test fun test4() = test()
  @Test fun test5() = test()
  @Test fun test6() = test()
  @Test fun test7() = test()
  @Test fun test8() = test()
  @Test fun test9() = test()
  @Test fun test10() = test()
  @Test fun test11() = test()
  @Test fun test12() = test()
  @Test fun test13() = test()
  @Test fun test14() = test()
  @Test fun test15() = test()
  @Test fun test16() = test()
  @Test fun test17() = test()
  @Test fun test18() = test()
  @Test fun test19() = test()
  @Test fun test20() = test()
  @Test fun test21() = test()
  @Test fun test22() = test()
  @Test fun test23() = test()
  @Test fun test24() = test()
  @Test fun test25() = test()
  @Test fun test26() = test()
  @Test fun test27() = test()
  @Test fun test28() = test()
  @Test fun test29() = test()
  @Test fun test30() = test()
  @Test fun test31() = test()
  @Test fun test32() = test()
  @Test fun test33() = test()
  @Test fun test34() = test()
  @Test fun test35() = test()
  @Test fun test36() = test()
  @Test fun test37() = test()
  @Test fun test38() = test()
  @Test fun test39() = test()
  @Test fun test40() = test()
  @Test fun test41() = test()
  @Test fun test42() = test()
  @Test fun test43() = test()
  @Test fun test44() = test()
  @Test fun test45() = test()
  @Test fun test46() = test()
  @Test fun test47() = test()
  @Test fun test48() = test()
  @Test fun test49() = test()
  @Test fun test50() = test()
  @Test fun test51() = test()
  @Test fun test52() = test()
  @Test fun test53() = test()
  @Test fun test54() = test()
  @Test fun test55() = test()
  @Test fun test56() = test()
  @Test fun test57() = test()
  @Test fun test58() = test()
  @Test fun test59() = test()
  @Test fun test60() = test()
  @Test fun test61() = test()
  @Test fun test62() = test()
  @Test fun test63() = test()
  @Test fun test64() = test()
  @Test fun test65() = test()
  @Test fun test66() = test()
  @Test fun test67() = test()
  @Test fun test68() = test()
  @Test fun test69() = test()
  @Test fun test70() = test()
  @Test fun test71() = test()
  @Test fun test72() = test()
  @Test fun test73() = test()
  @Test fun test74() = test()
  @Test fun test75() = test()
  @Test fun test76() = test()
  @Test fun test77() = test()
  @Test fun test78() = test()
  @Test fun test79() = test()
  @Test fun test80() = test()
  @Test fun test81() = test()
  @Test fun test82() = test()
  @Test fun test83() = test()
  @Test fun test84() = test()
  @Test fun test85() = test()
  @Test fun test86() = test()
  @Test fun test87() = test()
  @Test fun test88() = test()
  @Test fun test89() = test()
  @Test fun test90() = test()
  @Test fun test91() = test()
  @Test fun test92() = test()
  @Test fun test93() = test()
  @Test fun test94() = test()
  @Test fun test95() = test()
  @Test fun test96() = test()
  @Test fun test97() = test()
  @Test fun test98() = test()
  @Test fun test99() = test()
  @Test fun test100() = test()
}
