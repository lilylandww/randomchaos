# Random Chaos — Specification

A Fabric mod that turns the vanilla Minecraft playthrough (defeat the Ender
Dragon) into a timed challenge: every **X seconds** a random **chaos event**
is inflicted on **one randomly chosen player**. A client HUD shows the timers.
The challenge ends when the Ender Dragon dies.

- **Target:** Minecraft `26.1.2`, Java `25`, Fabric Loader `0.19.3`,
  Fabric API `0.153.0+26.1.2`, Mojang official mappings.
- **Mod id:** `randomchaos` — group `org.tupi.randomchaos`.

---

## 1. Core loop

1. **Challenge starts** the moment the first player joins (state
   `challengeStartTick` is set). See §4 for the full state model.
2. A server tick hook (`ChaosScheduler`, on `ServerTickEvents.END_SERVER_TICK`)
   advances the loop every tick:
   - If an effect is active and its expiry has passed → clear it, broadcast.
   - If `now >= nextEventTick` → **fire an event** (see §3), then schedule the
     next one at `now + intervalTicks`.
   - Otherwise, broadcast the full state to clients every 20 ticks so the HUD
     countdowns stay fresh.
3. **Challenge ends** when the Ender Dragon dies (`challengeEndTick` is set);
   the tick hook stops firing events and the HUD freezes on the final time.

The loop only runs while the challenge is active (`challengeStartTick != 0`
and `challengeEndTick == 0`).

## 2. Timing rules

| Quantity | Rule |
|---|---|
| Event interval `X` | configurable, default **120 s** (`intervalSeconds`). See §6. |
| Effect duration cap | `min(event.defaultDurationTicks, intervalTicks × effectCapRatio)`. Default ratio **0.7** → at 120 s interval, an effect lasts at most **84 s**. |
| Instant events | `defaultDurationTicks == 0` — fire once, leave no lingering effect (HUD "Now" line stays blank until the next timed event). |
| Timed events | duration clamped to the cap above; the HUD shows the active event, its victim, and the remaining time. |

A timed effect therefore **always expires before the next event fires**
(≤70% of the interval), so a player is never under two effects at once.

## 3. Firing an event

When `now >= nextEventTick` and the challenge is active:

1. Collect online players. If none → push `nextEventTick` forward by one
   interval and wait.
2. If the registry is empty → same (push forward, wait).
3. **Pick a victim** via `ChaosScheduler.pickVictimUuid(...)` — uniform random
   subject to the **no-5-in-a-row** rule (§5).
4. **Pick an event** uniformly at random from `ChaosEventRegistry`.
5. Apply it (`event.apply(victim)`); failures are caught and logged so a
   buggy event never crashes the tick loop.
6. Write the result into state:
   - Timed event → `currentEventId`, `currentVictimUuid`,
     `currentEffectExpiryTick = now + clamp(...)`.
   - Instant event → clear all three.
7. Update the consecutive-pick counter (§5) and set `nextEventTick`.

## 4. State model & persistence

Canonical state lives on the **overworld** as a `SavedData` named
`randomchaos_state` (`ChaosState`), so it survives server restarts.

| Field | Type | Meaning |
|---|---|---|
| `challengeStartTick` | `long` | `0` = not started. |
| `challengeEndTick` | `long` | `0` = not ended. |
| `nextEventTick` | `long` | Tick at which the next event fires. |
| `currentEventId` | `String` | `""` = no active effect. The event's `Identifier` as a string otherwise. |
| `currentVictimUuid` | `UUID?` | `null` = no active effect. |
| `currentEffectExpiryTick` | `long` | `0` = no active effect. |
| `lastVictimUuid` | `UUID?` | For the no-5-in-a-row rule. |
| `consecutivePicks` | `int` | Times `lastVictimUuid` was picked in a row. |

Serialization is codec-based (`ChaosState.CODEC`).

### Networking (S2C)
`ChaosStatePayload` (`randomchaos:id("state")`) is broadcast to all clients
on every state change and once per second. It carries `serverTick` (so clients
can compute live countdowns) plus a mirror of the state fields above.

## 5. Victim selection — "no 5 in a row"

`ChaosScheduler.pickVictimUuid(playerUuids, lastVictimUuid, consecutivePicks, rng)`:

- Empty list → `IllegalStateException`.
- Single player → always returned (the rule cannot be enforced with no
  alternative; the only player may exceed 4 in a row).
- Otherwise: draw uniformly at random, **rejecting** a candidate that would be
  the **5th consecutive** pick of the same player. After 16 rejections, fall
  back to the first player that isn't the last victim.

Verified by `ChaosSelfTest` at startup (500-iteration invariant check: max
observed run ≤ 4, except the single-survivor case).

## 6. Configuration

`<game-dir>/config/randomchaos.json`, Gson-serialized, auto-written with
defaults if missing or unreadable (parse failures fall back to defaults with an
`ERROR` log — never crashes).

```json
{
  "intervalSeconds": 120,
  "effectCapRatio": 0.7
}
```

