Releasing
========

 1. Change the version in `gradle.properties` to a non-SNAPSHOT version.
 2. Update the `CHANGELOG.md` for the impending release.
 3. Update the `README.md` with the new version.
 4. `git commit -am "Prepare version X.Y.Z"` (where X.Y.Z is the new version)
 5. `./gradlew -p paparazzi clean publishMavenPublicationToMavenCentralRepository paparazzi-gradle-plugin:publishPluginMavenPublicationToMavenCentralRepository paparazzi-gradle-plugin:publishPaparazziPluginMarkerMavenPublicationToMavenCentralRepository --no-parallel`
 6. Visit [Sonatype Nexus](https://oss.sonatype.org/) and promote the artifacts.
 7. `git tag -a X.Y.Z -m "X.Y.Z"` (where X.Y.Z is the new version)
 8. Update the `gradle.properties` to the next SNAPSHOT version.
 9. `git commit -am "Prepare next development version"`
 10. `git push && git push --tags`
 11. Update the sample app to the release version and send a PR.


If step 5 or 6 fails, drop the Sonatype repo, fix the problem, commit, and start again at step 4.


Prerequisites
-------------

In `~/.gradle/gradle.properties`, set the following:

 * `mavenCentralUsername` - Sonatype username for releasing to `app.cash`.
 * `mavenCentralPassword` - Sonatype password for releasing to `app.cash`.
