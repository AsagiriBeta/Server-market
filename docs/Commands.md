# Commands

All commands are under `/svm`. Most player commands require the player to be online and holding relevant items where noted.

Permission nodes follow the pattern `servermarket.command.<subcommand>` for players and `servermarket.admin.<subcommand>` for admins (requires OP level 4 by default).

---

## Player commands

| Command | Description |
|---------|-------------|
| `/svm` | Open the market GUI (alias of `/svm menu`) |
| `/svm menu` | Open the market GUI |
| `/svm money` | Show your balance |
| `/svm balance` | Alias of `/svm money` |
| `/svm pay <player> <amount>` | Transfer money (supports offline players who have joined before) |
| `/svm history [page]` | View your transaction history (10 entries per page) |

### Player shop

Hold the item in your main hand unless noted.

| Command | Description |
|---------|-------------|
| `/svm sell <price>` | List held item at `<price>` (first listing) |
| `/svm restock <quantity>` | Add stock to an already-listed item |
| `/svm pull` | Remove held item from your shop |
| `/svm list` | List items on the market |
| `/svm search <keyword>` | Search market listings |
| `/svm buy <quantity> <item> [seller]` | Buy from market (`<item>` is an item ID, e.g. `minecraft:diamond`) |

> When multiple NBT variants of the same item exist, use the GUI to pick the exact variant; `/svm buy` will not guess.

### Physical currency

| Command | Description |
|---------|-------------|
| `/svm cash <value> <quantity>` | Exchange balance for physical currency items (item must be configured by admin) |
| `/svm exchange <amount>` | Convert held physical currency back to balance |

### Buy orders (player)

Hold the item you want to buy.

| Command | Description |
|---------|-------------|
| `/svm order <price> <amount>` | Create/update a buy order (alias of purchase args) |
| `/svm purchase <price> <amount>` | Create/update a buy order |
| `/svm supply <quantity>` | Sell held items to matching buy orders |
| `/svm selltopurchase <quantity>` | Legacy alias for `/svm supply` |

---

## Admin commands

Base: `/svm admin` (requires `servermarket.admin`, OP 4)

### Balance management

| Command | Description |
|---------|-------------|
| `/svm admin balance <player>` | Query balance (offline OK) |
| `/svm admin set <player> <amount>` | Set balance |
| `/svm admin add <player> <amount>` | Add balance |
| `/svm admin remove <player> <amount>` | Deduct balance (offline OK; fails if insufficient) |
| `/svm admin rank` | Show balance leaderboard |
| `/svm admin history <player> [page]` | View a player's transaction history |

### System shop

Hold the item in your main hand.

| Command | Description |
|---------|-------------|
| `/svm admin price <price> [limitPerDay]` | Set system shop price (`limitPerDay`: -1 = unlimited) |
| `/svm admin pull` | Remove held item from system shop |
| `/svm admin purchase <price> [limitPerDay]` | Set system buy order |

### Physical currency (admin)

| Command | Description |
|---------|-------------|
| `/svm admin cash <value>` | Register held item as physical currency with face value |
| `/svm admin cash get` | Show face value of held item |
| `/svm admin cash del` | Remove physical currency setting for held item |
| `/svm admin cash list [item]` | List all physical currency configs |

### Server

| Command | Description |
|---------|-------------|
| `/svm admin lang <zh\|en>` | Switch server-side message language |
| `/svm admin reload` | Reload `config.properties` |

---

## GUI

The market GUI (`/svm` or `/svm menu`) provides:

- Browse system and player shops
- Purchase with exact NBT variant selection
- My shop management, parcel station, balance rank

Most operations available via commands can also be done through the GUI.
