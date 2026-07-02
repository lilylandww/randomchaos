# Plan Review: Tiered Events — 2026-07-01

**Plan:** `/home/tupi/code/random-chaos-mc-mod/docs/2026-07-01-tiered-events-plan.md`
**Base branch:** `agent/random-chaos-core` (commit `2be7b99`)
**Review date:** 2026-07-01
**Method:** 5 parallel `general` subagents + independent `javap` verification of MC 26.1.2 APIs

**VERDICT: REVISE**

The plan is architecturally sound and aligns well with the existing spec, but it
has **5 critical gaps** that will cause compile/runtime failures or gameplay
incidents if executed as written. It also needs stronger verification, a
pre-mortem, and clarification of several ambiguous event behaviors before
implementation starts.

---

## Pre-commitment predictions vs. actual findings

| # | Predicted problem area | Found? | Notes |
|---|------------------------|--------|-------|
| 1 | `ChaosState` codec backward compat mishandled | Partially | Plan uses `optionalFieldOf` correctly, but config defaults for new GSON fields are wrong (0, not stated defaults) |
| 2 | Scheduler/event-registry rewrite breaks existing self-tests or core loop | Yes | Execution order would create a compile break between steps 4 and 8; `tick()`/`onEnd()` lacks try-catch |
| 3 | New events use wrong Mojang mappings | Yes | 3 API assumptions are wrong (`DIG_SLOWDOWN`, `CONFUSION`, `DyeColor.getWoolBlock()`) |
| 4 | Anti-burst picker has subtle edge cases | Yes | MAJOR too rare (~2.5%), off-by-one risk, fallback semantics ambiguous |
| 5 | Deferred actions introduce persistence/desync issues | Yes | Unbounded list, no staleness eviction, no victim-offline handling |

All 5 predictions materialized.

---

## Critical findings (blocks execution)

### C1. Wrong `MobEffects` names in 3 planned events

**Plan excerpt:** §6 API assumptions — `MobEffects.DIG_SLOWDOWN`, `MobEffects.CONFUSION`

**Evidence:** `javap` of `net.minecraft.world.effect.MobEffects` in the 26.1.2
Mojang-mapped jar shows:
- `MINING_FATIGUE` exists; `DIG_SLOWDOWN` does **not**
- `NAUSEA` exists; `CONFUSION` does **not**
- `BLINDNESS` exists (correct)

**Impact:** `MiningFatigueEvent` and `DizzinessEvent` will not compile. The plan
will fail at step 13 (`./gradlew build`).

**Fix:** Update the plan to use `MobEffects.MINING_FATIGUE` and
`MobEffects.NAUSEA`.

---

### C2. `DyeColor.getWoolBlock()` does not exist

**Plan excerpt:** §6 — `DyeColor` and wool block lookup; §4 `RainbowRoadEvent`

**Evidence:** `javap` of `net.minecraft.world.item.DyeColor` shows no
block-related methods (`getId`, `getName`, `getMapColor`, `getTextColor`,
`getMixedColor`, but no `getWoolBlock`).

**Impact:** `RainbowRoadEvent` will not compile.

**Fix:** Replace with a static lookup table:
```java
private static final Block[] WOOL_BY_DYE = {
    Blocks.WHITE_WOOL, Blocks.ORANGE_WOOL, Blocks.MAGENTA_WOOL,
    Blocks.LIGHT_BLUE_WOOL, Blocks.YELLOW_WOOL, Blocks.LIME_WOOL,
    Blocks.PINK_WOOL, Blocks.GRAY_WOOL, Blocks.LIGHT_GRAY_WOOL,
    Blocks.CYAN_WOOL, Blocks.PURPLE_WOOL, Blocks.BLUE_WOOL,
    Blocks.BROWN_WOOL, Blocks.GREEN_WOOL, Blocks.RED_WOOL, Blocks.BLACK_WOOL
};
// usage: WOOL_BY_DYE[dyeColor.ordinal()]
```

---

### C3. New config fields will default to 0, not the stated defaults

**Plan excerpt:** §3 — `majorCooldownPicks = 6 (≥1)`, `minorWeight = 50`,
`mediumWeight = 35`, `majorWeight = 15`

