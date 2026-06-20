# Installation

## Requirements

- **Minecraft server**: 1.20 – 1.21.11 (Fabric)
- **Java**: **25** recommended for **1.21.9 – 1.21.11** (Common Economy API and current server practice); Java 21 for 1.20.5 – 1.21.8; Java 17 for 1.20 – 1.20.4
- **Fabric Loader** and **Fabric API** (version matched to your MC version)
- **Fabric Language Kotlin**
- **Fabric Permissions API** (required at runtime)

### Optional

| Component | Purpose |
|-----------|---------|
| [LuckPerms](https://luckperms.net/) | Permission management (recommended) |
| MySQL + XConomy tables | Shared economy storage across plugins |
| Placeholder API | Placeholders (built into every version-group JAR; MC-matched API version) |
| Common Economy API | Economy integration for other mods (built into every version-group JAR) |

## Download

1. Open [Releases](https://github.com/AsagiriBeta/Server-market/releases).
2. Download the JAR whose suffix matches your Minecraft version:

| JAR suffix | Minecraft range |
|------------|-----------------|
| `Server-market_1_20_4-*.jar` | 1.20 – 1.20.4 |
| `Server-market_1_20_6-*.jar` | 1.20.5 – 1.20.6 |
| `Server-market_1_21_1-*.jar` | 1.21 – 1.21.1 |
| `Server-market_1_21_5-*.jar` | 1.21.2 – 1.21.5 |
| `Server-market_1_21_8-*.jar` | 1.21.6 – 1.21.8 |
| `Server-market_1_21_11-*.jar` | 1.21.9 – 1.21.11 |

3. Place the JAR in your server's `mods/` folder alongside Fabric API, Kotlin, and Permissions API.
4. Start the server once to generate the config file.

## Configuration

Config path: `config/server-market/config.properties`

Reload in-game: `/svm admin reload`

### Economy

| Key | Default | Description |
|-----|---------|-------------|
| `initial_player_balance` | `100.0` | Balance for first-time joiners |
| `max_transfer_amount` | `1000000.0` | Max amount per `/svm pay` |
| `enable_transaction_history` | `true` | Record transactions in the `history` table |
| `max_history_records` | `10000` | Auto-prune oldest records beyond this limit |
| `enable_tax` | `false` | Deduct tax from player shop sales |
| `market_tax_rate` | `0.05` | Tax rate (0.0–1.0) when `enable_tax=true` |

### Storage (SQLite — default)

| Key | Default | Description |
|-----|---------|-------------|
| `storage_type` | `sqlite` | `sqlite` or `mysql` |
| `sqlite_path` | `market.db` | SQLite database file path |

SQLite works out of the box; no extra setup required.

### Storage (MySQL / XConomy)

Set `storage_type=mysql` and configure:

| Key | Default |
|-----|---------|
| `mysql_host` | `localhost` |
| `mysql_port` | `3306` |
| `mysql_database` | `server_market` |
| `mysql_user` | `root` |
| `mysql_password` | *(empty)* |
| `mysql_use_ssl` | `false` |
| `mysql_jdbc_params` | *(empty)* |

XConomy table names (when sharing an existing XConomy database):

| Key | Default |
|-----|---------|
| `xconomy_player_table` | `xconomy` |
| `xconomy_non_player_table` | `xconomynon` |
| `xconomy_record_table` | `xconomyrecord` |
| `xconomy_login_table` | `xconomylogin` |
| `xconomy_system_account` | `SERVER` |
| `xconomy_write_record` | `false` |

Set `xconomy_write_record=true` to mirror transactions into XConomy's record table (MySQL only).

### Debug

| Key | Default | Description |
|-----|---------|-------------|
| `enable_debug_logging` | `false` | Verbose debug logs |

## Permissions

The mod uses [Fabric Permissions API](https://github.com/lucko/fabric-permissions-api). With LuckPerms, grant nodes such as:

- `servermarket.command` — base access to `/svm`
- `servermarket.command.pay` — use `/svm pay`
- `servermarket.admin` — all admin subcommands

See command-specific nodes in [Commands](./Commands.md).

## Placeholder API

Placeholder API is bundled in every version-group JAR (no separate download). Available placeholders:

- `%server-market:balance%`
- `%server-market:balance_short%`
- `%server-market:parcel_count%`
- `%server-market:player_name%`
- `%server-market:top_name:<rank>%` (rank 1–10)
- `%server-market:top_balance:<rank>%`
- `%server-market:top_balance_short:<rank>%`
