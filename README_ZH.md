# Server Market

[English](./README.md)

**Server Market** 是一款 Fabric 服务端模组，提供玩家经济与交易市场（GUI + 命令）。

## 功能概览

- 玩家余额、转账与交易历史
- 玩家商店与系统商店
- 收购订单、快递驿站投递、可选实物货币
- 默认 SQLite；可选 MySQL / XConomy 记录写入
- 模组联动：**Common Economy API**（余额）+ **ServerMarketApi**（包裹、历史、GUI）与市场事件

## 支持版本

Minecraft **1.20 – 1.21.11**（Fabric）。请从 [Releases](https://github.com/AsagiriBeta/Server-market/releases) 下载与服务器版本匹配的 JAR。

## 文档

详细说明见仓库内文档（可直接用作 GitHub Wiki 页面）：

| 页面 | 说明 |
|------|------|
| [Installation](./docs/Installation.md) | 依赖、安装与配置 |
| [Commands](./docs/Commands.md) | `/svm` 命令完整参考 |
| [Dev](./docs/Dev.md) | 源码构建、项目结构与 API |

## 快速上手

```bash
/svm              # 打开市场 GUI
/svm money        # 查询余额
/svm pay <玩家> <金额>
```

## 许可证

参见 [LICENSE.txt](./LICENSE.txt)。
