# Server Market 模组使用指南 | [English](./README.md)

## 简介
为服务器提供完整玩家经济与物品交易市场：玩家余额、转账、玩家与系统商店、全局搜索、图形界面、可选“实物货币”机制。

## 玩家指令
- **/money** 查看当前余额。
- **/mpay <玩家> <金额>** 向其他玩家转账。
- **/mprice <价格>** 设置手持物品的出售单价（影响后续补货的该物品+NBT 条目）。
- **/msell <数量>** 将手持物品补货到个人商店。
- **/mpull** 下架手持物品并取回库存。
- **/msearch <物品ID>** 全服市场搜索指定物品。
- **/mbuy <数量> <物品ID> [卖家]** 购买物品；未指定卖家时自动聚合最低价，指定玩家名或 `server` 固定来源。可跨多个上架分批成交；系统商品可能有“每日限购”。
- **/mlist [玩家|server]** 查看指定玩家或系统商店上架列表。
- **/mcash <面值> <数量>** 将余额兑换为配置好的“实物货币”物品。
- **/mexchange <数量>** 将主手及背包中同签名（物品ID+NBT）的实物货币兑换回余额。
- **/mmenu** 打开 GUI 市场（首页 / 卖家列表 / 店铺分页；左键购 1，右键尝试整组，上限与限购生效）。

## 管理员指令
- **/mset <玩家> <金额>** 精确设置玩家余额。
- **/aprice <价格> [limitPerDay]** 设置系统商店价格与可选“单玩家每日限购”（-1 不限）。
- **/apull** 将手持物品从系统商店移除。
- **/mlang <语言>** 切换语言（如 `en`, `zh`）。
- **/acash <面值> | get | del | list [物品ID]** 管理“实物货币”映射：设置 / 查询 / 删除 / 列表。
- **/mreload** 重新加载配置（含语言）。

## 系统特性概览
1. 双市场：玩家市场（有限库存，自主定价）+ 系统商店（无限库存，可限购）。
2. 智能购买：自动最低价聚合，可指定卖家。
3. GUI：`/mmenu` 便捷分类浏览与快速购买。
4. 实物货币：管理员映射任意物品（可含 NBT）为面值；玩家自由兑换与回收。

## 实物货币说明
- 唯一签名 = 物品ID + NBT。
- 推荐使用含自定义 NBT 的物品降低仿制风险。
- 发放时自动按最大堆叠数分组。

## 数据库配置（仅 MySQL）
本模组现仅支持 MySQL，且直接使用 XConomy 的表结构。配置文件 `config/server-market/config.properties`：

必要项：
```
storage_type = mysql
mysql_host = <主机>
mysql_port = 3306
mysql_database = server_market
mysql_user = <用户>
mysql_password = <密码>
mysql_use_ssl = false
# 附加 JDBC 参数（使用 & 连接）：
mysql_jdbc_params = rewriteBatchedStatements=true&connectTimeout=10000
```
说明：
- 首次运行会自动生成默认配置。
- 代码内部会自动附加：`useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC&allowPublicKeyRetrieval=true`。

### XConomy（Paper）兼容
兼容已内置。将 Fabric 与 Paper 指向同一 MySQL 数据库；若缺表会自动创建。

关键键值：
```
xconomy_player_table = xconomy
xconomy_nonplayer_table = xconomynon
xconomy_record_table = xconomyrecord
xconomy_login_table = xconomylogin
xconomy_system_account = SERVER
xconomy_write_record = false
```
行为：
- 玩家余额直连 XConomy 玩家表（`UID`, `player`, `balance`, `hidden`）。
- 系统账户（UUID 全 0）映射为 XConomy 非玩家表 `account = xconomy_system_account`（默认 `SERVER`）。
- 转账（如购买）在 XConomy 表内完成：玩家 → 系统 → 卖家。
- `xconomy_write_record = true` 时，会将交易镜像到 `xconomyrecord`（尽力而为，不匹配会忽略且不影响交易）。

注意：
- Fabric 与 Paper 必须连接同一个 MySQL 库。
- 表名需与 Paper/XConomy 实际创建一致（若自定义了后缀/前缀，请同步修改）。
- 本模组不介入 XConomy 的缓存/Redis，请避免与其他插件产生同时写入冲突。

## 关键配置项（核心）
- `initial_player_balance` 新玩家初始余额。
- `max_transfer_amount` 单次转账上限。
- `enable_transaction_history` 是否记录交易/转账历史。
- `max_history_records` 历史记录软上限。
- `enable_tax` 与 `market_tax_rate` 启用与设置市场税（0–1，例如 0.05 = 5%）。
- `enable_debug_logging` 输出调试日志。

## 每日限购（系统商店）
若系统条目设置 `limit_per_day >= 0`，则按服务器本地自然日对该 (itemID + NBT) 的玩家购买量累计并限制，日期变更自动重置。

## 语言
使用 `/mlang` 运行时切换；`/mreload` 或重启后会按配置文件默认加载。

## 安全提示
- 优先选择带独特 NBT 的物品作为货币。
- 管理指令仅授予可信任管理员。

## 许可证
参见 `LICENSE.txt`。
