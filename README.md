# Server Market Mod Guide | [中文文档](./README_ZH.md)

## Introduction
This mod adds a complete player economy and item trading market to Minecraft servers: player balances, transfers, player & system shops, global search, GUI, and optional physical currency items.

## Player Commands (Concise)
- **/money** – Show current balance.
- **/mpay <player> <amount>** – Transfer balance to another player.
- **/mprice <price>** – Set sale price for the held item (applies to future stock of that item+NBT in your shop).
- **/msell <quantity>** – Add (restock) held item into your personal shop.
- **/mpull** – Unlist the held item and return all remaining stock of that listing.
- **/msearch <itemID>** – Search global market for an item ID.
- **/mbuy <quantity> <itemID> [seller]** – Purchase items; auto‑matches lowest prices across sellers unless a seller (player name or `server`) is specified. Notes: system items may have per‑player daily limits; purchase may span multiple listings until quantity is satisfied.
- **/mlist [player|server]** – View listings of a player or the system shop.
- **/mcash <value> <quantity>** – Convert balance into configured physical currency items.
- **/mexchange <quantity>** – Convert matching physical currency stacks (held + inventory) back into balance (signature = itemID + NBT if any).
- **/mmenu** – Open interactive GUI (home, seller list, shop pages; left click = 1, right click = up to stack respecting limits).

## Admin Commands (OP 4)
- **/mset <player> <amount>** – Set player balance exactly.
- **/aprice <price> [limitPerDay]** – Set or update system shop price (optional per‑player daily limit, -1 = unlimited; stock is infinite regardless).
- **/apull** – Remove the held item from the system shop.
- **/mlang <language>** – Switch system language (e.g. `en`, `zh`).
- **/acash <value> | get | del | list [itemID]** – Manage physical currency mapping for the held item (set / query / delete / list).
- **/mreload** – Reload configuration (including language if defined in config file).

## Market System Overview
1. Dual Markets: Player Market (finite player stock, custom prices) + System Market (admin defined, infinite stock, optional per‑player daily caps).
2. Smart Purchasing: Auto lowest-price aggregation across sellers; optional seller pin.
3. GUI: `/mmenu` for browsing sellers & listings with pagination and quick purchases.
4. Physical Currency: Admin maps arbitrary items (optionally with custom NBT) to face values; players cash in/out via `/mcash` & `/mexchange`.

## Physical Currency Notes
- Unique signature = Item ID + NBT.
- Use distinctive NBT to reduce counterfeits.
- Currency issuance respects max stack size automatically.

## Database Configuration (SQLite / MySQL)
Default storage: embedded SQLite file `market.db` (created automatically). To use MySQL, edit (or let the mod generate then edit) `config/server-market/config.properties`:

Set:
```
storage_type = mysql
mysql_host = <host>
mysql_port = 3306
mysql_database = server_market
mysql_user = <user>
mysql_password = <password>
mysql_use_ssl = false
# Optional extra JDBC parameters (append form):
mysql_jdbc_params = rewriteBatchedStatements=true&connectTimeout=10000
```
Key points:
- If the file doesn’t exist it is generated with defaults on first run.
- Switch `storage_type` back to `sqlite` anytime (databases are independent; manual migration not automatic).
- SSL: set `mysql_use_ssl = true` if your server requires it; you can still supply additional params via `mysql_jdbc_params`.
- Character set & timezone parameters are auto‑appended: `useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC&allowPublicKeyRetrieval=true` (do not duplicate them yourself).

### Migration / Schema
Tables are auto-created if missing. SQLite only applies lightweight `ALTER` additions on load (ignored if already present). No destructive migrations are performed automatically.

### Recommended MySQL Index / Tuning
The schema already defines primary & unique keys plus basic indices. For very large history tables consider periodic archival or pruning if you disable or exceed `max_history_records` logic in configs (see below).

## Configuration Keys (Core)
(Located in the same properties file; only essential keys listed here.)
- `initial_player_balance` – Starting balance for new players.
- `max_transfer_amount` – Transfer cap per operation.
- `enable_transaction_history` – Enable recording trade / transfer events.
- `max_history_records` – Soft cap for retained history rows.
- `enable_tax` + `market_tax_rate` – Enable & set a tax on market transactions (0–1, e.g. 0.05 = 5%).
- `enable_debug_logging` – Extra log noise for troubleshooting.

## Daily Limits (System Shop)
If a system listing has `limit_per_day >= 0`, each player’s purchases of that exact (itemID + NBT) are capped per real‑world server local day (resets at date change).

## Language
`/mlang` changes active language at runtime; restarting or using `/mreload` re-reads config default if provided.

## Security Tips
- Use custom NBT for currency items.
- Restrict admin commands to trusted operators only.

## License
See `LICENSE.txt`.
