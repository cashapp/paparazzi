# layoutlib 16.2.4 → 17.0.1 upgrade: investigation findings

Context for the layoutlib 17.0.1 upgrade branch. Records what regressed, what did **not**,
which hypotheses were tested and **falsified**, and where the remaining work is.

The falsified section is deliberately detailed — several plausible-sounding explanations were
disproven by measurement, and re-deriving them costs hours.

---

## 1. The bisect boundary

| | commit | layoutlib | layoutlib-api |
|---|---|---|---|
| before | `14262f5cf` "Bump to LayoutLib 16.2.4" | 16.2.4 | 32.0.1 |
| after | `35ed2ac4b` "bump layout lib 17.0.1" | 17.0.1 | 32.3.2 |

`git diff 14262f5cf 35ed2ac4b` touches **only** `gradle/libs.versions.toml` — two version strings,
zero source changes. Every behavioural difference below is attributable to layoutlib itself.

> Note: branch names and commit subjects in this stack are misleading after rebases. Several
> commits titled for 16.2.x already contain `layoutlib = "17.0.1"`. Trust
> `git log -S'layoutlib = "17.0.1"' -- gradle/libs.versions.toml`, not the subjects.

## 2. Test matrix across the boundary

At **16.2.4** the Compose surface is fully green. At **17.0.1**:

| Inner test | 16.2.4 | 17.0.1 |
|---|---|---|
| `RecomposeTest.recomposesOnStateChange` | PASS | PASS |
| `RecomposeTest.recomposesOnTextLayout` | PASS | **FAIL** 0.061178% |
| `RecomposeTest.recomposesOnGlobalPositioning` | PASS | **FAIL** 2.205942% |
| `ComposeRenderingModeSizingTest` (`compose_v_scroll`) | PASS | PASS — §2 entry was wrong, see §5-bis |
| `ComposeTest` (6 tests, incl. `gif`) | PASS | PASS |
| `ComposeRippleTest` | PASS | PASS |
| compose-wear `ComposeTest` | PASS | PASS |
| `CoroutineDelayMainTest.delayUsesMainDispatcher` | PASS | PASS |
| `LifecycleCoroutineScopeTest` (produceState + delay) | PASS | PASS |
| `LaunchedEffectExceptionTest` propagation | PASS | PASS |
| `LifecycleUsageTest` (3 tests) | PASS | PASS |

### Compose **effect APIs are not regressed**

Explicitly verified on both versions: `LaunchedEffect`, `DisposableEffect`, `SideEffect`,
`produceState`, and `delay` all behave identically. `delay(250)` still maps to exactly 250 ms of
frame time, and exception propagation through `Handler_Delegate` → `DispatchedTask` is intact.
Multi-frame rendering also still works — `verifyGif` and the `compose` fixture's own `gif` test
pass on 17.0.1, so successive renders *can* produce differing frames.

## 3. What actually regressed

Two distinct defects:

1. **Layout-phase feedback never reaches the captured frame.** State written during layout
   (`onTextLayout`, `onGloballyPositioned`) is applied to the composition — `Recomposer.changeCount`
   demonstrably advances — but the rasterised output keeps the pre-layout content.
2. **Measurement/sizing.** `compose_v_scroll` renders 15px narrower (514 vs 529). Reproduces on
   pristine HEAD with no settle-loop changes, so it is independent of (1).

### Key observation: re-rendering is usually a pixel no-op

With per-render full-coverage pixel hashing of `bridgeRenderSession.image`, for
`recomposesOnGlobalPositioning` under a `repeat(4)` settle loop:

```
render#0 t=0         pending=true  change=0  imageHash=2004242148
render#1 t=16666666  pending=true  change=1  imageHash=2004242148
render#2 t=33333332  pending=false change=2  imageHash=2004242148
render#3 t=49999998  pending=false change=2  imageHash=2004242148
```

`changeCount` advances 0→1→2 while the image never changes. Composition updates; rasterisation
does not follow.

### The one configuration that moves pixels

