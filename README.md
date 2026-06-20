# Server Market

[中文说明](./README_ZH.md)

**Server Market** is a Fabric server-side mod that adds a player economy and trading marketplace (GUI + commands).

## Features

- Player balances, transfers, and transaction history
- Player shops and system store
- Buy orders, parcel station delivery, optional physical currency
- SQLite by default; optional MySQL / XConomy record integration
- Inter-mod API (`ServerMarketApi`, Common Economy API) and market events

## Supported versions

Minecraft **1.20 – 1.21.11** (Fabric). Pick the JAR that matches your server version from [Releases](https://github.com/AsagiriBeta/Server-market/releases).

## Documentation

Detailed docs live in the repo (also suitable as GitHub Wiki pages):

| Page | Description |
|------|-------------|
| [Installation](./docs/Installation.md) | Dependencies, setup, configuration |
| [Commands](./docs/Commands.md) | Full `/svm` command reference |
| [Dev](./docs/Dev.md) | Building from source, project layout, API |

## Quick start

```bash
/svm              # open market GUI
/svm money        # check balance
/svm pay <player> <amount>
```

## License

See [LICENSE.txt](./LICENSE.txt).
