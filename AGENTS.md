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
- **Keep docs in sync.** Any significant change — new event, command, config key,
  behaviour, or workflow — must update the relevant doc under `docs/` and
  `spec.md` / `README.md` if it describes the changed surface. Out-of-date docs
  are a bug.

## Git
- Never commit to `main`. Branch as `agent/<task>` or `feat/<task>`.
- `run/`, `build/`, `.gradle/` are gitignored — never commit them.
- `run/eula.txt` has `eula=true` (dev server).

## Versioning & releases
Versioning is **Semantic Versioning** `MAJOR.MINOR.PATCH` (e.g. `1.2.0`),
stored in `gradle.properties` → `mod_version`. The version applies to the mod
itself, not to the Minecraft/Fabric versions it targets.

### Bump rules
Pick the **highest** level that applies to the change set:

| Bump | When to use | Examples |
|---|---|---|
| **MAJOR** (`x.0.0`) | Anything that breaks existing worlds, saves, configs, or clients. | `ChaosState` codec/SavedData changes incompatible with existing `randomchaos_state` saves; `config/randomchaos.json` schema breaks; `ChaosStatePayload` wire format changes that break older clients; removing/renaming an event id that a running world references; dropping a supported MC version. |
| **MINOR** (`0.x.0`) | New functionality, backward-compatible. | New chaos event or command; new config key (additive); new HUD element; new tier; behaviour additions that don't invalidate existing saves. |
| **PATCH** (`0.0.x`) | Bug fixes / hardening with no behaviour change for correct usage. | Fix to event cleanup; balance tweak (cooldown/weight/ratio); logging or error-message fix; null-guard / crash fix; internal refactor. |

Rules of thumb:
- **Save-format = major.** The `randomchaos_state` SavedData and the
  `ChaosStatePayload` S2C payload are the contract with existing worlds/clients.
  Breaking either is always a MAJOR bump, even if the change feels small.
- **New config key = minor** as long as it has a default and old configs still load.
  Renaming/removing a config key = MAJOR (or migrate it explicitly).
- **Balance tweaks (interval, weights, ratios, cooldowns) = patch** — they change
  feel, not the format.
- When in doubt between patch and minor, choose **minor** (additive ≠ invisible).
- Only one bump per release: a release that contains a breaking change plus
  features and fixes is just a MAJOR bump.

### Release flow (tag-driven, no manual `gradle.properties` edit)
Releases are produced by `.github/workflows/release.yml`. **Do not hand-edit
`mod_version` before tagging** — the workflow derives the version from the git
tag and writes it into `gradle.properties` at build time.

1. Decide the new version per the rules above (e.g. `1.2.0`).
2. On `main` (after your feature branch is merged), tag and push:
   ```
   git tag v1.2.0
   git push origin v1.2.0
   ```
3. The `Release` workflow strips the `v`, writes `mod_version=1.2.0` into
   `gradle.properties`, runs `./gradlew build`, and creates a GitHub Release
   with `randomchaos-1.2.0.jar` (and `-sources`) attached.
4. **After** a release goes out, commit `mod_version` back to `main` so the
   working tree matches the published artifact (the CI bump is not committed):
   ```
   git checkout main && git pull
   # set gradle.properties mod_version=1.2.0, commit "release: bump to 1.2.0"
   ```

Tag format must be `v<MAJOR.MINOR.PATCH>` (optionally with a suffix like
`-rc.1`). Anything else fails the workflow's version check. CI (`.github/workflows/build.yml`)
runs `./gradlew build` on every push/PR to `main`.

## First-run notes
- `run/eula.txt` is gitignored; created on first `runServer` — set `eula=true` then re-run.
- First build downloads MC 26.1.2 + mappings + Fabric API (~1–2 min).
- Client-side visuals cannot be verified headless; use GameTests or a real client with a GPU.
