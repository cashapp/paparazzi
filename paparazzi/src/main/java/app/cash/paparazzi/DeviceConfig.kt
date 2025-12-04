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

package app.cash.paparazzi

import android.content.res.Configuration
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
import com.android.ide.common.resources.configuration.ScreenRoundQualifier
import com.android.ide.common.resources.configuration.ScreenSizeQualifier
import com.android.ide.common.resources.configuration.TextInputMethodQualifier
import com.android.ide.common.resources.configuration.TouchScreenQualifier
import com.android.ide.common.resources.configuration.UiModeQualifier
import com.android.ide.common.resources.configuration.VersionQualifier
import com.android.resources.Density
import com.android.resources.Keyboard
import com.android.resources.KeyboardState
import com.android.resources.LayoutDirection
import com.android.resources.Navigation
import com.android.resources.NightMode
import com.android.resources.ScreenOrientation
import com.android.resources.ScreenRatio
import com.android.resources.ScreenRound
import com.android.resources.ScreenSize
import com.android.resources.TouchScreen
import com.android.resources.UiMode
import com.google.android.collect.Maps
import dev.drewhamilton.poko.Poko
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.lang.UnsupportedOperationException
import java.util.Properties
import kotlin.math.max
import kotlin.math.min

/**
 * Provides [FolderConfiguration] and [HardwareConfig] for various devices. Also provides utility
 * methods to parse `build.prop` and `attrs.xml` to generate the appropriate maps.
 *
 * Defaults are for a Nexus 4 device.
 */
