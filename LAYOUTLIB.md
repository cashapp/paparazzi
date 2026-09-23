# Layoutlib versions

Paparazzi renders with Android's layoutlib. It is built against one default version
(`libs.versions.layoutlib` in `gradle/libs.versions.toml`), and users can render with another:

```groovy
paparazzi {
  layoutlibVersion = "17.0.3" // or -Papp.cash.paparazzi.layoutlibVersion=17.0.3
}
```

Every version Paparazzi supports is recorded in
[`gradle/layoutlib-compat.properties`](gradle/layoutlib-compat.properties). That file is the only
source of truth:

| Consumer | Uses |
|---|---|
| Gradle plugin (`LayoutlibVersions`, generated) | minimum `layoutlib-api` per version, "not verified" warning |
| Runtime (`LayoutlibCompat`, bundled resource) | ICU data file per version, default version |
| `LayoutlibCompatibilityTest` | the versions to render against |
| Build | fails if the default version isn't recorded |

Behavioral differences between versions (APIs removed or changed in layoutlib internals) are handled
in `paparazzi/src/main/java/app/cash/paparazzi/internal/LayoutlibCompat.kt`.

## Verifying a version

```shell
# 1. Static checks; --write records (or refreshes) the entry.
./gradlew verifyLayoutlibVersion --layoutlib-version=17.0.3 --write

# 2. Render (static, SHRINK and gif snapshots) against it.
./gradlew :paparazzi-gradle-plugin:test \
  --tests app.cash.paparazzi.gradle.LayoutlibCompatibilityTest \
  -Ppaparazzi.layoutlib.versionsUnderTest=17.0.3
```

`verifyLayoutlibVersion` (implemented in `build-logic/layoutlib-verifier`) checks, in order:

| Step | Check |
|---|---|
| artifacts | `layoutlib`, `layoutlib-resources` and `layoutlib-runtime` for linux, win, mac and mac-arm exist |
| icu | exactly one `icudt*l.dat` in `layoutlib-runtime/data/icu`, identical across classifiers |
| api | minimum `layoutlib-api` satisfying every API class/method/field the layoutlib jar references |
| linkage | every layoutlib class/member Paparazzi references *directly* still exists (reflective access via `LayoutlibCompat` isn't covered) |
| resources | `res/` resource types and value tags match the default version and `values/attrs.xml` exists; new types or tags fail and need review before adding a resource hook |

Without `--write`, an already-recorded version must match what's computed, so CI catches stale entries
(for example after bumping the pinned `layoutlib-api`).

Rendering is a separate step on purpose: static checks can't see behavioral changes. For example,
17.0.3 dropped `BridgeRenderSession.getImage()` while the `layoutlib-api` default still exists, so it
only failed when rendered.

Other tasks:

- `./gradlew listUnverifiedLayoutlibVersions [--floor=16.0.1]` lists releases not yet recorded
  (JSON in `build/layoutlib-verifier/unverified.json`).
- `./gradlew mergeLayoutlibEntries --entries-dir=DIR` merges entry fragments, for example CI artifacts.
- `./gradlew checkLayoutlibVerifier` runs the verifier's unit tests.

By default `LayoutlibCompatibilityTest` renders only the default version, so `./gradlew check` stays
fast. Use `-Ppaparazzi.layoutlib.versionsUnderTest=all` to render every recorded version.

## CI

| Workflow | Trigger | Does |
|---|---|---|
| `build` | every push/PR | `check`, which renders the default layoutlib on each OS/JDK |
| `layoutlib-compat` | PRs/pushes touching layoutlib-related paths, weekly, manual | for every recorded version: static verification (drift check) + render; verifier unit tests |
| `layoutlib-new-versions` | daily, manual | finds unrecorded releases ≥ 16.0.1, verifies and renders each, opens one PR recording the passing ones and an issue per failing one |

`layoutlib-new-versions` needs `contents`, `pull-requests` and `issues` write permissions. PRs opened
with the default `GITHUB_TOKEN` don't trigger other workflows, so set a `LAYOUTLIB_BOT_TOKEN` secret (a
fine-grained PAT or GitHub App token) if the PR should run `build` and `layoutlib-compat`.

## Updating the default version

1. `layoutlib-new-versions` records the new release in `layoutlib-compat.properties`, usually before
   Renovate's bump arrives. Otherwise run `verifyLayoutlibVersion --write` yourself.
2. Bump `libs.versions.layoutlib`. The build fails until the version is recorded.
3. Re-run `verifyLayoutlibVersion` for the recorded versions. The linkage and resources steps compare
   against the *default* version, so a new default can surface differences.

---

## Spec: Renovate rule for layoutlib

Status: proposed, not yet applied to `renovate.json5`.

### Goal

When Renovate proposes a new default layoutlib, the PR must also carry a verified entry in
`gradle/layoutlib-compat.properties`. Without one the build fails by design, because the default must
be recorded.

### Constraints

- `layoutlib`, `layoutlib-resources` and `layoutlib-runtime` share the `layoutlib` version ref in
  `libs.versions.toml`, so Renovate proposes them as one update. `layoutlib-api` is pinned separately.
- Renovate's `postUpgradeTasks` could run the verifier inside the Renovate PR, but only self-hosted
  Renovate supports it (with `allowedPostUpgradeCommands`). The hosted Mend Renovate app doesn't run
  arbitrary commands. The spec therefore uses a GitHub workflow keyed on a label, and notes
  `postUpgradeTasks` as the self-hosted alternative.
