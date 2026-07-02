# Session: Tiered Events — 2026-07-01

## Goal
Add event tiers (MINOR / MEDIUM / MAJOR) with an anti-burst picker, and implement the first batch of 12 events. Driven by plan `docs/2026-07-01-tiered-events-plan.md` (v2 revised per `docs/review-2026-07-01-tiered-events-plan.md`).

## Branch
`agent/tiered-events` (off `main` @ `5cc4d1f`). **Not committed** — awaiting user go-ahead.

## What changed

### Core: tier system + anti-burst picker
- **`event/ChaosTier.java`** (new) — enum `MINOR | MEDIUM | MAJOR` + `isHarmful()`.
- **`event/ChaosEvent.java`** — added `ChaosTier tier()` (default `MEDIUM`), `default void tick(ServerPlayer, long now)`, `default void onEnd(ServerPlayer)`.
- **`event/ChaosEventRegistry.java`** — tier-grouped storage; `pickRandom(rng, ChaosTier)`; kept `pickRandom(rng)` as a delegating overload (avoids compile breaks); `hasTier(ChaosTier)`.
- **`scheduler/ChaosScheduler.java`** — rewritten:
  - `chooseTier(...)` pure weighted picker: MAJOR candidate only when `picksSinceLastMajor >= majorCooldownPicks`; normalizes weights over **available** tiers only; relaxes cooldown if MAJOR is the only tier with events; uniform fallback if all weights are 0.
  - Per-tick: `onEnd()` (before clear) → `tick()` active event → `fireEvent()` → `drainDeferred()`. `tick()`/`onEnd()`/`apply()` all wrapped in `try/catch(Throwable)`; victim-online null guards.
  - `drainDeferred()` with stale eviction (`fireAtTick < now - 6000` dropped) + `enqueueDeferred` cap (`MAX_QUEUE = 64`).
  - `spawnLightningNear()` helper.
- **`state/ChaosState.java`** — added `picksSinceLastMajor` (int) and `deferredActions` (`List<DeferredAction>`) via `optionalFieldOf` (backward-compatible codec); `enqueueDeferred(...)` helper.
- **`state/DeferredAction.java`** (new) — record `(fireAtTick, victimUuid, kind)` + codec + `KIND_LIGHTNING`/`MAX_QUEUE`/`STALE_TICKS` constants.
- **`config/ChaosConfig.java`** — `majorCooldownPicks` (6), `minorWeight` (50), `mediumWeight` (35), `majorWeight` (40); Java field initializers **and** `load()` validation (C3 fix).

### Events (12)
- **MINOR:** `HungerDrainEvent` (−3 food), `SpawnSpiderEvent`, `CobbleCageEvent` (3×3 walls, 2 tall, skip center + non-replaceable).
- **MEDIUM:** `SpawnZombieEvent` (reclassified), `MiningFatigueEvent` (`MINING_FATIGUE` 30s), `SpawnCreeperEvent`, `DizzinessEvent` (`NAUSEA` 30s), `RainbowRoadEvent` (per-tick wool trail, per-victim state map, cleaned in `onEnd`).
- **MAJOR:** `BlindnessEvent` (`BLINDNESS` 30s), `TeleportToGroundEvent` (scan-down safe landing + heightmap fallback, creative/spectator skip), `ThunderStrikeEvent` (3× lightning via deferred queue at 0/+60/+120 ticks), `CraterEvent` (Euclidean r≤3 sphere, `destroyBlock(pos,true)` drops, void/bedrock guards).
- Registered all 12 in `RandomChaosMod.onInitialize()`.

### Tests
- **`ChaosSelfTest.java`** — 7 new `chooseTier` cases (cooldown block, eligibility, weight distribution ±5%, empty-pool fallback, all-empty throw, cooldown relaxation). Total **22 checks** (was 15).