A `while (recomposer.hasPendingWork)` settle loop with a per-pass frame-clock advance **plus** a
final render after the loop exits ("Variant E") produces:

```
render#0 t=0         pending=true  change=0  imageHash=2004242148
loop#1   t=16666666  pending=true  change=1  imageHash=2004242148
loop#2   t=33333332  pending=false change=2  imageHash=2004242148
FINAL    t=49999998  pending=false change=2  imageHash=631930620   <-- changes
```

Improving `recomposesOnTextLayout` 0.061178% → **0.047774%** and
`recomposesOnGlobalPositioning` 2.205942% → **0.084374%**. Reproducible across runs.

**Both tests still fail in this configuration.** It is an improvement, not a fix.

### What the residuals are

From visual inspection of the failure deltas:

- `recomposesOnTextLayout` renders the **correct** text (`Text Line count: 1`). The residual is
  faint outlines on *every* glyph, including the static "Sample text" that never recomposes →
  font anti-aliasing, not stale state.
- `recomposesOnGlobalPositioning` positions the square and populates the offset text, but reports
  `Offset(253.0, 72.0)` vs expected `Offset(211.0, 72.0)` → a **root-width difference**, same
  family as the independent `compose_v_scroll` sizing failure.

This suggests the remaining work is the **sizing/measurement regression**, which plausibly explains
both residuals *and* the separate `compose` fixture failure.

## 4. Falsified hypotheses

Each of these was advanced, tested, and **disproven**. Do not re-litigate without new evidence.

### 4.1 `Choreographer_Delegate.doCallbacks` signature change — FALSIFIED

The API changed from `doCallbacks(Choreographer, int, long)` to `doCallbacks(Choreographer, int)`,
which looks like a behavioural change. Decompilation shows it is not:

```
16.2.4  doCallbacks(Choreographer, int, long)     17.0.1  doCallbacks(Choreographer, int)
  mCallbacksRunning = true                          mCallbacksRunning = true
  getChoreographerCallbacks()                       getChoreographerCallbacks()
  System_Delegate.nanoTime()   <-- not the param!   System_Delegate.nanoTime()
  execute(J, log)                                   execute(J, log)
  mCallbacksRunning = false                         mCallbacksRunning = false
```

16.2.4 **ignored** its `long` parameter. 17.0.1 removed dead code. Supporting evidence:

- `postCallbackDelayedInternal` is **byte-identical** in both versions.
- `ChoreographerCallbacks$Callback` is **byte-identical**.
- `add()` computes `mDueTime = SystemClock_Delegate.uptimeMillis() + delay` in both.
- `mDueTime` is built from **milliseconds** while `execute()`'s cutoff is **nanoseconds**, so
  pending callbacks are effectively always due — "callbacks not firing" was never plausible.

**Consequence:** manually invoking callbacks via reflection instead of `doCallbacks` would change
nothing. There is nothing to route around.

### 4.2 "The frame clock must advance" — FALSIFIED as a standalone cause

`withTime` sets two clocks from one value: `Choreographer_Delegate.sChoreographerTime` and
`renderSession.setElapsedFrameTimeNanos(...)`. Splitting them into independent parameters:

| Clock advance | Post-quiescence render | TextLayout | GlobalPositioning |
|---|---|---|---|
| none | no | 0.061178% | 2.205942% |
| elapsed-frame only | no | 0.061178% | 2.205942% |
| Choreographer only | no | 0.061178% | 2.205942% |
| both together | no | 0.061178% | 2.205942% |
| none | yes | 0.061178% | 2.205942% |
| both together | yes | **0.047774%** | **0.084374%** |

Advancing either clock, or both, changes nothing on its own. Note also that `withTime` sets
`sChoreographerTime = 0` *before* `doFrame` and before `block()`, so the render itself always
observes clock 0 regardless.

`setElapsedFrameTimeNanos` only feeds `AnimatedVectorDrawable`'s native `sFrameTime` and is inert
for these fixtures.

