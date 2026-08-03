# Colony Bridge

Colony Bridge is a NeoForge mod for Minecraft Java 1.21.1 that exports MineColonies colony state as read-only local JSON snapshots and provides the optional Royal Exchange trade market.

It is the local data source for external read-only dashboards such as Kingdom Chronicle. It does not add an inbound HTTP API, WebSocket server, worker assignment, AI advisor, or save editor.

## Safety

Snapshot export remains read-only with respect to Minecraft and MineColonies gameplay data. The Royal Exchange never edits MineColonies data; its server-authoritative trades intentionally exchange player inventory goods and diamonds. Bridge-owned JSON is written only under:

```text
<minecraft instance>/colonybridge/
```

It does not parse MineColonies NBT as its main data source or write to MineColonies save folders. The Royal Exchange reads the public online market by HTTPS GET and applies its issuer movements one-way to local prices; no Minecraft data is sent to the market. If the feed is unavailable or stale, cached data expires and local pricing continues safely. Remote snapshot sync remains disabled by default. When explicitly configured, it sends only a sanitized snapshot by authenticated HTTPS PUT to the configured endpoint; the mod never opens a port and has no telemetry, analytics, or background bug detector.

## Supported Versions

- Minecraft: `1.21.1`
- Loader: NeoForge `21.1.x`
- MineColonies target: `1.1.1319-1.21.1`
- Designed to tolerate newer 1.21.1 MineColonies builds when the inspected API remains compatible.

The project was built against the installed Create Adventures CurseForge instance and its MineColonies JAR.

## Installation

1. Build the project with `gradlew build`.
2. Copy `build/libs/colonybridge-0.28.2.jar` into your Minecraft instance `mods` folder.
3. Launch Minecraft with NeoForge and MineColonies installed.
4. Start or load a world containing MineColonies.
5. Check `<instance>/colonybridge/bridge-info.json` and `<instance>/colonybridge/latest/`.

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

Craft the Royal Exchange with a lectern in the center, an emerald above it, and copper ingots in every other slot. Its Buy and Sell tabs provide delayed live quotes for vanilla goods. The Contracts tab posts rotating premium Crown orders. Prices use the `royal_exchange_v1` valuation formula, broad registry-derived categories, deterministic trends, trade pressure, and decaying lore events. Only requested and contracted goods are simulated.

Trades are executed on the server. It verifies the item, quantity, player, timing, completion state, payment, daily sale allowance, game mode, and exact default item components. Damaged, enchanted, renamed, or otherwise modified goods cannot be sold or submitted to contracts. Modded item namespaces are rejected, and inventory-changing trades require Survival or Adventure mode.

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

Royal Exchange state is stored atomically in the world root at `colonybridge/market.json`. It contains the saved seed, requested-item records, bounded price history, events, quotes, contracts, completions, daily sale totals, and effective configuration. Version 1 state migrates automatically. Corrupt data is preserved under a timestamped filename before a fresh market is created.

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

`latest` is updated on every export. Historical snapshots are skipped when the meaningful colony payload fingerprint has not changed.

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

## Live Dashboard Workflow

```text
Play Minecraft
-> Colony Bridge exports the colony state
-> Colony Bridge uploads a sanitized copy when remote sync is enabled
-> Kingdom Chronicle shows how the colony was left
-> Next export is compared with the previous snapshot
```

## Build From Source

```text
gradlew build
```

The build expects local development MineColonies dependency JARs in:

```text
C:\Users\marko\curseforge\minecraft\Instances\Create Adventures\mods
```

The produced bridge JAR does not bundle MineColonies or its dependencies.

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

- If no files appear, run `/colonybridge status`.
- If MineColonies is missing or incompatible, the adapter reports unavailable and exports no fabricated data.
- If a field fails for one citizen/building/request, the snapshot includes a scoped error and continues.
- If `/cb status` reports remote sync as misconfigured, verify that the endpoint is HTTPS and that both endpoint and token are present.
- For troubleshooting, provide `bridge-info.json`, the latest snapshot JSON, and the normal NeoForge log around the export. Do not share a whole world save unless you intend to.