**Evidence:** Current `ChaosConfig.java:52` uses `GSON.fromJson(reader,
ChaosConfig.class)`. GSON leaves missing JSON fields at Java default values
(`0` for `int`). The existing validation only covers `intervalSeconds` and
`effectCapRatio`. The plan does **not** specify Java field initializers or
validation for the new fields.

**Impact:** Loading an existing `config/randomchaos.json` (which lacks the new
keys) yields `majorCooldownPicks=0`, `minorWeight=0`, etc. Weighted selection
crashes with `nextInt(0)`, or MAJOR fires every pick.

**Fix:** Add to `ChaosConfig`:
```java
public int majorCooldownPicks = 6;
public int minorWeight = 50;
public int mediumWeight = 35;
public int majorWeight = 15;
```
And validation:
```java
if (loaded.majorCooldownPicks < 1) { loaded.majorCooldownPicks = 6; }
if (loaded.minorWeight < 0 || loaded.mediumWeight < 0 || loaded.majorWeight < 0) { ... clamp ... }
if (loaded.minorWeight + loaded.mediumWeight + loaded.majorWeight == 0) { ... clamp to defaults ... }
```

---

### C4. `tick()` and `onEnd()` are not protected by try-catch

**Plan excerpt:** §2 — "Tick active event", "On end"

**Evidence:** The existing `ChaosScheduler.fireEvent` wraps `event.apply(victim)`
in `try/catch` (`ChaosScheduler.java:77-81`), but the plan's scheduler additions
say only:
> - **Tick active event**: ... call `event.tick(victim, now)`
> - **On end**: ... call `oldEvent.onEnd(victim)`

No error handling is specified.

**Impact:** A crashing `tick()` or `onEnd()` will kill the server tick loop,
freezing the server.

**Fix:** Wrap both calls identically to `apply`:
```java
try {
    event.tick(victim, now);
} catch (Throwable t) {
    LOGGER.error("Chaos event {} threw in tick", event.id(), t);
}
```

---

### C5. Execution order creates a compile break

**Plan excerpt:** §7 steps 4, 6, 7, 8

**Evidence:**
- Step 4 says "Replace `pickRandom(RandomSource)` with `pickRandom(RandomSource, ChaosTier)`".
- Step 8 says "Rewrite scheduler".
- The current `ChaosScheduler.fireEvent` calls
  `ChaosEventRegistry.INSTANCE.pickRandom(rng)` (`ChaosScheduler.java:76`).

If step 4 removes the old signature before step 8 rewrites the call site, the
project will not compile between those steps.

Similarly, step 6 (add `deferredActions` to `ChaosState` codec) depends on
`DeferredAction.CODEC`, which is created in step 7.

**Impact:** Partial implementation is impossible; the plan is not incrementally
executable.

**Fix:** Either (a) keep `pickRandom(RandomSource)` as a delegating overload
that picks from all tiers, or (b) merge steps 4 and 8, and reorder 7 before 6.

---

## Major findings (causes significant rework)

### M1. MAJOR events will fire only ~2.5% of the time

**Plan excerpt:** §3 — `majorCooldownPicks = 6`, weights 50/35/15

**Analysis:** MAJOR is available 1 in 6 picks. When available, its weight is
15 / 100 = 15%. Combined effective rate = 15% × 16.7% ≈ **2.5%**. In a 2-hour
session with 120s intervals (~60 events), expected MAJOR count ≈ 1.5, with a
~21% chance of seeing zero MAJOR events.

**Impact:** Players will perceive MAJOR events as "never happening", defeating
the tier system's purpose.

**Fix:** Either raise `majorWeight` to 25–30, reduce `majorCooldownPicks` to
3–4, or apply a cooldown-bypass boost. Add a probability analysis section to
the plan.

---

### M2. `CraterEvent` lacks spawn protection, game-mode, and Y-floor guards

**Plan excerpt:** §4 MAJOR — "Sphere radius 3 centered 2 below player's feet:
`level.destroyBlock(pos, true)` ... skipping air & bedrock"

**Impact:** Can destroy spawn platform, creative builds, or (if near bedrock
layer) carve into the void. Massive griefing potential.

**Fix:** Add guards:
- Skip if victim is in creative or spectator mode.
- Enforce `center.getY() > level.getMinBuildHeight() + 5`.
- Skip spawn-protected area (use `ServerPlayer.canInteractWith` or a radius
check around world spawn).

---

### M3. `TeleportToGroundEvent` is unsafe in Nether and End

