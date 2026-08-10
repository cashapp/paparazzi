# Spike 001 — Per-frame Layoutlib elapsed time

## Question

Given a Paparazzi snapshot or GIF frame at `offsetMillis`, when Layoutlib renders
that frame, does `AnimatedVectorDrawable` receive the same elapsed time in
milliseconds?

## Why this matters

`RenderTestBase` in Google's deviceless Layoutlib screenshot tests sets
`RenderSession.setElapsedFrameTimeNanos(frameTimeNanos)` before rendering. The
Layoutlib 16.2.3 bridge uses that value to set
`AnimatedVectorDrawable_VectorDrawableAnimatorUI_Delegate.sFrameTime`.

Paparazzi already advances the Compose/Choreographer clock for each frame, but
it initialized Layoutlib elapsed-frame time to zero only once when constructing
the render session. Native animated-vector timing therefore remained at zero
for snapshots taken at a nonzero offset.

## Experimental change

Immediately before each primary frame render in `PaparazziSdk.takeSnapshots`,
set:

```kotlin
renderSession.setElapsedFrameTimeNanos(nowNanos)
```

This is deliberately narrow: it leaves the existing Compose frame-clock
workaround unchanged and supplies Layoutlib's public per-frame animation input.

## Evidence

### RED

Before the change, this regression test failed:

```text
PaparazziTest.layoutlibAnimatedVectorFrameTimeUsesSnapshotOffset
expected: 300
but was : 0
```

The test snapshots at `offsetMillis = 300` and asserts Layoutlib's animated
vector frame-time delegate is `300` milliseconds.

### GREEN

After the change, both of these passed:

- `PaparazziTest.layoutlibAnimatedVectorFrameTimeUsesSnapshotOffset`
- `PaparazziTest.choreographerFrameCallbackUsesSnapshotOffset`

`spotlessCheck` and `git diff --check` also passed.

## Verdict: VALIDATED

### What worked

- Layoutlib receives the requested elapsed time for a nonzero snapshot offset.
- The existing Choreographer timestamp regression remains green, so the change
  does not replace or disturb the Compose timing path.

### What did not change

- This spike verifies Layoutlib's frame-time delegate, not a rendered
  animated-vector golden image. A follow-up integration fixture should verify
  pixels across multiple GIF frames.

### Recommendation for the real build

Keep the per-frame `setElapsedFrameTimeNanos(nowNanos)` call and add a small
animated-vector GIF fixture if native animation image coverage becomes a
release requirement.
