# DistributedDimensions

[![CI](https://github.com/DistributedDimensions/DistributedDimensions/actions/workflows/ci.yml/badge.svg)](https://github.com/DistributedDimensions/DistributedDimensions/actions/workflows/ci.yml)

A Minecraft plugin system that splits a world across **one Paper server per dimension**, coordinated by a Velocity proxy.

```
[Player] ──► [Velocity Proxy (dd-velocity)]
                  │
        ┌─────────┼─────────┐
        ▼         ▼         ▼
  [Overworld]  [Nether]   [End]
  (dd-paper)  (dd-paper) (dd-paper)
```

Crossing a portal transfers player inventory, HP, XP, hunger, gamemode, and advancement progress between servers. Tamed mobs keep their owner. Chat stays local to each server.

---

## How it works

1. Player enters a portal → the Paper plugin cancels the vanilla event
2. The full player state (inventory, XP, hunger, gamemode…) is serialized and sent to Velocity via a plugin message channel
3. Velocity routes the player to the target server and forwards the message
4. The target server deserializes the state, finds a safe spawn location (or builds a portal), and teleports the player

Nether coordinate scaling (1:8 ratio) and End platform spawning are handled automatically.

---

## Modules

| Module | Description |
|---|---|
| `dd-common` | Shared library: `Dimension` enum, `LocationData`, `Channels`, `DimensionSwitchService` interface |
| `dd-paper` | Paper plugin — portal detection, state serialization, player teleportation, dimension guard |
| `dd-velocity` | Velocity plugin — player routing and state forwarding |

- **Paper API:** 1.21.11 · **Java:** 21
- **Velocity API:** 3.5.0-SNAPSHOT

---

## Requirements

- Java 21
- A Velocity 3.x proxy
- Three Paper 1.21.11 servers (one per dimension)

---

## Build & deploy

```bash
make all      # build + deploy JARs to .dev/
make build    # mvn clean package only
make deploy   # copy JARs to .dev/*/plugins/
make start    # docker compose up -d
make stop     # docker compose stop
make logs     # docker compose logs -f
```

A ready-to-use Docker Compose environment is included in `.dev/`.  
Connect with a Minecraft 1.21.11 client on `localhost:25565`.

---

## Configuration

### Overview

Velocity uses **Modern Forwarding** to securely pass authenticated player data (UUID, username, skin) to the Paper backend servers. This requires a shared secret that must be identical in three places:

```
.dev/velocity/forwarding.secret          ← secret value (plain text)
.dev/velocity/velocity.toml              ← references this file implicitly
.dev/paper/<dimension>/config/paper-global.yml  ← must contain the same value
```

If the secret mismatches, Paper will refuse connections from Velocity and players will be kicked.

---

### `.dev/velocity/forwarding.secret`

Plain-text file containing the shared secret. One line, no trailing newline.

```
distributeddimensions-dev
```

> Change this to a strong random string in production (e.g. `openssl rand -hex 32`).

---

### `.dev/velocity/velocity.toml`

Key settings relevant to this project:

```toml
bind = "0.0.0.0:25565"
online-mode = true

# MODERN forwarding: Velocity authenticates the player and forwards
# their real UUID/skin to Paper using the shared secret.
player-info-forwarding-mode = "MODERN"

[servers]
overworld = "overworld:25566"
nether    = "nether:25567"
end       = "end:25568"

# Server players connect to by default
try = ["overworld"]
```

---

### `.dev/paper/<dimension>/config/paper-global.yml`

Each Paper server must enable Velocity forwarding and provide the **exact same secret**:

```yaml
proxies:
  velocity:
    enabled: true
    online-mode: true
    secret: distributeddimensions-dev   # must match forwarding.secret
```

---

### `.dev/paper/<dimension>/server.properties`

Paper servers must run with `online-mode=false` because Velocity handles authentication:

```properties
online-mode=false
server-port=25566   # 25566 overworld · 25567 nether · 25568 end
```

---

### `.dev/paper/<dimension>/plugins/DistributedDimensions/config.yml`

Each Paper server declares which dimension it serves:

```yaml
# .dev/paper/overworld/plugins/DistributedDimensions/config.yml
world: OVERWORLD   # OVERWORLD | NETHER | END
debug: false
```

```yaml
# .dev/paper/nether/plugins/DistributedDimensions/config.yml
world: NETHER
debug: false
```

```yaml
# .dev/paper/end/plugins/DistributedDimensions/config.yml
world: END
debug: false
```

---

### `.dev/velocity/plugins/distributed-dimensions/config.toml`

Maps each dimension to its Velocity server name. Names must match the `[servers]` section in `velocity.toml`:

```toml
[settings]
debug = false

[servers]
overworld = "overworld"
nether    = "nether"
end       = "end"
```

---

## Roadmap

- Potion effects not transferred on dimension switch
- Player data is in-memory only (reset on server reload)
- No timeout handling when target server is offline
- No payload validation on received plugin messages
