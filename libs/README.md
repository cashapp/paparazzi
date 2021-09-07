Paparazzi Libraries
-------------------

This project publishes artifacts from [Android Studio's][android_studio] UI renderer to Maven
Central so that we can consume them in Paparazzi.

Note that layoutlib's version tracks [Android Studio Releases][studio_releases] and not Paparazzi.

1. Build and upload:

    ```
    ./gradlew publishMavenNativeLibraryPublicationToMavenCentralRepository
    ```

   This may take a few minutes. It clones a large repo (2.4 GiB) and then uploads a large artifact
   (30 MiB) to Maven Central.


2. Visit [Sonatype Nexus][nexus] to promote the artifact. Or drop it if there is a problem!


Prerequisites
-------------

In `~/.gradle/gradle.properties`, set the following:

 * `mavenCentralUsername` - Sonatype username for releasing to `app.cash`.
 * `mavenCentralPassword` - Sonatype password for releasing to `app.cash`.


[android_studio]: https://developer.android.com/studio
[studio_releases]: https://developer.android.com/studio/releases
[nexus]: https://oss.sonatype.org/