### 4.3 Render count / timestamps / sequence — FALSIFIED

For `recomposesOnGlobalPositioning`, the `repeat(4)` shape and Variant E issue **four renders at
identical timestamps** (0 / 16666666 / 33333332 / 49999998) with identical `pending` and
`changeCount` progressions — yet produce 2.205942% vs 0.084374%.

### 4.4 `hasPendingWork` read location — FALSIFIED

Moving the read from inside the `withTime` block to outside reproduced the stale result exactly.

### 4.5 Final-render call-site location — FALSIFIED

Hoisting the final render out of the loop into its own call site, at the identical timestamp,
reproduced the stale result exactly (hash `2004242148`, not `631930620`).

### 4.6 Cross-test / JVM-reused Bridge state — FALSIFIED

The divergence is intra-test and localised to the fourth render; hashes agree on renders #0–#2.

### 4.7 Gradle dependency caching — FALSIFIED

Fixtures resolve `app.cash.paparazzi:2.0.0-SNAPSHOT` from `build/localMaven`, and
`:paparazzi-gradle-plugin:test` depends on
`:paparazzi:publishMavenPublicationToProjectLocalMavenRepository`. The TestKit-local cache under
`paparazzi-gradle-plugin/build/tmp/test/work/.gradle-test-kit/` was verified to contain the
freshly published jar. Edits do reach fixtures.

### 4.8 View dirty-flagging — FALSIFIED

Forcing `requestLayout()` + `invalidate()` before each settle render changed nothing.

## 5-bis. CORRECTION: neither §2 nor §5 describes a real defect

Both sections below are built on bad measurements. Superseded by this section.

### `compose_v_scroll` is not failing, and never was

`EXPECTED_CURRENT_V_SCROLL_WIDTH` is **514** on `origin/master` (#2363, `6b777c9fb`, predates any
layoutlib upgrade). The "expected 529" came from `7798aa59d "test: update LayoutLib rendering
baselines"`, which raised 514 → 529; `45cf1d6bc "Cleanup changes"` reverted it. 529 was a
fabricated baseline. 514 is both what upstream expects and what layoutlib 17.0.1 produces.

`ComposeRenderingModeSizingTest` **passed in every one of the ~10 runs** performed for this
section. There is no sizing regression and no "sizing path" to fix.

### The settle loop is dead code

Instrumenting `render()` shows `Recomposer.hasPendingWork` is **`false` at frame 0 in every
fixture** (`compose`, `gif`, `ripple`, `v_scroll`, `anchoredDraggable`). `attempt=1` never
executes; `MAX_RECOMPOSER_WORK_RETRY` has no runtime effect. Any experiment that varies settle-pass
behaviour is varying nothing — including §5's entire bisect table.

### The real defect: exactly one Compose animation renders per test run

`ComposeTest.gif` and `ComposeRippleTest.ripple` are both 31-frame `gif()` captures. **Exactly one
of the two fails per run, nondeterministically, with identical code:**

| run | ComposeRenderingModeSizing | ripple | gif |
|---|---|---|---|
| 1 | PASS | **FAIL 22** | PASS |
| 2 | PASS | PASS | **FAIL 30** |
| 3 | PASS | **FAIL 22** | PASS |

Reproduced on **committed HEAD with the working tree stashed**, so this is pre-existing and
independent of the settle-loop work.

The failure mode is total, not subtle. Sampling the centre pixel of the failing `gif` across the
delta APNG's 31 frames:

```
frame  expected (golden)   actual
0      (0,255,255) cyan    (0,255,255)
1      (86,245,255)        (0,255,255)
...                        ...
15     (255,0,255) magenta (0,255,255)
30     (86,245,255)        (0,255,255)
```

Per-frame hashing of `copyImage()` confirms it: the failing test emits **31 byte-identical
frames**, while the passing one's hashes all differ. Meanwhile `Recomposer.changeCount` advances
by exactly 1 per frame in the failing test — **composition runs, rasterisation is frozen.** This is
the same family as the §3 defect (1) observation.

