/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.squareup.paparazzi.internal;

import com.android.ide.common.rendering.api.HardwareConfig;
import com.android.ide.common.resources.configuration.CountryCodeQualifier;
import com.android.ide.common.resources.configuration.DensityQualifier;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.ide.common.resources.configuration.KeyboardStateQualifier;
import com.android.ide.common.resources.configuration.LayoutDirectionQualifier;
import com.android.ide.common.resources.configuration.LocaleQualifier;
import com.android.ide.common.resources.configuration.NavigationMethodQualifier;
import com.android.ide.common.resources.configuration.NetworkCodeQualifier;
import com.android.ide.common.resources.configuration.NightModeQualifier;
import com.android.ide.common.resources.configuration.ScreenDimensionQualifier;
import com.android.ide.common.resources.configuration.ScreenOrientationQualifier;
import com.android.ide.common.resources.configuration.ScreenRatioQualifier;
import com.android.ide.common.resources.configuration.ScreenSizeQualifier;
import com.android.ide.common.resources.configuration.TextInputMethodQualifier;
import com.android.ide.common.resources.configuration.TouchScreenQualifier;
import com.android.ide.common.resources.configuration.UiModeQualifier;
import com.android.ide.common.resources.configuration.VersionQualifier;
import com.android.resources.Density;
import com.android.resources.Keyboard;
import com.android.resources.KeyboardState;
import com.android.resources.Navigation;
import com.android.resources.NightMode;
import com.android.resources.ScreenOrientation;
import com.android.resources.ScreenRatio;
import com.android.resources.ScreenSize;
import com.android.resources.TouchScreen;
import com.android.resources.UiMode;
import com.google.android.collect.Maps;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Properties;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

/**
 * Provides {@link FolderConfiguration} and {@link HardwareConfig} for various devices. Also
 * provides utility methods to parse build.prop and attrs.xml to generate the appropriate maps.
 */
@SuppressWarnings("UnusedDeclaration") // For the pre-configured nexus generators.
public class DeviceConfig {

    public static final DeviceConfig NEXUS_4 = new DeviceConfig();

    public static final DeviceConfig NEXUS_5 = new DeviceConfig()
                                                        .setScreenHeight(1920)
                                                        .setScreenWidth(1080)
                                                        .setXdpi(445)
                                                        .setYdpi(445)
                                                        .setOrientation(ScreenOrientation.PORTRAIT)
                                                        .setDensity(Density.XXHIGH)
                                                        .setRatio(ScreenRatio.NOTLONG)
                                                        .setSize(ScreenSize.NORMAL)
                                                        .setKeyboard(Keyboard.NOKEY)
                                                        .setTouchScreen(TouchScreen.FINGER)
                                                        .setKeyboardState(KeyboardState.SOFT)
                                                        .setSoftButtons(true)
                                                        .setNavigation(Navigation.NONAV);

    public static final DeviceConfig NEXUS_7 = new DeviceConfig()
                                                        .setScreenHeight(1920)
                                                        .setScreenWidth(1200)
                                                        .setXdpi(323)
                                                        .setYdpi(323)
                                                        .setOrientation(ScreenOrientation.PORTRAIT)
                                                        .setDensity(Density.XHIGH)
                                                        .setRatio(ScreenRatio.NOTLONG)
                                                        .setSize(ScreenSize.LARGE)
                                                        .setKeyboard(Keyboard.NOKEY)
                                                        .setTouchScreen(TouchScreen.FINGER)
                                                        .setKeyboardState(KeyboardState.SOFT)
                                                        .setSoftButtons(true)
                                                        .setNavigation(Navigation.NONAV);

    public static final DeviceConfig NEXUS_10 = new DeviceConfig()
                                                        .setScreenHeight(1600)
                                                        .setScreenWidth(2560)
                                                        .setXdpi(300)
                                                        .setYdpi(300)
                                                        .setOrientation(ScreenOrientation.LANDSCAPE)
                                                        .setDensity(Density.XHIGH)
                                                        .setRatio(ScreenRatio.NOTLONG)
                                                        .setSize(ScreenSize.XLARGE)
                                                        .setKeyboard(Keyboard.NOKEY)
                                                        .setTouchScreen(TouchScreen.FINGER)
                                                        .setKeyboardState(KeyboardState.SOFT)
                                                        .setSoftButtons(true)
                                                        .setNavigation(Navigation.NONAV);

