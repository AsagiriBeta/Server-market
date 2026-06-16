# Server Market

[English](./README.md)

为 Minecraft 服务器提供玩家经济 + 交易市场（GUI + 命令）。

## 版本 / 依赖

### 支持的 Minecraft 版本

主支 `master` 一次 `./gradlew buildAll` 可构建 **5 个 JAR**，覆盖 **1.20.5 – 1.21.11**：

| JAR 文件名后缀 | 兼容版本 |
|---|---|
| `Server-market_1_20_6-*.jar` | 1.20.5 – 1.20.6 |
| `Server-market_1_21_1-*.jar` | 1.21 – 1.21.1 |
| `Server-market_1_21_5-*.jar` | 1.21.2 – 1.21.5 |
| `Server-market_1_21_8-*.jar` | 1.21.6 – 1.21.8 |
| `Server-market_1_21_11-*.jar` | 1.21.9 – 1.21.11 |

> 1.20 – 1.20.4 因物品/NBT API 差异过大，暂未纳入统一构建；如需旧版请使用历史分支。

### 构建

```bash
# 构建全部版本
./gradlew buildAll

# 构建单个版本组
./gradlew build -Pmc_group=1_21_11
```

### 运行时依赖

必需：
- Fabric Permissions API（版本随 MC 版本变化，见 `gradle.properties`）
- Fabric Language Kotlin

可选：
- LuckPerms（推荐）用于权限管理

## 功能

- 玩家余额（转账/交易）
- 玩家市场 + 系统商店
- 收购订单（玩家/管理员）+ 快递驿站自动投递
- 可选：实物货币
- SQLite（默认）/ MySQL（可选，支持写入 XConomy 记录）

## 命令

玩家：
```bash
/svm
/svm menu

/svm money
/svm balance

/svm pay <玩家> <金额>

/svm sell <价格>
/svm restock <数量>
/svm pull

/svm list
/svm search <关键词>

/svm buy <数量> <物品> [卖家]

/svm cash <金额>
/svm exchange <金额>

/svm order <价格> <数量>
/svm supply <数量>
```

管理员：
```bash
/svm admin balance <玩家>
/svm admin set <玩家> <金额>
/svm admin add <玩家> <金额>
/svm admin remove <玩家> <金额>

/svm admin price <价格> [限购]
/svm admin pull
/svm admin purchase <价格> [限额]

/svm admin cash get
/svm admin cash del
/svm admin cash list [物品]
/svm admin cash <面值>

/svm admin rank
/svm admin reload
```

## 源码结构

```
src/
  common/          # 所有版本共享的业务逻辑
  versions/
    v1_20_6/       # 1.20.5 – 1.21.8 专用代码（ItemKey、GUI 等）
    v1_21_11/      # 1.21.9+ 专用代码（Placeholder API、现代 ItemKey 等）
```

## Placeholder API

仅 **1.21.9+** JAR 内置 Placeholder API，并提供：
- `%server-market:balance%`
- `%server-market:balance_short%`
- `%server-market:parcel_count%`
- `%server-market:player_name%`
- `%server-market:top_name:<rank>%`（名次 1-10）
- `%server-market:top_balance:<rank>%`（名次 1-10）
- `%server-market:top_balance_short:<rank>%`（名次 1-10）

## 联动 API

入口：`asagiribeta.serverMarket.api.ServerMarketApiProvider`

- `getBalance`, `getParcelCount`
- `addBalance`, `withdraw`, `transfer`

事件：
- `ServerMarketEvents.BALANCE_CHANGED`

## 配置 / 数据库

配置文件：`config/server-market/config.properties`

SQLite 开箱即用；MySQL 请设置 `storage_type = mysql` 并填写 `mysql_*`。

## CI / 发布

GitHub Actions 仅在推送到 **master** 分支时运行 `./gradlew buildAll` 并发布 Release 附件。

## 许可证

参见 [LICENSE.txt](./LICENSE.txt)
