# Colony Bridge

Colony Bridge is a NeoForge mod for Minecraft 1.21.1. It saves MineColonies colony data as JSON for dashboards such as Kingdom Chronicle and adds the Royal Exchange, an in-game market for buying and selling goods with diamonds.

Colony exports are read-only. Royal Exchange trades use your inventory and do not change MineColonies colony data.

Download the JAR from the [current release](https://github.com/markoleksanderchat-ctrl/Colony-Bridge-Mod/releases/latest).

## Data and privacy

Colony exports are saved under `<minecraft instance>/colonybridge/`. Exporting reads colony data without changing MineColonies saves.

The Royal Exchange reads prices from the online exchange. It does not send gameplay or trade data back. If the feed becomes unavailable or too old, the mod returns to local pricing.

Remote snapshot sync is off by default. If you enable it, the mod sends a copy with identifying and location data removed to your configured HTTPS endpoint. It does not send the complete local snapshot, open an inbound port, or collect telemetry.

## Supported Versions

- Minecraft: `1.21.1`
- Loader: NeoForge `21.1.x`
- MineColonies target: `1.1.1319-1.21.1`
- Newer MineColonies builds for Minecraft 1.21.1 may work, but compatibility depends on API changes.

## Building from source

Use Java 21 and the included Gradle wrapper. Required development JARs and their SHA-256 checksums are listed in `dev-dependencies.json`. Put those JARs in `dev-mods/`, set `COLONYBRIDGE_DEV_MODS_DIR`, or pass `-Pcolonybridge.devModsDir=<directory>`.

```powershell
.\gradlew.bat '-Pcolonybridge.devModsDir=C:\path\to\locked-dependencies' test build --no-daemon
```

On Linux or macOS, use `./gradlew`. Add `--offline` if the required Gradle and Maven dependencies are already cached. The built JAR is saved in `build/libs/`. MineColonies and its dependencies are installed separately and are not bundled in the JAR.

The `measurePhase4` and `measurePhase12` tasks use sample data included in this repository. They do not need a Minecraft world or Kingdom Chronicle.

This repository contains the mod and its in-game Royal Exchange. The online exchange and Kingdom Chronicle are separate projects.

## Installation

1. Use Minecraft 1.21.1 with NeoForge 21.1.x.
2. Install MineColonies and its matching dependencies: Structurize, BlockUI, Domum Ornamentum and Multi-Piston. See the [MineColonies installation guide](https://minecolonies.com/wiki/installation/manual/).
3. Download the Colony Bridge JAR from the [current release](https://github.com/markoleksanderchat-ctrl/Colony-Bridge-Mod/releases/latest) and put it in your instance's `mods` folder. Keep only one Colony Bridge JAR installed.
4. Launch Minecraft and open a MineColonies world. Run `/cb status` to check the mod, or craft the Royal Exchange to use the market.

JEI is optional. Create and Kingdom Chronicle are not required. To compile your own JAR, follow Building from source above.

## Commands

All commands are server commands. In integrated single-player worlds, they are available without enabling cheats. On dedicated servers they require permission level 2:

```text
/colonybridge status
/colonybridge export
/colonybridge export <colonyId>
/colonybridge paths
/colonybridge version
/colonybridge market status
/colonybridge market events
/colonybridge market forceevent <event>
/colonybridge market reset
/colonybridge market quote <item> <quantity>
```

Commands never alter colony state.

## Royal Exchange

Craft the Royal Exchange with a lectern in the center, an emerald above it, and copper ingots in the remaining slots. Use Buy and Sell to trade vanilla goods for diamonds. Contracts offer rotating Crown orders with extra rewards. Quotes take a short time to arrive and expire if you leave them too long.

Prices use the `royal_exchange_v1` formula. They reflect item traits, crafting complexity, fixed base values, local trends, trade activity and market events. These are gameplay prices, not measurements of player trading. Local trends update every 30 minutes. Fresh online market movements can also adjust prices within set limits. Only requested items and contract goods are simulated. After a long break, the market catches up in a single update. Bulk discounts cannot reduce the unit price below the configured sell ratio. Old contract completion records are removed as contract periods change.

The server checks each trade before changing your inventory, including the item, quantity, player, quote timing, completion state, payment, daily sale limit and game mode. Selling and contract deliveries accept only unmodified vanilla items: damaged, enchanted, renamed and modded goods are excluded. Trades require Survival or Adventure mode.

In integrated single-player worlds, the commands are available without enabling cheats. On dedicated servers they still require operator permission.

Short aliases are also available:

```text
/cb
/cb status
/cb export
/cb export <colonyId>
/cb paths
/cb path
/cb version
```

`/cb` by itself queues a manual export.

## Configuration

Server config values:

```text
enabled=true
exportOnStartup=true
exportOnPlayerJoin=true
exportOnShutdown=true
exportOnLastPlayerDisconnect=true
periodicExportEnabled=true
periodicExportSeconds=120
retainSnapshots=50
prettyPrintJson=true
showExportProgress=false
includeCitizenPositions=false
includeOwnerUuid=false

[remoteSync]
enabled=false
endpoint=""
token=""

[notifications]
dayCounterEnabled=true

[royalExchange]
quoteDelaySeconds=3
quoteValiditySeconds=120
eventFrequencyMinutes=90
volatilityStrength=0.16
minimumPriceMultiplier=0.60
maximumPriceMultiplier=1.80
buyingEnabled=true
operatorEventControls=true
sellingEnabled=true
sellPriceRatio=0.72
dailySellDiamondLimit=64
activeContractCount=3
contractDurationMinutes=360
contractRewardPremium=1.25
```

Royal Exchange state is stored atomically in the world root at `colonybridge/market.json`. It contains the saved seed, requested-item records, bounded price history, events, contracts, completions, daily sale totals, and effective configuration. Quotes are session-local and are not restored after a restart. Version 1 state migrates automatically. Corrupt data is preserved under a timestamped filename before a fresh market is created.

`periodicExportSeconds` defaults to 120 seconds and is clamped to a minimum of 30 seconds. Any value at or above that minimum is preserved, including 2-, 5-, and 10-minute intervals. Automatic interval exports pause while no players are connected; the last-player-disconnect export preserves the final state. Snapshot retention never follows symlinks and only deletes JSON files inside this bridge's own per-colony snapshot directories.

Remote sync requires an HTTPS URL without embedded credentials and a private bearer token. Before upload, world IDs, UUIDs, coordinates, locations, bounds, and other sensitive position keys are removed recursively. Internal building and request IDs are replaced with token-keyed aliases. The complete local snapshot is not uploaded.

When `showExportProgress` is enabled, completed join-time and periodic exports write a green message in chat and play the experience pickup sound.

When `dayCounterEnabled` is enabled, a player inside MineColonies' actual colony limits sees a gold `COLONY DAY X` title, hears a firework launch, and receives one of 25 rotating kingdom messages when MineColonies advances that colony to a new persisted colony day. Each colony is tracked independently, and joining during an existing colony day does not trigger the title.

## Output Layout

```text
colonybridge/
  bridge-info.json
  latest/
    colony-<dimension>-<colony-id>.json
  snapshots/
    colony-<dimension>-<colony-id>/
      2026-07-10T01-22-35Z.json
```

The latest file is updated on every export. A historical snapshot is saved only when the colony data has changed.

## JSON Schema

See [docs/SCHEMA.md](docs/SCHEMA.md). Snapshots include:

- `game`
- `world`
- `colony`
- `summary`
- `citizens`
- `buildings`
- `requests`
- `construction`
- `environment` (center biome, sampled colony biomes, day/time, moon, and weather)
- `territory` (claimed chunks, approximate area, bounds when available, and loaded/ticketed counts; ticketed chunks provide the fallback count if MineColonies' claim map is unavailable)
- `livestock` (animals assigned to staffed animal huts, grouped by hut location, worker IDs, and animal type)
- `research` (completed and in-progress research plus effective benefits)
- `statistics` (bounded MineColonies lifetime counters)
- `recentStatistics` (today and rolling seven-colony-day productivity counters)
- `foodSupply` (bounded Restaurant-menu-approved reserves, learned daily use, estimated runway, menu coverage, and top food types)
- `stockLedger` (bounded item totals from known colony building storage, cached and refreshed every two colony days)
- `capabilities`
- `warnings`
- `errors`

Unknown values are `null`. Unsupported features are marked in `capabilities` instead of being guessed.

## Using a dashboard

Colony Bridge exports data while you play. A local dashboard can read those files directly. If remote sync is enabled, Kingdom Chronicle can use the uploaded copy to show the colony's latest state.



## Development Client / Server

```text
gradlew runClient
gradlew runServer
```

Use `/colonybridge export` in a loaded MineColonies world, then inspect the `colonybridge` output directory.

## Verifying No World Data Is Modified

- The mod has no calls to MineColonies mutators in the adapter except unavoidable API object accessors.
- It writes only to `<instance>/colonybridge`.
- It does not write to `saves/<world>/minecolonies`.
- It does not scan all chunks, entities, inventories, or blocks.

For manual verification, compare world and MineColonies save timestamps before and after `/colonybridge export`; only the bridge output directory should change because of the command.

## Known Unsupported Fields

- Warehouse-wide inventory totals.
- Exact construction material required/delivered/missing totals.
- Request creation/update timestamps.
- Full resolver internals for every MineColonies request type.
- Full arbitrary building-module state beyond the stable generic and worker-building fields.

## Troubleshooting

- No export files? Run `/colonybridge status`.
- If MineColonies is missing or incompatible, the status explains why colony data is unavailable.
- If part of an export fails, the snapshot includes an error for that part and keeps the data it could read.
- For remote sync errors, check that the endpoint uses HTTPS and that an endpoint and token are configured.
- When reporting a problem, include `bridge-info.json`, the relevant export and the NeoForge log around the failure. Check for private data before sharing files; a full world save is usually unnecessary.

## Upcoming fixes

- Diamonds, diamond blocks and diamond ores cannot be traded as goods. Diamond equipment can still be traded.
- Trades need room for the full reward. If your inventory is full, the trade is cancelled and rolled back instead of dropping items.
- Each player can have one active quote, with up to 1,024 quotes across all players. New quotes replace old ones, and quotes are cleared on restart. Completed trades remain saved.
- The Exchange screen and chat show trade failures. Client and server must use the same updated mod build because the menu includes an extra result-code slot.
- Switching worlds clears collection caches and export status. Failed inventory or menu reads are reported as incomplete.
- Disabling citizen positions also removes bed, home and status locations. Remote uploads remove territory coordinates, hide livestock hut references and remove locations from error details.
- Market announcements expire, have a storage limit and do not repeat while retained. Closing a market client prevents late responses from being applied.
- Snapshot schema 2, layout 1 and the one-way online feed are unchanged. World switching, full inventories and multiplayer still need in-game testing before release.
