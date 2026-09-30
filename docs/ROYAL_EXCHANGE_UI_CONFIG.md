# Royal Exchange appearance

Change Royal Exchange colors and text positions in the client config. Start Minecraft once to create the file:

`<minecraft instance>/config/colonybridge-royal-exchange-client.toml`

If Configured is installed, open Mods -> Colony Bridge -> Config -> Royal Exchange client config. These settings affect appearance only; prices, trades and server settings stay the same.

Do not use **Create's config menu** to save Colony Bridge settings. The bundled Ponder config screen can crash while saving a changed string in Colony Bridge's server config, even when you are editing the Contracts category. For Royal Exchange appearance, use Configured or edit the client TOML above while Minecraft is closed. For gameplay settings such as contract count, duration, or premium, edit the world's `serverconfig/colonybridge-server.toml` while Minecraft is closed. Keep the remote sync token private; Create's crash report may include it if this bug occurs.

## How to edit

- `colors` groups use quoted `#RRGGBB` values. For example, `selectedBackground = "#2B6855"` makes the active tab green.
- `textPositions` groups use small X/Y pixel shifts. `0` is the current layout; positive X moves right and positive Y moves down. Values range from `-12` to `12`.
- The names describe the exact screen part. `buyTabLabelX`, `sellTabLabelY`, `searchTextX`, `quantityNumberY`, and `marketQuoteDetailsX` are examples.
- The `basket` text positions adjust the sell basket rows, heading, quote total, and status. They do not move the row click targets.
- The three tab backgrounds and borders have separate selected, other, and hover colors. If a tab label becomes hard to read, adjust `colors.tabs.text` with its background.
- Save changes in Configured or edit the TOML file, then close and reopen the Royal Exchange screen. A Minecraft restart is a reliable fallback if an external file edit has not reloaded yet. No JAR rebuild is needed for later color or text-position edits.
- To restore the original look, use Configured's reset action for a setting or delete the client TOML while Minecraft is closed so NeoForge generates defaults on the next launch.

Keep shifts small enough for the text to remain inside its existing button, input, or card. These settings move text only; they do not move click targets or change the screen size.
