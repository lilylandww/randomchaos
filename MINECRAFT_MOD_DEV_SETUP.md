# Minecraft Mod Development Setup (Headless Dev Server)

## Prerequisites

- A Linux server (headless, no GUI)
- SSH access
- Ability to run a Minecraft server on it

---

## 1. Install JDK 25

Minecraft 26.1 requires Java 25.

```bash
# Debian/Ubuntu
sudo apt install openjdk-25-jdk

# Or download from Adoptium
# https://adoptium.net/temurin/releases/?version=25

# Verify
java -version
```

---

## 2. Create the project

### Option A: Use the official Fabric template generator (recommended)

Go to **https://fabricmc.net/develop/template/** in your browser.

Fill in:
- **Mod name** (e.g. `My First Mod`)
- **Package name** (e.g. `com.example.myfirstmod`)
- **Minecraft version** (e.g. `26.1.2`)
- Click **Generate**, download the zip

Upload the zip to your server or extract locally and `scp` it up.

### Option B: Clone the example mod repo (CLI-only, all on server)

```bash
git clone https://github.com/FabricMC/fabric-example-mod.git my-mod
cd my-mod
rm -rf .git   # remove git history, start fresh
```

Then edit the project metadata:

**`gradle.properties`** — change `maven_group` and `archive_base_name` to match your mod.

**`fabric.mod.json`** — change `id`, `name`, `description` to match your mod.

---

## 3. Build the project

```bash
cd my-mod
./gradlew build
```

First run downloads Minecraft, Fabric API, mappings, and all dependencies (~1-2 min).

Your compiled mod `.jar` will be at:
```
build/libs/<mod-name>-<version>.jar
```

---

## 4. Test server-side gameplay

```bash
./gradlew runServer
```

This launches a full Minecraft dedicated server with your mod loaded, running on `localhost:25565`. Connect from any normal Minecraft client on another machine.

The server runs in the terminal. Press `Ctrl+C` to stop.

---

## 5. Test client-side code (optional, headless)

If your mod has client-only features (UIs, screens, rendering):

```bash
# Install Xvfb (virtual framebuffer)
sudo apt install xvfb

# Run the client headless
xvfb-run ./gradlew runClient
```

The game renders to an offscreen buffer — you won't see it but the code runs.

---

## 6. Automated tests (best for CI / quick verification)

Minecraft has a built-in **GameTest Framework** for writing automated tests:

```java
// Example test in your mod
@GameTest(template = "my_mod:tests/break_test")
public void breakTest(TestContext context) {
    context.pushButton(2, 3, 2);
    context.expectBlock(Blocks.AIR, 2, 3, 2);
}
```

Run all tests:
```bash
./gradlew runGameTest
```

Output shows pass/fail per test — fully headless, no display needed.

---

## 7. Iteration workflow

```bash
# 1. Edit code (vim, VSCode Remote, Cursor, etc.)

# 2. Build
./gradlew build

# 3. Run server to test
./gradlew runServer
# (stop with Ctrl+C and re-run after each change)

# Or run automated tests
./gradlew runGameTest
```

### Hotswap (optional, faster iteration)

Add to `build.gradle` in the `loom` block:

```groovy
loom {
    runConfigs.client {
        vmArgs "-XX:+AllowEnhancedClassRedefinition"
    }
    runConfigs.server {
        vmArgs "-XX:+AllowEnhancedClassRedefinition"
    }
}
```

Then changes apply without restarting the server (only method body changes, no structural changes).

---

## 8. Distributing the mod

```bash
./gradlew build
```

The final distributable `.jar` is in `build/libs/`. Drop it into any Minecraft `mods/` folder with the matching Fabric Loader installed.

---

## Quick command reference

| Goal | Command |
|---|---|
| Build the mod | `./gradlew build` |
| Run dev server | `./gradlew runServer` |
| Run client (headless) | `xvfb-run ./gradlew runClient` |
| Run automated tests | `./gradlew runGameTest` |
| Clean build artifacts | `./gradlew clean` |
| Full rebuild | `./gradlew clean build` |

---

## Notes

- You do **not** need to install Gradle separately — the `gradlew` wrapper script is included in the template and downloads the correct version automatically.
- The Fabric server runs in `--nogui` mode by default — perfect for headless.
- Client-only rendering features (shaders, custom UIs with complex visuals) cannot be verified visually on a headless server. Either use automated GameTests or transfer the `.jar` to a machine with a GPU and a real Minecraft client.