"30 frames differed" / "22 frames differed" are just `frameCount - 1` and the count of ripple
frames that visibly differ from frame 0; they are not shift signatures.

### Falsified: leaked `AndroidUiDispatcher` singleton state

`AndroidUiDispatcher.Main` is a process-wide lazy singleton whose
`scheduledFrameDispatch`/`scheduledTrampolineDispatch` flags gate whether `withFrameNanos` posts to
the Choreographer, while the callbacks themselves live in the per-session
`SessionInteractiveData.choreographerCallbacks`. A stale `true` flag pointing at a discarded queue
would explain the freeze exactly.

It is not the cause. Reflective probing at teardown shows
`frameDispatch=false trampoline=false onFrame=0 tramp=0` after **every** snapshot. The state is
already clean; a reset is a no-op and did not change the flake rate (3 runs, still 1 failure each).

### Where to look next

The freeze is in rasterisation, not composition, so §8's layoutlib 17 buffer-pool restructuring is
the strongest remaining lead:

- `prepareRenderBuffers` only runs when `mNewRenderSize || mImage == null || disableBitmapCaching`;
  otherwise the pooled buffer is reused across renders.
- `copyImage()` calls `getRecyclableImage()` and `use {}`-closes it, returning the buffer to
  `SessionBufferPool` after every frame. Note `bridgeRenderSession.image` is **null** under 17.0.1
  when a recyclable image is in play — the §8 claim that `getImage()` is a usable non-perturbing
  probe no longer holds.
- Next experiment: set `RenderParamsFlags.FLAG_KEY_DISABLE_BITMAP_CACHING` on the session (Paparazzi
  currently sets no session flags) and see whether the flake disappears. If it does, the pooled
  buffer is being handed out while still referenced.

Until this is fixed, `gif`/`ripple` results are coin flips and **must not be used as signal when
bisecting anything else** — which is how §5's table was produced.

### Incidental bug fixed while measuring

`return@repeat` (§7) was a `continue`, so frame 0 always rendered `MAX_RECOMPOSER_WORK_RETRY` times
regardless of `hasPendingWork`. Now a real `break` over an indexed `for`. `withTime` also gained a
`settlePass` flag that suppresses the redundant second `doFrame`. Neither changes current
behaviour, because the retry never fires.

---

## 5. The `compose_v_scroll` width regression — root cause found

> **Superseded — see §5-bis. The 529 baseline does not exist upstream, and the bisect table below
> is test-ordering noise: the settle pass it varies never executes, and `gif`/`ripple` fail
> nondeterministically one-per-run regardless of configuration.**

`withTime()` on this branch accumulated three additions relative to master (`be8f485d2`):

1. `renderSession.setElapsedFrameTimeNanos(frameNanos)`
2. a manual `Choreographer_Delegate.doCallbacks(Choreographer.getInstance(), CALLBACK_ANIMATION)`
   dispatch, placed **before** `doFrame`
3. `Choreographer_Delegate.sChoreographerTime = 0` immediately before `doFrame`

Bisecting them against the three affected tests:

| Config | (1) elapsed | (2) doCallbacks | (3) zeroing | `compose_v_scroll` | `ComposeTest.gif` | `ripple` |
|---|---|---|---|---|---|---|
| **A** (HEAD) | ✓ | before `doFrame` | ✓ | **FAIL** 514 vs 529 | PASS | PASS |
| **B** (master) | ✗ | ✗ | ✗ | PASS | **FAIL** 30 frames | PASS |
| **C** | ✓ | ✗ | ✗ | PASS | PASS | **FAIL** 22 frames |
| **D** | ✓ | ✗ | ✓ | PASS | **FAIL** 30 frames | PASS |
| **E** | ✓ | **after `doFrame`** | ✓ | **PASS** | PASS | **FAIL** 22 frames |

### Root cause

