# Server Market

[中文文档](./README_ZH.md)

A Fabric mod that adds a complete player economy and item trading market to Minecraft servers.

## Features

- 💰 **Player Economy** - Balance system with transfers and trading
- 🛒 **Dual Market** - Player shops (limited stock) + System shop (infinite stock, admin-defined)
- 📥 **Purchase System** - Players and admins can set purchase orders, others can sell items for money
- 📦 **Parcel Station** - Purchased items are delivered to a personal parcel station for safe pickup
- 🔍 **Smart Shopping** - Auto-matches lowest prices across all sellers
- 🖥️ **GUI Interface** - Easy-to-use menu for browsing, purchasing, and selling
- 💵 **Physical Currency** - Optional item-based currency system
- 🗄️ **Flexible Storage** - SQLite (default) or MySQL with XConomy support

## Quick Start

### Player Commands

```bash
/svm money                           # Check balance
/svm buy <qty> <item> [seller]       # Buy items
/svm sell <qty>                      # List items for sale
/svm menu                            # Open GUI (access parcel station here)
/svm menu                            # Open GUI
/svm purchase <price> <amount>       # Set purchase order (hold item)
/svm selltopurchase <quantity>       # Sell to buyers (hold item)
```
**How the Parcel Station Works:**
- When someone sells items to your purchase order, they are sent to your parcel station
- Open `/svm menu` and click the "Parcel Station" button (minecart chest icon)
- Same items are automatically merged and show total quantity
- Click any parcel to receive all quantities of that item into your inventory
- The home page shows how many parcels are waiting for you


### Admin Commands

```bash
/svm edit set <player> <amount>      # Set player balance
/svm edit price <price> [limit]      # Set system shop price
/svm edit cash <value>               # Configure currency items
/svm edit purchase <price> [limit]   # Set system purchase (hold item)
/svm edit reload                     # Reload configuration
```

## Installation

1. Download the mod JAR file
2. **Download [Fabric Permissions API](https://modrinth.com/mod/fabric-permissions-api)** (required dependency)
3. Place both JAR files in your server's `mods` folder
4. Start the server - configuration will be auto-generated at `config/server-market/config.properties`
5. (Optional) Install [LuckPerms](https://luckperms.net/) for advanced permissions

**Note:** Fabric Permissions API is a required dependency. Make sure to download the correct version:
- For Minecraft 1.21.6+: Use fabric-permissions-api v0.4.0 or later
- For Minecraft 1.21.5 and below: Use fabric-permissions-api v0.3.3

## Database Setup

**SQLite (Default)** - No setup required, works out of the box.

**MySQL** - Edit `config/server-market/config.properties`:

```properties
storage_type = mysql
mysql_host = localhost
mysql_port = 3306
mysql_database = server_market
mysql_user = your_user
mysql_password = your_password
```

**XConomy Compatibility** - When using MySQL, the mod can share balances with Paper servers running XConomy. Point both servers to the same database.

## Permissions

The mod uses Fabric Permissions API with LuckPerms support.

**Quick Setup (LuckPerms):**
```bash
# Grant all player commands
/lp group default permission set servermarket.command.* true

# Grant all admin commands
/lp group admin permission set servermarket.admin.* true
```

**Fallback:** Without a permissions plugin, all players can use player commands, and OP level 4 is required for admin commands.

## Configuration

Key settings in `config/server-market/config.properties`:

- `initial_player_balance` - Starting balance for new players
- `enable_tax` / `market_tax_rate` - Market transaction tax
- `storage_type` - Database type (sqlite/mysql)
- `language` - Default language (en/zh)

Old commands like `/mbuy`, `/mpay`, `/mset` have been replaced with the unified `/svm` command system.

## License

See [LICENSE.txt](./LICENSE.txt)

