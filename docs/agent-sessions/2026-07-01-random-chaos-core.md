# 2026-07-01 — Random Chaos core (parallel agent orchestration)

## Goal
Implement the core loop for the Random Chaos challenge mod: a random event
fires on one player every X seconds (configurable, default 120s), effects last
at most 70% of the interval, a client HUD shows time-since-start / time-to-next
/ active-effect, and the challenge ends when the Ender Dragon dies. Target:
MC 26.1.2 / Java 25 / Fabric, Mojang mappings.

## Approach
Work was delegated to 5 parallel `coder` sub-agents (Wave 1), each with a
pinned interface contract so the pieces integrated with no signature drift:

| Wave 1 agent (parallel)             | Deliverables                                                       |
|-------------------------------------|--------------------------------------------------------------------|
| State layer                         | `state/ChaosState.java`, `state/ChaosStateManager.java`            |
| Event system                        | `event/ChaosEvent.java`, `event/ChaosEventRegistry.java`, `events/SpawnZombieEvent.java` |
| Networking (payload + server sender)| `net/ChaosStatePayload.java`, `net/ChaosNetworking.java`           |
| Config                              | `config/ChaosConfig.java`                                          |
| Client side                         | `client/net/ClientChaosState.java`, `client/net/ClientNetworking.java`, `client/hud/ChaosHudOverlay.java` (modified `RandomChaosClient.java`) |

Wave 2 (central, coupled wiring) was done directly by the orchestrator:
`scheduler/ChaosScheduler.java`, `lifecycle/ChaosLifecycle.java`,
`command/RandomChaosCommand.java`, plus `RandomChaosMod.onInitialize()` wiring
and `ChaosSelfTest.java` (startup self-test replacing structure-based
GameTests — see notes).

## Files changed
- **New (main source set, 12):**
  - `state/ChaosState.java`, `state/ChaosStateManager.java`
  - `event/ChaosEvent.java`, `event/ChaosEventRegistry.java`, `events/SpawnZombieEvent.java`
  - `net/ChaosStatePayload.java`, `net/ChaosNetworking.java`
  - `config/ChaosConfig.java`
  - `scheduler/ChaosScheduler.java`, `lifecycle/ChaosLifecycle.java`, `command/RandomChaosCommand.java`
  - `ChaosSelfTest.java`
- **New (client source set, 3):** `client/net/ClientChaosState.java`, `client/net/ClientNetworking.java`, `client/hud/ChaosHudOverlay.java`
- **Modified:** `RandomChaosMod.java` (onInitialize wiring), `RandomChaosClient.java` (register receiver + HUD)
- **Generated at runtime (gitignored under run/):** `run/config/randomchaos.json`

## Commands run
- `./gradlew build` — green (main + client source sets)
- `./gradlew clean build` — green
- `./gradlew runServer` (bounded, to exercise startup) — mod init OK,
  `Random Chaos self-test PASSED (10 checks)`, `Events registered: 1`,
  `run/config/randomchaos.json` written with defaults.

## Notable MC 26.1.2 API discoveries
Sub-agents had to resolve several APIs that differ from older guides:
- `PersistentState` → `net.minecraft.world.level.saveddata.SavedData` +
  `SavedDataType<T>` (codec-based, no `writeNbt`/`load`).
- Fabric `CustomPayload` removed → use vanilla `CustomPacketPayload` with
  `.Type<T>` / `.type()` (field renamed `ID` → `TYPE`).
- Fabric `HudRenderCallback` removed → use
  `net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.addLast(...)`
  with a `HudElement` whose method is `extractRenderState(GuiGraphicsExtractor, DeltaTracker)`.
- `GuiGraphics` renamed to `GuiGraphicsExtractor` (`text(...)` not `drawString(...)`).
- `Zombie` moved to `net.minecraft.world.entity.monster.zombie.Zombie`;
  `EntityType.ZOMBIE.create(Level, EntitySpawnReason)`.
- Lifecycle tick event is `ServerTickEvents.END_SERVER_TICK` (not `END_SERVER`).
- Command permissions are now `PermissionSet` / `PermissionLevel` /
  `Permission.HasCommandLevel` — `CommandSourceStack.hasPermission(int)` is gone.
- `ServerPlayConnectionEvents.JOIN` handler exposes `handler.player` (field).

## Testing decision
The Fabric gametest API in 26.1.2 requires a per-test `.nbt` structure template
(no structureless option), which is high friction for two pure-function checks.
Implemented a startup `ChaosSelfTest` instead that runs at `onInitialize`,
exercises the two pure helpers, and throws on failure:
- `ChaosScheduler.clampDuration(event, interval, ratio)` — 5 cases incl. 70% cap.
- `ChaosScheduler.pickVictimUuid(...)` — single-player, empty-list, and a
  500-iteration "no 5-in-a-row" invariant check (max observed ≤ 4), plus the
  single-survivor exception.
All 10 checks pass at runtime. Formal `@GameTest`s can be added later if a
structure-based suite is desired.

## Open items
- Real event list: each future event = one class in `events/` + one
  `ChaosEventRegistry.INSTANCE.register(...)` line in `onInitialize`.
- Instant events currently leave the HUD's "Now" line blank (no display
  linger); timed events populate it. Revisit if instant events should flash.
- Client HUD victim-name lookup is local-only; offline victims show "?".

## Branch
`agent/random-chaos-core` (forked from `agent/setup-dev-env`). Not merged.
