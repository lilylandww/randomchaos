# Tiered Events Plan — 2026-07-01

## Goal
Introduce event tiers (MINOR / MEDIUM / MAJOR), an anti-burst picker that prevents too many high-tier events in a row, and implement the first batch of 12 events.

## Branch
`agent/tiered-events`

---

## 1. Tier system (core)

### `event/ChaosTier.java` (new)
```java
public enum ChaosTier {
    MINOR, MEDIUM, MAJOR;
    public boolean isHarmful() { return this == MEDIUM || this == MAJOR; }
}
```

### `event/ChaosEvent.java` (modified)
- Add `ChaosTier tier()` — default `MEDIUM` (back-compat for existing `SpawnZombieEvent`).
- Add `default void tick(ServerPlayer victim, long now) {}` — called each server tick while event is the active (current) timed event.
- Add `default void onEnd(ServerPlayer victim) {}` — called when the active event expires.

### `event/ChaosEventRegistry.java` (modified)
- Group events by `ChaosTier` internally (tier → list).
- Replace `pickRandom(RandomSource)` with `pickRandom(RandomSource, ChaosTier)` — uniform within a tier.
- Fallback: if chosen tier pool is empty, try next non-empty tier in order MINOR → MEDIUM → MAJOR. If all empty, scheduler already bails.

---

## 2. Anti-burst picker

### Rule (per user)
**MAJOR must not be picked more than once every 6 events.** No hard cap on MEDIUM streaks (weighted baseline handles it).

### `state/ChaosState.java` (modified — codec additions)
Persisted (via `.optionalFieldOf` with defaults for backward compat):
- `int picksSinceLastMajor` — counts picks since last MAJOR. Resets to 0 when MAJOR fires.
- `List<DeferredAction> deferredActions` — for thunder ×3 strikes.

### `config/ChaosConfig.java` (modified)
New fields (GSON):
- `majorCooldownPicks` = 6 (≥1)
- `minorWeight` = 50, `mediumWeight` = 35, `majorWeight` = 15 (non-negative)

### `scheduler/ChaosScheduler.java` — `fireEvent()` changes
1. **Choose tier** via `chooseTier(rng, state, cfg)`:
   - MAJOR available iff `state.picksSinceLastMajor >= cfg.majorCooldownPicks` AND MAJOR pool non-empty.
   - Build candidate list from {MINOR, MEDIUM} + MAJOR if available.
   - Weighted random using cfg weights (relative, sum need not be 100).
   - If chosen tier pool empty → fallback to any non-empty tier (MINOR → MEDIUM → MAJOR).
2. **Pick event** within chosen tier via `registry.pickRandom(rng, tier)`.
3. **Update streak**:
   - If chosen tier == MAJOR → `state.picksSinceLastMajor = 0`
   - Else → `state.picksSinceLastMajor += 1`
4. `consecutiveHarmful` tracking for potential future use (punted for now).

### Scheduler — per-tick additions
In `tick()`, after expiry and before firing new events:
- **Drain deferred actions**: for each `DeferredAction` where `fireAtTick <= now`, dispatch by kind (currently "lightning"), then remove.
- **Tick active event**: if `currentEffectExpiryTick != 0 && now < expiry`, resolve event from registry by `currentEventId`, call `event.tick(victim, now)`.
- **On end**: at expiry, call `oldEvent.onEnd(victim)` before clearing fields.

---

## 3. Deferred actions (new infra, for thunder ×3)

### `state/DeferredAction.java` (new record + codec)
```java
public record DeferredAction(long fireAtTick, UUID victimUuid, double x, double y, double z, String kind) {
    public static final Codec<DeferredAction> CODEC = RecordCodecBuilder.create(...);
}
```
- Fields: `fireAtTick`, `victimUuid`, `x/y/z` (spawn position), `kind` (discriminator).
- Codec uses `Codec.LONG`, `UUIDUtil.CODEC`, `Codec.DOUBLE` (×3), `Codec.STRING`.
- Stored as `List<DeferredAction>` in `ChaosState` (optional, default empty).

