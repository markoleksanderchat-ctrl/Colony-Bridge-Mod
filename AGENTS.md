# Colony Bridge repository

- Preserve protocol `com.colonybridge.snapshot`, schema `2`, layout `1`, filesystem transport and read-only snapshot export.
- Minecraft may read the public Royal Exchange feed but must never write gameplay or market state back to it.
- Keep the mod independently buildable with Java 21 and the SHA-256 locked dependencies in `dev-dependencies.json`.
- Preserve working behavior and user changes. Run the relevant tests after code changes; keep credentials, saves, local development JARs and generated builds outside Git.
- Do not upload, publish a release, replace an installed JAR or touch Minecraft saves without the user's explicit instruction.

# Royal Exchange UI

- Read `docs/ROYAL_EXCHANGE_NATIVE_UI.md` before changing the in-game visuals.
- Use the actual Minecraft 1.21.1 rendering APIs, font, item previews, widget sprites, tooltips and container conventions. Keep green/gold accents small.
- Preserve transactions, quotes, baskets, currency, inventory eligibility, networking and server validation. Preview wells are not deposit slots. Never add per-frame network requests.
- Review the shared shell and Contracts before redesigning Buy/Sell. Compile with the locked offline dependencies; report actual-client visual verification separately.
- Test visual states with isolated development fixtures. Do not install a JAR or test against live saves/economy without a separate user request.

## Commit messages

- Use short, plain descriptions of what changed, such as `Fix sell basket quantity warnings` or `Update build instructions`.
- Prefer a specific action and object. Avoid wording such as polish pass, reliability reorganization, checkpoint, or release gate unless it accurately names the work.
- Use a prefix such as `fix:` or `docs:` only when it helps; do not add one by default.
- Explain a non-obvious reason in the commit body. Keep routine details out of the subject.
- Leave existing commits and tags unchanged. Do not amend, rebase or force-push published history for a wording cleanup.
