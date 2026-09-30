# Reading exports in a desktop app

Desktop apps can read Colony Bridge's schema 2 exports without connecting to Minecraft or changing its data.

## Discovery

The desktop application starts with `<minecraft instance>/colonybridge/bridge-info.json`. It must require:

- `protocolId: "com.colonybridge.snapshot"`
- `schemaVersion: 2`
- `outputLayoutVersion: 1`
- `transport: "filesystem"`
- `readOnly: true`

It then reads only the paths listed in `latestFiles`. Files are written atomically, so a reader sees either the previous complete snapshot or the next complete snapshot.

## Compatibility

- Reject an unknown protocol ID or output-layout version with a clear upgrade message.
- Accept additive fields within schema 2 and ignore fields the client does not understand.
- Never infer missing values. Use `capabilities`, `warnings`, and `errors` to explain incomplete data.
- Validate the complete snapshot before replacing the last known good view.
- Keep the last valid snapshot available while Minecraft is closed.

## Offline use

A desktop app can work offline by watching `bridge-info.json` and the latest snapshot files. Remote sync is optional. Reading local exports does not require an HTTP server, an open port, or access to Minecraft saves.

## Read-only access

Desktop apps should read Colony Bridge exports without opening or modifying MineColonies saves. This protocol does not support writing changes back to Minecraft.