### Dispatch
- Kind `"lightning"`: create `EntityType.LIGHTNING_BOLT` at (x,y,z), `setVisualOnly(false)`, `addFreshEntity`. (Re-randomize offset around victim at enqueue time.)

---

## 4. Events — full list

### MINOR (3)
| Event | Class | Behaviour | Duration |
|---|---|---|---|
| Hunger −3 | `HungerDrainEvent` | `victim.getFoodData().setFoodLevel(max(0, cur-3))` | 0 (instant) |
| Spawn spider | `SpawnSpiderEvent` | One `EntityType.SPIDER` 3–5 blocks from victim (same pattern as zombie). | 0 |
| Cobblestone cage | `CobbleCageEvent` | 3×3 walls, 2 tall: surrounding 8 columns @ feet & feet+1 level, skip player's column and non-air blocks. Uses `Blocks.COBBLESTONE`, `level.setBlock(pos, state, 3)`. | 0 |

### MEDIUM (5)
| Event | Class | Behaviour | Duration |
|---|---|---|---|
| Spawn zombie | `SpawnZombieEvent` *(existing, reclassify)* | Same code, tier `MEDIUM`. | 0 |
| Mining fatigue | `MiningFatigueEvent` | `victim.addEffect(MobEffectInstance(MobEffects.DIG_SLOWDOWN, 600, 1))`. | 600 ticks (30s) |
| Spawn creeper | `SpawnCreeperEvent` | One `EntityType.CREEPER` 3–5 blocks from victim. | 0 |
| Dizziness | `DizzinessEvent` | `victim.addEffect(MobEffectInstance(MobEffects.CONFUSION, 600, 0))`. | 600 ticks (30s) |
| Rainbow road | `RainbowRoadEvent` | Per-tick: get block below `victim.blockPosition()`; if different from last-placed pos and is air/replaceable, place wool (colored from palette via `DyeColor`), cycle index. Map per-victim state (`lastPos`, `colorIdx`). Cleanup in `onEnd()`. | 600 ticks (30s) |

### MAJOR (4)
| Event | Class | Behaviour | Duration |
|---|---|---|---|
| Blindness | `BlindnessEvent` | `victim.addEffect(MobEffectInstance(MobEffects.BLINDNESS, 600, 0))`. | 600 ticks (30s) |
| Teleport to ground | `TeleportToGroundEvent` | `level.getHeight(MOTION_BLOCKING_NO_LEAVES, bx, bz)` → teleport. | 0 |
| Thunder ×3 | `ThunderStrikeEvent` | First lightning immediately at random offset near victim. Enqueue 2 `DeferredAction`s of kind `"lightning"` at `now+60` and `now+120` with victim UUID. Scheduler delivers remaining strikes. | 0 (instant + deferred) |
| Crater | `CraterEvent` | Sphere radius 3 centered 2 below player's feet: `level.destroyBlock(pos, true)` for each block in sphere, skipping air & bedrock. Player drops into hole. | 0 |

### Deferred (later batch)
- Always jump (needs per-tick, no vanilla effect for "force jump")
- Always sprint (needs per-tick or client mixin)
- Reverse controls (needs client mixin + networking)

---

## 5. Files changed

### New files (13)
- `src/main/java/org/tupi/randomchaos/event/ChaosTier.java`
- `src/main/java/org/tupi/randomchaos/state/DeferredAction.java`
- `src/main/java/org/tupi/randomchaos/events/HungerDrainEvent.java`
- `src/main/java/org/tupi/randomchaos/events/SpawnSpiderEvent.java`
- `src/main/java/org/tupi/randomchaos/events/CobbleCageEvent.java`
- `src/main/java/org/tupi/randomchaos/events/MiningFatigueEvent.java`
- `src/main/java/org/tupi/randomchaos/events/SpawnCreeperEvent.java`
- `src/main/java/org/tupi/randomchaos/events/DizzinessEvent.java`
- `src/main/java/org/tupi/randomchaos/events/RainbowRoadEvent.java`
- `src/main/java/org/tupi/randomchaos/events/BlindnessEvent.java`
- `src/main/java/org/tupi/randomchaos/events/TeleportToGroundEvent.java`
- `src/main/java/org/tupi/randomchaos/events/ThunderStrikeEvent.java`
- `src/main/java/org/tupi/randomchaos/events/CraterEvent.java`