## Notable: pre-existing bug found & fixed
`ChaosState` declared `TYPE` **before** `CODEC`, and `TYPE`'s initializer referenced `ChaosState.CODEC` — a static forward-reference. Java initializes static fields in textual order, so `TYPE` captured `CODEC` while it was still `null`. This made `SavedDataType.codec()` return null, crashing the server on the first autosave (`SavedDataStorage.encodeUnchecked` → `codec.encodeStart` → NPE). The save path had never been exercised before, so the bug was latent in committed code. **Fix:** reordered so `CODEC` is declared before `TYPE`. Verified: server now survives autosave and writes `randomchaos_state.dat`.

## API verification (independent `javap` against `minecraft-common-deobf-26.1.2.jar`)
Confirmed (not trusted from review): `MobEffects.MINING_FATIGUE`/`NAUSEA`/`BLINDNESS` (NOT `DIG_SLOWDOWN`/`CONFUSION`); `DyeColor` has no wool method (used static `Block[]`); `FoodData.getFoodLevel()/setFoodLevel(int)`; `MobEffectInstance(Holder<MobEffect>, int, int)`; `EntityType.{SPIDER,CREEPER,LIGHTNING_BOLT}`; `Heightmap.Types.MOTION_BLOCKING_NO_LEAVES`; `Level.getHeight/destroyBlock(pos,true via LevelWriter)/setBlock`; `BlockState.canBeReplaced()/isAir()` (class is in package `block.state`, not `block`); `LightningBolt.setVisualOnly(boolean)`; build-height accessors are `getMinY()`/`getHeight()` (not `getMin/MaxBuildHeight`).

## Decisions (resolving review ambiguities)
- A1 fallback = scan available tiers (empty tier never selected by construction).
- A2 weights normalized over available tiers only.
- A3/A4 Rainbow Road: target `blockPosition().below()`, replace unless air/bedrock/block-entity; wool **left in world** on `onEnd` (only state map cleared).
- A5 crater = Euclidean ≤ 3.0.
- MAJOR cooldown stays **6** (user's hard rule); `majorWeight` raised 15→40 so MAJOR fires ~32% of eligible picks (≈3 per 60-event session). Effective per-pick ≈ 5% with hard ceiling of 1 per 6.
- HUD tier display = server-only for v1 (payload/overlay unchanged).
- Victim offline mid-effect → skip `tick`/`onEnd`.
- `picksSinceLastMajor` starts at 0 → MAJOR blocked for the first 6 events (grace period).

## Deferred (later batch)
Always jump, Always sprint (need per-tick), Reverse controls (need client mixin + networking).

## Verification run
- `./gradlew build` — **BUILD SUCCESSFUL** (compile = typecheck).
- `./gradlew runServer` (dev server, ~130s) — **22/22 self-tests PASSED**, 12 events registered, no NPE, autosave succeeded, `randomchaos_state.dat` written.

## Commands run
```
git checkout -b agent/tiered-events
./gradlew build                 # iterated to green
./gradlew runServer             # runtime self-tests + save check (timeout-killed, no crash)
javap -cp ...minecraft-common-deobf-26.1.2.jar ...   # API verification
```

## Notes
- A stray untracked `net/` directory (3 `.class` files) exists at repo root — not created in this session, not staged. Left as-is.
- All `run/` artifacts are gitignored.

## Follow-up: settings via slash commands
User opted for admin slash commands over a config UI. Extended `/randomchaos`
(op level 2, inherited from the root node's `GAMEMASTERS` requirement):
- **`config/ChaosConfig.java`** — extracted `validate()`, added `save()`
  (validate + write), generalized the writer.
- **`command/RandomChaosCommand.java`** — added `show`, `interval <s>`,
  `cap <ratio>`, `cooldown <picks>`, `weight minor|medium|major <value>`.
  Each setter mutates the field, calls `ChaosConfig.save()`, and replies with
  the post-validation value.
- **Verified live** on the dev server console: `show` →
  `interval=120s, cap=0.70, majorCooldown=6, weights=50/35/40`;
  `interval 90` → `intervalSeconds set to 90s (saved).`;
  `weight major 45` → persisted. `save()` also surfaced the previously-missing
  tier keys into `randomchaos.json`.
- `spec.md` §9 updated with the command table.
