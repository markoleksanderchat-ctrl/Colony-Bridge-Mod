# Colony Bridge JSON Schema

Schema version: `2`

Colony Bridge emits neutral JSON snapshots rather than serialized MineColonies Java objects. Unknown values are `null`, not fabricated defaults.

Top-level shape:

```json
{
  "schemaVersion": 2,
  "bridgeVersion": "0.21.2",
  "generatedAt": "2026-07-10T01:22:35Z",
  "trigger": "manual",
  "fingerprint": "sha256...",
  "game": {},
  "world": {},
  "colony": {},
  "summary": {},
  "citizens": [],
  "buildings": [],
  "requests": [],
  "construction": [],
  "environment": {},
  "territory": {},
  "livestock": {},
  "research": {},
  "statistics": {},
  "recentStatistics": {},
  "foodSupply": {},
  "stockLedger": {},
  "capabilities": {},
  "warnings": [],
  "errors": []
}
```

`capabilities` distinguishes unsupported data from empty data. For example, an empty `requests` list with `requests.supported=true` means no requests were exported. `constructionMaterials.supported=false` means the adapter did not expose a safe material view.

`trigger` is one of `manual`, `startup`, `player_join`, `interval`, `disconnect`, or `shutdown`.

Schema 2 adds environment, claimed-territory, research, statistics, richer citizen details, richer building state, and stable work-order/request metadata. Existing schema 1 field names remain unchanged. Environment biome sampling is bounded to the colony center and up to 31 building positions; it does not scan or load the colony's chunks. Territory bounds are `null` when the live claim map is unavailable; in that case, `claimedChunks` and `approximateClaimedBlocks` use the colony's ticketed-chunk count as a conservative fallback.

`livestock` counts animals inside MineColonies' own building bounds for staffed huts with an animal-herding module, using that module's native species compatibility filter. `huts` groups those animals by building ID and includes the hut registry type, display name, block position, assigned worker IDs, total, and counts by entity type. Animals elsewhere on claimed land and animals at unstaffed huts are excluded. Disconnect and shutdown exports retain the last completed in-session hut observation after entities unload.

`stockLedger` is an additive schema 2 field. The bridge deduplicates known colony building item handlers, scans at most 512 handlers and 16,384 slots, and refreshes the cache every two MineColonies colony days. `refreshedColonyDay`, `nextRefreshColonyDay`, and `cacheAgeDays` expose freshness. Up to 512 item types are exported; `omittedItemTypes` and `truncated` report incomplete display or scan coverage.

`foodSupply` reads the selected menus from the colony's Restaurant or Dining Hall modules, then cross-references them against the cached stock ledger. `approvedMenuItems`, `menuApprovedFoodTypes`, and `diningHallsScanned` make the filter auditable. `averageMealsPerDay` uses up to seven completed MineColonies colony days, and `estimatedDaysRemaining` assumes no new food is added. Because it reuses the performance-safe ledger, stored-food counts can be up to two colony days old. A `null` estimate with `status: "learning"` means there is not yet enough observed consumption; `no_dining_hall` and `menu_empty` explain missing menu configuration without inventing a reserve.

Snapshots are written to:

```text
<instance>/colonybridge/latest/colony-<dimension>-<colony-id>.json
<instance>/colonybridge/snapshots/colony-<dimension>-<colony-id>/<timestamp>.json
```

`bridge-info.json` is the stable discovery file for hosted and future desktop clients. It contains the bridge version, protocol ID (`com.colonybridge.snapshot`), schema version, output-layout version, `filesystem` transport, the read-only guarantee, health status, and latest output paths.
