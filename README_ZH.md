# Server Market

[English](./README.md)

为 Minecraft 服务器提供玩家经济 + 交易市场（GUI + 命令）。

## 版本 / 依赖

必需：
- Fabric Permissions API **0.5.0**
- Fabric Language Kotlin **1.13.8+kotlin.2.3.0**

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

## 更新

- 命令用词调整：
  - 收购订单：`/svm purchase` -> `/svm order`
  - 向收购者出售：`/svm selltopurchase` -> `/svm supply`
- 已移除命令组：`/svm edit`（管理员命令统一使用 `/svm admin`）。
- 已废弃：`/svm admin lang`

## Placeholder API

本模组已内置 **Placeholder API**，并提供：
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

## 许可证

参见 [LICENSE.txt](./LICENSE.txt)
