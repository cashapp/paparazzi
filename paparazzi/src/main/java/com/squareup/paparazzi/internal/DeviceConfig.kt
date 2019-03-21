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

package com.squareup.paparazzi.internal

import com.android.ide.common.rendering.api.HardwareConfig
import com.android.ide.common.resources.configuration.CountryCodeQualifier
import com.android.ide.common.resources.configuration.DensityQualifier
import com.android.ide.common.resources.configuration.FolderConfiguration
import com.android.ide.common.resources.configuration.KeyboardStateQualifier
import com.android.ide.common.resources.configuration.LayoutDirectionQualifier
import com.android.ide.common.resources.configuration.LocaleQualifier
import com.android.ide.common.resources.configuration.NavigationMethodQualifier
import com.android.ide.common.resources.configuration.NetworkCodeQualifier
import com.android.ide.common.resources.configuration.NightModeQualifier
import com.android.ide.common.resources.configuration.ScreenDimensionQualifier
import com.android.ide.common.resources.configuration.ScreenOrientationQualifier
import com.android.ide.common.resources.configuration.ScreenRatioQualifier
import com.android.ide.common.resources.configuration.ScreenSizeQualifier
import com.android.ide.common.resources.configuration.TextInputMethodQualifier
import com.android.ide.common.resources.configuration.TouchScreenQualifier
import com.android.ide.common.resources.configuration.UiModeQualifier
import com.android.ide.common.resources.configuration.VersionQualifier
import com.android.resources.Density
import com.android.resources.Keyboard
import com.android.resources.KeyboardState
import com.android.resources.Navigation
import com.android.resources.NightMode
import com.android.resources.ScreenOrientation
import com.android.resources.ScreenRatio
import com.android.resources.ScreenSize
import com.android.resources.TouchScreen
import com.android.resources.UiMode
import com.google.android.collect.Maps
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.Properties

/**
 * Provides [FolderConfiguration] and [HardwareConfig] for various devices. Also
 * provides utility methods to parse build.prop and attrs.xml to generate the appropriate maps.
 */
class DeviceConfig {

  // Device Configuration. Defaults are for a Nexus 4 device.
  private var screenHeight = 1280
  private var screenWidth = 768
  private var xdpi = 320
  private var ydpi = 320
  private var orientation = ScreenOrientation.PORTRAIT
  private var density = Density.XHIGH
  private var ratio = ScreenRatio.NOTLONG
  private var size = ScreenSize.NORMAL
  private var keyboard = Keyboard.NOKEY
  private var touchScreen = TouchScreen.FINGER
  private var keyboardState = KeyboardState.SOFT
  private var softButtons = true
  private var navigation = Navigation.NONAV

  // some default qualifiers.
  val folderConfig: FolderConfiguration
    get() {
      val config = FolderConfiguration.createDefault()
      config.densityQualifier = DensityQualifier(density)
      config.navigationMethodQualifier = NavigationMethodQualifier(navigation)
      if (screenWidth > screenHeight) {
        config.screenDimensionQualifier = ScreenDimensionQualifier(
            screenWidth,
            screenHeight
        )
      } else {
        config.screenDimensionQualifier = ScreenDimensionQualifier(
            screenHeight,
            screenWidth
        )
      }
      config.screenRatioQualifier = ScreenRatioQualifier(ratio)
      config.screenSizeQualifier = ScreenSizeQualifier(size)
      config.textInputMethodQualifier = TextInputMethodQualifier(keyboard)
      config.touchTypeQualifier = TouchScreenQualifier(touchScreen)
      config.keyboardStateQualifier = KeyboardStateQualifier(keyboardState)
      config.screenOrientationQualifier = ScreenOrientationQualifier(orientation)

      config.updateScreenWidthAndHeight()
      config.uiModeQualifier = UiModeQualifier(UiMode.NORMAL)
      config.nightModeQualifier = NightModeQualifier(NightMode.NOTNIGHT)
      config.countryCodeQualifier = CountryCodeQualifier()
      config.layoutDirectionQualifier = LayoutDirectionQualifier()
      config.networkCodeQualifier = NetworkCodeQualifier()
      config.localeQualifier = LocaleQualifier()
      config.versionQualifier = VersionQualifier()
      return config
    }

  val hardwareConfig: HardwareConfig
    get() = HardwareConfig(
        screenWidth, screenHeight, density, xdpi.toFloat(), ydpi.toFloat(), size,
        orientation, null, softButtons
    )

