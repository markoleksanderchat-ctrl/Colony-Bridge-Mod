# Changelog

## 0.28.1

- Keep the world visible behind the Royal Exchange, matching standard Minecraft container screens instead of applying an additional dark backdrop.

## 0.28.0

- Rework Royal Exchange text fitting after in-game review: long goods now use explicit ellipses, explanatory copy wraps within its card, and footer labels remain inside the frame.
- Give contract navigation a clear center lane with compact status text so the arrow buttons never cover it.
- Retain the MineColonies-style paper panels, simple item rows, and generous separation between navigation, content, and actions.

## 0.27.0

- Reads persistent Royal Exchange anomalies and applies their severe issuer movements through the existing one-way online feed.
- Announces each newly observed anomaly once in Minecraft chat with severity, a short account, affected guilds, and current impact.

## 0.26.0

- Use `royalexchange.net` as the primary market feed with the legacy Worker hostname as automatic failover.
- Share the website's exact 1,332-item issuer catalog with the Market Block so displayed and applied movements agree.
- Show live connection state in the Market Block and keep its 316 x 236 layout within Minecraft's 320 x 240 minimum scaled GUI.
- Shorten contract guidance that could cross its panel boundary.

## 0.25.0

- Read the public Royal Exchange market asynchronously and apply each mapped issuer's percentage movement to Minecraft buy, sell, and contract prices.
- Add deterministic coverage for every vanilla item family across all 38 online issuers, with tested mappings for metals, redstone, transport, and rare wares.
- Cache the last valid market tick, reject incomplete or malformed feeds, and fall back to local pricing when the site is unavailable or stale.
- Keep the connection strictly one-way: Minecraft never sends gameplay or trade data to the online exchange.

## 0.22.4

- Show an explicit per-diamond exchange rate in every ready quote.
- For goods worth less than one diamond each, raise undersized requests to the nearest practical 1-diamond bundle so the displayed rate matches the delivered quantity.
- Extend supported trade quantities to 1,024 items while retaining the single capped bulk discount.

## 0.22.3

- Keep inventory and gameplay keybinds from firing while the Royal Exchange search field is focused; Escape now leaves the field before closing the screen.
- Hide JEI ingredient and bookmark overlays while the Royal Exchange is open without changing JEI behavior elsewhere.

## 0.22.2

- Replace the placeholder Royal Exchange block appearance with a bespoke oak, oxidized-copper, and brass texture.
- Rework the trader screen into clean item-selection and quote columns with stronger hierarchy, spacing, scrolling, tooltips, and concise market copy.

## 0.22.1

- Fix the Royal Exchange recipe's Minecraft 1.21.1 ingredient format.
- Add the Royal Exchange to the Functional Blocks creative tab so JEI indexes the block and recipe.

## 0.22.0

- Add the Royal Exchange block with searchable vanilla items, quantities, delayed expiring quotes, market conditions, and server-authoritative diamond purchases.
- Implement `royal_exchange_v1` pricing through broad vanilla classification, exceptional-item overrides, bulk valuation, deterministic lazy trends, bounded volatility, and twelve decaying lore events.
- Persist a versioned market seed, requested goods, bounded histories, events, quotes, completed trades, and effective configuration atomically under the world-owned Colony Bridge directory.
- Add market status, event, forced-event, reset, and quote commands plus regression tests for pricing, deterministic advancement, boundaries, decay, quote integrity, payment, namespaces, and persistence.

## 0.21.2

- Simplify MineColonies version discovery and remove obsolete build output without changing the schema or runtime behavior.

## 0.21.1

- Mark guard-kill periods as reconciled only when per-creature detail exactly equals MineColonies' authoritative total.
- Export a registry-derived category beside every creature detail and omit zero-value rows.
- Isolate building-stat read failures so one malformed hut cannot suppress the rest of the colony record.
- Warn if detailed kill evidence ever exceeds the MineColonies summary total.

## 0.21.0

- Export existing guard and ranger mob-kill totals from MineColonies, including persisted per-entity history from defensive buildings.
- Separate MineColonies raiders, other hostile mobs, peaceful or other mobs, and legacy unclassified kills.
- Export the independent `animals_butchered` production statistic from animal-worker buildings.

## 0.20.1

- Uses MineColonies' own building bounds and herding-module compatibility filters to count animals physically tended at staffed animal huts.
- Preserves the last completed hut observation for disconnect and shutdown exports after entities unload.

