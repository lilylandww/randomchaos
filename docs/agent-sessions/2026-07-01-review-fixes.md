# 2026-07-01 — Review fixes

## Goal
Verify the findings in `docs/review-2026-07-01.md` against the actual code and
fix the ones worth fixing.

## Verification outcome
Every claim was checked against the source. Findings:
- **Confirmed & fixed:** C1 (NaN config), C2 (interval overflow), C3 (HUD
  torn read), C4 (dead mixins), I4 (dead `ChaosStateManager`), I9 (untested
  fallback), I10 (clamp edge cases), I11 (apply-failure fabricates HUD state).
- **Rejected:** I2 (duplicate payload) — the reviewer missed that
  `onPlayerJoin` broadcasts *only* on the first join (when the challenge
  starts). `sendTo` is the sole immediate sync for every later joiner; deleting
  it would regress join sync. Left as-is.
- **Skipped (low value / cosmetic):** I1, I5, I6, I7, I8, S1–S9.

## Bonus from testing
I10's new edge-case test surfaced a latent bug: `clampDuration(100, -100, 0.7)`
returned `-70` (negative interval → negative duration). Made `clampDuration`
defensive (`cap <= 0 → return 0`) so it now guarantees a non-negative result.

## Files changed
- `config/ChaosConfig.java` — reject `NaN`/non-finite ratio; cap `intervalSeconds`
  to `[1, 86400]`; named defaults.
- `scheduler/ChaosScheduler.java` — `clampDuration` defensive against
  non-positive cap; `fireEvent` skips active-effect state when `apply` throws.
- `client/net/ClientChaosState.java` — immutable `Snapshot` record published
  over a single `volatile` reference (fixes torn reads).
- `client/net/ClientNetworking.java` — publish a `Snapshot`.
- `client/hud/ChaosHudOverlay.java` — read `snapshot()` once per frame.
- `ChaosSelfTest.java` — +4 checks (clamp edges incl. negative interval/zero
  interval/negative duration; 16-rejection fallback via a stub `RandomSource`).
- Deleted: `mixin/RandomChaosMixin.java`, `client/mixin/RandomChaosClientMixin.java`,
  both `*.mixins.json`, `state/ChaosStateManager.java`; removed the `mixins`
  block from `fabric.mod.json`.

## Commands run
- `./gradlew build` — green
- `./gradlew runServer` (bounded) — `Random Chaos self-test PASSED (14 checks)`,
  mod initializes with mixins removed, no startup errors.

## Branch
`agent/review-fixes` (off `agent/random-chaos-core`). Not merged.