### Modified files (8)
- `src/main/java/org/tupi/randomchaos/event/ChaosEvent.java`
- `src/main/java/org/tupi/randomchaos/event/ChaosEventRegistry.java`
- `src/main/java/org/tupi/randomchaos/scheduler/ChaosScheduler.java`
- `src/main/java/org/tupi/randomchaos/state/ChaosState.java`
- `src/main/java/org/tupi/randomchaos/config/ChaosConfig.java`
- `src/main/java/org/tupi/randomchaos/events/SpawnZombieEvent.java` (reclassify tier)
- `src/main/java/org/tupi/randomchaos/RandomChaosMod.java` (register all events)
- `src/main/java/org/tupi/randomchaos/ChaosSelfTest.java` (add tier/cooldown/fallback tests)

---

## 6. Verification
```
./gradlew build
```
Compilation IS the typecheck per `AGENTS.md`. No `runGameTest` since no GameTests exist.

**⚠️ API assumptions** (to verify against 26.1.2 Mojang mappings during build):
- `FoodData.getFoodLevel()` / `setFoodLevel(int)`
- `MobEffects.DIG_SLOWDOWN`, `MobEffects.CONFUSION`, `MobEffects.BLINDNESS`
- `EntityType.SPIDER`, `EntityType.CREEPER`, `EntityType.LIGHTNING_BOLT`
- `Heightmap.Types.MOTION_BLOCKING_NO_LEAVES` + `level.getHeight(type, x, z)`
- `DyeColor` and `Blocks.WOOL` → use `DyeColor.getWoolBlock()` or `Block.state` lookup
- `level.destroyBlock(pos, true)` (drops)
- `LightningBolt.setVisualOnly(false)`
- `MobEffectInstance` constructor: `MobEffectInstance(MobEffect, int duration, int amplifier)`

All will be confirmed during implementation by building against the real loom-mapped MC jars.

---

## 7. Execution order
1. Create branch `agent/tiered-events`
2. Add `ChaosTier` enum
3. Add `tier()` + `tick()` + `onEnd()` to `ChaosEvent`
4. Modify `ChaosEventRegistry` — tier grouping + tiered `pickRandom`
5. Add config fields to `ChaosConfig`
6. Add state fields + codec to `ChaosState`
7. Add `DeferredAction` record + codec
8. Rewrite scheduler: `chooseTier`, deferred drain, active tick, onEnd, streak
9. Reclassify `SpawnZombieEvent`
10. Implement all 12 new events
11. Register all in `RandomChaosMod`
12. Add self-tests
13. `./gradlew build` — fix any mapping skids
14. Commit

---

# v2 — Review Revisions (2026-07-01)

Addresses every finding in `docs/review-2026-07-01-tiered-events-plan.md`. All API
claims below were independently verified via `javap` against
`minecraft-common-deobf-26.1.2.jar` (not just trusted from the review).

## Critical fixes (C1–C5)

- **C1 — MobEffects names.** Use `MobEffects.MINING_FATIGUE`, `MobEffects.NAUSEA`,
  `MobEffects.BLINDNESS`. (`DIG_SLOWDOWN`/`CONFUSION` do not exist.) VERIFIED.
- **C2 — Wool lookup.** `DyeColor` has no block methods. Use a static `Block[]`
  indexed by `DyeColor.ordinal()`:
  ```java
  private static final Block[] WOOL = { Blocks.WHITE_WOOL, Blocks.ORANGE_WOOL,
      Blocks.MAGENTA_WOOL, Blocks.LIGHT_BLUE_WOOL, Blocks.YELLOW_WOOL, Blocks.LIME_WOOL,
      Blocks.PINK_WOOL, Blocks.GRAY_WOOL, Blocks.LIGHT_GRAY_WOOL, Blocks.CYAN_WOOL,
      Blocks.PURPLE_WOOL, Blocks.BLUE_WOOL, Blocks.BROWN_WOOL, Blocks.GREEN_WOOL,
      Blocks.RED_WOOL, Blocks.BLACK_WOOL };
  ```
