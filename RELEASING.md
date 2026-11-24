# Releasing

 1. Update `VERSION_NAME` in `gradle.properties` to the release (non-SNAPSHOT) version.
 2. Update `CHANGELOG.md` for the impending release.
    1. Change the `Unreleased` header to the version, appending today's date
    2. Add a new `Unreleased` section to the top.
    3. Add a link URL at the bottom to ensure the impending release header link works.
    4. Update the `Unreleased` link URL to compare this new version...HEAD
 3. Update `README.md` with the new version.
 4. `git commit -am "Prepare version X.Y.Z"` (where X.Y.Z is the new version)
 5. `git tag -a X.Y.Z -m "X.Y.Z"` (where X.Y.Z is the new version)
 6. Update `VERSION_NAME` in `gradle.properties` to the next SNAPSHOT version.
 7. `git commit -am "Prepare next development version"`
 8. `git push && git push --tags`

This will trigger a GitHub Action workflow which will create a GitHub release and upload the release artifacts to Maven Central.

## Internal Releasing

1. Update `VERSION_NAME` in `gradle.properties` to the internal release (non-SNAPSHOT) version. [2.0.0-internal01] Ensure that the name doesn't collide with an already released version.
2. Update `RELEASE_SIGNING_ENABLED` in `gradle.properties` to `false`.
3. Check that the internal variables are configured correctly:
   1. `internalUrl` is set in `~/.gradle/gradle.properties` to the internal repository URL.
   2. Check `internalUsername` and `internalPassword` are set in `~/.gradle/gradle.properties` to the internal repository credentials.
4. Run `./gradlew publishMavenPublicationToInternalRepository paparazzi-gradle-plugin:publishAllPublicationsToInternalRepository --no-parallel` to publish the internal release.
   * *Note* if gradle publish fails with `403` error, ensure the `VERSION_NAME` in step 1 is unique and isn't already published.
