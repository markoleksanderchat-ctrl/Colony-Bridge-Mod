# Desktop Integration Contract

Colony Bridge schema 2 provides the stable, read-only boundary used by the offline Windows application.

## Discovery

The desktop application starts with `<minecraft instance>/colonybridge/bridge-info.json`. It must require:

- `protocolId: "com.colonybridge.snapshot"`
- `schemaVersion: 2`
- `outputLayoutVersion: 1`
- `transport: "filesystem"`
- `readOnly: true`

It then reads only the paths listed in `latestFiles`. Files are written atomically, so a reader sees either the previous complete snapshot or the next complete snapshot.

## Compatibility Rules

- Reject an unknown protocol ID or output-layout version with a clear upgrade message.
- Accept additive fields within schema 2 and ignore fields the client does not understand.
- Never infer missing values. Use `capabilities`, `warnings`, and `errors` to explain incomplete data.
- Validate the complete snapshot before replacing the last known good view.
- Keep the last valid snapshot available while Minecraft is closed.

## Offline Operation

The future Windows application can operate without a network connection by watching `bridge-info.json` and the latest snapshot files. Hosted sync remains optional and independent. No local HTTP server, inbound port, Minecraft save access, or write-back API is required.

## Security Boundary

The desktop application must remain read-only by default. It may read Colony Bridge output but must not open or modify MineColonies save data. If write-back is ever considered, it requires a separate explicit design and cannot be added to this protocol silently.
