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
/svm money
/svm buy <数量> <物品> [卖家]
/svm sell <数量>
/svm menu
/svm purchase <价格> <数量>
/svm selltopurchase <数量>
```

管理员：
```bash
/svm edit set <玩家> <金额>
/svm edit price <价格> [限购]
/svm edit cash <面值>
/svm edit purchase <价格> [限额]
/svm edit reload
```

## Placeholder API

本模组已内置 **Placeholder API**，并提供：
- `%server-market:balance%`
- `%server-market:parcel_count%`
- `%server-market:player_name%`

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
