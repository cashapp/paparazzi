Paparazzi
========

An Android library to render your application screens without a physical device or emulator.

```kotlin
class LaunchViewTest {
  @get:Rule
  val paparazzi = Paparazzi(
    deviceConfig = PIXEL_5,
    theme = "android:Theme.Material.Light.NoActionBar"
    // ...see docs for more options
  )

  @Test
  fun launchView() {
    val view = paparazzi.inflate<LaunchView>(R.layout.launch)
    // or...
    // val view = LaunchView(paparazzi.context)

    view.setModel(LaunchModel(title = "paparazzi"))
    paparazzi.snapshot(view)
  }

  @Test
  fun launchComposable() {
    paparazzi.snapshot {
      MyComposable()
    }
  }
}
```

See the [project website][paparazzi] for documentation and APIs.

Tasks
-------

```bash
./gradlew :sample:testDebug
```

Runs tests and generates an HTML report at `sample/build/reports/paparazzi/` showing all
test runs and snapshots.

```bash
./gradlew :sample:recordPaparazziDebug
```

Saves snapshots as golden values to a predefined source-controlled location
(defaults to `src/test/snapshots`).

```bash
./gradlew :sample:verifyPaparazziDebug
```

Runs tests and verifies against previously-recorded golden values. Failures generate diffs at `sample/build/paparazzi/failures`.

For more examples, check out the [sample][sample] project.

Git LFS
--------
It is recommended you use [Git LFS][lfs] to store your snapshots.  Here's a quick setup:

```bash
brew install git-lfs
git config core.hooksPath  # optional, confirm where your git hooks will be installed
git lfs install --local
git lfs track "**/snapshots/**/*.png"
git add .gitattributes
# Optional to improve git checkout performance
git config lfs.setlockablereadonly false
```

On CI, you might set up something like:

`$HOOKS_DIR/pre-receive`
```bash
# compares files that match .gitattributes filter to those actually tracked by git-lfs
diff <(git ls-files ':(attr:filter=lfs)' | sort) <(git lfs ls-files -n | sort) >/dev/null

ret=$?
if [[ $ret -ne 0 ]]; then
  echo >&2 "This remote has detected files committed without using Git LFS. Run 'brew install git-lfs && git lfs install' to install it and re-commit your files.";
  exit 1;
fi
```

`your_build_script.sh`
```bash
if [[ is running snapshot tests ]]; then
  # fail fast if files not checked in using git lfs
  "$HOOKS_DIR"/pre-receive
  git lfs install --local
  git lfs pull
fi
```

Jetifier
--------

If using Jetifier to migrate off Support libraries, add the following to your `gradle.properties` to
exclude bundled Android dependencies.

```properties
android.jetifier.ignorelist=android-base-common,common
```

Lottie
--------

When taking screenshots of Lottie animations, you need to force Lottie to not run on a background thread, otherwise Paparazzi can throw exceptions [#494](https://github.com/cashapp/paparazzi/issues/494), [#630](https://github.com/cashapp/paparazzi/issues/630).

```kotlin
@Before
fun setup() {
    LottieTask.EXECUTOR = Executor(Runnable::run)
}
```

LocalInspectionMode
--------
Some Composables -- such as `GoogleMap()` -- check for `LocalInspectionMode` to short-circuit to a `@Preview`-safe Composable.

However, Paparazzi does not set `LocalInspectionMode` globally to ensure that the snapshot represents the true production output, similar to how it overrides `View.isInEditMode` for legacy views.

As a workaround, we recommend wrapping such a Composable in a custom Composable with a `CompositionLocalProvider` and setting `LocalInspectionMode` there.

```kotlin
 @Test
  fun inspectionModeView() {
    paparazzi.snapshot(
      CompositionLocalProvider(LocalInspectionMode provides true) {
        YourComposable()
      }
    )
  }
```

Releases
--------

Our [change log][changelog] has release history.

Using plugin application:
```groovy
buildscript {
  repositories {
    mavenCentral()
    google()
  }
  dependencies {
    classpath 'app.cash.paparazzi:paparazzi-gradle-plugin:2.0.0-alpha04'
  }
}

apply plugin: 'app.cash.paparazzi'
```

Using the plugins DSL:
```groovy
plugins {
  id 'app.cash.paparazzi' version '2.0.0-alpha04'
}
```

Snapshots of the development version are available in [the Central Portal Snapshots repository][snap].

```groovy
repositories {
  // ...
  maven {
    url 'https://central.sonatype.com/repository/maven-snapshots/'
  }
}
```

License
-------

```
Copyright 2019 Square, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

 [paparazzi]: https://cashapp.github.io/paparazzi/
 [sample]: https://github.com/cashapp/paparazzi/tree/master/sample
 [lfs]: https://git-lfs.github.com/
 [changelog]: https://cashapp.github.io/paparazzi/changelog/
 [snap]: https://central.sonatype.com/service/rest/repository/browse/maven-snapshots/app/cash/paparazzi/