  // Methods to set the configuration values.

  fun setScreenHeight(height: Int): DeviceConfig {
    screenHeight = height
    return this
  }

  fun setScreenWidth(width: Int): DeviceConfig {
    screenWidth = width
    return this
  }

  fun setXdpi(xdpi: Int): DeviceConfig {
    this.xdpi = xdpi
    return this
  }

  fun setYdpi(ydpi: Int): DeviceConfig {
    this.ydpi = ydpi
    return this
  }

  fun setOrientation(orientation: ScreenOrientation): DeviceConfig {
    this.orientation = orientation
    return this
  }

  fun setDensity(density: Density): DeviceConfig {
    this.density = density
    return this
  }

  fun setRatio(ratio: ScreenRatio): DeviceConfig {
    this.ratio = ratio
    return this
  }

  fun setSize(size: ScreenSize): DeviceConfig {
    this.size = size
    return this
  }

  fun setKeyboard(keyboard: Keyboard): DeviceConfig {
    this.keyboard = keyboard
    return this
  }

  fun setTouchScreen(touchScreen: TouchScreen): DeviceConfig {
    this.touchScreen = touchScreen
    return this
  }

  fun setKeyboardState(state: KeyboardState): DeviceConfig {
    keyboardState = state
    return this
  }

  fun setSoftButtons(softButtons: Boolean): DeviceConfig {
    this.softButtons = softButtons
    return this
  }

  fun setNavigation(navigation: Navigation): DeviceConfig {
    this.navigation = navigation
    return this
  }

  companion object {

    val NEXUS_4 = DeviceConfig()

    val NEXUS_5 = DeviceConfig()
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
        .setNavigation(Navigation.NONAV)

    val NEXUS_7 = DeviceConfig()
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
        .setNavigation(Navigation.NONAV)

    val NEXUS_10 = DeviceConfig()
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
        .setNavigation(Navigation.NONAV)

    val NEXUS_5_LAND = DeviceConfig()
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
        .setNavigation(Navigation.NONAV)

    val NEXUS_7_2012 = DeviceConfig()
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
        .setNavigation(Navigation.NONAV)

    private val TAG_ATTR = "attr"
    private val TAG_ENUM = "enum"
    private val TAG_FLAG = "flag"
    private val ATTR_NAME = "name"
    private val ATTR_VALUE = "value"

    fun loadProperties(path: File): Map<String, String> {
      val p = Properties()
      val map = Maps.newHashMap<String, String>()
      try {
        p.load(FileInputStream(path))
        for (key in p.stringPropertyNames()) {
          map[key] = p.getProperty(key)
        }
      } catch (e: IOException) {
        e.printStackTrace()
      }

      return map
    }

    fun getEnumMap(path: File): Map<String, Map<String, Int>> {
      val map = mutableMapOf<String, MutableMap<String, Int>>()
      try {
        val xmlPullParser = XmlPullParserFactory.newInstance().newPullParser()
        xmlPullParser.setInput(FileInputStream(path), null)
        var eventType = xmlPullParser.eventType
        var attr: String? = null
        while (eventType != XmlPullParser.END_DOCUMENT) {
          if (eventType == XmlPullParser.START_TAG) {
            if (TAG_ATTR == xmlPullParser.name) {
              attr = xmlPullParser.getAttributeValue(null, ATTR_NAME)
            } else if (TAG_ENUM == xmlPullParser.name || TAG_FLAG == xmlPullParser.name) {
              val name = xmlPullParser.getAttributeValue(null, ATTR_NAME)
              val value = xmlPullParser.getAttributeValue(null, ATTR_VALUE)
              // Integer.decode cannot handle "ffffffff", see JDK issue 6624867
              val i = (java.lang.Long.decode(value) as Long).toInt()
              require(attr != null)
              var attributeMap: MutableMap<String, Int>? = map[attr]
              if (attributeMap == null) {
                attributeMap = Maps.newHashMap()
                map[attr] = attributeMap
              }
              attributeMap!![name] = i
            }
          } else if (eventType == XmlPullParser.END_TAG) {
            if (TAG_ATTR == xmlPullParser.name) {
              attr = null
            }
          }
          eventType = xmlPullParser.next()
        }
      } catch (e: XmlPullParserException) {
        e.printStackTrace()
      } catch (e: IOException) {
        e.printStackTrace()
      }

      return map
    }
  }
}
