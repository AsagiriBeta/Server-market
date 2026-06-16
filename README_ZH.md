# Server Market

[English](./README.md)

为 Minecraft 服务器提供玩家经济 + 交易市场（GUI + 命令）。

## 版本 / 依赖

### 支持的 Minecraft 版本

主支 `master` 一次 `./gradlew buildAll` 可构建 **6 个 JAR**，覆盖 **1.20 – 1.21.11**：

| JAR 文件名后缀 | 兼容版本 |
|---|---|
| `Server-market_1_20_4-*.jar` | 1.20 – 1.20.4 |
| `Server-market_1_20_6-*.jar` | 1.20.5 – 1.20.6 |
| `Server-market_1_21_1-*.jar` | 1.21 – 1.21.1 |
| `Server-market_1_21_5-*.jar` | 1.21.2 – 1.21.5 |
| `Server-market_1_21_8-*.jar` | 1.21.6 – 1.21.8 |
| `Server-market_1_21_11-*.jar` | 1.21.9 – 1.21.11 |

> 1.20 – 1.20.4 使用独立 overlay（纯 NBT API，Java 17 字节码）；1.20.5+ 使用组件 API overlay。

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
    v1_20_4/       # 1.20 – 1.20.4 专用代码（NBT ItemKey、GUI 等）
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

### ServerMarketApi

入口：`asagiribeta.serverMarket.api.ServerMarketApiProvider`

- `getBalance`, `hasEnough`, `getParcelCount`
- `addBalance`, `withdraw`, `setBalance`, `transfer`
- `getTopBalances`, `getHistory`, `format`
- `openMenu`, `getModVersion`

### EconomyProvider（Vault 风格）

入口：`asagiribeta.serverMarket.api.economy.EconomyProviderRegistry`

其他模组可通过 `EconomyProviderRegistry.get()` 获取统一经济接口，支持余额查询、存取款、转账与格式化。

### 事件

- `ServerMarketEvents.BALANCE_CHANGED` — 任意余额变动后触发
- `ServerMarketEvents.PRE_PURCHASE` — 购买前拦截（返回 false 取消）
- `ServerMarketEvents.POST_PURCHASE` — 购买完成后通知

### 经济特性

- **市场税**：配置 `enable_tax` / `market_tax_rate`，卖家结算时自动扣税
- **交易历史**：`enable_transaction_history` + `max_history_records` 自动修剪
- **离线转账**：`/svm pay` 支持离线玩家（基于余额表 lookup）
- **历史查询**：`/svm history [页码]`、`/svm admin history <玩家> [页码]`

## 配置 / 数据库

配置文件：`config/server-market/config.properties`

SQLite 开箱即用；MySQL 请设置 `storage_type = mysql` 并填写 `mysql_*`。

## CI / 发布

GitHub Actions 仅在推送到 **master** 分支时运行 `./gradlew buildAll` 并发布 Release 附件。

## 许可证

参见 [LICENSE.txt](./LICENSE.txt)
