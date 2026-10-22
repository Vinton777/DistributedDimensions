# DistributedDimensions — Agent Guide

## Vision

DistributedDimensions est un système de plugins Minecraft qui divise un monde en **un serveur par dimension** :
- un serveur Paper pour l'**Overworld**
- un serveur Paper pour le **Nether**
- un serveur Paper pour l'**End**

Le tout est piloté par un **proxy Velocity** qui orchestre les transitions entre serveurs.

L'objectif est une expérience **seamless** pour le joueur : les portails sont liés par coordonnées et l'inventaire, l'ender chest, les HP, l'XP, le gamemode et les progrès des advancements sont transférés entre serveurs. Le chat reste local à chaque serveur.

---

## Architecture

```
[Joueur] ──► [Velocity Proxy (dd-velocity)]
                  │
        ┌─────────┼─────────┐
        ▼         ▼         ▼
  [Overworld]  [Nether]   [End]
  (dd-paper)  (dd-paper) (dd-paper)
```

La communication entre les serveurs Paper et le proxy Velocity se fait via des **plugin message channels** (BungeeCord messaging protocol).

---

## Modules

### `dd-common` — Librairie partagée ⚠️ Toujours privilégier ce module

Contient **toutes les classes partagées** entre dd-paper et dd-velocity :
- **`model/Dimension`** : enum OVERWORLD/NETHER/END avec `toBukkitWorldName()`
- **`model/LocationData`** : coordonnées x/y/z/yaw/pitch
- **`messaging/Channels`** : noms des plugin message channels (source de vérité unique)
- **`service/DimensionSwitchService`** : interface d'envoi de switch

> **Règle absolue** : toute classe utilisée à la fois côté Paper et côté Velocity doit vivre dans `dd-common`. Ne jamais dupliquer du code entre dd-paper et dd-velocity.

### `dd-paper` — Plugin Paper (serveur de jeu)

Plugin installé sur chaque serveur Paper (Overworld, Nether, End).

**Structure des packages :**

| Package | Rôle |
|---|---|
| `listener/` | Écouteurs d'événements Bukkit : portails Nether/End, réception des messages Velocity |
| `service/` | Implémentation de `DimensionSwitchService` via plugin messages |
| `messaging/` | `PlayerStateSerializer` — sérialise/désérialise l'état complet du joueur |
| `portal/` | `SafeLocationFinder` (trouve un spawn sûr) et `PortalBuilder` (construit un portail physique) |

Version API ciblée : **Paper 1.21.11** (Java 21)

### `dd-velocity` — Plugin Velocity (proxy)

Plugin installé sur le proxy Velocity.

**Structure des packages :**

| Package | Rôle |
|---|---|
| `handler/` | `DimensionSwitchHandler` — route le joueur et transmet son état; `EntityTransferHandler` — transfère les entités |

Version API ciblée : **Velocity 3.5.0-SNAPSHOT**

---

## Canaux de communication (`dd-common/Channels.java`)

| Canal | Direction | Payload |
|---|---|---|
| `dd:dimension_switch` | Paper → Velocity → Paper | UUID joueur, dimension cible, x/y/z/yaw/pitch, XP, faim, gamemode, inventaire complet |
| `dd:advancements` | Paper → Velocity → Paper | UUID joueur, lots de critères déjà obtenus |
| `dd:ender_chest` | Paper → Velocity → Paper | UUID joueur, lots de l'inventaire complet de l'ender chest |

La sérialisation utilise **Guava `ByteStreams`**. Le format exact est documenté dans `PlayerStateSerializer.java`.