## 0.20.0

- Replaces the claimed-chunk animal scan with MineColonies' own animal-to-home-building assignments.
- Counts only animals at animal huts with an assigned citizen whose active job points to that hut.
- Adds per-hut totals, animal types, worker IDs, names, and block positions while retaining aggregate livestock fields.

## 0.19.0

- Added a read-only scan of loaded claimed chunks for animal entities and deduplicated them against MineColonies managed animals.
- Reported entity registry identifiers and MineColonies housing counts.

## 0.18.0

- Removes the temporary local bug detector, health report, event log, shadow collections, phase instrumentation, and diagnostics commands after the completed investigation.
- Preserves normal read-only snapshots, history deduplication, remote sync, status/export/path/version commands, and shutdown-after-disconnect export deduplication.

## 0.17.1

- Replaces DTO fingerprints in deep serialization verification with numeric-aware canonical JSON comparison and exact mismatch paths.
- Separates normal analyzer timing from sampled deep-verification timing.
- Generates performance findings from trigger-specific baselines and requires repeated anomaly evidence.
- Migrates schema-1 health reports without losing valid counters, removing the known false round-trip signature and mixed legacy timing evidence.
- Skips a redundant shutdown export after a recent successful last-player-disconnect export.

## 0.17.0

- Adds intensive per-section collection timings for sources, citizens, buildings, requests, construction, colony metadata, environment, territory, livestock, research, statistics, inventory, and world metadata.
- Instruments previously silent adapter and inventory fallback reads by scope and exception type.
- Adds read-only snapshot consistency checks for counts, identities, references, capacities, health, construction progress, territory, food, and stock totals.
- Compares top-level sections with the previous snapshot and records exact change breadth without retaining sensitive values in diagnostics.
- Adds adaptive serialization verification and a second read-only shadow collection every 15 interval exports.
- Learns separate startup, interval, manual, disconnect, and shutdown baselines and reports phase-level timing outliers.
- Detects recurring redundant shutdown snapshots and expands `/cb diagnostics` with fallback and verification totals.

## 0.16.0

- Adds a local, persistent Colony Bridge health collector with bounded raw events and an atomic summarized report.
- Learns separate collection, local I/O, and remote-sync timing baselines after ten exports.
- Detects recurring sanitized Bridge issues, export/remote failure streaks, excessive retries, and high unchanged-export rates.
- Adds `/colonybridge diagnostics` and `/cb diagnostics` without changing snapshot schema 2 or writing to Minecraft saves.
- Prevents diagnostics write failures from changing the result of an otherwise successful colony export.

## 0.15.1

- Counts MineColonies guards with the authoritative job API, covering Knights, Rangers, and Druids without relying on registry-name text.
- Exports an explicit per-citizen `guard` flag for downstream consumers.
- Separates actual workplace workers from broader building associations such as residents and linked warehouse couriers.
- Corrects staffed-building summaries so housing occupants no longer inflate workplace coverage.
- Adds regression coverage for defensive roles and workplace-derived staffing.

## 0.15.0

- Extracted bounded inventory and food analysis into a focused collector instead of mixing it into snapshot orchestration.
- Aligned the development runtime with the installed NeoForge 21.1.238 environment.
- Enabled comprehensive Java compiler linting and explicit Java toolchain resolution.
- Kept schema 2 and the desktop protocol fully backward compatible.

## 0.14.0

- Advertises a stable protocol ID, filesystem transport, output-layout version, and read-only guarantee in `bridge-info.json`.
- Sends the same protocol ID with hosted uploads so incompatible receivers can reject mismatched contracts safely.
- Documents the supported offline desktop integration boundary without adding ports, write-back, or save mutation.

## 0.13.0

- Hardened remote synchronization with bounded `Retry-After` support and explicit bridge protocol headers.
- Improved site ingestion validation, real payload-size enforcement, malformed JSON handling, and out-of-order journal protection.
- Added runtime schema validation and stale-report status to the dashboard polling loop.

## 0.12.0

- Export the exact construction completion percentage displayed by the assigned Builder's Hut.
- Include aggregate remaining and total required-resource counts for progress provenance.

## 0.11.0

