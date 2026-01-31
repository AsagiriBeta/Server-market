# Server Market

[中文文档](./README_ZH.md)

A Fabric mod providing player economy + item market (GUI + commands) for Minecraft servers.

## Requirements

Required:
- Fabric Permissions API **0.5.0**
- Fabric Language Kotlin **1.13.8+kotlin.2.3.0**

Optional:
- LuckPerms (recommended) for permission management

## Features

- Player balance (transfer / trading)
- Player market + system shop
- Purchase orders (players/admins) + parcel station delivery
- Optional physical-currency items
- SQLite (default) / MySQL (optional, with XConomy record writing)

## Commands

Player:
```bash
/svm
/svm menu

/svm money
/svm balance

/svm pay <player> <amount>

/svm sell <price>
/svm restock <qty>
/svm pull

/svm list
/svm search <keyword>

/svm buy <qty> <item> [seller]

/svm cash <amount>
/svm exchange <amount>

/svm order <price> <amount>
/svm supply <quantity>
```

Admin:
```bash
/svm admin balance <player>
/svm admin set <player> <amount>
/svm admin add <player> <amount>

/svm admin price <price> [limit]
/svm admin pull
/svm admin purchase <price> [limit]

/svm admin cash get
/svm admin cash del
/svm admin cash list [item]
/svm admin cash <value>

/svm admin rank
/svm admin reload
```

## Updates

- Command wording updated:
  - purchase order: `/svm purchase` -> `/svm order`
  - sell to buyer: `/svm selltopurchase` -> `/svm supply`
- Removed command group: `/svm edit` (admin commands are now under `/svm admin`).
- Deprecated: `/svm admin lang`

## Placeholder API

This mod bundles **Placeholder API** and registers:
- `%server-market:balance%`
- `%server-market:balance_short%`
- `%server-market:parcel_count%`
- `%server-market:player_name%`
- `%server-market:top_name:<rank>%` (rank 1-10)
- `%server-market:top_balance:<rank>%` (rank 1-10)
- `%server-market:top_balance_short:<rank>%` (rank 1-10)

## Integration API

Entry point: `asagiribeta.serverMarket.api.ServerMarketApiProvider`

- `getBalance`, `getParcelCount`
- `addBalance`, `withdraw`, `transfer`

Event:
- `ServerMarketEvents.BALANCE_CHANGED`

## Configuration / Storage

Config file: `config/server-market/config.properties`

SQLite works out of the box. For MySQL, set `storage_type = mysql` and fill `mysql_*` fields.

## License

See [LICENSE.txt](./LICENSE.txt)