- **C3 — Config defaults.** New GSON fields get Java initializers AND validation
  in `load()` (since GSON leaves missing keys at `0`):
  ```java
  public int majorCooldownPicks = DEFAULT_MAJOR_COOLDOWN_PICKS;       // 6
  public int minorWeight   = DEFAULT_TIER_WEIGHTS[0];                  // 50
  public int mediumWeight  = DEFAULT_TIER_WEIGHTS[1];                  // 35
  public int majorWeight   = DEFAULT_TIER_WEIGHTS[2];                  // 40  (see M1)
  ```
  Validation: clamp `majorCooldownPicks` to `>=1`; clamp each weight to `>=0`;
  if `minor+medium+major == 0`, reset all to defaults.
- **C4 — tick()/onEnd() error handling.** Both wrapped in
  `try { ... } catch (Throwable t) { LOGGER.error(...); }`, identical to the
  existing `apply()` guard.
- **C5 — Execution order / compile break.** Two mitigations:
  1. `ChaosEventRegistry.pickRandom(RandomSource)` is KEPT as a delegating
     overload (picks uniformly across all tiers) so the call site compiles at
     every intermediate step.
  2. `DeferredAction` (record + codec) is created BEFORE `ChaosState` codec is
     extended. Execution order updated below.

## Major fixes (M1–M8)

- **M1 — MAJOR too rare (~2.5%).** `majorCooldownPicks` stays `6` (user's hard
  constraint: "MAJOR not picked more than once every 6 events"). Instead
  `majorWeight` is raised `15 → 40` so MAJOR fires ~40/125 = 32% of the time it
  is eligible.
  - **Probability analysis (defaults 50/35/40, cooldown 6, interval 120s):**
    - MAJOR eligible 1/6 ≈ 16.7% of picks; when eligible, P(MAJOR)=40/125=32%.
    - Effective per-pick P(MAJOR) ≈ 5.3%; per-pick P(MINOR)≈37%, P(MEDIUM)≈58%.
    - Hard ceiling: never more than 1 MAJOR per 6 picks.
    - Over a 2h session (~60 events): expected MAJORs ≈ 3.2 (vs 1.5 before).
  - `ChaosConfig` exposes the weights, so this is tunable without code changes.
- **M2 — CraterEvent safety guards.**
  - Skip if `victim.isCreative() || victim.isSpectator()`.
  - Abort if `centerY <= level.getMinBuildHeight() + 4` (no void carving).
  - Shape: **Euclidean distance ≤ 3.0** (resolves A5), centered 2 below feet.
  - Skip air + bedrock (`state.isAir()` or `state.getDestroySpeed(...)<=-1` /
    `Blocks.BEDROCK`).
  - **Spawn protection:** v1 relies on gamemode + Y guards; world-spawn radius
    check is documented as a follow-up (no claim-mod API assumed).
- **M3 — TeleportToGroundEvent dimension safety.** Dimension-aware target
  resolution:
  - Compute candidate via `level.getHeight(MOTION_BLOCKING_NO_LEAVES, x, z)`.
  - If candidate lands the player inside a solid block OR above the Nether
    ceiling bedrock, fall back to scanning DOWNWARD from the player's current Y
    to the first motion-blocking block with 2 air above it.
  - Abort (no-op) if no safe landing found. Skip creative/spectator.
- **M4 — DeferredAction bounds.**
  - Cap enqueue at `MAX_DEFERRED = 64` (drop oldest + warn).
  - On drain, also drop any action with `fireAtTick < now - STALE_TICKS`
    (STALE_TICKS = 6000, 5 min) — covers post-restart staleness.
- **M5 — onEnd ordering.** Explicit scheduler pseudocode (resolve BEFORE clear):
  ```java
  if (currentEffectExpiryTick != 0 && now >= currentEffectExpiryTick) {
      ChaosEvent old = currentEventId.isBlank() ? null
          : registry.get(Identifier.tryParse(currentEventId));
      ServerPlayer oldVictim = (currentVictimUuid == null) ? null
          : findByUuid(players, currentVictimUuid);
      if (old != null && oldVictim != null) {
          try { old.onEnd(oldVictim); } catch (Throwable t) { LOGGER.error(...); }
      }
      currentEventId = ""; currentVictimUuid = null; currentEffectExpiryTick = 0;
      changed = true;
  }
  ```
