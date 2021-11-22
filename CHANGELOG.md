Change Log
==========

## Version 0.9.0
_2021-11-22_

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


## Version 0.8.0
_2021-10-07_

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

## Version 0.7.1
_2021-05-17_

### New
* [Gradle Plugin] Support the --tests option for record/verify tasks

### Fixed
* [Gradle Plugin] Defer task configuration until created

## Version 0.7.0
_2021-02-26_

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

## Version 0.6.0
_2020-10-02_

As of this release, consumers must build on Java 11 environments.

### New
* Point to a more recent version of layoutlib that runs on Android Q and builds with Java 11.
* Refactor Paparazzi to better support non-Gradle builds
* Added device configs for Pixel 4 series

## Version 0.5.2
_2020-09-17_

### Fixed
* [Gradle Plugin] Fixed record and verify tasks in multi-module projects.

## Version 0.5.1
_2020-09-17_

### Fixed
* [Gradle Plugin] Fixed race condition in record and verify tasks.

## Version 0.5.0
_2020-09-16_

* Initial release.