> Ne jamais hardcoder un nom de channel : utiliser `Channels.DIM_SWITCH.toString()` (et non `.name()` qui retourne le nom de l'enum, pas la valeur).

---

## Flux d'un switch de dimension

1. Le joueur entre dans un portail → `NetherPortalListener` ou `EndPortalListener` cancelle l'événement vanilla
2. Le listener appelle `DimensionSwitchService.sendSwitchRequest()` avec les coordonnées calculées
3. `VelocityPluginMessageSwitchService` sérialise l'état complet du joueur via `PlayerStateSerializer` et envoie le message
4. Velocity reçoit le message → `DimensionSwitchHandler` route le joueur vers le serveur cible, puis forward le message identique
5. Le serveur cible reçoit le message → `DimensionSwitchListener` désérialise, appelle `SafeLocationFinder`
6. `SafeLocationFinder` cherche un sol solide + 2 blocs d'air. Si introuvable → `PortalBuilder` construit un portail
7. Le joueur est téléporté et son état complet est restauré

---

## État d'avancement

### ✅ Implémenté

- Détection et interception des portails Nether et End
- Ratio de coordonnées 1:8 Overworld↔Nether calculé manuellement
- Routing Velocity vers le bon serveur
- Sérialisation/désérialisation de l'état complet : inventaire (36 slots + armor + offhand), XP, faim, saturation, exhaustion, gamemode
- Spawn sûr : sol solide + 2 blocs d'air vrais (pas lave, feu, eau)
- Construction d'un portail physique 4×5 + plateforme si aucun sol trouvé
- Chat cross-serveur via Velocity
- Environnement Docker Compose complet

### ❌ Non implémenté (roadmap)

- **Effets de potion** : non transmis lors du switch
- **Persistance des données joueur** : tout est en mémoire, rechargement du serveur = reset
- **Respawn cross-serveur** : si mort dans le Nether, le respawn doit se faire sur l'Overworld
- **Timeout de transition** : gérer le cas où le serveur cible est hors-ligne
- **Sécurité des messages** : validation des payloads reçus

---

## Conventions de développement

- **Java 21** (class file 65.0) — Paper 1.21.11 l'exige
- **Séparation des concerns** : chaque classe a une responsabilité unique
  - Les listeners ne font que lire des événements et déléguer
  - La sérialisation vit exclusivement dans `PlayerStateSerializer`
  - La logique de portail vit dans `portal/`
- Les channels sont définis uniquement dans `Channels.java` — utiliser `.toString()` pas `.name()`
- Build : Maven multi-module

---

## Build & déploiement

```bash
# Compiler + copier les JARs dans .dev/
make all

# Ou séparément :
make build   # mvn clean package
make deploy  # copie les JARs dans .dev/*/plugins/

# Démarrer / arrêter
make start   # docker compose up -d
make stop    # docker compose stop
make logs    # docker compose logs -f
```

---

## Environnement de développement (`.dev/`)

Le dossier `.dev/` contient tout le nécessaire pour lancer un environnement de test local complet via Docker Compose.

### Structure

```
.dev/
├── velocity/
│   ├── velocity-3.5.0-SNAPSHOT-576.jar
│   ├── velocity.toml             ← config proxy (ports, serveurs, forwarding)
│   ├── forwarding.secret         ← secret partagé avec les serveurs Paper
│   └── plugins/                  ← dd-velocity.jar déployé ici
└── paper/
    ├── overworld/                ← port 25566
    │   ├── paper-1.21.11-116.jar
    │   ├── server.properties
    │   ├── config/paper-global.yml
    │   └── plugins/              ← dd-paper.jar déployé ici
    ├── nether/                   ← port 25567
    └── end/                      ← port 25568
```

### Ports

| Serveur    | Port  |
|------------|-------|
| Velocity   | 25565 |
| Overworld  | 25566 |
| Nether     | 25567 |
| End        | 25568 |

### Forwarding Velocity → Paper

Le secret est dans `.dev/velocity/forwarding.secret` et doit correspondre au champ `secret` dans `config/paper-global.yml` de chaque serveur Paper. Les serveurs Paper tournent avec `online-mode=false`, c'est Velocity qui gère l'authentification.

Se connecter sur `localhost:25565` avec un client Minecraft 1.21.11.


## Vision

DistributedDimensions est un système de plugins Minecraft qui divise un monde en **un serveur par dimension** :
- un serveur Paper pour l'**Overworld**
- un serveur Paper pour le **Nether**
- un serveur Paper pour l'**End**

Le tout est piloté par un **proxy Velocity** qui orchestre les transitions entre serveurs.

L'objectif est une expérience **seamless** pour le joueur : traverser un portail doit sembler identique à ce que ferait un monde vanilla — les portails sont liés par coordonnées, l'inventaire, l'XP, le chat et les achievements sont partagés en temps réel entre tous les serveurs.

---

## Architecture

```
[Joueur] ──► [Velocity Proxy (dd-velocity)]
                  │
        ┌─────────┼─────────┐
        ▼         ▼         ▼
  [Overworld]  [Nether]   [End]
  (dd-paper)  (dd-paper) (dd-paper)
```

La communication entre les serveurs Paper et le proxy Velocity se fait via des **plugin message channels** (BungeeCord messaging protocol).

---

## Modules

### `dd-common` — Librairie partagée ⚠️ Toujours privilégier ce module

Contient **toutes les classes partagées** entre dd-paper et dd-velocity :
- **Modèles** : `Dimension` (enum OVERWORLD/NETHER/END), `PortalLink`, `LocationData`
- **Channels** : `Channels.java` — définit les noms des plugin message channels
- **Services abstraits** : interfaces et logique métier réutilisable

> **Règle absolue** : toute classe utilisée à la fois côté Paper et côté Velocity doit vivre dans `dd-common`. Ne jamais dupliquer du code entre dd-paper et dd-velocity.

### `dd-paper` — Plugin Paper (serveur de jeu)

Plugin installé sur chaque serveur Paper (Overworld, Nether, End). Rôles :
- Détecter les événements de portail (`NetherPortalListener`, `EndPortalListener`)
- Envoyer des demandes de changement de dimension au proxy via plugin messages
- Recevoir les instructions de téléportation du proxy et positionner le joueur
- Synchroniser les mappings de portails reçus du proxy

Version API ciblée : **Paper 1.21**

### `dd-velocity` — Plugin Velocity (proxy)

Plugin installé sur le proxy Velocity. Rôles :
- Recevoir les demandes de dimension switch des serveurs Paper
- Router le joueur vers le bon serveur cible
- Broadcaster les synchronisations de portails à tous les serveurs
- (À terme) Centraliser la synchronisation des données joueur

Version API ciblée : **Velocity 3.4.0**

---

## Canaux de communication (`dd-common/Channels.java`)

| Canal | Direction | Payload |
|---|---|---|
| `dd:dimension_switch` | Paper → Velocity | UUID joueur, dimension cible, x/y/z, yaw/pitch |
| `dd:portal_link_sync` | Velocity → Paper (broadcast) | Données d'un `PortalLink` |

La sérialisation utilise **Guava `ByteStreams`** (standard Minecraft plugin messages) : UTF-8 pour les strings, `double` pour les coordonnées, `float` pour yaw/pitch.

---

## État d'avancement

### ✅ Implémenté

- Détection des portails Nether et End côté Paper
- Envoi du message `dd:dimension_switch` au proxy
- Routing du joueur vers le bon serveur Velocity
- Forwarding des coordonnées de destination au serveur cible
- Mapping en mémoire des portails (`InMemoryPortalMappingService`)

### 🚧 Partiellement implémenté / bugs connus

- **`PortalMappingSyncListener`** (Paper) : les méthodes `onPluginMessageReceived` sont vides — la désérialisation des `PortalLink` reçus n'est pas faite
- **`InMemoryPortalMappingService.createLink()`** : contient un TODO pour broadcaster le nouveau lien via plugin messaging — non implémenté
- **Persistance des portails** : tout est en mémoire (`ConcurrentHashMap`), les mappings sont perdus au redémarrage

### ❌ Non implémenté (roadmap)

#### Priorité haute
- **Synchronisation de l'inventaire** : sérialiser/désérialiser l'inventaire complet du joueur lors d'un changement de dimension
- **Synchronisation XP / niveaux** : transmettre les niveaux et points d'XP avec le joueur
- **Persistance des portails** : sauvegarder les `PortalLink` en base (SQLite ou fichier JSON) pour survivre aux redémarrages
- **Compléter le PortalLinkSync** : implémenter la désérialisation dans `PortalMappingSyncListener` et le broadcast dans `createLink()`

#### Priorité moyenne
- **Bridge de chat** : partager les messages entre tous les serveurs (via Velocity, qui voit tous les joueurs connectés)
- **Synchronisation des achievements/advancements** : s'assurer que les advancements débloqués sur un serveur sont répercutés sur les autres
- **Gestion des états de jeu** : faim, effets de potion, statistiques — à préserver lors des transitions
- **Respawn cross-serveur** : si un joueur meurt dans le Nether, le respawn doit se faire sur le serveur Overworld avec les bonnes coordonnées

#### Priorité basse / améliorations
- **Sécurité des messages** : validation des payloads reçus pour éviter les injections/exploits
- **Timeout de transition** : gérer le cas où le serveur cible est hors-ligne lors d'un changement de dimension
- **Versioning des messages** : ajouter un numéro de version au format des plugin messages pour faciliter les migrations futures
- **Gestion du spawn Nether** : le ratio de coordonnées 1:8 Overworld↔Nether doit être appliqué automatiquement lors du calcul du portail de destination
- **Support multi-monde par serveur** : permettre à terme d'avoir plusieurs mondes sur un même serveur Paper (ex : dimensions custom)
- **Metrics / observabilité** : logs structurés pour suivre les transitions et détecter les anomalies

---

## Conventions de développement

- **Java 17** minimum
- **Code propre et réutilisable** : éviter la duplication, extraire les abstractions dans `dd-common`
- Toute logique de sérialisation/désérialisation de messages doit être dans `dd-common` (ni dans dd-paper ni dans dd-velocity en isolation)
- Les channels de communication sont définis uniquement dans `Channels.java` (dd-common) — ne jamais hardcoder un nom de channel ailleurs
- Build : Maven multi-module (`mvn install` à la racine)

---

## Build & lancement

```bash
# Compiler tout le projet
mvn clean install

# Les JARs produits sont dans dd-paper/target/ et dd-velocity/target/
# Copier dd-paper/target/dd-paper-*.jar dans le dossier plugins/ de chaque serveur Paper
# Copier dd-velocity/target/dd-velocity-*.jar dans le dossier plugins/ du proxy Velocity
```

---

## Environnement de développement (`.dev/`)

Le dossier `.dev/` contient tout le nécessaire pour lancer un environnement de test local complet.

### Structure

```
.dev/
├── start-all.sh                  ← lance les 4 processus d'un coup
├── velocity/
│   ├── velocity-3.5.0-SNAPSHOT-576.jar
│   ├── velocity.toml             ← config proxy (ports, serveurs, forwarding)
│   └── forwarding.secret         ← secret partagé avec les serveurs Paper
└── paper/
    ├── overworld/                ← port 25566
    │   ├── paper-1.21.11-116.jar
    │   ├── eula.txt
    │   ├── server.properties
    │   └── config/paper-global.yml
    ├── nether/                   ← port 25567
    │   └── ...
    └── end/                      ← port 25568
        └── ...
```

### Ports

| Serveur    | Port  |
|------------|-------|
| Velocity   | 25565 |
| Overworld  | 25566 |
| Nether     | 25567 |
| End        | 25568 |

### Démarrage

```bash
# 1. Compiler les plugins
mvn clean install

# 2. Copier les JARs compilés
cp dd-paper/target/dd-paper-*.jar .dev/paper/overworld/plugins/
cp dd-paper/target/dd-paper-*.jar .dev/paper/nether/plugins/
cp dd-paper/target/dd-paper-*.jar .dev/paper/end/plugins/
cp dd-velocity/target/dd-velocity-*.jar .dev/velocity/plugins/

# 3. Tout lancer
cd .dev && ./start-all.sh
```

Se connecter ensuite sur `localhost:25565` avec un client Minecraft 1.21.11.

### Forwarding Velocity → Paper

Le secret de forwarding est défini dans `.dev/velocity/forwarding.secret`.
Il doit correspondre exactement à la valeur `secret` dans le `config/paper-global.yml` de chaque serveur Paper.
Les serveurs Paper tournent avec `online-mode=false` car c'est Velocity qui gère l'authentification.