- **M6 — Victim offline.** Decision: **skip** `tick()`/`onEnd()` when victim is
  `null` (offline/dead). Scheduler guards; events need not null-check.
- **M7 — Self-tests.** Concrete cases added (see "Self-tests v2" below).
- **M8 — HUD / payload.** Decision: **tiers are server-side only for v1.** The
  `ChaosStatePayload` and `ChaosHudOverlay` are NOT modified; HUD continues to
  show event name + victim. Tier display is a documented follow-up.

## Ambiguity resolutions (A1–A5)

- **A1 tier fallback = Interpretation A.** After weighted pick, if chosen tier's
  pool is empty, scan MINOR→MEDIUM→MAJOR for the first non-empty pool. If ALL
  pools empty, scheduler bails (existing `size()==0` path).
- **A2 weight normalization = Interpretation A.** Normalize over **available
  tiers only**. Formula:
  `P(tier) = weight(tier) / Σ weight(availableTiers)`. No "no-pick" zone.
- **A3 Rainbow Road block-below = `victim.getOnPos().below()`** (the block the
  player is standing on, i.e. feet support block's top → place wool on the floor
  under the player). Equivalent intent: wool appears at the player's feet level
  on the surface they walk on. Use `blockPosition().below()` (feet block).
- **A4 Rainbow Road cleanup = leave wool in world.** `onEnd()` only clears the
  event's internal per-victim state map; placed wool stays (it's a decorative
  trail). No per-block tracking/removal (avoids unbounded memory).
- **A5 Crater shape = Euclidean distance ≤ 3.0.** (See M2.)

## Open questions — decisions

1. Rainbow Road cleanup → **leave wool** (A4).
2. MAJOR at challenge start → **grace period**: `picksSinceLastMajor` initializes
   to `0`, so MAJOR is blocked for the first `majorCooldownPicks` events (≈12 min
   at 120s interval). Gives new players a warm-up. Tunable via config.
3. HUD tier display → **server-only for v1** (M8).
4. Victim offline mid-event → **skip** tick/onEnd (M6).
5. Spawn-region guards → **gamemode + Y floor only for v1**; world-spawn radius
   check is a follow-up.
6. Deferred lightning position → **victim's CURRENT position at fire time**
   (resolve by UUID). `DeferredAction` stores `victimUuid` + `kind` only (no
   fixed x/y/z). If victim offline at fire time → skip the strike.

## DeferredAction (final shape)

```java
public record DeferredAction(long fireAtTick, UUID victimUuid, String kind) {
    public static final Codec<DeferredAction> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.LONG.fieldOf("fire_at_tick").forGetter(DeferredAction::fireAtTick),
        UUIDUtil.CODEC.fieldOf("victim_uuid").forGetter(DeferredAction::victimUuid),
        Codec.STRING.fieldOf("kind").forGetter(DeferredAction::kind)
    ).apply(i, DeferredAction::new));
}
```
Dispatch kind `"lightning"`: resolve victim by UUID; if online, spawn
`LIGHTNING_BOLT` at a random offset (2–4 blocks) around current pos,
`setVisualOnly(false)`, `addFreshEntity`.

## Self-tests v2 (concrete cases for `ChaosSelfTest`)

1. `chooseTierMajorBlockedByCooldown` — `picksSinceLastMajor=5`,
   `majorCooldownPicks=6`: MAJOR never returned over 2000 forced picks.
2. `chooseTierMajorAllowedAndResets` — `picksSinceLastMajor=6`,
   `majorCooldownPicks=6`, rigged RNG in MAJOR bucket: returns MAJOR; caller
   resets counter to 0.
3. `chooseTierWeightDistribution` — `majorCooldownPicks=1` (always eligible),
   20000 picks: each tier within ±5% of `weight/sum` for 50/35/40.
4. `chooseTierFallbackEmptyPool` — registry with only MEDIUM events: weighted
   pick of MINOR (empty) resolves to MEDIUM; pick of MAJOR resolves to MEDIUM.
