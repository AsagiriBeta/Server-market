# AGENTS.md

## Cursor Cloud specific instructions

This repo is a single product: **Server Market**, a server-side **Minecraft 1.21.10 Fabric mod** written in Kotlin and built with Gradle (Kotlin DSL) via the `./gradlew` wrapper. There is no JS/Python toolchain, no `Makefile`, and no automated test source set (`:test` is `NO-SOURCE`).

### Prerequisites (already provided by the environment)
- JDK 21 (the Gradle Java toolchain targets Java 21).
- Gradle is provided by the wrapper (`./gradlew`, pinned to 9.2.0). Do not install Gradle globally.
- First `build`/`runServer`/`runClient` downloads Minecraft, Yarn mappings, and Maven deps from the network; these are cached in `~/.gradle` and the Loom cache, so subsequent runs are fast.

### Build / compile (this is the "lint")
- Build the mod jar: `./gradlew build` → outputs `build/libs/Server-market_1.21.10-<version>.jar`.
- There is no separate linter; the meaningful static check is `:compileKotlin`, which runs as part of `build`.
- The warning `(3.45.1.0) is not valid semver for dependency org.xerial:sqlite-jdbc` during `:processIncludeJars` is benign.

### Run the dev server (primary way to test end-to-end)
- Run: `./gradlew runServer`. This launches a dedicated Minecraft server (the mod's `environment` is `server`) on port **25565** with the run directory at `run/` (gitignored).
- First run stops immediately with "You need to agree to the EULA". To proceed you must set `eula=true` in `run/eula.txt`, then re-run. This is a one-time gotcha per fresh `run/` dir.
- To allow the dev client (or any offline client) to connect without Mojang auth, set `online-mode=false` and `enforce-secure-profile=false` in `run/server.properties`.
- The server console is interactive: you can type mod commands directly (without a leading `/`), e.g. `svm admin set <player> <amount>` and `svm admin balance <player>`. The console has op level 4, so admin commands work from it.
- Storage defaults to embedded **SQLite** (`run/market.db`); no external DB is required. MySQL is optional (`storage_type=mysql` + `mysql_*` in `config/server-market/config.properties`).

### Run the dev client (optional, for in-game / GUI testing)
- Run: `DISPLAY=:1 ./gradlew runClient` (a display server is available at `:1`). Audio/OpenAL errors in the log are expected (no sound card) and are non-fatal.
- The dev client auto-generates a player username like `Player###` (visible in the server log on join). Use that name for admin commands such as `svm admin set <name> <amount>`.
- In-game, open chat with `t`, then run e.g. `/svm money` (shows balance) or `/svm menu` (opens the Server Market GUI). `/svm admin set` requires the target player to be online.

### Notes
- `fabric-permissions-api` is a hard dependency but is pulled by Gradle for the dev runtime; on a real server it must be installed separately. LuckPerms / XConomy are optional.
