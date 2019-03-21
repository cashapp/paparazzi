/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.layoutlib.bridge.impl;

import org.junit.Test;

import static org.junit.Assert.*;

public class ResourceHelperTest {
    private static void assertNumberFormatException(Runnable runnable) {
        try {
            runnable.run();
            fail("NumberFormatException expected");
        } catch (NumberFormatException ignored) {
        }
    }

    @Test
    public void testGetColor() {
        assertNumberFormatException(() -> ResourceHelper.getColor(""));
        assertNumberFormatException(() -> ResourceHelper.getColor("AFAFAF"));
        assertNumberFormatException(() -> ResourceHelper.getColor("AAA"));
        assertNumberFormatException(() -> ResourceHelper.getColor("#JFAFAF"));
        assertNumberFormatException(() -> ResourceHelper.getColor("#AABBCCDDEE"));
        assertNumberFormatException(() -> ResourceHelper.getColor("#JAAA"));
        assertNumberFormatException(() -> ResourceHelper.getColor("#AA BBCC"));

        assertEquals(0xffaaaaaa, ResourceHelper.getColor("#AAA"));
        assertEquals(0xffaaaaaa, ResourceHelper.getColor("  #AAA"));
        assertEquals(0xffaaaaaa, ResourceHelper.getColor("#AAA   "));
        assertEquals(0xffaaaaaa, ResourceHelper.getColor("  #AAA   "));
        assertEquals(0xaaaaaa, ResourceHelper.getColor("#0AAA"));
        assertEquals(0xffaabbcc, ResourceHelper.getColor("#AABBCC"));
        assertEquals(0x12aabbcc, ResourceHelper.getColor("#12AABBCC"));
        assertEquals(0x12345, ResourceHelper.getColor("#12345"));
    }
}