5. `chooseTierFallbackAllEmpty` — registry empty → scheduler bails (no call to
   chooseTier); covered by existing `size()==0` path + a defensive guard.
6. `deferredDrainDueFires` — actions with `fireAtTick <= now` dispatched+removed;
   future actions remain.
7. `deferredStaleEvicted` — action with `fireAtTick < now - 6000` dropped
   without firing.
8. `stateCodecRoundTrip` — encode/decode `ChaosState` with `picksSinceLastMajor=3`
   and 2 deferred actions; assert equality.
9. `stateCodecBackwardCompat` — decode a map missing `picks_since_last_major`
   and `deferred_actions`; assert defaults `0` and empty list.

## Pre-mortem (added per review)

- **Risk: registry empty at runtime** → scheduler already bails; chooseTier
  guards all-empty and throws a caught `IllegalStateException`, logged once.
- **Risk: weighted pick with all-zero weights after bad config** → C3 validation
  resets to defaults; additionally `chooseTier` treats `sum==0` as uniform.
- **Risk: `tick()` throws every tick** → C4 try-catch prevents loop death; a
  throwing tick is logged each tick but does not advance expiry (acceptable; the
  event still expires on time).
- **Risk: rainbow wool griefs spawns/builds** → low; wool is replaceable, left
  in world (A4). Document; add a future `/randomchaos clear` if needed.
- **Risk: crater on claimed/protected land** → M2 gamemode guard; spawn-radius
  check deferred. Acceptable for a dev/friends server; revisit for public use.
- **Risk: deferred lightning after restart** → M4 stale eviction; also
  `DeferredAction` persists via codec so state survives cleanly.

## Verification v2

- `./gradlew build` (compile = typecheck).
- `ChaosSelfTest` (9 cases above) runs at startup.
- **Manual in-game checklist** (dev server `./gradlew runServer`):
  - [ ] Force-fire each tier via temp config (`intervalSeconds=5`); confirm HUD.
  - [ ] Cobble cage: 3×3 walls, 2 tall, player column + existing blocks intact.
  - [ ] Rainbow road: wool appears under feet while moving, color cycles, wool
        remains after event ends.
  - [ ] Thunder: 3 strikes ~3s apart, near (not always on) player.
  - [ ] Crater: creeper-radius hole, drops on ground, no void below min Y.
  - [ ] Teleport: works overworld; safe-fallback in Nether (no ceiling embed).
  - [ ] Cooldown: 6 MAJOR-eligible-spaced observation — no 2 MAJORs within 6.

## Config / save migration notes

- Old `config/randomchaos.json` (missing new keys) → C3 validation restores
  defaults. Next `reload` writes them back.
- Old saves missing `picks_since_last_major` / `deferred_actions` →
  `optionalFieldOf` defaults (`0`, empty list). Forward-compatible.
- `spec.md` §12 ("per-event weighting not yet supported") → updated: tier-level
  weighting now exists; per-event weights still future work.

## Revised execution order (v2)

1. Branch `agent/tiered-events` ✓
2. `ChaosTier` enum
3. `ChaosEvent` += `tier()` (default MEDIUM), `tick()`, `onEnd()`
4. `ChaosEventRegistry` += tier grouping + `pickRandom(rng, tier)`; KEEP
   `pickRandom(rng)` as delegating overload
5. `DeferredAction` record + codec
6. `ChaosState` += `picksSinceLastMajor`, `deferredActions` (optionalFieldOf)
7. `ChaosConfig` += cooldown + weights (field init + validation)
8. `ChaosScheduler` rewrite: `chooseTier`, deferred drain (+stale/cap), active
   `tick()` (try-catch, null guard), `onEnd()` before-clear (try-catch), streak
   update
9. `SpawnZombieEvent` += `tier() = MEDIUM`
10. 12 new event classes
11. Register all in `RandomChaosMod`
12. `ChaosSelfTest` += 9 cases
13. `./gradlew build` → fix skids
14. Update `spec.md` §12; write `docs/agent-sessions/2026-07-01-tiered-events.md`
15. (commit only on user request)
