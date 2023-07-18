# Change Log

## [Unreleased]

## [1.3.1] - 2023-07-18

### New
* Migrated to new resource and asset loading mechanisms.  To explicitly opt-out and fall back to the
legacy mechanisms, add either/both of the following to your `gradle.properties`:
```
app.cash.paparazzi.legacy.resource.loading=true
app.cash.paparazzi.legacy.asset.loading=true
```

* The Android system ui (status + navigation bar) is now hidden by default; to re-enable:
```
  @get:Rule
  val paparazzi = Paparazzi(
    showSystemUi = true
  )
```

* Relocate failure deltas from `PROJECT_ROOT/out/failures/` to `BUILD_DIR/paparazzi/failures/`
* Support for application and dynamic feature modules
* [Gradle Plugin] Gradle 8.2.1

### Fixed
* Fix accessibility labels when mergeDescendants is true
* Fixes compose alert dialogs not rendering when using RenderingMode.SHRINK

Kudos to @kevinzheng-ap, @adamalyyan and others for contributions this release!

## [1.3.0] - 2023-05-31

As of this release, consumers must build on Java 17+ environments.

### New
* Migrate Paparazzi to layoutlib Flamingo 2022.2.1
* Add accessibility support for Composables
* Add layout accessibility check support
* Compose 1.4.7
* Kotlin 1.8.21
* [Gradle Plugin] Gradle 8.1.1
* [Gradle Plugin] Android Gradle Plugin 8.0.2

### Fixed
* Configure android.os.Build values via reflection
* Various bug fixes with AccessibilityRenderExtension
* Make sure changes to system properties actually affect test tasks
* Fix caching bug with preparePaparazziResources task
* Use Dispatchers.Main for delay functionality
* Recomposition does not happen unless lifecycle is RESUMED
* Fix NPE when unit test variant is disabled
* Fix incompatibility with androidx.savedstate:1.1.0

Kudos to @gamepro65, @geoff-powell, @TWiStErRob, @adamalyyan and others for contributions this release!

## [1.2.0] - 2023-01-18

### New
* Migrate Paparazzi to layoutlib Electric Eel 2022.1.1
* Add support for RenderingMode.SHRINK to allow view-only screenshots
* Expose flag to show/hide system ui
* Register a default OnBackPressedDispatcherOwner if its present in classpath
* Bump default compileSdk to API 33
* Compose 1.3.1
* Kotlin 1.7.20
* [Gradle Plugin] Gradle 7.6
* [Gradle Plugin] Android Gradle Plugin 7.4.0

### Fixed
* Flush errors on unsafeUpdateConfig
* Only apply wear circle shape to full device screenshots
* Synchronize access to Handler_Delegate.queue
* Apply compose hooks to all snapshot calls
* Register LifecycleOwner and SavedStateRegistryOwner to all views
* Execute Handler callbacks after snapshots to clean up Compose references
* Fix RecyclerView issue due to layoutlib Dolphin update
* Keep AGP and tools dependencies aligned

Kudos to @gamepro65, @saket, @rharter and others for contributions this release!

## [1.1.0] - 2022-10-12

### New
* Migrate Paparazzi to layoutlib Chipmunk 2021.2.1
* Add support for multiplatform plugin
* Add support for JDKs 16+
* Add support for locales and layout direction (LTR/RTL)
* Add Pixel 6 & Pixel 6 Pro device configs
* Enable night mode for legacy views and composables
* Enable ui mode to support form factors other than phones/tablets, e.g., auto, watches, etc.
* Google Wear DeviceConfig support
* Expose an API for offsetting frame capture time
* Add InstantAnimationsRule to delay snapshot capture until the last frame.
* Compose 1.3.0
* Kotlin 1.7.10
* [Gradle Plugin] Gradle 7.5.1