    public static final DeviceConfig NEXUS_5_LAND = new DeviceConfig()
                                                        .setScreenHeight(1080)
                                                        .setScreenWidth(1920)
                                                        .setXdpi(445)
                                                        .setYdpi(445)
                                                        .setOrientation(ScreenOrientation.LANDSCAPE)
                                                        .setDensity(Density.XXHIGH)
                                                        .setRatio(ScreenRatio.NOTLONG)
                                                        .setSize(ScreenSize.NORMAL)
                                                        .setKeyboard(Keyboard.NOKEY)
                                                        .setTouchScreen(TouchScreen.FINGER)
                                                        .setKeyboardState(KeyboardState.SOFT)
                                                        .setSoftButtons(true)
                                                        .setNavigation(Navigation.NONAV);

    public static final DeviceConfig NEXUS_7_2012 = new DeviceConfig()
                                                        .setScreenHeight(1280)
                                                        .setScreenWidth(800)
                                                        .setXdpi(195)
                                                        .setYdpi(200)
                                                        .setOrientation(ScreenOrientation.PORTRAIT)
                                                        .setDensity(Density.TV)
                                                        .setRatio(ScreenRatio.NOTLONG)
                                                        .setSize(ScreenSize.LARGE)
                                                        .setKeyboard(Keyboard.NOKEY)
                                                        .setTouchScreen(TouchScreen.FINGER)
                                                        .setKeyboardState(KeyboardState.SOFT)
                                                        .setSoftButtons(true)
                                                        .setNavigation(Navigation.NONAV);

    private static final String TAG_ATTR = "attr";
    private static final String TAG_ENUM = "enum";
    private static final String TAG_FLAG = "flag";
    private static final String ATTR_NAME = "name";
    private static final String ATTR_VALUE = "value";

    // Device Configuration. Defaults are for a Nexus 4 device.
    private int screenHeight = 1280;
    private int screenWidth = 768;
    private int xdpi = 320;
    private int ydpi = 320;
    private ScreenOrientation orientation = ScreenOrientation.PORTRAIT;
    private Density density = Density.XHIGH;
    private ScreenRatio ratio = ScreenRatio.NOTLONG;
    private ScreenSize size = ScreenSize.NORMAL;
    private Keyboard keyboard = Keyboard.NOKEY;
    private TouchScreen touchScreen = TouchScreen.FINGER;
    private KeyboardState keyboardState = KeyboardState.SOFT;
    private boolean softButtons = true;
    private Navigation navigation = Navigation.NONAV;

    public FolderConfiguration getFolderConfig() {
        FolderConfiguration config = FolderConfiguration.createDefault();
        config.setDensityQualifier(new DensityQualifier(density));
        config.setNavigationMethodQualifier(new NavigationMethodQualifier(navigation));
        if (screenWidth > screenHeight) {
            config.setScreenDimensionQualifier(new ScreenDimensionQualifier(screenWidth,
                screenHeight));
        } else {
            config.setScreenDimensionQualifier(new ScreenDimensionQualifier(screenHeight,
                screenWidth));
        }
        config.setScreenRatioQualifier(new ScreenRatioQualifier(ratio));
        config.setScreenSizeQualifier(new ScreenSizeQualifier(size));
        config.setTextInputMethodQualifier(new TextInputMethodQualifier(keyboard));
        config.setTouchTypeQualifier(new TouchScreenQualifier(touchScreen));
        config.setKeyboardStateQualifier(new KeyboardStateQualifier(keyboardState));
        config.setScreenOrientationQualifier(new ScreenOrientationQualifier(orientation));

        config.updateScreenWidthAndHeight();

        // some default qualifiers.
        config.setUiModeQualifier(new UiModeQualifier(UiMode.NORMAL));
        config.setNightModeQualifier(new NightModeQualifier(NightMode.NOTNIGHT));
        config.setCountryCodeQualifier(new CountryCodeQualifier());
        config.setLayoutDirectionQualifier(new LayoutDirectionQualifier());
        config.setNetworkCodeQualifier(new NetworkCodeQualifier());
        config.setLocaleQualifier(new LocaleQualifier());
        config.setVersionQualifier(new VersionQualifier());
        return config;
    }