- Reuses one per-export view of assigned citizens and open building requests instead of querying the same MineColonies structures repeatedly.
- Reuses the colony day and completed-research list throughout snapshot collection.
- Pauses periodic exports while no players are connected and resets the interval after a join export to avoid immediate redundant work.
- Raises the default periodic interval from 60 to 120 seconds while preserving every explicit configured value.
- Adds collection, local I/O, and remote-upload phase timings to the rotating diagnostics log.
- Preserves schema 2, bounded inventory scanning, privacy sanitization, and the read-only architecture.

## 0.10.0

- Revalidates provisional startup inventory data on the first normal export.
- Adds explainable handler and slot coverage by building type.
- Adds inferred workplace links and rotating structured export diagnostics.
- Preserves the two-colony-day bounded stock cache and remote sanitization.

## 0.9.0

- Adds a bounded colony-wide stock ledger across known building storage.
- Refreshes the inventory count once every two MineColonies colony days and reuses the cached result between scans.
- Makes Food Runway reuse the same cache, removing the former per-export storage walk.
- Exports stock refresh age, next scheduled refresh, scan coverage, truncation state, and up to 512 item totals.
- Keeps raid forecast inputs available for the dashboard's transparent raid-readiness assessment.

## 0.8.1

- Uses each Restaurant or Dining Hall's configured menu as the authoritative food whitelist.
- Counts a stored stack only when it matches at least one selected menu entry, excluding unused edible ingredients such as onions and acorns.
- Exposes Dining Hall coverage and approved menu items so the dashboard can show how the reserve was filtered.
- Returns explicit no-Dining-Hall and empty-menu states instead of producing a misleading runway.

## 0.8.0

- Adds a bounded edible-food inventory scan across already-known colony building handlers.
- Learns average meal usage from the previous seven completed colony days.
- Estimates stored servings, food runway, and the projected colony day of depletion when no new meals are added.
- Reports confidence, top stored foods, and scan coverage without exporting full inventory contents or loading chunks.

## 0.7.0

- Adds a managed-livestock overview with housing coverage and counts by animal type.
- Adds a defensive raid forecast with eligibility, spy status, and estimated raider count.
- Reports whether sick or injured citizens are sleeping at a hospital.
- Resolves completed research into the colony's effective research benefits and strengths.
- Adds per-statistic today and rolling seven-colony-day productivity totals.
- Keeps every new field read-only, bounded, deterministic, privacy-sanitized, and backward-compatible with schema 2.

## 0.6.2

- Replaces predictable remote building/request hashes with stable token-keyed HMAC aliases.
- Recursively strips sensitive identity, coordinate, location, and bounds keys from remote payloads.
- Uses one HTTPS endpoint validator for configuration status and publishing.
- Skips redundant player-join exports immediately after a clean startup export.
- Queues manual exports behind active automatic exports so commands report their own result.
- Honors last-player-disconnect exports on dedicated servers as well as integrated worlds.
- Applies lowered history retention even when colony state is unchanged and writes new history before replacing `latest`.
- Makes request delivery, work-order, optional job-request, and colony-day reads more defensive.
- Preserves absent adapter values as JSON `null` instead of the string `"null"`.
- Refreshes the README and integration checklist to match the live remote-sync release.

## 0.6.1

- Makes statistics and capability serialization deterministic across JVM runs.
- Removes a just-completed export race and makes completion callbacks safe to chain.
- Keeps worst-case remote retry timing inside the shutdown budget.
- Reduces routine interval-upload log noise while retaining actionable failure detail.

## 0.6.0

- Hardens HTTPS publishing, retry behavior, payload limits, atomic storage, retention, configuration caching, and shutdown handling.
- Extracts the remote sanitizer and adds defensive immutable snapshots, status values, and server contexts.
- Adds focused logic tests and a documented evidence-based stability audit.

## 0.4.3

- Changes the gold title to the MineColonies persisted colony day instead of Minecraft's resettable calendar day.
- Tracks each colony independently and announces `COLONY DAY X` only to players inside the colony whose day advanced.
- Keeps day trackers synchronized while notifications are disabled so re-enabling them cannot replay an old dawn.

## 0.4.2

- Preserves user-selected 2-, 5-, and 10-minute export intervals instead of rewriting them to one minute.
- Prevents `/cb export <colonyId>` from exporting every colony when the requested ID does not exist.
- Returns command completion messages on the Minecraft server thread and reports disabled exports or unknown colony IDs clearly.
- Keeps shutdown moving if its final snapshot write fails and labels player-join exports correctly.
- Uses ticketed chunks as a conservative claim-count fallback when MineColonies' claim map is unavailable during shutdown.
- Exports readable colony colors and canonical citizen job names while retaining the raw job activity description in details.
- Orders colonies, citizens, buildings, and construction records deterministically.
- Recovers automatically if a previous `latest` snapshot is malformed.

