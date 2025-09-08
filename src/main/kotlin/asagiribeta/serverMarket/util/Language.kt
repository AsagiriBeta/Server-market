package asagiribeta.serverMarket.util

import asagiribeta.serverMarket.ServerMarket
import java.io.File
import java.util.Properties

object Language {
    private const val DEFAULT_LANGUAGE = "en"
    private var currentLanguage = DEFAULT_LANGUAGE
    private val translations = mutableMapOf<String, MutableMap<String, String>>()
    private val configFile = File("config/server-market/language.properties")

    init {
        loadSavedLanguage()
        loadTranslations()
    }
    
    fun getCurrentLanguage(): String = currentLanguage
    
    fun setLanguage(lang: String): Boolean {
        return if (translations.containsKey(lang)) {
            currentLanguage = lang
            saveLanguageSetting()
            true
        } else {
            false
        }
    }
    
    fun get(key: String, vararg args: Any): String {
        val template = translations[currentLanguage]?.get(key) 
            ?: translations[DEFAULT_LANGUAGE]?.get(key) 
            ?: key
            
        return if (args.isEmpty()) {
            template
        } else {
            String.format(template, *args)
        }
    }
    
    private fun loadSavedLanguage() {
        try {
            if (configFile.exists()) {
                val props = Properties()
                configFile.inputStream().use { props.load(it) }
                val savedLang = props.getProperty("language")
                if (!savedLang.isNullOrBlank()) {
                    currentLanguage = savedLang
                    ServerMarket.LOGGER.info("Loaded saved language setting: $savedLang")
                }
            }
        } catch (e: Exception) {
            ServerMarket.LOGGER.warn("Failed to load saved language setting, using default: $DEFAULT_LANGUAGE", e)
        }
    }

    private fun saveLanguageSetting() {
        try {
            val dataDir = configFile.parentFile
            if (!dataDir.exists()) {
                dataDir.mkdirs()
            }

            val props = Properties()
            props.setProperty("language", currentLanguage)
            props.setProperty("last_updated", System.currentTimeMillis().toString())

            configFile.outputStream().use {
                props.store(it, "ServerMarket Language Configuration")
            }
            ServerMarket.LOGGER.info("Saved language setting: $currentLanguage")
        } catch (e: Exception) {
            ServerMarket.LOGGER.error("Failed to save language setting", e)
        }
    }

