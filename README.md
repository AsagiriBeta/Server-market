# Server Market

[中文说明](./README_ZH.md)

Currency and market mod for Minecraft Fabric servers (GUI + commands).

## Supported versions

Run `./gradlew buildAll` on `master` to produce **5 JARs** covering **Minecraft 1.20.5 – 1.21.11**:

| Artifact suffix | Minecraft range |
|---|---|
| `Server-market_1_20_6-*.jar` | 1.20.5 – 1.20.6 |
| `Server-market_1_21_1-*.jar` | 1.21 – 1.21.1 |
| `Server-market_1_21_5-*.jar` | 1.21.2 – 1.21.5 |
| `Server-market_1_21_8-*.jar` | 1.21.6 – 1.21.8 |
| `Server-market_1_21_11-*.jar` | 1.21.9 – 1.21.11 |

Build everything:

```bash
./gradlew buildAll
```

Build one group:

```bash
./gradlew build -Pmc_group=1_21_11
```

## Admin balance commands

```bash
/svm admin add <player> <amount>      # add balance
/svm admin remove <player> <amount>     # deduct balance
/svm admin set <player> <amount>      # set balance
/svm admin balance <player>             # query balance (offline OK)
```

## Source layout

```
src/
  common/       # shared logic
  versions/
    v1_20_6/    # legacy-compatible ItemKey / GUI helpers
    v1_21_11/   # 1.21.9+ Placeholder API + modern ItemKey
```

## CI

GitHub Actions runs `buildAll` and publishes release assets **only on pushes to `master`**.

See [README_ZH.md](./README_ZH.md) for the full feature list and Chinese documentation.