## 0.4.1

- Changes automatic exports to once per minute.
- Disables automatic export chat and sound notifications by default.
- Set the fresh-install default export interval to one minute.

## 0.4.0

- Advances the additive snapshot schema to version 2.
- Adds center and sampled colony biomes, world time, moon phase, daylight, and weather.
- Adds exact MineColonies claim counts, territory bounds, approximate area, and loaded/ticketed chunk counts without loading chunks.
- Adds completed and in-progress research plus bounded MineColonies statistics.
- Adds population potential, raid history/readiness, graves, waypoints, members, colony style, and contact metadata.
- Adds citizen family, food, disease, mourning, pause, job-state, and position metadata.
- Adds worker-building skills, hiring mode, rain behavior, equipment level, containers, claim radius, and requirement metadata.
- Adds stable request IDs and richer request descriptions, strategy, icons, and delivery state.
- Adds stable work-order IDs, priorities, builder assignment, structure details, rotation, location, and bounding boxes.
- Reuses point-in-time MineColonies collections and moves file work to a dedicated single I/O worker.

## 0.3.2

- Plays a firework launch sound for each player who receives the colony day title.

## 0.3.1

- Replaces day-change chat lines with a gold title and yellow subtitle.
- Adds 25 deterministic kingdom messages that rotate with the world day.
- Keeps celebrations restricted to players inside actual MineColonies limits.

## 0.3.0

- Adds a MineColonies-aware day counter synchronized with Minecraft's world day.
- Announces `Day X` and `The kingdom perseveres.` only when a new day begins while the player is inside actual colony limits.
- Adds the `notifications.dayCounterEnabled` server config option.

## 0.2.5

- Prevents shutdown export deadlocks by collecting immediately when already on the server thread.
- Coalesces overlapping requests onto the active export result instead of reporting stale status.
- Polishes export chat grammar and removes stale placeholder metadata.
- Revalidates against Create Adventures with MineColonies `1.1.1319-1.21.1`.

## 0.2.4

- Removes the Colony Book item, recipe, creative-tab entry, and item assets.
- Changes the periodic export default to 2 minutes.
- Migrates old 5-minute and 10-minute defaults to the new 2-minute cadence at runtime.
- Sends export completion and failure notices to normal chat instead of the actionbar.
- Notifies online players in chat when a periodic export finishes.

## 0.2.3

- Rewrites the Colony Book pages into readable player-facing summaries.
- Adds a priority page that translates colony stats into plain next steps.
- Replaces raw registry IDs and terse status labels with friendlier names and short descriptions.

## 0.2.2

- Adds the Colony Book to the vanilla Tools & Utilities creative tab through NeoForge's mod event bus.
- Verifies there is no JEI plugin, ingredient blacklist, ingredient filter, or runtime ingredient-removal code hiding the item.

## 0.2.1

- Ensures the Colony Book item stack itself carries the generated book content before opening.
- Confirms JEI compatibility through normal item registration plus vanilla shapeless recipe indexing.

## 0.2.0

- Added craftable `Colony Book` item.
- Recipe: `minecraft:book` + `structurize:shapetool`.
- Using the Colony Book opens a generated written-book view of live colony data.
- Adds dashboard, construction, request, citizen, and building/staffing pages.

## 0.1.3

- Replaced the join-time progress bar with a green success message.
- Plays the experience pickup sound when a join-time export finishes successfully.

## 0.1.2

- Added a join-time actionbar progress display from 1% to 100%.
- Added `exportOnPlayerJoin` and `showExportProgress` config options.
- Joining a world now triggers a visible read-only export so players know the bridge is working.

## 0.1.1

- Changed automatic periodic export runtime default to 10 minutes.
- Added `/cb` command aliases.
- Allows bridge commands in integrated single-player without enabling cheats.
- Keeps dedicated servers protected by operator permission.

## 0.1.0

- Initial read-only NeoForge bridge foundation.
- Adds neutral JSON snapshot schema version 1.
- Adds atomic latest/snapshot storage, retention, and duplicate fingerprinting.
- Adds MineColonies 1.21.1 compatibility adapter targeting installed `1.1.1319-1.21.1`.
- Adds commands, config defaults, examples, and unit-testable storage/model utilities.