**The manual `doCallbacks(CALLBACK_ANIMATION)` dispatch, when placed before `doFrame`, causes the
width regression.** It injects an animation-driven layout pass whose remeasure is baked into the
frame that is subsequently sized, yielding 514 instead of 529. Moving the dispatch after `doFrame`
(Config E) fixes `v_scroll` while keeping `gif` green — establishing the causal link.

### The blocking conflict

- `v_scroll` requires the animation dispatch to happen **after** `doFrame` (so the measured size is
  not perturbed).
- `ripple` requires it **before** `doFrame` (a ripple's visual state is established during the
  frame it is dispatched in). `ripple` fails with exactly 22 frames in both configs where the
  dispatch does not precede `doFrame` under a zeroed clock (C and E).

**No single dispatch position satisfies both.** Subset membership is also exhausted — no subset of
{(1),(2),(3)} makes all three tests pass.

Other dependencies established by the bisect:

- `setElapsedFrameTimeNanos` (1) alone is sufficient for `gif`; the manual dispatch is not required
  for gif *per se*.
- `sChoreographerTime = 0` (3) is what `ripple` needs — it fails without it (C).
- (3) also starves `gif` when (2) is absent (D), because zeroing pins the Choreographer clock back
  to 0 each frame. So (2) exists to re-supply the dispatch that (3) suppresses. The three are
  entangled by design.

### Suggested directions

- Fix the **sizing path** so the snapshot width is read from the layout pass preceding the animation
  dispatch, then keep Config A's ordering (which `ripple` needs). This targets the actual defect —
  size sampled from a perturbed frame — rather than hunting a dispatch position that pleases both.
- Or keep A's ordering and prevent the manual dispatch from triggering a relayout, restoring the
  root's pre-dispatch constraints before `block()` renders.

### `withTime` is NOT responsible for the recomposition failures

`recomposesOnTextLayout` (0.061178%) and `recomposesOnGlobalPositioning` (2.205942%) were
**byte-identical across all five configs** above. Those two regressions are conclusively
independent of `withTime` and belong to the settle loop or to layoutlib 17.0.1 itself.

## 6. Open question (recomposition)

Every observable instrumented so far is identical between the working (E) and non-working
(`repeat`) shapes, yet the fourth render diverges. Untested leading hypothesis: **coroutine
suspension** — `while`'s exit-condition evaluation may yield and let queued Compose work drain in
a way `repeat`'s inline lambda does not. Discriminating experiment: insert a bare `yield()` before
the hoisted final render in the `repeat` shape.

## 7. Incidental bug found

In the current in-tree settle loop:

```kotlin
repeat(if (frame == 0) maxRetryCount else 1) {
  withTime(nowNanos) { ... }
  if (!hasPendingWork) return@repeat   // <-- continue, NOT break
}
```

`return@repeat` returns from the `repeat` lambda, i.e. it is a `continue`. All iterations always
run regardless of `hasPendingWork`. Confirmed at runtime — traces show 4 renders even when
`pending=false` after the first. Almost certainly not the intent.

## 8. Useful methodology notes

- `bridgeRenderSession.image` (`getImage()`) is a **non-perturbing** probe — verified by
  reproducing exact diff percentages with and without per-render hashing.
- `RenderSession.copyImage()` **recycles** the underlying buffer back to layoutlib's pool. Calling
  it twice for diagnostics corrupts results; it also crops 1152→1080 logical bounds, which can be
  mistaken for content divergence.
- 17.0.1 restructured the render pipeline: new `SessionBufferPool`, `mImageWidth`/`mImageHeight`,
  `getRecyclableImage()`, and split `drawAndCopyImage` / `prepareRenderBuffers` /
  `updateAndTraverseViews` / `handleAnimations` / `runLayoutValidation`.
- `prepareRenderBuffers` is only invoked when `mNewRenderSize || mImage == null ||
  disableBitmapCaching`; otherwise the pooled buffer is reused.
  `RenderParamsFlags.FLAG_KEY_DISABLE_BITMAP_CACHING` exists and Paparazzi currently sets no
  session flags — untested.
