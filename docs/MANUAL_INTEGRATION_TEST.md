# Manual Integration Test

1. Build with `gradlew clean build --offline`.
2. Confirm only `colonybridge-0.18.0.jar` is present in the Create Adventures `mods` folder.
3. Start Minecraft 1.21.1 with NeoForge and MineColonies, then open the Create Adventures world.
4. Use `/cb status` and confirm exports are enabled at the configured interval.
5. Use `/cb` and confirm chat reports a successful export.
6. Use `/cb export 999999` and confirm it reports that the colony was not found without exporting every colony.
7. Confirm `bridge-info.json` reports protocol `com.colonybridge.snapshot`, schema `2`, output layout `1`, transport `filesystem`, `readOnly: true`, and bridge version `0.18.0`.
8. Confirm `colonybridge/latest/colony-minecraft-overworld-1.json` reports schema `2`, bridge version `0.18.0`, and trigger `manual` or `player_join`.
9. Confirm colony, citizens, buildings, requests, construction, environment, territory, research, statistics, capabilities, warnings, and errors are present.
10. Confirm territory does not report zero claimed chunks when ticketed claim data is available.
11. Export twice without changing the colony and confirm `latest` updates without creating duplicate historical snapshots.
12. Let MineColonies advance to a new colony day while inside colony limits and confirm `COLONY DAY X` and the firework sound appear once.
13. Stop the world and confirm the shutdown export completes without modifying MineColonies save data.
14. With remote sync enabled, confirm the log reports a successful sanitized upload and Kingdom Chronicle updates while Minecraft remains open.
15. Inspect the remote payload and confirm it contains no world ID, UUID, center, position, location, or bounds keys.
16. Open Kingdom Chronicle `/api/health` and confirm it reports the same protocol ID and schema.
17. Confirm `/cb diagnostics` is no longer registered and no new `bridge-events.jsonl` or `bridge-health.json` files are created.
