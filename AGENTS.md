# Repository guidance

Paparazzi is a host-side Android screenshot-testing library built around Android's Layoutlib renderer.

## Relevant upstream source

When behavior depends on Android framework or rendering internals, consult:

- Layoutlib renderer and delegates:
  https://android.googlesource.com/platform/frameworks/layoutlib/
- Android framework implementation and resources:
  https://android.googlesource.com/platform/frameworks/base/

Before investigating upstream behavior, determine the Layoutlib version pinned in
`gradle/libs.versions.toml`. Prefer source from the corresponding Studio release tag;
do not assume upstream `main` matches the version used by Paparazzi.

Use upstream source for reference only. Do not copy upstream code into this repository.

When diagnosing rendering behavior, distinguish:

- Android framework behavior from `frameworks/base`
- Host-side adaptations from `frameworks/layoutlib`
- Paparazzi-specific orchestration, lifecycle, capture, and reporting
