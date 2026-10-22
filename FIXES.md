# Codex transfer fixes

The changes are preserved as successive tags in this fork. Earlier tags document the development history; install only the latest release.

| Tag | Change | Status |
| --- | --- | --- |
| `codex-fix-v1` | Clear storage entities before removing them from the source server; restore chest state first. | Superseded |
| `codex-fix-v2` | Transfer and restore the selected hotbar slot. | Superseded |
| `codex-fix-v3` | Stop replaying stale player snapshots; apply player state by UUID; remove source vehicles with rollback on a failed switch. | Superseded |
| `codex-fix-v4` | Route ridden vehicles through the player's switch and ignore legacy PLAYER passenger payloads. | Superseded |
| `codex-fix-v5` | Transfer mob and allay equipment, villager trades and progress; remove proxy-wide chat and plugin join/quit messages. | Superseded |
| `codex-fix-v6` | Preserve tameable owner UUIDs and merge awarded advancement criteria across servers in bounded batches. | Superseded |
| `codex-fix-v7` | Transfer player HP, base maximum HP, and absorption on dimension switches. | Superseded |
| `codex-fix-v8` | Transfer ender chest contents in bounded packets and apply only after the complete inventory arrives. | Superseded |
| `codex-fix-v9` | Preserve villager curing reputation, horse attributes, and body armor; synchronize bed/anchor spawn, manual `/server` switches, and graceful backend shutdown snapshots. | Superseded |
| `codex-fix-v10` | Target Paper 26.3 and transfer sulfur cubes when they touch a Nether portal, including their swallowed block, size, and behavior. | Superseded |
| `codex-fix-v11` | Restore v10 respawn behavior and transfer the happy ghast's BODY-slot harness without dropping it on the source server. | Current |

## Install

Build with Java 25 and Maven (`mvn clean package`), or download both JARs from the latest `codex-fix-v11` release. This version targets **Paper 26.3**. Replace the old plugin JAR on **every** Paper server and on Velocity. Remove old copies from each `plugins/` directory before restarting. The v11 release was republished; replace any previously downloaded v11 JARs. The old Velocity `player-states/` snapshots are no longer read; they are left on disk for manual backup or removal.

The build was verified with Maven against Paper 26.3. Cross-server behavior still needs testing on a running network, especially a harnessed happy ghast with a rider, sulfur cubes with swallowed blocks, `/server` switches, and graceful backend shutdown. An abrupt process kill cannot send a final snapshot.
