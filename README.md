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
/svm money
/svm buy <qty> <item> [seller]
/svm sell <price>
/svm restock <qty>
/svm menu
/svm purchase <price> <amount>
/svm order <price> <amount>
/svm selltopurchase <quantity>
/svm supply <quantity>
```

Admin:
```bash
/svm edit set <player> <amount>
/svm edit price <price> [limit]
/svm edit cash <value>
/svm edit purchase <price> [limit]
/svm edit reload
```

## Placeholder API

This mod bundles **Placeholder API** and registers:
- `%server-market:balance%`
- `%server-market:balance_short%`
- `%server-market:parcel_count%`
- `%server-market:player_name%`

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