    public HardwareConfig getHardwareConfig() {
        return new HardwareConfig(screenWidth, screenHeight, density, xdpi, ydpi, size,
            orientation, null, softButtons);
    }

    public static Map<String, String> loadProperties(File path) {
        Properties p = new Properties();
        Map<String, String> map = Maps.newHashMap();
        try {
            p.load(new FileInputStream(path));
            for (String key : p.stringPropertyNames()) {
                map.put(key, p.getProperty(key));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return map;
    }

    public static Map<String, Map<String, Integer>> getEnumMap(File path) {
        Map<String, Map<String, Integer>> map = Maps.newHashMap();
        try {
            XmlPullParser xmlPullParser = XmlPullParserFactory.newInstance().newPullParser();
            xmlPullParser.setInput(new FileInputStream(path), null);
            int eventType = xmlPullParser.getEventType();
            String attr = null;
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    if (TAG_ATTR.equals(xmlPullParser.getName())) {
                        attr = xmlPullParser.getAttributeValue(null, ATTR_NAME);
                    } else if (TAG_ENUM.equals(xmlPullParser.getName())
                            || TAG_FLAG.equals(xmlPullParser.getName())) {
                        String name = xmlPullParser.getAttributeValue(null, ATTR_NAME);
                        String value = xmlPullParser.getAttributeValue(null, ATTR_VALUE);
                        // Integer.decode cannot handle "ffffffff", see JDK issue 6624867
                        int i = (int) (long) Long.decode(value);
                        assert attr != null;
                        Map<String, Integer> attributeMap = map.get(attr);
                        if (attributeMap == null) {
                            attributeMap = Maps.newHashMap();
                            map.put(attr, attributeMap);
                        }
                        attributeMap.put(name, i);
                    }
                } else if (eventType == XmlPullParser.END_TAG) {
                    if (TAG_ATTR.equals(xmlPullParser.getName())) {
                        attr = null;
                    }
                }
                eventType = xmlPullParser.next();
            }
        } catch (XmlPullParserException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return map;
    }

    // Methods to set the configuration values.

    public DeviceConfig setScreenHeight(int height) {
        screenHeight = height;
        return this;
    }

    public DeviceConfig setScreenWidth(int width) {
        screenWidth = width;
        return this;
    }

    public DeviceConfig setXdpi(int xdpi) {
        this.xdpi = xdpi;
        return this;
    }

    public DeviceConfig setYdpi(int ydpi) {
        this.ydpi = ydpi;
        return this;
    }

    public DeviceConfig setOrientation(ScreenOrientation orientation) {
        this.orientation = orientation;
        return this;
    }

    public DeviceConfig setDensity(Density density) {
        this.density = density;
        return this;
    }

    public DeviceConfig setRatio(ScreenRatio ratio) {
        this.ratio = ratio;
        return this;
    }

    public DeviceConfig setSize(ScreenSize size) {
        this.size = size;
        return this;
    }

    public DeviceConfig setKeyboard(Keyboard keyboard) {
        this.keyboard = keyboard;
        return this;
    }

    public DeviceConfig setTouchScreen(TouchScreen touchScreen) {
        this.touchScreen = touchScreen;
        return this;
    }

    public DeviceConfig setKeyboardState(KeyboardState state) {
        keyboardState = state;
        return this;
    }

    public DeviceConfig setSoftButtons(boolean softButtons) {
        this.softButtons = softButtons;
        return this;
    }

    public DeviceConfig setNavigation(Navigation navigation) {
        this.navigation = navigation;
        return this;
    }
}