**Plan excerpt:** §4 MAJOR — "`level.getHeight(MOTION_BLOCKING_NO_LEAVES, bx,
bz)` → teleport"

**Impact:** In the Nether this returns the bedrock ceiling (Y=127), embedding
the player in netherrack. In the End outer islands it may return Y=0 (void).

**Fix:** Restrict to overworld, or validate target Y is within
`[minBuildHeight + 2, getHeight(...) - 1]` and not inside a solid block.

---

### M4. `DeferredAction` list is unbounded and lacks staleness eviction

**Plan excerpt:** §3 — "Drain deferred actions: for each `DeferredAction` where
`fireAtTick <= now`, dispatch ... then remove"

**Impact:** If the server lags or many thunder events fire, the list grows
without bound. Stale actions (from before a restart) still fire on load.

**Fix:** Add:
- Max list size (e.g. 100) with warning on discard.
- Drop actions older than `now - 6000` ticks (5 minutes).

---

### M5. `onEnd()` ordering is fragile — `RainbowRoadEvent` cleanup may never run

**Plan excerpt:** §2 — "On end: at expiry, call `oldEvent.onEnd(victim)` before
clearing fields"

**Evidence:** The current `ChaosScheduler.tick` clears fields first
(`currentEventId = ""`, etc.). The plan states the desired order in prose but
does not provide pseudocode for the expiry block.

**Impact:** An implementer following the existing pattern will clear fields
first, then be unable to resolve `oldEvent` to call `onEnd()`. Wool paths from
`RainbowRoadEvent` persist forever.

**Fix:** Provide explicit scheduler pseudocode in the plan:
```java
if (currentEffectExpiryTick != 0 && now >= currentEffectExpiryTick) {
    ChaosEvent oldEvent = registry.get(id(currentEventId));
    ServerPlayer oldVictim = findByUuid(players, currentVictimUuid);
    if (oldEvent != null && oldVictim != null) {
        try { oldEvent.onEnd(oldVictim); } catch (Throwable t) { ... }
    }
    clearCurrentEvent();
    changed = true;
}
```

---

### M6. Victim may be offline when `tick()` or `onEnd()` is called

**Plan excerpt:** §2 — "resolve event from registry by `currentEventId`, call
`event.tick(victim, now)`"

**Evidence:** The current `findByUuid` returns `null` for offline players
(`ChaosScheduler.java:139-144`). The plan does not specify a null guard.

**Impact:** `RainbowRoadEvent.tick(null, now)` → NPE.

**Fix:** Either skip `tick()`/`onEnd()` when victim is offline, or require every
event implementation to null-check. Add a decision to the plan.

---

### M7. Self-tests are underspecified

**Plan excerpt:** §5 — "`ChaosSelfTest.java` (add tier/cooldown/fallback tests)";
§7 step 12 — "Add self-tests"

**Impact:** The plan adds ~5 new testable pure functions (`chooseTier`, tier
fallback, deferred drain, cooldown reset, codec round-trip) but lists no
specific assertions. An implementer will add 1–2 superficial tests and ship
logic bugs.

**Fix:** Expand §7 step 12 into concrete test cases (see "Recommended test
additions" below).

---

### M8. Decision on client HUD / payload is missing

**Plan excerpt:** None — `ChaosStatePayload` and `ChaosHudOverlay` are absent
from §5 "Files changed"

**Impact:** If the HUD should show event tier, the payload must be extended.
If not, the plan should state tiers are server-only. The silence forces an
implementation-time decision.

**Fix:** Add a one-line decision: "Tiers are server-side only; HUD continues to
show only event name and victim." OR update §5 to include payload/HUD files.

---

## Minor findings (suboptimal but functional)

| # | Finding | Location | Fix |
|---|---------|----------|-----|
| m1 | `MobEffectInstance(MobEffect, int, int)` text is inaccurate; actual constructor is `(Holder<MobEffect>, int, int)` | §6 | Update text; code works because constants are `Holder`s |
| m2 | `consecutiveHarmful` is mentioned but not in execution order | §2 | Remove or add to §7 |
| m3 | `picksSinceLastMajor` initial value not specified | §2 | State whether first challenge starts at 0 (MAJOR blocked) or `majorCooldownPicks` (MAJOR allowed) |
| m4 | `SpawnZombieEvent` reclassification to MEDIUM is trivial but should be explicit in §7 timing | §4, §7 | Ensure `tier()` override is added before step 11 registration |
| m5 | `RainbowRoadEvent` cleanup semantics ambiguous | §4 | State whether wool is removed in `onEnd()` or left in world |
| m6 | `CraterEvent` radius ambiguous (Euclidean vs Manhattan, inclusive vs exclusive) | §4 | Specify "Euclidean distance ≤ 3.0" |
| m7 | `RainbowRoadEvent` "block below" ambiguous | §4 | Specify `blockPosition().below()` vs `below(2)` |
| m8 | `ThunderStrikeEvent` lightning targets old victim position if victim moves | §4 | Either track victim UUID and resolve current position at fire time, or document the design |

---

## What's missing

- **Pre-mortem:** The plan contains no "assume this failed" analysis.
- **Expanded verification:** Only `./gradlew build` + vague "add self-tests".
  No GameTest strategy for spatial events (`CobbleCageEvent`, `CraterEvent`,
  `RainbowRoadEvent`), which are the highest-value test candidates.
- **Manual test checklist:** No in-game verification steps.
- **Config migration notes:** No note on what happens if an old mod loads a
  save with `deferred_actions` (it will ignore the unknown field, but this
  should be stated).
- **`spec.md` update:** The spec still says "per-event weighting is not yet
  supported". If this plan lands, §12 should be updated.
- **Error handling for `tick()`/`onEnd()`:** See C4.
- **Victim-offline policy:** See M6.
- **Client-side decision:** See M8.

---

## Ambiguity risks

### A1. Tier fallback semantics

**Quote:** "If chosen tier pool empty → fallback to any non-empty tier (MINOR →
MEDIUM → MAJOR)" (§2 and §1)

- **Interpretation A:** After weighted pick, if the chosen tier's event pool is
  empty, scan MINOR→MEDIUM→MAJOR for a non-empty pool.
- **Interpretation B:** Fallback only when the entire candidate list is empty.

**Risk if B is chosen:** If only MAJOR events exist and MAJOR is on cooldown,
scheduler bails instead of picking MAJOR.

**Fix:** State A explicitly.

---

### A2. Weight normalization when MAJOR is on cooldown

**Quote:** "Build candidate list from {MINOR, MEDIUM} + MAJOR if available"
(§2)

- **Interpretation A:** Normalize over available tiers only (MINOR=50,
  MEDIUM=35 → ~59%/41%).
- **Interpretation B:** Keep MAJOR's 15 weight in denominator, creating a
  15/100 "no pick" zone.

**Risk if B is chosen:** ~15% of ticks produce no event or crash.

**Fix:** State A explicitly and show the weight formula.

---

### A3. Rainbow Road "block below"

**Quote:** "get block below `victim.blockPosition()`" (§4)

- **Interpretation A:** `blockPosition().below()` — the block the player is
  standing on.
- **Interpretation B:** `blockPosition().below(2)` — the block under that.

**Risk:** Wool placed at wrong Y (floating or buried).

**Fix:** Specify the target Y.

---

### A4. Rainbow Road cleanup

**Quote:** "Cleanup in `onEnd()`" (§4)

- **Interpretation A:** Remove all placed wool blocks.
- **Interpretation B:** Just clear internal state map, leave wool in world.

**Risk:** Permanent colored wool paths or unexpectedly large memory usage from
storing every placed position.

**Fix:** Pick one and document it.

---

### A5. Crater shape

**Quote:** "Sphere radius 3 centered 2 below player's feet" (§4)

- **Interpretation A:** Euclidean distance ≤ 3.0 (standard sphere).
- **Interpretation B:** Manhattan distance ≤ 3 (diamond).
- **Interpretation C:** Euclidean distance < 3.0 (excludes boundary).

**Risk:** Wrong blast radius / shape.

**Fix:** Specify "Euclidean distance ≤ 3.0".

---

## Multi-perspective notes

### Executor
- Steps 4 and 8 must be atomic or the old `pickRandom` kept as overload.
- Steps 6 and 7 should be reordered (7 before 6) or merged.
- Many event behaviors are under-specified (cleanup, radius, block-below).
  I will have to make implementation decisions not documented in the plan.
- No specific self-test cases are given; I need to invent them.

### Stakeholder
- The plan delivers the requested tier system and 12 events.
- However, the effective MAJOR rate (~2.5%) may not meet the "MAJOR events feel
  impactful" goal. Suggest play-testing weights before release.
- Griefing risks (`CraterEvent`, `TeleportToGroundEvent`) could harm player
  experience on public servers.

### Skeptic
- Why weighted tiers instead of per-event weights? The spec §12 explicitly
  mentions per-event weights as future work; this plan chooses tier-level
  weights without explaining why per-event weights were rejected.
- Why a custom `DeferredAction` system instead of scheduling future events
  through the existing `nextEventTick` mechanism? The deferred lightning could
  have been modeled as recurring events.
- The plan does not justify the default weights 50/35/15 or cooldown 6 with any
  play-testing or probability analysis.

---

## Verdict justification

**Verdict: REVISE**

The plan is rejected for direct execution because of **5 critical findings**
that will cause compile failures (C1, C2, C5), runtime crashes (C3, C4), or
server instability (C4). Additionally, **8 major findings** indicate
significant gameplay or safety issues that require design decisions before code
is written.

Review operated in **ADVERSARIAL mode** after discovering the first critical
finding (wrong MobEffects names), then escalated further when the second and
third critical findings (missing config validation, missing try-catch) were
confirmed.

**To upgrade to ACCEPT-WITH-RESERVATIONS**, the author must:
1. Fix C1–C5.
2. Add a probability analysis and adjust MAJOR rarity (M1).
3. Add safety guards to `CraterEvent` and `TeleportToGroundEvent` (M2, M3).
4. Specify `onEnd()` pseudocode, victim-offline policy, and client HUD decision
   (M5, M6, M8).
5. Expand §7 step 12 into concrete self-test cases (M7).

**To upgrade to ACCEPT**, also address:
- Add a pre-mortem section to the plan.
- Add a verification section with specific GameTest or manual-test strategy for
  spatial events.
- Resolve all ambiguity risks A1–A5 explicitly.

---

## Open questions (unscored)

1. Should `RainbowRoadEvent` cleanup remove wool blocks or just clear state?
2. Should MAJOR events be allowed immediately at challenge start, or blocked
   for the first `majorCooldownPicks`?
3. Should the HUD display event tier, or are tiers purely server-side?
4. What is the intended behavior when the victim logs out mid-event: skip
   `tick()`/`onEnd()`, or call them with `null` and let events handle it?
5. Should `CraterEvent` and `TeleportToGroundEvent` be disabled in protected
   regions by checking world spawn radius, or by hooking into a claim-mod API?
6. Is the deferred lightning supposed to strike the victim's *current* position
   at fire time, or the position at enqueue time?

---

## Recommended test additions for §7 step 12

Add these concrete tests to `ChaosSelfTest`:

1. `chooseTier` with `picksSinceLastMajor=5` and `majorCooldownPicks=6` never
   returns MAJOR over 1000 forced picks.
2. `chooseTier` with `picksSinceLastMajor=6` returns MAJOR when RNG lands in
   the MAJOR bucket, and resets counter to 0.
3. `chooseTier` weight distribution over 10000 picks matches 50/35/15 within
   ±5% (with `majorCooldownPicks=1` so MAJOR is always available).
4. Tier fallback: when MINOR pool is empty and weighted pick selects MINOR,
   result is MEDIUM (or MAJOR if MEDIUM also empty).
5. Deferred action drain: actions with `fireAtTick <= now` are dispatched and
   removed; future actions remain.
6. `ChaosState` codec round-trip: encode state with new fields, decode, assert
   equality.
7. Backward compat: decode JSON/NBT missing `picks_since_last_major` and
   `deferred_actions`; assert defaults 0 and empty list.

---

## Ralplan summary row

| Gate | Status | Reason |
|------|--------|--------|
| Principle/Option Consistency | **Pass** | Tiered weighting is consistent with spec §12 future-work item; no contradictions |
| Alternatives Depth | **Fail** | No alternatives to tier-level weights or custom `DeferredAction` system are discussed |
| Risk/Verification Rigor | **Fail** | Verification is only `./gradlew build` + vague "add self-tests"; no pre-mortem, no expanded test plan, no spatial-event verification |
| Deliberate Additions (pre-mortem + expanded test plan) | **Fail** | Plan contains neither a pre-mortem nor an expanded unit/integration/e2e/observability test plan |