    private fun loadTranslations() {
        try {
            val dataDir = File("config/server-market")
            if (!dataDir.exists()) {
                dataDir.mkdirs()
            }
            
            // 加载中文翻译
            val zhTranslations = mutableMapOf<String, String>()
            // 通用错误信息
            zhTranslations["error.player_only"] = "该命令只能由玩家执行"
            zhTranslations["error.operation_failed"] = "操作失败"
            
            // Money 命令
            zhTranslations["command.money.balance"] = "您的当前余额: %s"
            
            // MPay 命令
            zhTranslations["command.mpay.amount_must_be_positive"] = "金额必须大于0"
            zhTranslations["command.mpay.amount_too_large"] = "转账金额过大，最大允许 1,000,000"
            zhTranslations["command.mpay.cannot_pay_self"] = "不能向自己转账"
            zhTranslations["command.mpay.player_offline"] = "目标玩家不在线"
            zhTranslations["command.mpay.success"] = "成功向 %s 转账 %s"
            zhTranslations["command.mpay.received"] = "%s 向您转账 %s"
            zhTranslations["command.mpay.transfer_failed"] = "转账失败"
            
            // MPrice 命令
            zhTranslations["command.mprice.player_only"] = "只有玩家可以执行此命令"
            zhTranslations["command.mprice.hold_item"] = "请手持要上架的物品"
            zhTranslations["command.mprice.add_success"] = "成功上架 %s 单价为 %s"
            zhTranslations["command.mprice.update_success"] = "成功更新 %s 单价为 %s"
            zhTranslations["command.mprice.operation_failed"] = "上架操作失败"
            
            // MPull 命令
            zhTranslations["command.mpull.player_only"] = "只有玩家可以执行此命令"
            zhTranslations["command.mpull.hold_item"] = "请手持要下架的物品"
            zhTranslations["command.mpull.not_listed"] = "该物品未上架"
            zhTranslations["command.mpull.success"] = "成功下架 %s（返还 %d 个）"
            zhTranslations["command.mpull.operation_failed"] = "下架操作失败"
            
            // MSearch 命令
            zhTranslations["command.msearch.not_found"] = "没有找到物品 %s 的销售信息"
            zhTranslations["command.msearch.title"] = "=== 全服 %s 销售列表 ==="
            zhTranslations["command.msearch.system_market"] = "系统市场"
            zhTranslations["command.msearch.player_market"] = "玩家市场"
            zhTranslations["command.msearch.search_failed"] = "搜索失败"
            
            // MList 命令
            zhTranslations["command.mlist.no_items"] = "%s 没有上架任何物品"
            zhTranslations["command.mlist.title"] = "=== %s 上架物品 ==="
            zhTranslations["command.mlist.player_not_found"] = "找不到玩家 %s"
            zhTranslations["command.mlist.query_failed"] = "查询失败"
            
            // MSell 命令
            zhTranslations["command.msell.player_only"] = "该命令只能由玩家执行"
            zhTranslations["command.msell.not_listed"] = "该物品尚未在您的店铺上架"
            zhTranslations["command.msell.insufficient_items"] = "物品总数量不足（需要 %d 个）"
            zhTranslations["command.msell.success"] = "成功补货 %d 个 %s"
            zhTranslations["command.msell.operation_failed"] = "补货失败"
            
            // MBuy 命令
            zhTranslations["command.mbuy.not_found"] = "没有找到可购买的 %s"
            zhTranslations["command.mbuy.insufficient_stock"] = "全服库存不足，最多可购买 %d 个"
            zhTranslations["command.mbuy.insufficient_balance"] = "余额不足，需要 %s"
            zhTranslations["command.mbuy.success"] = "成功购买 %d 个 %s，花费 %s"
            zhTranslations["command.mbuy.error"] = "购买过程中发生错误"
            
            // MLang 命令
            zhTranslations["command.mlang.success"] = "语言已切换为%s"
            zhTranslations["command.mlang.invalid"] = "无效的语言代码，支持的语言: zh, en"
            
            // 管理员命令
            zhTranslations["command.mset.player_offline"] = "目标玩家不在线"
            zhTranslations["command.mset.negative_amount"] = "金额不能为负数"
            zhTranslations["command.mset.success"] = "成功设置玩家 %s 的余额为 %s"
            zhTranslations["command.mset.failed"] = "设置余额失败"
            
            zhTranslations["command.mreload.success"] = "配置重新加载成功"
            zhTranslations["command.mreload.failed"] = "配置重新加载失败"

            zhTranslations["command.aprice.player_only"] = "只有玩家可以执行此命令"
            zhTranslations["command.aprice.hold_item"] = "请手持要设置价格的物品"
            zhTranslations["command.aprice.add_success"] = "成功上架 %s 价格为 %s"
            zhTranslations["command.aprice.update_success"] = "成功更新 %s 价格为 %s"
            zhTranslations["command.aprice.operation_failed"] = "操作失败"
            
            zhTranslations["command.apull.player_only"] = "只有玩家可以执行此命令"
            zhTranslations["command.apull.hold_item"] = "请手持要下架的物品"
            zhTranslations["command.apull.not_listed"] = "该物品未上架"
            zhTranslations["command.apull.success"] = "成功下架 %s"
            zhTranslations["command.apull.operation_failed"] = "操作失败"

            // ACash 命令
            zhTranslations["command.acash.player_only"] = "只有玩家可以执行此命令"
            zhTranslations["command.acash.hold_item"] = "请手持要设置为货币的物品"
            zhTranslations["command.acash.non_positive_value"] = "货币面值必须大于 0"
            zhTranslations["command.acash.success"] = "已将 %s 设置为面值 %s 的实体货币"
            zhTranslations["command.acash.operation_failed"] = "设置实体货币失败"
            // 新增：ACash 子命令
            zhTranslations["command.acash.get.success"] = "当前面值：%s"
            zhTranslations["command.acash.get.not_set"] = "该物品未设置为实体货币"
            zhTranslations["command.acash.del.success"] = "已取消 %s 的实体货币设置"
            zhTranslations["command.acash.del.not_set"] = "该物品未设置为实体货币，无需删除"
            zhTranslations["command.acash.list.empty"] = "暂无实体货币配置"
            zhTranslations["command.acash.list.title"] = "共 %d 条实体货币配置："
            zhTranslations["command.acash.list.entry"] = "%s | NBT=%s | 值=%s"

            // 新增：MCash/MExchange 普通玩家命令
            zhTranslations["command.mcash.player_only"] = "只有玩家可以执行此命令"
            zhTranslations["command.mcash.invalid_value"] = "面值必须大于 0"
            zhTranslations["command.mcash.invalid_quantity"] = "数量必须大于 0"
            zhTranslations["command.mcash.value_not_found"] = "未配置该面值的实体货币：%s"

            zhTranslations["command.mcash.insufficient_balance"] = "余额不足，需要 %s"
            zhTranslations["command.mcash.success"] = "已兑换 %d 个 %s，共花费 %s"
            zhTranslations["command.mcash.failed"] = "兑换现金失败"

            zhTranslations["command.mexchange.player_only"] = "只有玩家可以执行此命令"
            zhTranslations["command.mexchange.invalid_amount"] = "数量必须大于 0"
            zhTranslations["command.mexchange.not_currency"] = "主手物品不是服务器认定的实体货币"
            zhTranslations["command.mexchange.insufficient_items"] = "物品数量不足（需要 %d 个）"
            zhTranslations["command.mexchange.success"] = "已将 %d 个 %s 兑换为余额 %s"
            zhTranslations["command.mexchange.failed"] = "兑换失败"
            // 新增：MExchange 专用提示
            zhTranslations["command.mexchange.hold_item"] = "请手持要兑换的现金物品"

            // UI 相关
            zhTranslations["ui.seller"] = "卖家: %s"
            zhTranslations["ui.price"] = "单价: %s"
            zhTranslations["ui.quantity"] = "数量: %s"
            // 菜单新增
            zhTranslations["menu.title"] = "服务器市场"
            zhTranslations["menu.prev"] = "上一页"
            zhTranslations["menu.next"] = "下一页 %s"
            zhTranslations["menu.close"] = "关闭"
            zhTranslations["menu.limit_reached"] = "已到达今日限购上限"
            zhTranslations["menu.out_of_stock"] = "该商品已售罄"
            zhTranslations["menu.no_money"] = "余额不足，需要 %s"
            zhTranslations["menu.buy_ok"] = "成功购买 %d 个 %s 花费 %s"
            zhTranslations["menu.buy_error"] = "购买失败"
            // 首页新增键
            zhTranslations["menu.balance"] = "当前余额: %s"
            zhTranslations["menu.enter_market"] = "进入市场"
            zhTranslations["menu.back_home"] = "返回首页"

            // 加载英文翻译
            val enTranslations = mutableMapOf<String, String>()
            // 通用错误信息
            enTranslations["error.player_only"] = "Only players can execute this command"
            enTranslations["error.operation_failed"] = "Operation failed"
            
            // Money 命令
            enTranslations["command.money.balance"] = "Your current balance: %s"
            
            // MPay 命令
            enTranslations["command.mpay.amount_must_be_positive"] = "Amount must be positive"
            enTranslations["command.mpay.amount_too_large"] = "Transfer amount too large, maximum allowed: 1,000,000"
            enTranslations["command.mpay.cannot_pay_self"] = "Cannot transfer money to yourself"
            enTranslations["command.mpay.player_offline"] = "Target player is offline"
            enTranslations["command.mpay.success"] = "Successfully transferred %s to %s"
            enTranslations["command.mpay.received"] = "Received %s from %s"
            enTranslations["command.mpay.transfer_failed"] = "Transfer failed"
            
            // MPrice 命令
            enTranslations["command.mprice.player_only"] = "Only players can execute this command"
            enTranslations["command.mprice.hold_item"] = "Please hold the item you want to sell"
            enTranslations["command.mprice.add_success"] = "Successfully listed %s at price %s"
            enTranslations["command.mprice.update_success"] = "Successfully updated %s price to %s"
            enTranslations["command.mprice.operation_failed"] = "Listing operation failed"
            
            // MPull 命令
            enTranslations["command.mpull.player_only"] = "Only players can execute this command"
            enTranslations["command.mpull.hold_item"] = "Please hold the item you want to unlist"
            enTranslations["command.mpull.not_listed"] = "This item is not listed"
            enTranslations["command.mpull.success"] = "Successfully unlisted %s (returned %d items)"
            enTranslations["command.mpull.operation_failed"] = "Unlisting operation failed"
            
            // MSearch 命令
            enTranslations["command.msearch.not_found"] = "No sales information found for item %s"
            enTranslations["command.msearch.title"] = "=== Server-wide %s Sales List ==="
            enTranslations["command.msearch.system_market"] = "System Market"
            enTranslations["command.msearch.player_market"] = "Player Market"
            enTranslations["command.msearch.search_failed"] = "Search failed"
            
            // MList 命令
            enTranslations["command.mlist.no_items"] = "%s has no items listed"
            enTranslations["command.mlist.title"] = "=== %s Listed Items ==="
            enTranslations["command.mlist.player_not_found"] = "Player %s not found"
            enTranslations["command.mlist.query_failed"] = "Query failed"
            
            // MSell 命令
            enTranslations["command.msell.player_only"] = "Only players can execute this command"
            enTranslations["command.msell.not_listed"] = "This item is not listed in your shop"
            enTranslations["command.msell.insufficient_items"] = "Insufficient items (need %d)"
            enTranslations["command.msell.success"] = "Successfully stocked %d %s"
            enTranslations["command.msell.operation_failed"] = "Stocking failed"
            
            // MBuy 命令
            enTranslations["command.mbuy.not_found"] = "No %s available for purchase"
            enTranslations["command.mbuy.insufficient_stock"] = "Insufficient server stock, maximum available: %d"
            enTranslations["command.mbuy.insufficient_balance"] = "Insufficient balance, need %s"
            enTranslations["command.mbuy.success"] = "Successfully purchased %d %s for %s"
            enTranslations["command.mbuy.error"] = "Error occurred during purchase"
            
            // MLang 命令
            enTranslations["command.mlang.success"] = "Language changed to %s"
            enTranslations["command.mlang.invalid"] = "Invalid language code, supported languages: zh, en"
            
            // 管理员命令
            enTranslations["command.mset.player_offline"] = "Target player is offline"
            enTranslations["command.mset.negative_amount"] = "Amount cannot be negative"
            enTranslations["command.mset.success"] = "Successfully set player %s's balance to %s"
            enTranslations["command.mset.failed"] = "Failed to set balance"
            
            enTranslations["command.mreload.success"] = "Configuration reloaded successfully"
            enTranslations["command.mreload.failed"] = "Failed to reload configuration"

            enTranslations["command.aprice.player_only"] = "Only players can execute this command"
            enTranslations["command.aprice.hold_item"] = "Please hold the item you want to price"
            enTranslations["command.aprice.add_success"] = "Successfully listed %s at price %s"
            enTranslations["command.aprice.update_success"] = "Successfully updated %s price to %s"
            enTranslations["command.aprice.operation_failed"] = "Operation failed"
            
            enTranslations["command.apull.player_only"] = "Only players can execute this command"
            enTranslations["command.apull.hold_item"] = "Please hold the item you want to unlist"
            enTranslations["command.apull.not_listed"] = "This item is not listed"
            enTranslations["command.apull.success"] = "Successfully unlisted %s"
            enTranslations["command.apull.operation_failed"] = "Operation failed"

            // ACash 命令
            enTranslations["command.acash.player_only"] = "Only players can execute this command"
            enTranslations["command.acash.hold_item"] = "Please hold the item you want to set as currency"
            enTranslations["command.acash.non_positive_value"] = "Currency value must be greater than 0"
            enTranslations["command.acash.success"] = "Set %s as currency with value %s"
            enTranslations["command.acash.operation_failed"] = "Failed to set currency"
            // 新增：ACash 子命令
            enTranslations["command.acash.get.success"] = "Current value: %s"
            enTranslations["command.acash.get.not_set"] = "This item is not configured as currency"
            enTranslations["command.acash.del.success"] = "Removed currency setting for %s"
            enTranslations["command.acash.del.not_set"] = "This item is not configured as currency"
            enTranslations["command.acash.list.empty"] = "No currency mappings yet"
            enTranslations["command.acash.list.title"] = "%d currency mappings:"
            enTranslations["command.acash.list.entry"] = "%s | NBT=%s | value=%s"

            // 新增：MCash/MExchange 普通玩家命令
            enTranslations["command.mcash.player_only"] = "Only players can execute this command"
            enTranslations["command.mcash.invalid_value"] = "Value must be greater than 0"
            enTranslations["command.mcash.invalid_quantity"] = "Quantity must be greater than 0"
            enTranslations["command.mcash.value_not_found"] = "No currency configured for value: %s"

            enTranslations["command.mcash.insufficient_balance"] = "Insufficient balance, need %s"
            enTranslations["command.mcash.success"] = "Redeemed %d %s for %s"
            enTranslations["command.mcash.failed"] = "Cash redemption failed"

            enTranslations["command.mexchange.player_only"] = "Only players can execute this command"
            enTranslations["command.mexchange.invalid_amount"] = "Quantity must be greater than 0"
            enTranslations["command.mexchange.not_currency"] = "The item in main hand is not a configured currency"
            enTranslations["command.mexchange.insufficient_items"] = "Insufficient items (need %d)"
            enTranslations["command.mexchange.success"] = "Exchanged %d %s for balance %s"
            enTranslations["command.mexchange.failed"] = "Exchange failed"
            // 新增：MExchange 专用提示
            enTranslations["command.mexchange.hold_item"] = "Please hold the currency item you want to exchange"

            // UI 相关
            enTranslations["ui.seller"] = "Seller: %s"
            enTranslations["ui.price"] = "Price: %s"
            enTranslations["ui.quantity"] = "Quantity: %s"
            // 菜单新增
            enTranslations["menu.title"] = "Server Market"
            enTranslations["menu.prev"] = "Prev"
            enTranslations["menu.next"] = "Next %s"
            enTranslations["menu.close"] = "Close"
            enTranslations["menu.limit_reached"] = "Daily purchase limit reached"
            enTranslations["menu.out_of_stock"] = "Out of stock"
            enTranslations["menu.no_money"] = "Insufficient balance, need %s"
            enTranslations["menu.buy_ok"] = "Purchased %d %s for %s"
            enTranslations["menu.buy_error"] = "Purchase failed"
            // 首页新增键
            enTranslations["menu.balance"] = "Balance: %s"
            enTranslations["menu.enter_market"] = "Enter Market"
            enTranslations["menu.back_home"] = "Home"

            translations["zh"] = zhTranslations
            translations["en"] = enTranslations
            
            // 持久化到文件
            saveTranslationsToFile("zh", zhTranslations)
            saveTranslationsToFile("en", enTranslations)
            
        } catch (e: Exception) {
            ServerMarket.LOGGER.error("Failed to load language files", e)
        }
    }
    
    private fun saveTranslationsToFile(lang: String, translations: Map<String, String>) {
        val dataDir = File("config/server-market")
        val langFile = File(dataDir, "lang_${'$'}lang.properties")

        if (!langFile.exists()) {
            val props = Properties()
            translations.forEach { (key, value) -> props.setProperty(key, value) }
            
            langFile.outputStream().use { 
                props.store(it, "ServerMarket Language File (${'$'}lang)")
            }
        }
    }
}
