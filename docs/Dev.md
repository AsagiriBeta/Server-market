# Development

## Prerequisites

- **JDK 21** (Gradle toolchain; older MC groups may compile with `--release 17` bytecode)
- Git

Use the Gradle wrapper: `./gradlew`

## Build

```bash
# Build all version groups (6 JARs)
./gradlew buildAll

# Build a single group
./gradlew build -Pmc_group=1_21_11
```

Output: `build/libs/Server-market_<group>-<version>.jar`

Version groups are defined in `gradle.properties` (`version_groups`, `group.*`).

| Group key | MC range | Overlay |
|-----------|----------|---------|
| `1_20_4` | 1.20 – 1.20.4 | `v1_20_4` |
| `1_20_6` | 1.20.5 – 1.20.6 | `v1_20_6` |
| `1_21_1` | 1.21 – 1.21.1 | `v1_20_6` |
| `1_21_5` | 1.21.2 – 1.21.5 | `v1_20_6` |
| `1_21_8` | 1.21.6 – 1.21.8 | `v1_20_6` |
| `1_21_11` | 1.21.9 – 1.21.11 | `v1_21_11` |

There is no separate linter; `:compileKotlin` during `build` is the main static check.

> Warning `(3.45.1.0) is not valid semver for dependency org.xerial:sqlite-jdbc` during `:processIncludeJars` is benign.

## Source layout

```
src/
  common/                 # Shared Kotlin + resources (business logic, commands, API)
  versions/
    v1_20_4/              # MC 1.20 – 1.20.4 (NBT ItemKey, GUI, Java 17 bytecode)
    v1_20_6/              # MC 1.20.5 – 1.21.8 (ItemKey, GUI helpers)
    v1_21_11/             # MC 1.21.9+ (modern ItemKey, Placeholder API)
```

Gradle merges `src/common` with the overlay for the selected `-Pmc_group`.

## Local dev server

```bash
./gradlew runServer                      # defaults to 1_21_11
./gradlew runServer -Pmc_group=1_21_8    # pick another group
```

First run:

1. Set `eula=true` in `run/eula.txt`
2. For offline testing, set in `run/server.properties`:
   - `online-mode=false`
   - `enforce-secure-profile=false`

Console commands omit the leading `/`, e.g. `svm admin remove Player123 50`.

### Headless player testing (Minecraft Console Client)

For low-resource integration testing without a GUI client:

```bash
# Terminal 1 — dev server
./gradlew runServer

# Terminal 2 — console client (offline server)
curl -fsSL https://mccteam.github.io/install.sh | sh   # downloads ./MinecraftClient
./MinecraftClient TestPlayer - localhost:25565
# then type commands, e.g. /svm balance
```

Set `online-mode=false` in `run/server.properties` for offline mode.


Storage defaults to SQLite at `run/market.db`.

## Local dev client (optional)

```bash
DISPLAY=:1 ./gradlew runClient -Pmc_group=1_21_11
```

## CI / releases

GitHub Actions workflow `.github/workflows/build-release.yml`:

- Runs `./gradlew buildAll` on pushes to `master`
- Skipped when **only** Markdown files change (see `paths-ignore` in the workflow)
- Publishes JARs to GitHub Releases

Manual trigger: **Actions → Build and Release → Run workflow**.

## Inter-mod API

### ServerMarketApi

Entry: `asagiribeta.serverMarket.api.ServerMarketApiProvider`

```kotlin
val api = ServerMarketApiProvider.get() ?: return
api.getBalance(uuid)
api.hasEnough(uuid, amount)
api.transfer(from, to, amount, reason = "my-mod")
api.getHistory(uuid, page = 1)
api.format(1234.5)
api.openMenu(player)
```

Methods: `getBalance`, `hasEnough`, `getParcelCount`, `addBalance`, `withdraw`, `setBalance`, `transfer`, `getTopBalances`, `getHistory`, `format`, `openMenu`, `getModVersion`.

Use **ServerMarketApi** when you need market-specific features (parcels, GUI, history). For balance-only integration, prefer **Common Economy API** below.

### Common Economy API (Patbox)

Server Market registers a provider with [Common Economy API](https://github.com/Patbox/common-economy-api) when that mod is installed on the server (not bundled — use a build matching your Java/Minecraft version). Other mods should use:

```kotlin
val account = CommonEconomy.getAccount(player, Identifier.of("server-market", player.uuidAsString))
account?.balance() // BigInteger minor units (scale 2)
```

Provider id: `server-market`, currency id: `server-market:coin`.

### Events

```kotlin
// Fired after any balance change (commands, market, API)
ServerMarketEvents.BALANCE_CHANGED.register { uuid, delta, reason, actor -> ... }

// Return false to cancel a market purchase
ServerMarketEvents.PRE_PURCHASE.register { buyer, itemId, nbt, qty, cost -> qty <= 64 }

// Fired after purchase attempt (success or failure)
ServerMarketEvents.POST_PURCHASE.register { buyer, itemId, qty, cost, success -> ... }
```

## Architecture notes

- **EconomyService** — single entry point for balance mutations, history, and events
- **Database** — single-thread executor; use `database.supplyAsync { }` for async DB work
- **MarketService** — purchase/sell logic; applies market tax when configured
- **MarketOverviewService** — sell/buy order snapshot shown when listing items (Stonks-inspired)
- **CommonEconomyBridge** — reflection-based Common Economy API v2 registration (Yarn/Mojang mapping safe)
- **PlayerLookupService** — offline player name ↔ UUID resolution via balance table

## Related files

- `gradle.properties` — mod version, version groups, dependency pins
- `build.gradle.kts` — multi-version overlay build logic
- `AGENTS.md` — Cursor Cloud agent instructions (not user-facing wiki)
