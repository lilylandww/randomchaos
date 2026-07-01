# Random Chaos — Agent Notes

Fabric mod targeting **Minecraft 26.1.2** on **Java 25** (Debian 13 / trixie).

## Stack
- Minecraft: `26.1.2`
- Fabric Loader: `0.19.3`
- Fabric API: `0.153.0+26.1.2`
- Fabric Loom: `1.17-SNAPSHOT` (Mojang official mappings — no Yarn)
- Group: `org.tupi.randomchaos`, mod id: `randomchaos`

## Commands
| Goal | Command |
|---|---|
| Build | `./gradlew build` |
| Dev server (localhost:25565) | `./gradlew runServer` |
| Dev client (headless) | `xvfb-run ./gradlew runClient` |
| Automated GameTests | `./gradlew runGameTest` |
| Clean | `./gradlew clean` |
| Full rebuild | `./gradlew clean build` |

## Lint / typecheck
No separate linter configured. Compilation IS the typecheck:
```
./gradlew build
```
A successful `build` includes `compileJava` (main + client source sets) and `runData`/remap. Treat any compile error as a hard failure.

## Source layout (split source sets)
- `src/main/java/` — common + server entrypoint (`RandomChaosMod`)
- `src/client/java/` — client entrypoint (`RandomChaosClient`) and client mixins
- `src/main/resources/fabric.mod.json` — mod metadata
- Mixin configs: `src/main/resources/randomchaos.mixins.json`, `src/client/resources/randomchaos.client.mixins.json`

## Conventions
- Package everything under `org.tupi.randomchaos`.
- Use `RandomChaosMod.LOGGER` for logging; `RandomChaosMod.id(path)` for `Identifier`s.
- Server-only logic goes in `src/main`; rendering/UI/screens go in `src/client`.

## Git
- Never commit to `main`. Branch as `agent/<task>` or `feat/<task>`.
- `run/`, `build/`, `.gradle/` are gitignored — never commit them.
- `run/eula.txt` has `eula=true` (dev server).

## First-run notes
- `run/eula.txt` is gitignored; created on first `runServer` — set `eula=true` then re-run.
- First build downloads MC 26.1.2 + mappings + Fabric API (~1–2 min).
- Client-side visuals cannot be verified headless; use GameTests or a real client with a GPU.
