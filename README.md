Paparazzi
========

An Android library to render your application screens without a physical device or emulator.

See the [project website][paparazzi] for documentation and APIs.

Jetifier
--------

If using Jetifier to migrate off Support libraries, add the following to your `gradle.properties` to 
exclude bundled Android dependencies.

```groovy
android.jetifier.ignorelist=android-base-common,common
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
    classpath 'app.cash.paparazzi:paparazzi-gradle-plugin:0.7.1'
  }
}

apply plugin: 'app.cash.paparazzi'
```

Using the plugins DSL:
```groovy
plugins {
  id 'app.cash.paparazzi' version '0.7.1'
}
```

Snapshots of the development version are available in [Sonatype's `snapshots` repository][snap].

```groovy
repositories {
  mavenCentral()
  maven {
    url 'https://oss.sonatype.org/content/repositories/snapshots/'
  }
}
```

Tasks
-------
```
$ ./gradlew some-project:testDebug
```

Runs tests and generates an HTML report at `some-project/build/reports/paparazzi/debug/` showing all 
test runs and snapshots. 

```
$ ./gradlew some-project:recordPaparazziDebug
```

Saves snapshots as golden values to a predefined source-controlled location 
(default: `src/test/snapshots`).

```
$ ./gradlew some-project:verifyPaparazziDebug
```

Runs tests and verifies against previously-recorded golden values.

Check out the [sample][sample].

Known Limitations
-------

#### Resource Resolution Errors
Ex: 
```
java.lang.NullPointerException
  at com.android.ide.common.resources.ResourceResolver.getUnresolvedResource(ResourceResolver.java:421)
```
Paparazzi does not currently support namespaced resources. If you are seeing this error, verify that the following flag is 
not present in the project's `gradle.properties`:
`android.nonTransitiveRClass=true`

-------- 

#### Running Tests from the IDE
Ex:
```
java.lang.NullPointerException
  at java.base/java.io.File.<init>(File.java:278)
  at app.cash.paparazzi.EnvironmentKt.detectEnvironment(Environment.kt:36)
```
Running tests from the IDE requires Android Studio Arctic Fox or later. 

-------- 

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

 [changelog]: https://cashapp.github.io/paparazzi/changelog/
 [paparazzi]: https://cashapp.github.io/paparazzi/
 [sample]: https://github.com/cashapp/paparazzi/tree/master/sample
 [snap]: https://oss.sonatype.org/content/repositories/snapshots/app/cash/paparazzi/
