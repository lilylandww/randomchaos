# Session — 2026-07-01 — Dev Environment Setup

## Goal
Set up a working Fabric mod development environment for `random-chaos-mc-mod`,
targeting a **stable Minecraft server version** (chose **26.1.2**) on a headless
Debian 13 server, ready for iterative development.

## Target stack (locked)
| Component | Version |
|---|---|
| Minecraft | `26.1.2` (stable) |
| Fabric Loader | `0.19.3` |
| Fabric API | `0.153.0+26.1.2` |
| Fabric Loom | `1.17-SNAPSHOT` (resolved to 1.17.13) |
| Mappings | Mojang official (no Yarn published for 26.1.2 yet) |
| JDK | OpenJDK **25.0.3** (`openjdk-25-jdk`, Debian trixie) |
| Mod id | `randomchaos` · group `org.tupi.randomchaos` |

## Decisions
- **MC version**: 26.1.2 (matches `MINECRAFT_MOD_DEV_SETUP.md` and the official
  `fabric-example-mod` default; user-chosen over 26.2 / 1.21.11).
- **Loader**: Fabric (user-chosen over NeoForge/Forge).
- **Scope**: server + client features → xvfb installed for headless client runs.
- **Package**: `org.tupi.randomchaos` (user asked to use `org` not `com`).
- **EULA**: `run/eula.txt` set to `eula=true` (user-approved; file is gitignored).
- **JDK install**: user ran `sudo apt install -y openjdk-25-jdk xvfb` (sudo needs
  a password, so agent could not run it non-interactively).
- **Hotswap**: the doc's `-XX:+AllowEnhancedClassRedefinition` flag is a
  JetBrains-Runtime/DCEVM-only option and is **rejected by vanilla OpenJDK 25**,
  which broke `runServer`. Removed from `build.gradle`; left a commented note on
  how to re-enable after installing JBR.

## Files changed (this session)
**Created (scaffolded from `fabric-example-mod`, customized):**
- `build.gradle`, `settings.gradle`, `gradle.properties`
- `gradlew`, `gradlew.bat`, `gradle/wrapper/*`
- `src/main/java/org/tupi/randomchaos/RandomChaosMod.java`
- `src/main/java/org/tupi/randomchaos/mixin/RandomChaosMixin.java`
- `src/client/java/org/tupi/randomchaos/client/RandomChaosClient.java`
- `src/client/java/org/tupi/randomchaos/client/mixin/RandomChaosClientMixin.java`
- `src/main/resources/{fabric.mod.json, randomchaos.mixins.json, assets/randomchaos/icon.png}`
- `src/client/resources/randomchaos.client.mixins.json`
- `.gitignore`, `.gitattributes`, `LICENSE` (MIT), `README.md`
- `AGENTS.md` (local agent notes / build commands)
- `docs/agent-sessions/2026-07-01-setup-dev-env.md` (this file)

**Unchanged:** `MINECRAFT_MOD_DEV_SETUP.md` (verified identical via `git diff`).

## Commands run
| Command | Result |
|---|---|
| `git init -b main` + initial doc commit + `git checkout -b agent/setup-dev-env` | branch created |
| `sudo apt install -y openjdk-25-jdk xvfb` (run by user) | OpenJDK 25.0.3 + xvfb installed |
| `./gradlew build --no-daemon` | **BUILD SUCCESSFUL** in 1m32s → `build/libs/randomchaos-1.0.0.jar` |
| `./gradlew runServer --no-daemon` (smoke test) | server **up in 35s**; `Done (8.927s)!`; mod loaded (`randomchaos 1.0.0`), `Hello Fabric world!` logged |

## Verification
- [x] `java -version` → openjdk 25.0.3
- [x] `xvfb-run` present
- [x] `./gradlew build` succeeds, jar produced (`randomchaos-1.0.0.jar`, 6.6 KB)
- [x] `./gradlew runServer` boots, loads mod, reaches `Done!`, listens on `*:25565`
- [x] No `com.example` / `examplemod` / `ExampleMod` leakage in sources
- [x] `MINECRAFT_MOD_DEV_SETUP.md` unchanged
- [ ] `xvfb-run ./gradlew runClient` — **not run**: client entrypoint compiles
      (`:compileClientJava` green) but is empty/trivial; live headless client run
      deferred to user (no visual verification possible headless anyway).
- [ ] `./gradlew runGameTest` — **not run**: no GameTests defined yet
      (`:compileTestJava NO-SOURCE`).

## Known limitations / next steps
- Enhanced hotswap requires JBR/DCEVM (not installed). Standard JDWP method-body
  hotswap works via debug configs without extra flags.
- `fabric.mod.json` `contact.sources` still points at the example-mod repo —
  update once the project has its own repository URL.
- No actual mod logic yet (`onInitialize` just logs). Add chaos-event features
  under `org.tupi.randomchaos` next.
- Add GameTests for server-side behavior once features exist.

## Git
- Branch: `agent/setup-dev-env` (off `main`).
- `main` holds the initial `MINECRAFT_MOD_DEV_SETUP.md` commit.
