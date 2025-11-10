# Server Market

[English](./README.md)

为 Minecraft 服务器提供完整的玩家经济与物品交易市场系统。

## 功能特性

- 💰 **玩家经济** - 余额系统，支持转账和交易
- 🛒 **双重市场** - 玩家商店（有限库存）+ 系统商店（无限库存，管理员定义）
- 📥 **收购系统** - 玩家和管理员可设置收购订单，其他玩家可出售物品换钱
- 📦 **快递驿站** - 收购的物品会送到专属快递驿站，安全领取
- 🔍 **智能购物** - 自动匹配全服最低价
- 🖥️ **图形界面** - 便捷的菜单浏览、购买和出售系统
- 💵 **实物货币** - 可选的物品货币系统
- 🗄️ **灵活存储** - SQLite（默认）或 MySQL，支持 XConomy

## 快速开始

### 玩家命令

```bash
/svm money                           # 查询余额
/svm buy <数量> <物品> [卖家]         # 购买物品
/svm sell <数量>                     # 上架物品
/svm menu                            # 打开 GUI（可在此访问快递驿站）
/svm menu                            # 打开 GUI
/svm purchase <价格> <数量>           # 设置收购订单（手持物品）
/svm selltopurchase <数量>            # 向收购者出售（手持物品）
```
**快递驿站使用方法：**
- 当有人向你的收购订单出售物品时，物品会自动发送到你的快递驿站
- 打开 `/svm menu` 点击"快递驿站"按钮（箱子矿车图标）
- 相同的物品会自动合并显示总数量
- 点击任意包裹即可领取该物品的所有数量到背包
- 主页会显示你有多少个待领取的包裹


### 管理员命令

```bash
/svm edit set <玩家> <金额>           # 设置玩家余额
/svm edit price <价格> [限购]         # 设置系统商店价格
/svm edit cash <面值>                # 配置货币物品
/svm edit purchase <价格> [限额]      # 设置系统收购（手持物品）
/svm edit reload                     # 重载配置
```

## 安装方法

1. 下载模组 JAR 文件
2. 放入服务器的 `mods` 文件夹
3. 启动服务器 - 配置文件会自动生成在 `config/server-market/config.properties`
4. （可选）安装 [LuckPerms](https://luckperms.net/) 以使用高级权限管理

## 数据库设置

**SQLite（默认）** - 无需配置，开箱即用。

**MySQL** - 编辑 `config/server-market/config.properties`：

```properties
storage_type = mysql
mysql_host = localhost
mysql_port = 3306
mysql_database = server_market
mysql_user = 用户名
mysql_password = 密码
```

**XConomy 兼容** - 使用 MySQL 时，本模组可与运行 XConomy 的 Paper 服务器共享余额。只需将两个服务器指向同一个数据库。

## 权限配置

本模组使用 Fabric Permissions API，支持 LuckPerms。

**快速设置（LuckPerms）：**
```bash
# 授予所有玩家命令
/lp group default permission set servermarket.command.* true

# 授予所有管理员命令
/lp group admin permission set servermarket.admin.* true
```

**回退方案：** 没有权限插件时，所有玩家可使用玩家命令，管理员命令需要 OP 等级 4。

## 配置选项

`config/server-market/config.properties` 中的关键设置：

- `initial_player_balance` - 新玩家初始余额
- `enable_tax` / `market_tax_rate` - 市场交易税
- `storage_type` - 数据库类型（sqlite/mysql）
- `language` - 默认语言（en/zh）

旧命令如 `/mbuy`、`/mpay`、`/mset` 已被统一的 `/svm` 命令系统取代。

## 许可证

参见 [LICENSE.txt](./LICENSE.txt)