### Fixed
* Generate resource ids to support aapt inline resources in composables
* Reset AndroidUiDispatcher between compose snapshots
* Fix OOM error when a large number of compose snapshots are verified
* Fix HTML report in development mode
* Honor customization of Gradle's build output directory
* [Gradle Plugin] Configure native platform transformed path directly in test task to reduce cache misses
* [Gradle Plugin] Fix accidental eager task creation reducing memory pressure
* [Gradle Plugin] Fail explicitly when applying Android application plugin

Kudos to @chris-horner, @swankjesse, @yschimke, @dniHze, @TWiStErRob, @gamepro65, @liutikas and others for contributions this release!

## [1.0.0] - 2022-06-03

### New
* Support for Composable snapshots
* Migrate Paparazzi to layoutlib Bumblebee 2021.1.1 for better rendering and API 31 support
* Update Paparazzi configuration via new `unsafeUpdateConfig` method instead of `snapshot`/`gif`
* Cache Paparazzi bootstrap logic for better performance per test suite
* Surface internally thrown exceptions from layoutlib
* Throw a more helpful exception if Android platform is missing
* Bump default compileSdk to API 31
* Compose 1.1.1
* Kotlin 1.6.10
* [Gradle Plugin] Gradle 7.4.2
* [Gradle Plugin] Android Gradle Plugin 7.1.2

### Fixed
* Prepend paths with file:// for clickable error output in IDE
* Update default SDK path on Linux
* Fix accessibility test logic to avoid unnecessary coloring changes on updated view ids
* Fixes crash when using InputMethodManager to show/hide keyboard
* Temporarily work around Compose runtime memory leaks
* [Gradle Plugin] Prefer namespace DSL declaration over manifest package declaration
* [Gradle Plugin] Publish plugin marker on snapshot builds
* [Gradle Plugin] Exclude androidTest sourceSets during paparazzi runs

Kudos to @luis-cortes, @nak5ive, @alexvanyo, @gamepro65 and others for contributions this release!

## [0.9.3] - 2022-01-20

### Fixed
* Load the correct mac arm artifact on M1 machines
* Generate fake View.id for consistent colors for accessibility entries when views are modified

Kudos to @geoff-powell, @nicbell for their contributions this release!


## [0.9.2] - 2022-01-20

Please ignore this release


## [0.9.1] - 2022-01-14

### Fixed
* Download mac arm artifact if on M1 machines
* Support for assets from transitive dependencies
* Add fix for ClassNotFoundException when using nonTransitiveRClass
* Update RELEASING notes to publish plugin marker artifact
* Avoid NPE in AccessibilityRenderExtension when layout params are not supplied
* Use View.id to generate consistent colors for accessibility entries when views are modified

Kudos to @luis-cortes, @geoff-powell, @autonomousapps and @LuK1709 for their contributions this release!


## [0.9.0] - 2021-11-22

### New
* Migrate Paparazzi to layoutlib Arctic Fox 2020.3.1, providing native support for M1 machines
* Migrate Paparazzi to layoutlib 4.2, unlocking future Compose support
* Add support for projects enabling non-transitive resources
* RenderExtension now visits view hierarchy pre-rendering instead of layering bitmaps post-rendering
* Fail-fast when Bridge.init fails, usually due to native crash
* Expose RenderingMode as a configurable option
* Bump default compileSdk to API 30
* Improve Java-interoperability experience
* Kotlin 1.5.31

### Fixed
* Don't require Android plugin to be declared before Paparazzi plugin
* Clear AnimationHandler leak after each snapshot
* Don't generate empty mov files on snapshot failure
* Add Kotlin platform bom to prevent classpath conflicts during test builds
* Use correct default Android SDK path on Windows
* Use platform-agnostic file paths in Gradle artifacts to support remote caching across platforms
* Use platform-agnostic file paths in Javascript for web page support on Windows
* Fix font scaling issue in AccessibilityRenderExtension by using bundled font

Kudos to @luis-cortes, @geoff-powell and @TWiStErRob for their contributions this release!


## [0.8.0] - 2021-10-07

