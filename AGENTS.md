# AGENTS.md

## Cursor Cloud specific instructions

This repo is **Server Market**, a server-side **Minecraft Fabric mod** (Kotlin + Gradle). One `master` branch builds multiple version-group JARs via `./gradlew buildAll`.

### Prerequisites
- JDK 21 (Gradle toolchain; older MC groups compile with `--release` matching their bytecode target when needed).
- Use the wrapper: `./gradlew` (Gradle 9.2.0).

### Build
- All supported groups: `./gradlew buildAll` → `build/libs/Server-market_<group>-<version>.jar`
- Single group: `./gradlew build -Pmc_group=1_21_11`
- Groups are defined in `gradle.properties` (`version_groups`, `group.*`).
- No separate linter; `:compileKotlin` during `build` is the main static check.
- `(3.45.1.0) is not valid semver for dependency org.xerial:sqlite-jdbc` during `:processIncludeJars` is benign.

### Source layout
- `src/common/` — shared Kotlin + resources
- `src/versions/v1_20_4/` — overlay for MC 1.20 – 1.20.4 (NBT ItemKey, GUI, Placeholder stub, Java 17 bytecode)
- `src/versions/v1_20_6/` — overlay for MC 1.20.5 – 1.21.8 (ItemKey, GUI helpers, Placeholder stub)
- `src/versions/v1_21_11/` — overlay for MC 1.21.9+ (modern ItemKey, Placeholder API)

### Dev server
- `./gradlew runServer` (defaults to latest group `1_21_11`)
- Override group: `./gradlew runServer -Pmc_group=1_21_8`
- First run needs `eula=true` in `run/eula.txt`.
- For offline testing: `online-mode=false`, `enforce-secure-profile=false` in `run/server.properties`.
- Console commands omit the leading `/`, e.g. `svm admin remove Player123 50`.
- Storage defaults to SQLite at `run/market.db`.

### Dev client (optional)
- `DISPLAY=:1 ./gradlew runClient -Pmc_group=1_21_11`

### CI
- `.github/workflows/build-release.yml` runs only on pushes to `master`.

### Notes
- `fabric-permissions-api` is required at runtime on real servers.
- LuckPerms / XConomy are optional integrations.
