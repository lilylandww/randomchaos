# Random Chaos

A Fabric mod that injects random chaos events into the game during a timed
challenge. Every **X seconds** a random event fires on a randomly chosen player.
The challenge ends when the Ender Dragon dies.

Targets Minecraft 26.1.2 / Fabric.

## Build
```
./gradlew build
```

## Run
```
./gradlew runServer
xvfb-run ./gradlew runClient
```

## Commands

All `/randomchaos` subcommands require **op level 2** (gamemasters).

| Command | Effect |
|---|---|
| `/randomchaos show` | Print current settings (interval, cap, cooldown, weights). |
| `/randomchaos reload` | Re-read `config/randomchaos.json` from disk. |
| `/randomchaos interval <seconds>` | Set event interval (1–86400). |
| `/randomchaos cap <ratio>` | Set timed-effect cap (0.001–1.0). |
| `/randomchaos cooldown <picks>` | Set min picks between MAJOR events (≥1). |
| `/randomchaos weight minor\|medium\|major <value>` | Set tier weight (≥0). |

Set commands validate and persist to `config/randomchaos.json` immediately.

## Event tiers

Events are grouped into three difficulty tiers:

| Tier | Examples | Effect |
|---|---|---|
| **MINOR** | hunger drain, spider spawn, cobble cage, adventure mode, clay fill, leaves on break | Minor inconveniences |
| **MEDIUM** | zombie spawn, mining fatigue, creeper, dizziness, rainbow road, slowness, phoenix path | Sabotage |
| **MAJOR** | blindness, teleport to ground, thunder ×3, crater | Severe disruption |

The picker uses weighted random selection across tiers, with a hard ceiling:
**MAJOR events fire at most once every 6 picks** (configurable via
`/randomchaos cooldown`).

## Configuration

`config/randomchaos.json` — all settings tunable via slash commands or by
editing the file directly (run `/randomchaos reload` after editing).

```json
{
  "intervalSeconds": 120,
  "effectCapRatio": 0.7,
  "majorCooldownPicks": 6,
  "minorWeight": 50,
  "mediumWeight": 35,
  "majorWeight": 40,
  "minorCooldown": 3,
  "mediumCooldown": 4
}
```