@Poko
public class DeviceConfig(
  public val screenHeight: Int = 1280,
  public val screenWidth: Int = 768,
  public val xdpi: Int = 320,
  public val ydpi: Int = 320,
  public val orientation: ScreenOrientation = ScreenOrientation.PORTRAIT,
  public val uiMode: UiMode = UiMode.NORMAL,
  public val nightMode: NightMode = NightMode.NOTNIGHT,
  public val density: Density = Density.XHIGH,
  public val fontScale: Float = 1f,
  public val layoutDirection: LayoutDirection = LayoutDirection.LTR,
  public val locale: String? = null,
  public val ratio: ScreenRatio = ScreenRatio.NOTLONG,
  public val size: ScreenSize = ScreenSize.NORMAL,
  public val keyboard: Keyboard = Keyboard.NOKEY,
  public val touchScreen: TouchScreen = TouchScreen.FINGER,
  public val keyboardState: KeyboardState = KeyboardState.SOFT,
  public val softButtons: Boolean = true,
  public val navigation: Navigation = Navigation.NONAV,
  public val screenRound: ScreenRound? = null,
  public val released: String = "November 13, 2012"
) {
  public val folderConfiguration: FolderConfiguration
    get() = FolderConfiguration.createDefault()
      .apply {
        densityQualifier = DensityQualifier(density)
        navigationMethodQualifier = NavigationMethodQualifier(navigation)
        screenDimensionQualifier = when {
          screenWidth > screenHeight -> ScreenDimensionQualifier(screenWidth, screenHeight)
          else -> ScreenDimensionQualifier(screenHeight, screenWidth)
        }
        screenRatioQualifier = ScreenRatioQualifier(ratio)
        screenSizeQualifier = ScreenSizeQualifier(size)
        textInputMethodQualifier = TextInputMethodQualifier(keyboard)
        touchTypeQualifier = TouchScreenQualifier(touchScreen)
        keyboardStateQualifier = KeyboardStateQualifier(keyboardState)
        screenOrientationQualifier = ScreenOrientationQualifier(orientation)

        updateScreenWidthAndHeight()
        uiModeQualifier = UiModeQualifier(uiMode)
        nightModeQualifier = NightModeQualifier(nightMode)
        countryCodeQualifier = CountryCodeQualifier()
        layoutDirectionQualifier = LayoutDirectionQualifier(layoutDirection)
        networkCodeQualifier = NetworkCodeQualifier()
        localeQualifier = if (locale != null) LocaleQualifier.getQualifier(locale) else LocaleQualifier()
        versionQualifier = VersionQualifier()
        screenRoundQualifier = ScreenRoundQualifier(screenRound)
      }

  public fun copy(
    screenHeight: Int = this.screenHeight,
    screenWidth: Int = this.screenWidth,
    xdpi: Int = this.xdpi,
    ydpi: Int = this.ydpi,
    orientation: ScreenOrientation = this.orientation,
    uiMode: UiMode = this.uiMode,
    nightMode: NightMode = this.nightMode,
    density: Density = this.density,
    fontScale: Float = this.fontScale,
    layoutDirection: LayoutDirection = this.layoutDirection,
    locale: String? = this.locale,
    ratio: ScreenRatio = this.ratio,
    size: ScreenSize = this.size,
    keyboard: Keyboard = this.keyboard,
    touchScreen: TouchScreen = this.touchScreen,
    keyboardState: KeyboardState = this.keyboardState,
    softButtons: Boolean = this.softButtons,
    navigation: Navigation = this.navigation,
    screenRound: ScreenRound? = this.screenRound
  ): DeviceConfig =
    DeviceConfig(
      screenHeight,
      screenWidth,
      xdpi,
      ydpi,
      orientation,
      uiMode,
      nightMode,
      density,
      fontScale,
      layoutDirection,
      locale,
      ratio,
      size,
      keyboard,
      touchScreen,
      keyboardState,
      softButtons,
      navigation,
      screenRound,
      this.released
    )

  public val hardwareConfig: HardwareConfig
    get() = HardwareConfig(
      currentWidth, currentHeight, density, xdpi.toFloat(), ydpi.toFloat(), size,
      orientation, screenRound, softButtons
    )

  public val uiModeMask: Int
    get() {
      val nightMask = if (nightMode == NightMode.NIGHT) {
        Configuration.UI_MODE_NIGHT_YES
      } else {
        Configuration.UI_MODE_NIGHT_NO
      }
      val typeMask = when (uiMode) {
        UiMode.NORMAL -> Configuration.UI_MODE_TYPE_NORMAL
        UiMode.DESK -> Configuration.UI_MODE_TYPE_DESK
        UiMode.CAR -> Configuration.UI_MODE_TYPE_CAR
        UiMode.TELEVISION -> Configuration.UI_MODE_TYPE_TELEVISION
        UiMode.APPLIANCE -> Configuration.UI_MODE_TYPE_APPLIANCE
        UiMode.WATCH -> Configuration.UI_MODE_TYPE_WATCH
        UiMode.VR_HEADSET -> Configuration.UI_MODE_TYPE_VR_HEADSET
      }
      return nightMask or typeMask
    }

  private val currentWidth: Int
    get() = when (orientation) {
      ScreenOrientation.PORTRAIT -> min(screenWidth, screenHeight)
      ScreenOrientation.LANDSCAPE -> max(screenWidth, screenHeight)
      else -> throw UnsupportedOperationException("Only Portrait or Landscape orientations are supported")
    }
  private val currentHeight: Int
    get() = when (orientation) {
      ScreenOrientation.PORTRAIT -> max(screenWidth, screenHeight)
      ScreenOrientation.LANDSCAPE -> min(screenWidth, screenHeight)
      else -> throw UnsupportedOperationException("Only Portrait or Landscape orientations are supported")
    }

  /**
   * Device specs per:
   * https://android.googlesource.com/platform/tools/base/+/mirror-goog-studio-master-dev/sdklib/src/main/java/com/android/sdklib/devices/nexus.xml
   *
   * Release dates obtained from Wikipedia.
   */

  public companion object {
    @JvmField
    public val NEXUS_4: DeviceConfig = DeviceConfig()

    @JvmField
    public val NEXUS_5: DeviceConfig = DeviceConfig(
      screenHeight = 1920,
      screenWidth = 1080,
      xdpi = 445,
      ydpi = 445,
      orientation = ScreenOrientation.PORTRAIT,
      density = Density.XXHIGH,
      ratio = ScreenRatio.NOTLONG,
      size = ScreenSize.NORMAL,
      keyboard = Keyboard.NOKEY,
      touchScreen = TouchScreen.FINGER,
      keyboardState = KeyboardState.SOFT,
      softButtons = true,
      navigation = Navigation.NONAV,
      released = "October 31, 2013"
    )

    @JvmField
    public val NEXUS_7: DeviceConfig = DeviceConfig(
      screenHeight = 1920,
      screenWidth = 1200,
      xdpi = 323,
      ydpi = 323,
      orientation = ScreenOrientation.PORTRAIT,
      density = Density.XHIGH,
      ratio = ScreenRatio.NOTLONG,
      size = ScreenSize.LARGE,
      keyboard = Keyboard.NOKEY,
      touchScreen = TouchScreen.FINGER,
      keyboardState = KeyboardState.SOFT,
      softButtons = true,
      navigation = Navigation.NONAV,
      released = "July 26, 2013"
    )

    @JvmField
    public val NEXUS_10: DeviceConfig = DeviceConfig(
      screenHeight = 1600,
      screenWidth = 2560,
      xdpi = 300,
      ydpi = 300,
      orientation = ScreenOrientation.LANDSCAPE,
      density = Density.XHIGH,
      ratio = ScreenRatio.NOTLONG,
      size = ScreenSize.XLARGE,
      keyboard = Keyboard.NOKEY,
      touchScreen = TouchScreen.FINGER,
      keyboardState = KeyboardState.SOFT,
      softButtons = true,
      navigation = Navigation.NONAV,
      released = "November 13, 2012"
    )

    @JvmField
    public val NEXUS_7_2012: DeviceConfig = DeviceConfig(
      screenHeight = 1280,
      screenWidth = 800,
      xdpi = 195,
      ydpi = 200,
      orientation = ScreenOrientation.PORTRAIT,
      density = Density.TV,
      ratio = ScreenRatio.NOTLONG,
      size = ScreenSize.LARGE,
      keyboard = Keyboard.NOKEY,
      touchScreen = TouchScreen.FINGER,
      keyboardState = KeyboardState.SOFT,
      softButtons = true,
      navigation = Navigation.NONAV,
      released = "July 13, 2012"
    )

    @JvmField
    public val PIXEL_C: DeviceConfig = DeviceConfig(
      screenHeight = 1800,
      screenWidth = 2560,
      xdpi = 308,
      ydpi = 308,
      orientation = ScreenOrientation.LANDSCAPE,
      density = Density.XHIGH,
      ratio = ScreenRatio.NOTLONG,
      size = ScreenSize.XLARGE,
      keyboard = Keyboard.QWERTY,
      touchScreen = TouchScreen.FINGER,
      keyboardState = KeyboardState.SOFT,
      softButtons = true,
      navigation = Navigation.NONAV,
      released = "December 8, 2015"
    )

    @JvmField
    public val PIXEL: DeviceConfig = DeviceConfig(
      screenHeight = 1920,
      screenWidth = 1080,
      xdpi = 440,
      ydpi = 440,
      orientation = ScreenOrientation.PORTRAIT,
      density = Density.create(420),
      ratio = ScreenRatio.NOTLONG,
      size = ScreenSize.NORMAL,
      keyboard = Keyboard.NOKEY,
      touchScreen = TouchScreen.FINGER,
      keyboardState = KeyboardState.SOFT,
      softButtons = true,
      navigation = Navigation.NONAV,
      released = "October 20, 2016"
    )

    @JvmField
    public val PIXEL_XL: DeviceConfig = DeviceConfig(
      screenHeight = 2560,
      screenWidth = 1440,
      xdpi = 534,
      ydpi = 534,
      orientation = ScreenOrientation.PORTRAIT,
      density = Density.create(560),
      ratio = ScreenRatio.NOTLONG,
      size = ScreenSize.NORMAL,
      keyboard = Keyboard.NOKEY,
      touchScreen = TouchScreen.FINGER,
      keyboardState = KeyboardState.SOFT,
      softButtons = true,
      navigation = Navigation.NONAV,
      released = "October 20, 2016"
    )

    @JvmField
    public val PIXEL_2: DeviceConfig = DeviceConfig(
      screenHeight = 1920,
      screenWidth = 1080,
      xdpi = 442,
      ydpi = 443,
      orientation = ScreenOrientation.PORTRAIT,
      density = Density.create(420),
      ratio = ScreenRatio.NOTLONG,
      size = ScreenSize.NORMAL,
      keyboard = Keyboard.NOKEY,
      touchScreen = TouchScreen.FINGER,
      keyboardState = KeyboardState.SOFT,
      softButtons = true,
      navigation = Navigation.NONAV,
      released = "October 19, 2017"
    )

    @JvmField
    public val PIXEL_2_XL: DeviceConfig = DeviceConfig(
      screenHeight = 2880,
      screenWidth = 1440,
      xdpi = 537,
      ydpi = 537,
      orientation = ScreenOrientation.PORTRAIT,
      density = Density.create(560),
      ratio = ScreenRatio.LONG,
      size = ScreenSize.NORMAL,
      keyboard = Keyboard.NOKEY,
      touchScreen = TouchScreen.FINGER,
      keyboardState = KeyboardState.SOFT,
      softButtons = true,
      navigation = Navigation.NONAV,
      released = "October 19, 2017"
    )

    @JvmField
    public val PIXEL_3: DeviceConfig = DeviceConfig(
      screenHeight = 2160,
      screenWidth = 1080,
      xdpi = 442,
      ydpi = 442,
      orientation = ScreenOrientation.PORTRAIT,
      density = Density.create(440),
      ratio = ScreenRatio.LONG,
      size = ScreenSize.NORMAL,
      keyboard = Keyboard.NOKEY,
      touchScreen = TouchScreen.FINGER,
      keyboardState = KeyboardState.SOFT,
      softButtons = true,
      navigation = Navigation.NONAV,
      released = "October 18, 2018"
    )

    @JvmField
    public val PIXEL_3_XL: DeviceConfig = DeviceConfig(
      screenHeight = 2960,
      screenWidth = 1440,
      xdpi = 522,
      ydpi = 522,
      orientation = ScreenOrientation.PORTRAIT,
      density = Density.create(560),
      ratio = ScreenRatio.LONG,
      size = ScreenSize.NORMAL,
      keyboard = Keyboard.NOKEY,
      touchScreen = TouchScreen.FINGER,
      keyboardState = KeyboardState.SOFT,
      softButtons = true,
      navigation = Navigation.NONAV,
      released = "October 18, 2018"
    )

    @JvmField
    public val PIXEL_3A: DeviceConfig = DeviceConfig(
      screenHeight = 2220,
      screenWidth = 1080,
      xdpi = 442,
      ydpi = 444,
      orientation = ScreenOrientation.PORTRAIT,
      density = Density.create(440),
      ratio = ScreenRatio.LONG,
      size = ScreenSize.NORMAL,
      keyboard = Keyboard.NOKEY,
      touchScreen = TouchScreen.FINGER,
      keyboardState = KeyboardState.SOFT,
      softButtons = true,
      navigation = Navigation.NONAV,
      released = "May 7, 2019"
    )

    @JvmField
    public val PIXEL_3A_XL: DeviceConfig = DeviceConfig(
      screenHeight = 2160,
      screenWidth = 1080,
      xdpi = 397,
      ydpi = 400,
      orientation = ScreenOrientation.PORTRAIT,
      density = Density.create(400),
      ratio = ScreenRatio.LONG,
      size = ScreenSize.NORMAL,
      keyboard = Keyboard.NOKEY,
      touchScreen = TouchScreen.FINGER,
      keyboardState = KeyboardState.SOFT,
      softButtons = true,
      navigation = Navigation.NONAV,
      released = "May 7, 2019"
    )

    @JvmField
    public val PIXEL_4: DeviceConfig = DeviceConfig(
      screenHeight = 2280,
      screenWidth = 1080,
      xdpi = 444,
      ydpi = 444,
      orientation = ScreenOrientation.PORTRAIT,
      density = Density.create(440),
      ratio = ScreenRatio.LONG,
      size = ScreenSize.NORMAL,
      keyboard = Keyboard.NOKEY,
      touchScreen = TouchScreen.FINGER,
      keyboardState = KeyboardState.SOFT,
      softButtons = true,
      navigation = Navigation.NONAV,
      released = "October 24, 2019"
    )

    @JvmField
    public val PIXEL_4_XL: DeviceConfig = DeviceConfig(
      screenHeight = 3040,
      screenWidth = 1440,
      xdpi = 537,
      ydpi = 537,
      orientation = ScreenOrientation.PORTRAIT,
      density = Density.create(560),
      ratio = ScreenRatio.LONG,
      size = ScreenSize.NORMAL,
      keyboard = Keyboard.NOKEY,
      touchScreen = TouchScreen.FINGER,
      keyboardState = KeyboardState.SOFT,
      softButtons = true,
      navigation = Navigation.NONAV,
      released = "October 24, 2019"
    )

    @JvmField
    public val PIXEL_4A: DeviceConfig = DeviceConfig(
      screenHeight = 2340,
      screenWidth = 1080,
      xdpi = 442,
      ydpi = 444,
      orientation = ScreenOrientation.PORTRAIT,
      density = Density.create(440),
      ratio = ScreenRatio.LONG,
      size = ScreenSize.NORMAL,
      keyboard = Keyboard.NOKEY,
      touchScreen = TouchScreen.FINGER,
      keyboardState = KeyboardState.SOFT,
      softButtons = true,
      navigation = Navigation.NONAV,
      released = "August 20, 2020"
    )

    @JvmField
    public val PIXEL_5: DeviceConfig = DeviceConfig(
      screenHeight = 2340,
      screenWidth = 1080,
      xdpi = 442,
      ydpi = 444,
      orientation = ScreenOrientation.PORTRAIT,
      density = Density.create(440),
      ratio = ScreenRatio.LONG,
      size = ScreenSize.NORMAL,
      keyboard = Keyboard.NOKEY,
      touchScreen = TouchScreen.FINGER,
      keyboardState = KeyboardState.SOFT,
      softButtons = true,
      navigation = Navigation.NONAV,
      released = "October 15, 2020"
    )

    // https://android.googlesource.com/platform/tools/base/+/mirror-goog-studio-master-dev/sdklib/src/main/java/com/android/sdklib/devices/wear.xml
    @JvmField
    public val WEAR_OS_SMALL_ROUND: DeviceConfig = DeviceConfig(
      screenHeight = 384,
      screenWidth = 384,
      xdpi = 320,
      ydpi = 320,
      orientation = ScreenOrientation.PORTRAIT,
      density = Density.XHIGH,
      ratio = ScreenRatio.LONG,
      size = ScreenSize.SMALL,
      keyboard = Keyboard.NOKEY,
      touchScreen = TouchScreen.FINGER,
      keyboardState = KeyboardState.HIDDEN,
      softButtons = false,
      navigation = Navigation.NONAV,
      screenRound = ScreenRound.ROUND,
      released = "June 7, 2014"
    )

    // https://android.googlesource.com/platform/tools/base/+/mirror-goog-studio-master-dev/sdklib/src/main/java/com/android/sdklib/devices/wear.xml
    @JvmField
    public val WEAR_OS_SQUARE: DeviceConfig = DeviceConfig(
      screenHeight = 280,
      screenWidth = 280,
      xdpi = 240,
      ydpi = 240,
      orientation = ScreenOrientation.PORTRAIT,
      density = Density.HIGH,
      ratio = ScreenRatio.LONG,
      size = ScreenSize.SMALL,
      keyboard = Keyboard.NOKEY,
      touchScreen = TouchScreen.FINGER,
      keyboardState = KeyboardState.HIDDEN,
      softButtons = false,
      navigation = Navigation.NONAV,
      screenRound = ScreenRound.NOTROUND,
      released = "June 7, 2014"
    )

    // https://www.techidence.com/galaxy-watch4-features-reviews-and-price/
    @JvmField
    public val GALAXY_WATCH4_CLASSIC_LARGE: DeviceConfig = DeviceConfig(
      screenHeight = 454,
      screenWidth = 454,
      xdpi = 320,
      ydpi = 320,
      orientation = ScreenOrientation.PORTRAIT,
      density = Density.XHIGH,
      ratio = ScreenRatio.NOTLONG,
      size = ScreenSize.SMALL,
      keyboard = Keyboard.NOKEY,
      touchScreen = TouchScreen.FINGER,
      keyboardState = KeyboardState.HIDDEN,
      softButtons = false,
      navigation = Navigation.NONAV,
      screenRound = ScreenRound.ROUND,
      released = "October 15, 2020"
    )

    @JvmField
    public val PIXEL_6: DeviceConfig = DeviceConfig(
      screenHeight = 2400,
      screenWidth = 1080,
      xdpi = 406,
      ydpi = 411,
      orientation = ScreenOrientation.PORTRAIT,
      density = Density.create(420),
      ratio = ScreenRatio.LONG,
      size = ScreenSize.NORMAL,
      keyboard = Keyboard.NOKEY,
      touchScreen = TouchScreen.FINGER,
      keyboardState = KeyboardState.SOFT,
      softButtons = true,
      navigation = Navigation.NONAV,
      released = "October 28, 2021"
    )

    @JvmField
    public val PIXEL_6_PRO: DeviceConfig = DeviceConfig(
      screenHeight = 3120,
      screenWidth = 1440,
      xdpi = 512,
      ydpi = 512,
      orientation = ScreenOrientation.PORTRAIT,
      density = Density.create(560),
      ratio = ScreenRatio.LONG,
      size = ScreenSize.NORMAL,
      keyboard = Keyboard.NOKEY,
      touchScreen = TouchScreen.FINGER,
      keyboardState = KeyboardState.SOFT,
      softButtons = true,
      navigation = Navigation.NONAV,
      released = "October 28, 2021"
    )

    @JvmField
    public val PIXEL_6A: DeviceConfig = DeviceConfig(
      screenHeight = 2400,
      screenWidth = 1080,
      xdpi = 429,
      ydpi = 429,
      orientation = ScreenOrientation.PORTRAIT,
      density = Density.create(420),
      ratio = ScreenRatio.LONG,
      size = ScreenSize.NORMAL,
      keyboard = Keyboard.NOKEY,
      touchScreen = TouchScreen.FINGER,
      keyboardState = KeyboardState.SOFT,
      softButtons = true,
      navigation = Navigation.NONAV,
      released = "May 11, 2022"
    )

    @JvmField
    public val PIXEL_7_PRO: DeviceConfig = DeviceConfig(
      screenHeight = 3120,
      screenWidth = 1440,
      xdpi = 386,
      ydpi = 383,
      orientation = ScreenOrientation.PORTRAIT,
      density = Density.create(560),
      ratio = ScreenRatio.LONG,
      size = ScreenSize.NORMAL,
      keyboard = Keyboard.NOKEY,
      touchScreen = TouchScreen.FINGER,
      keyboardState = KeyboardState.SOFT,
      softButtons = true,
      navigation = Navigation.NONAV,
      released = "October 6, 2022"
    )

    @JvmField
    public val PIXEL_7: DeviceConfig = DeviceConfig(
      screenHeight = 2400,
      screenWidth = 1080,
      xdpi = 416,
      ydpi = 418,
      orientation = ScreenOrientation.PORTRAIT,
      density = Density.create(420),
      ratio = ScreenRatio.LONG,
      size = ScreenSize.NORMAL,
      keyboard = Keyboard.NOKEY,
      touchScreen = TouchScreen.FINGER,
      keyboardState = KeyboardState.SOFT,
      softButtons = true,
      navigation = Navigation.NONAV,
      released = "October 6, 2022"
    )

    @JvmField
    public val PIXEL_FOLD: DeviceConfig = DeviceConfig(
      screenHeight = 2208,
      screenWidth = 1840,
      xdpi = 379,
      ydpi = 380,
      orientation = ScreenOrientation.PORTRAIT,
      density = Density.create(420),
      ratio = ScreenRatio.NOTLONG,
      size = ScreenSize.LARGE,
      keyboard = Keyboard.NOKEY,
      touchScreen = TouchScreen.FINGER,
      keyboardState = KeyboardState.SOFT,
      softButtons = true,
      navigation = Navigation.NONAV,
      released = "May 10, 2023"
    )

    @JvmField
    public val PIXEL_TABLET: DeviceConfig = DeviceConfig(
      screenHeight = 2560,
      screenWidth = 1600,
      xdpi = 276,
      ydpi = 276,
      orientation = ScreenOrientation.LANDSCAPE,
      density = Density.XHIGH,
      ratio = ScreenRatio.NOTLONG,
      size = ScreenSize.LARGE,
      keyboard = Keyboard.NOKEY,
      touchScreen = TouchScreen.FINGER,
      keyboardState = KeyboardState.SOFT,
      softButtons = true,
      navigation = Navigation.NONAV,
      released = "June 20, 2023"
    )

    @JvmField
    public val PIXEL_7A: DeviceConfig = DeviceConfig(
      screenHeight = 2400,
      screenWidth = 1080,
      xdpi = 440,
      ydpi = 440,
      orientation = ScreenOrientation.PORTRAIT,
      density = Density.create(420),
      ratio = ScreenRatio.LONG,
      size = ScreenSize.NORMAL,
      keyboard = Keyboard.NOKEY,
      touchScreen = TouchScreen.FINGER,
      keyboardState = KeyboardState.SOFT,
      softButtons = true,
      navigation = Navigation.NONAV,
      released = "May 10, 2023"
    )

    @JvmField
    public val PIXEL_8: DeviceConfig = DeviceConfig(
      screenHeight = 2400,
      screenWidth = 1080,
      xdpi = 429,
      ydpi = 427,
      orientation = ScreenOrientation.PORTRAIT,
      density = Density.create(420),
      ratio = ScreenRatio.LONG,
      size = ScreenSize.NORMAL,
      keyboard = Keyboard.NOKEY,
      touchScreen = TouchScreen.FINGER,
      keyboardState = KeyboardState.SOFT,
      softButtons = true,
      navigation = Navigation.NONAV,
      released = "October 12, 2023"
    )

    @JvmField
    public val PIXEL_8_PRO: DeviceConfig = DeviceConfig(
      screenHeight = 2992,
      screenWidth = 1344,
      xdpi = 488,
      ydpi = 491,
      orientation = ScreenOrientation.PORTRAIT,
      density = Density.XXHIGH,
      ratio = ScreenRatio.LONG,
      size = ScreenSize.NORMAL,
      keyboard = Keyboard.NOKEY,
      touchScreen = TouchScreen.FINGER,
      keyboardState = KeyboardState.SOFT,
      softButtons = true,
      navigation = Navigation.NONAV,
      released = "October 12, 2023"
    )

    @JvmField
    public val PIXEL_8A: DeviceConfig = DeviceConfig(
      screenHeight = 2400,
      screenWidth = 1080,
      xdpi = 440,
      ydpi = 440,
      orientation = ScreenOrientation.PORTRAIT,
      density = Density.create(420),
      ratio = ScreenRatio.LONG,
      size = ScreenSize.NORMAL,
      keyboard = Keyboard.NOKEY,
      touchScreen = TouchScreen.FINGER,
      keyboardState = KeyboardState.SOFT,
      softButtons = true,
      navigation = Navigation.NONAV,
      released = "May 7, 2024"
    )

    @JvmField
    public val PIXEL_9: DeviceConfig = DeviceConfig(
      screenHeight = 2424,
      screenWidth = 1080,
      xdpi = 428,
      ydpi = 424,
      orientation = ScreenOrientation.PORTRAIT,
      density = Density.create(420),
      ratio = ScreenRatio.LONG,
      size = ScreenSize.NORMAL,
      keyboard = Keyboard.NOKEY,
      touchScreen = TouchScreen.FINGER,
      keyboardState = KeyboardState.SOFT,
      softButtons = true,
      navigation = Navigation.NONAV,
      released = "August 13, 2024"
    )

    @JvmField
    public val PIXEL_9A: DeviceConfig = DeviceConfig(
      screenHeight = 2424,
      screenWidth = 1080,
      xdpi = 428,
      ydpi = 424,
      orientation = ScreenOrientation.PORTRAIT,
      density = Density.create(420),
      ratio = ScreenRatio.LONG,
      size = ScreenSize.NORMAL,
      keyboard = Keyboard.NOKEY,
      touchScreen = TouchScreen.FINGER,
      keyboardState = KeyboardState.SOFT,
      softButtons = true,
      navigation = Navigation.NONAV,
      released = "March 19, 2025"
    )

    @JvmField
    public val PIXEL_9_PRO: DeviceConfig = DeviceConfig(
      screenHeight = 2856,
      screenWidth = 1280,
      xdpi = 494,
      ydpi = 497,
      orientation = ScreenOrientation.PORTRAIT,
      density = Density.XXHIGH,
      ratio = ScreenRatio.LONG,
      size = ScreenSize.NORMAL,
      keyboard = Keyboard.NOKEY,
      touchScreen = TouchScreen.FINGER,
      keyboardState = KeyboardState.SOFT,
      softButtons = true,
      navigation = Navigation.NONAV,
      released = "August 13, 2024"
    )

    @JvmField
    public val PIXEL_9_PRO_XL: DeviceConfig = DeviceConfig(
      screenHeight = 2992,
      screenWidth = 1344,
      xdpi = 482,
      ydpi = 481,
      orientation = ScreenOrientation.PORTRAIT,
      density = Density.XXHIGH,
      ratio = ScreenRatio.LONG,
      size = ScreenSize.NORMAL,
      keyboard = Keyboard.NOKEY,
      touchScreen = TouchScreen.FINGER,
      keyboardState = KeyboardState.SOFT,
      softButtons = true,
      navigation = Navigation.NONAV,
      released = "August 13, 2024"
    )

    @JvmField
    public val PIXEL_9_PRO_FOLD: DeviceConfig = DeviceConfig(
      screenHeight = 2152,
      screenWidth = 2076,
      xdpi = 379,
      ydpi = 380,
      orientation = ScreenOrientation.PORTRAIT,
      density = Density.create(390),
      ratio = ScreenRatio.NOTLONG,
      size = ScreenSize.LARGE,
      keyboard = Keyboard.NOKEY,
      touchScreen = TouchScreen.FINGER,
      keyboardState = KeyboardState.SOFT,
      softButtons = true,
      navigation = Navigation.NONAV,
      released = "September 4, 2024"
    )

    private const val TAG_ATTR = "attr"
    private const val TAG_ENUM = "enum"
    private const val TAG_FLAG = "flag"
    private const val ATTR_NAME = "name"
    private const val ATTR_VALUE = "value"

    @Throws(IOException::class)
    internal fun loadProperties(path: File): Map<String, String> {
      val p = Properties()
      val map = Maps.newHashMap<String, String>()
      p.load(FileInputStream(path))
      for (key in p.stringPropertyNames()) {
        map[key] = p.getProperty(key)
      }
      return map
    }

    @Throws(IOException::class, XmlPullParserException::class)
    internal fun getEnumMap(path: File): Map<String, Map<String, Int>> {
      val map = mutableMapOf<String, MutableMap<String, Int>>()

      val xmlPullParser = XmlPullParserFactory.newInstance()
        .newPullParser()
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

      return map
    }
  }
}
