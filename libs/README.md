Paparazzi Libraries
-------------------

This project publishes artifacts from [Android Studio's][android_studio] UI renderer to Maven
Central so that we can consume them in Paparazzi.

Note that layoutlib's version tracks [Android Studio Releases][studio_releases] and not Paparazzi.

1. Find the corresponding [Android Studio version][studio_versions] you wish to update LayoutLib to.
   ```
   Flamingo => 2022.2.1
   ```
2. Find and click the [tag][prebuilt_refs] for the prebuilt corresponding to that version.
3. Copy the commit short sha from the resulting page
   ```
   # Example: https://android.googlesource.com/platform/prebuilts/studio/layoutlib/+/refs/tags/studio-2022.2.1

   512837137ea60b9b86836cab7169fec5c635f422 => 5128371
   ```
4. Update commit link, `layoutlib` and `layoutlibPrebuiltSha` in `libs.versions.toml` as expected.
   ```
   https://android.googlesource.com/platform/prebuilts/studio/layoutlib/+/5128371
   ...
   layoutlib = "2022.2.1-5128371"
   layoutlibPrebuiltSha = "5128371"
   ```
5. Build and upload:
    ```
    ./gradlew publishMavenNativeLibraryPublicationToMavenCentralRepository
    ```

   This may take a few minutes. It clones a large repo (2.4 GiB) and then uploads a large artifact
   (30 MiB) to Maven Central.

6. Visit [Sonatype Nexus][nexus] to promote the artifact. Or drop it if there is a problem!
7. Once deploy is live, continue with changeset from step 4 to update Paparazzi to consume this
   latest version.  Here's an [example PR][dolphin_bump].


Prerequisites
-------------

In `~/.gradle/gradle.properties`, set the following:

 * `mavenCentralUsername` - Sonatype username for releasing to `app.cash`.
 * `mavenCentralPassword` - Sonatype password for releasing to `app.cash`.


[android_studio]: https://developer.android.com/studio
[studio_releases]: https://developer.android.com/studio/releases
[studio_versions]: https://developer.android.com/studio/releases#android_gradle_plugin_and_android_studio_compatibility
[nexus]: https://oss.sonatype.org/
[prebuilt_refs]: https://android.googlesource.com/platform/prebuilts/studio/layoutlib/+refs
[dolphin_bump]: https://github.com/cashapp/paparazzi/pull/639
