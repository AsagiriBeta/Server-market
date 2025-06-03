# Server Market 模组使用指南 | [English](./README_EN.md)

## 模组简介
本模组为Minecraft服务器添加了玩家经济系统和物品交易市场，支持转账，交易、物品上架销售、全服市场搜索等功能。

## 主要指令列表

### 玩家基础指令
- **/money**  
  查看当前余额

- **/mpay <玩家> <金额>**  
  向其他玩家转账  
  示例: `/mpay Steve 100.5`

- **/mprice <价格>**  
  设置手持物品的出售价格  
  示例: `/mprice 5.0`

- **/msell <数量>**  
  补货手持物品到个人店铺  
  示例: `/msell 32`

- **/mpull**  
  下架手持物品并取回库存

- **/msearch <物品ID>**  
  搜索全服销售信息  
  示例: `/msearch minecraft:diamond`

- **/mbuy <数量> <物品ID>**  
  从市场购买物品  
  示例: `/mbuy 3 minecraft:emerald`

- **/mlist [玩家/server]**  
  查看指定玩家或系统商店的上架物品  
  示例: `/mlist Steve` 或 `/mlist server`

### 管理员指令 (需要权限等级4)
- **/mset <玩家> <金额>**  
  设置玩家余额  
  示例: `/mset Steve 1000.0`

- **/aprice <价格>**  
  设置手持物品的系统商店物品价格  
  示例: `/aprice 8.5`

- **/apull**  
  下架手持物品的系统商店物品

- **/mlang <语言>**  
  切换系统语言  
  示例: `/mlang en` 或 `/mlang zh`

## 交易系统特性
1. **双市场**
   - 玩家市场：玩家自主定价，库存有限
   - 系统市场：管理员设置，无限库存

2. **交易流程**
   - 自动匹配最低价商品
   - 支持跨玩家库存合并购买