| Key | Default | Valid | Notes |
|---|---|---|---|
| `intervalSeconds` | `120` | `> 0` (else clamped to 120 + warn) | Seconds between events. |
| `effectCapRatio` | `0.7` | `(0.0, 1.0]` (else clamped to 0.7 + warn) | Max fraction of the interval an effect may last. |

Reload at runtime with `/randomchaos reload` (permission level 2 /
`PermissionLevel.GAMEMASTERS`). Note: changes apply to **newly fired** events;
already-running effect expiries are not retroactively recomputed.

## 7. Client HUD

Top-right overlay (`ChaosHudOverlay`, registered via
`HudElementRegistry.addLast`). Renders with `GuiGraphicsExtractor`. Hidden when
no level/player is loaded.

| Line | When | Color |
|---|---|---|
| `Chaos  HH:MM:SS` | challenge running (elapsed since start) | white |
| `Chaos  --:--:--` | not started yet | grey |
| `Next  MM:SS` | running (countdown to next event) | gold |
| `Now: <event> → <victim>  (MM:SS)` | a timed effect is active | aqua |
| `Now: —` | no active effect | aqua |
| `FINISHED in HH:MM:SS` | challenge ended (`challengeEndTick != 0`) | green — replaces all other lines |

- Event name is humanized from the id path (`randomchaos:spawn_zombie` →
  `spawn zombie`).
- Victim name resolves via the local client level; `?` if not found.
- The client estimates the current server tick from the last payload's
  `serverTick` plus elapsed client game time, so countdowns tick smoothly
  between the once-per-second broadcasts.

## 8. Event extension model

Adding an event:

1. Write a class implementing `org.tupi.randomchaos.event.ChaosEvent`:
   ```java
   public interface ChaosEvent {
       Identifier id();                 // unique; use RandomChaosMod.id("...")
       void apply(ServerPlayer victim);
       int defaultDurationTicks();      // 0 = instant
       default boolean instant() { return defaultDurationTicks() <= 0; }
   }
   ```
2. Register it in `RandomChaosMod.onInitialize()`:
   ```java
   ChaosEventRegistry.INSTANCE.register(new MyEvent());
   ```
3. Nothing else — the scheduler, networking, HUD, and persistence pick it up
   automatically.

`ChaosEventRegistry` throws `IllegalStateException` on duplicate ids and on
`pickRandom` when empty. Selection is uniform; per-event weighting is not yet
supported.

## 9. Commands

| Command | Permission | Effect |
|---|---|---|
| `/randomchaos reload` | level 2 | Re-reads `config/randomchaos.json`. |

Start and end are automatic (first join / dragon death) — there are no manual
start/stop commands by design.

## 10. Source layout

```
src/main/java/org/tupi/randomchaos/
├── RandomChaosMod.java        // entrypoint; wires everything
├── ChaosSelfTest.java         // startup invariant checks
├── config/    ChaosConfig.java
├── state/     ChaosState.java, ChaosStateManager.java
├── event/     ChaosEvent.java, ChaosEventRegistry.java
├── events/    SpawnZombieEvent.java   // concrete events live here
├── scheduler/ ChaosScheduler.java     // tick loop + pure helpers
├── lifecycle/ ChaosLifecycle.java     // first-join start, dragon-death end
├── net/       ChaosStatePayload.java, ChaosNetworking.java
└── command/   RandomChaosCommand.java

src/client/java/org/tupi/randomchaos/client/
├── RandomChaosClient.java     // registers receiver + HUD
├── net/  ClientChaosState.java, ClientNetworking.java
└── hud/  ChaosHudOverlay.java
```

## 11. Verification

- `./gradlew build` — compiles main + client source sets (this is the
  typecheck; no separate linter).
- `ChaosSelfTest.run()` at startup — 10 checks covering `clampDuration` (5
  cases incl. the 70% cap) and `pickVictimUuid` (single-player, empty-list,
  500-iteration no-5-in-a-row invariant, single-survivor exception). Throws
  on failure, so a logic regression prevents the server from starting.
- Manual: `./gradlew runServer`, join once, confirm the HUD counts down and a
  zombie spawns on the victim at the interval; kill the dragon and confirm the
  HUD shows `FINISHED`.

> **Note on GameTests:** Fabric's 26.1.2 gametest API requires a per-test
> `.nbt` structure template (no structureless option), which is high friction
> for these two pure-function checks. The startup self-test covers them
> instead. Structure-based `@GameTest`s can be added later if desired.

## 12. Known limitations / future work

- **Instant events** don't linger on the HUD — the "Now" line only shows timed
  effects. Add a short display window if instant events should flash.
- **No per-event weights** — selection is uniform. Add a `weight()` to
  `ChaosEvent` and a weighted pick if severity tiers are needed.
- **Victim name lookup is client-local** — offline victims render as `?`.
- **No manual pause/resume** — the timer runs whenever the server runs.
- **Effect expiry is set at fire time** from the then-current config; later
  config reloads don't retroactively change running effects.
