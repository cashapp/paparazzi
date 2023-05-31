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