### New
* Migrate Paparazzi to use native layoutlib for better rendering and API 30 support
* Add new extension for rendering accessibility metadata
* Add support for fontScale in DeviceConfig
* Add device config for Pixel 5
* Add tasks to Gradle task verification group
* Migrate publishing to gradle-maven-publish-plugin
* Migrate builds to Github Actions
* Migrate sample test from Burst to TestParameterInjector
* Kotlin 1.5.21
* [Gradle Plugin] Support for configuration caching
* [Gradle Plugin] Gradle 7.2

### Fixed
* Add method interceptor for matrix multiplication operations
* Don't swallow FileNotFoundExceptions when overridden platform dir doesn't exist
* [Gradle Plugin] Fix remote caching bug by referencing relative, not absolute, paths in intermediate resources file.

## [0.7.1] - 2021-05-17

### New
* [Gradle Plugin] Support the --tests option for record/verify tasks

### Fixed
* [Gradle Plugin] Defer task configuration until created

## [0.7.0] - 2021-02-26

### New
* Kotlin 1.4.30
* Add support for inline complex XML resources
* Enable [Burst](https://github.com/square/burst) support
* Expose maximum percentage difference in image verification as a setting
* Render extension api to add extra information to snapshots
* Allow selection of night mode in DeviceConfig
* [Gradle Plugin] Gradle 6.8.3
* [Gradle Plugin] Creating an umbrella task to execute on all variants

### Fixed
* Properly execute Choreographer.doFrame after view has been laid out
* Fix broken text appearances when style resource names contain periods
* Fix ability to access asset files
* Use target-sdk to simulate device when available
* Always write screenshots to disk in record mode
* Don't crash when running on Java 12+
* [Gradle Plugin] Force test re-runs when a resource or asset has changed
* [Gradle Plugin] Force test re-runs if generated report or snapshot dirs are deleted

## [0.6.0] - 2020-10-02

As of this release, consumers must build on Java 11 environments.

### New
* Point to a more recent version of layoutlib that runs on Android Q and builds with Java 11.
* Refactor Paparazzi to better support non-Gradle builds
* Added device configs for Pixel 4 series

## [0.5.2] - 2020-09-17

### Fixed
* [Gradle Plugin] Fixed record and verify tasks in multi-module projects.

## [0.5.1] - 2020-09-17

### Fixed
* [Gradle Plugin] Fixed race condition in record and verify tasks.

## [0.5.0] - 2020-09-16

* Initial release.



[Unreleased]: https://github.com/cashapp/paparazzi/compare/1.3.1...HEAD
[1.3.1]: https://github.com/cashapp/paparazzi/releases/tag/1.3.1
[1.3.0]: https://github.com/cashapp/paparazzi/releases/tag/1.3.0
[1.2.0]: https://github.com/cashapp/paparazzi/releases/tag/1.2.0
[1.1.0]: https://github.com/cashapp/paparazzi/releases/tag/1.1.0
[1.0.0]: https://github.com/cashapp/paparazzi/releases/tag/1.0.0
[0.9.3]: https://github.com/cashapp/paparazzi/releases/tag/0.9.3
[0.9.2]: https://github.com/cashapp/paparazzi/releases/tag/0.9.2
[0.9.1]: https://github.com/cashapp/paparazzi/releases/tag/0.9.1
[0.9.0]: https://github.com/cashapp/paparazzi/releases/tag/0.9.0
[0.8.0]: https://github.com/cashapp/paparazzi/releases/tag/0.8.0
[0.7.1]: https://github.com/cashapp/paparazzi/releases/tag/0.7.1
[0.7.0]: https://github.com/cashapp/paparazzi/releases/tag/0.7.0
[0.6.0]: https://github.com/cashapp/paparazzi/releases/tag/0.6.0
[0.5.2]: https://github.com/cashapp/paparazzi/releases/tag/0.5.2
[0.5.1]: https://github.com/cashapp/paparazzi/releases/tag/0.5.1
[0.5.0]: https://github.com/cashapp/paparazzi/releases/tag/0.5.0