- Qualified releases such as `16.1.0-jdk17` are recorded as compatible, but must not become the
  default.

### Rule

This replaces the existing "LayoutLib shouldn't auto-merge" rule in `renovate.json5`:

```json5
{
  description: 'LayoutLib default bumps must be verified. See LAYOUTLIB.md.',
  groupName: 'LayoutLib',
  matchPackageNames: [
    'com.android.tools.layoutlib:layoutlib',
    'com.android.tools.layoutlib:layoutlib-resources',
    'com.android.tools.layoutlib:layoutlib-runtime',
  ],
  automerge: false,
  addLabels: ['layoutlib'],
  // Qualified builds (e.g. -jdk17) are recorded for users but never become the default.
  allowedVersions: '!/-/',
  // Give layoutlib-new-versions a chance to record the release first.
  minimumReleaseAge: '3 days',
  prBodyNotes: [
    'The `layoutlib-renovate` workflow verifies this version and commits its entry to `gradle/layoutlib-compat.properties` if missing. See LAYOUTLIB.md.',
  ],
},
{
  description: 'layoutlib-api bumps change which recorded minimums are needed; re-verify recorded versions.',
  matchPackageNames: ['com.android.tools.layoutlib:layoutlib-api'],
  automerge: false,
  addLabels: ['layoutlib'],
},
```

If the verifier's commits are pushed by a bot identity, add that identity to `gitIgnoredAuthors`. Renovate
then keeps rebasing the branch instead of treating it as edited by someone else:

```json5
gitIgnoredAuthors: ['layoutlib-bot@users.noreply.github.com'],
```

### Companion workflow: `.github/workflows/layoutlib-renovate.yml`

This runs on Renovate PRs labelled `layoutlib`:

1. Read the new `layoutlib` version from `gradle/libs.versions.toml` on the PR head.
2. If `gradle/layoutlib-compat.properties` already has it (the usual case, via
   `layoutlib-new-versions`), do nothing. `build` and `layoutlib-compat` cover it.
3. Otherwise run `verifyLayoutlibVersion --layoutlib-version=$V --write` and the
   `LayoutlibCompatibilityTest` render for `$V`, then commit the updated compat file to the PR branch.
   The commit uses `LAYOUTLIB_BOT_TOKEN` so checks re-run.
4. On failure, comment on the PR with the failing step and reproduce commands, and leave it red.

For a `layoutlib-api` bump, run `verifyLayoutlibVersion` without `--write` for every recorded version
and report which entries drifted. Its minimum-API search starts at the pinned version, so recorded
minimums at or below the new pin become unnecessary.

```yaml
name: layoutlib-renovate

on:
  pull_request:
    types: [opened, synchronize, reopened, labeled]
    paths: ['gradle/libs.versions.toml']

permissions:
  contents: write
  pull-requests: write

jobs:
  record:
    if: github.actor == 'renovate[bot]' && contains(github.event.pull_request.labels.*.name, 'layoutlib')
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v7
        with:
          ref: ${{ github.head_ref }}
          token: ${{ secrets.LAYOUTLIB_BOT_TOKEN }}
      - uses: actions/setup-java@v6
        with: { distribution: 'zulu', java-version: 21 }
      - uses: gradle/actions/setup-gradle@9c971963bec38e04b3d30dcc455b5382be2fdbfb # v6
        with: { cache-disabled: true }
      - id: version
        run: echo "v=$(sed -nE 's/^layoutlib = "([^"]+)"/\1/p' gradle/libs.versions.toml)" >> "$GITHUB_OUTPUT"
      - id: recorded
        run: grep -q "^${{ steps.version.outputs.v }}\.icu=" gradle/layoutlib-compat.properties && echo "yes=true" >> "$GITHUB_OUTPUT" || true
      - if: steps.recorded.outputs.yes != 'true'
        run: |
          ./gradlew verifyLayoutlibVersion --layoutlib-version=${{ steps.version.outputs.v }} --write
          ./gradlew :paparazzi-gradle-plugin:test --tests app.cash.paparazzi.gradle.LayoutlibCompatibilityTest \
            -Ppaparazzi.layoutlib.versionsUnderTest=${{ steps.version.outputs.v }}
      - if: steps.recorded.outputs.yes != 'true'
        run: |
          git config user.name layoutlib-bot
          git config user.email layoutlib-bot@users.noreply.github.com
          git commit -am "chore: record verified layoutlib ${{ steps.version.outputs.v }}"
          git push
```

A self-hosted Renovate could do the same inside the Renovate PR:

```json5
postUpgradeTasks: {
  commands: ['./gradlew verifyLayoutlibVersion --layoutlib-version={{{newVersion}}} --write'],
  fileFilters: ['gradle/layoutlib-compat.properties'],
  executionMode: 'branch',
},
```

In that case rendering still happens in `layoutlib-compat` on the PR.

### Acceptance

- A Renovate layoutlib PR is never auto-merged. It carries the `layoutlib` label and a recorded,
  rendered entry, or it fails with a clear reason.
- Qualified releases are never proposed as the default.
- A `layoutlib-api` bump that makes recorded minimums stale fails `layoutlib-compat` until the
  entries are refreshed with `--write`.
