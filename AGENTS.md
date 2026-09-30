# Royal Exchange UI

- Read `docs/ROYAL_EXCHANGE_NATIVE_UI.md` before changing the in-game visuals.
- Use the actual Minecraft 1.21.1 rendering APIs, font, item previews, widget sprites, tooltips and container conventions. Keep green/gold accents small.
- Preserve transactions, quotes, baskets, currency, inventory eligibility, networking and server validation. Preview wells are not deposit slots. Never add per-frame network requests.
- Review the shared shell and Contracts before redesigning Buy/Sell. Compile with the locked offline dependencies; report actual-client visual verification separately.
- Test visual states with isolated development fixtures. Do not install a JAR or test against live saves/economy without a separate user request.
