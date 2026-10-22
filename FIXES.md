# Codex transfer fixes

The changes are preserved as successive tags in this fork. Earlier tags document the development history; install only the latest release.

| Tag | Change | Status |
| --- | --- | --- |
| `codex-fix-v1` | Clear storage entities before removing them from the source server; restore chest state first. | Superseded |
| `codex-fix-v2` | Transfer and restore the selected hotbar slot. | Superseded |
| `codex-fix-v3` | Stop replaying stale player snapshots; apply player state by UUID; remove source vehicles with rollback on a failed switch. | Superseded |
| `codex-fix-v4` | Route ridden vehicles through the player's switch and ignore legacy PLAYER passenger payloads. | Current |

## Install

Build with Java 21 and Maven (`mvn clean package`), or download both JARs from the latest `codex-fix-v4` release. Replace the old plugin JAR on **every** Paper/Purpur server and on Velocity. Remove old copies from each `plugins/` directory before restarting. The old Velocity `player-states/` snapshots are no longer read; they are left on disk for manual backup or removal.

The build was verified with Maven. Cross-server behavior still needs testing on a running network, especially a chest boat or storage minecart crossing a portal, a failed switch, reconnect after dropping items, and an ordinary restart.
