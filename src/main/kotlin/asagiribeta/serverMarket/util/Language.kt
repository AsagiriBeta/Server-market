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
            zhTranslations["command.mpay.insufficient_funds"] = "余额不足"
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
            zhTranslations["command.mbuy.not_found"] = "没有找到可购买的商品"
            zhTranslations["command.mbuy.insufficient_stock"] = "全服库存不足，最多可购买 %d 个"
            zhTranslations["command.mbuy.insufficient_funds"] = "余额不足，需要 %s"
            zhTranslations["command.mbuy.limit_exceeded"] = "超出每日限购，剩余可购买 %d 个"
            zhTranslations["command.mbuy.success"] = "成功购买 %d 个 %s，花费 %s"
            zhTranslations["command.mbuy.error"] = "购买过程中发生错误"
            zhTranslations["command.mbuy.cannot_buy_own_item"] = "不能购买自己上架的商品"

            // MLang 命令
            zhTranslations["command.mlang.success"] = "语言已切换为%s"
            zhTranslations["command.mlang.invalid"] = "无效的语言代码，支持的语言: zh, en"
            
            // 管理员命令
            zhTranslations["command.mset.player_offline"] = "目标玩家不在线"
            zhTranslations["command.mset.negative_amount"] = "金额不能为负数"
            zhTranslations["command.mset.success"] = "成功设置玩家 %s 的余额为 %s"
            zhTranslations["command.mset.failed"] = "设置余额失败"
            
            zhTranslations["command.madd.player_offline"] = "目标玩家不在线"
            zhTranslations["command.madd.success"] = "成功为玩家 %s 增加余额 %s"
            zhTranslations["command.madd.received"] = "管理员已为您增加余额 %s"
            zhTranslations["command.madd.failed"] = "增加余额失败"

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

            // MPurchase 命令
            zhTranslations["command.mpurchase.player_only"] = "该命令只能由玩家执行"
            zhTranslations["command.mpurchase.hold_item"] = "请手持要收购的物品"
            zhTranslations["command.mpurchase.success"] = "成功设置收购 %s，单价 %s，目标数量 %d"
            zhTranslations["command.mpurchase.failed"] = "设置收购订单失败"

            // APurchase 命令
            zhTranslations["command.apurchase.player_only"] = "只有玩家可以执行此命令"
            zhTranslations["command.apurchase.hold_item"] = "请手持要设置系统收购的物品"
            zhTranslations["command.apurchase.add_success"] = "成功设置系统收购 %s，单价 %s，每日限额 %s"
            zhTranslations["command.apurchase.update_success"] = "成功更新系统收购 %s，单价 %s，每日限额 %s"
            zhTranslations["command.apurchase.failed"] = "设置系统收购失败"
            zhTranslations["command.apurchase.unlimited"] = "无限"

            // MSellToPurchase 命令
            zhTranslations["command.mselltopurchase.player_only"] = "该命令只能由玩家执行"
            zhTranslations["command.mselltopurchase.hold_item"] = "请手持要出售的物品"
            zhTranslations["command.mselltopurchase.insufficient_items"] = "物品数量不足（需要 %d 个）"
            zhTranslations["command.mselltopurchase.success"] = "成功出售 %d 个 %s，获得 %s"
            zhTranslations["command.mselltopurchase.not_found"] = "没有找到该物品的收购订单"
            zhTranslations["command.mselltopurchase.limit_exceeded"] = "超出每日出售限额，剩余可出售 %d 个"
            zhTranslations["command.mselltopurchase.buyer_no_money"] = "收购者余额不足，需要 %s"
            zhTranslations["command.mselltopurchase.error"] = "出售过程中发生错误"
            zhTranslations["command.mselltopurchase.failed"] = "出售失败"

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
            zhTranslations["menu.cannot_buy_own_item"] = "不能购买自己上架的商品"
            // 首页新增键
            zhTranslations["menu.balance"] = "当前余额: %s"
            zhTranslations["menu.enter_market"] = "进入市场"
            zhTranslations["menu.back_home"] = "返回首页"
            // 首页/导航新增键
            zhTranslations["menu.home.help_title"] = "市场导航"
            zhTranslations["menu.home.help1"] = "点击右下角进入按卖家分类"
            zhTranslations["menu.home.help2"] = "在卖家列表中选择卖家进入其店铺"
            zhTranslations["menu.home.help3"] = "店铺内左/右键购买 1/64"
            zhTranslations["menu.enter_market_sellers"] = "进入市场(按卖家)"
            // Purchase menu
            zhTranslations["menu.enter_purchase"] = "进入收购页面"
            zhTranslations["menu.purchase_list.title"] = "收购列表"
            zhTranslations["menu.purchase_list.tip1"] = "点击物品出售给收购者"
            zhTranslations["menu.purchase_list.tip2"] = "箭头翻页"
            zhTranslations["menu.purchase_list.tip3"] = "下界之星返回首页"
            zhTranslations["menu.purchase_list.tip4"] = "屏障关闭"
            zhTranslations["menu.purchase.buyer"] = "收购者: %s"
            zhTranslations["menu.purchase.price"] = "收购价: %s"
            zhTranslations["menu.purchase.limit"] = "限额: %s"
            zhTranslations["menu.purchase.click_tip"] = "左键: 卖1  |  右键: 卖64"
            zhTranslations["menu.purchase.sell_ok"] = "成功出售 %d 个 %s，获得 %s"
            zhTranslations["menu.purchase.not_enough_items"] = "物品不足"
            zhTranslations["menu.purchase.limit_exceeded"] = "已达每日出售限额"
            zhTranslations["menu.purchase.buyer_no_money"] = "收购者余额不足"
            zhTranslations["menu.purchase.error"] = "出售失败"
            zhTranslations["menu.loading"] = "加载中..."
            // 卖家列表/卖家条目
            zhTranslations["menu.seller.items"] = "商品数: %d"
            zhTranslations["menu.seller.open_shop"] = "左键打开店铺"
            zhTranslations["menu.seller_list.title"] = "卖家列表"
            zhTranslations["menu.seller_list.tip1"] = "点击卖家进入店铺"
            zhTranslations["menu.seller_list.tip2"] = "箭头翻页"
            zhTranslations["menu.seller_list.tip3"] = "下界之星返回首页"
            zhTranslations["menu.seller_list.tip4"] = "屏障关闭"
            // 店铺导航/提示
            zhTranslations["menu.shop.title"] = "店铺操作"
            zhTranslations["menu.shop.tip_left"] = "左键: 购买1"
            zhTranslations["menu.shop.tip_right"] = "右键: 购买64"
            zhTranslations["menu.shop.tip_back"] = "下界之星返回卖家列表"
            zhTranslations["menu.shop.tip_close"] = "屏障关闭"
            zhTranslations["menu.back_sellers"] = "返回卖家"
            zhTranslations["menu.shop.click_tip"] = "左键: +1  |  右键: +64"

            // 快递驿站
            zhTranslations["menu.enter_parcel"] = "快递驿站"
            zhTranslations["menu.enter_my_shop"] = "我的店铺"
            zhTranslations["menu.enter_my_purchase"] = "我的收购"
            zhTranslations["menu.parcel.title"] = "快递驿站"
            zhTranslations["menu.parcel.tip1"] = "点击物品领取包裹"
            zhTranslations["menu.parcel.tip2"] = "箭头翻页"
            zhTranslations["menu.parcel.tip3"] = "下界之星返回首页"
            zhTranslations["menu.parcel.tip4"] = "屏障关闭"
            zhTranslations["menu.parcel.reason"] = "来源: %s"
            zhTranslations["menu.parcel.time"] = "时间: %s"
            zhTranslations["menu.parcel.click_tip"] = "点击领取"
            zhTranslations["menu.parcel.received"] = "成功领取 %d 个 %s"
            zhTranslations["menu.parcel.empty"] = "暂无包裹"
            zhTranslations["menu.parcel.error"] = "领取失败"
            zhTranslations["menu.parcel.count"] = "包裹数: %d"

            // 我的店铺
            zhTranslations["menu.myshop.title"] = "我的店铺"
            zhTranslations["menu.myshop.tip1"] = "点击商品进入管理界面"
            zhTranslations["menu.myshop.count"] = "商品数: %d"
            zhTranslations["menu.myshop.click_to_manage"] = "点击管理"

            // 我的店铺详情
            zhTranslations["menu.myshop.detail.total_value"] = "总价值: %s"
            zhTranslations["menu.myshop.detail.current_stock"] = "当前库存"
            zhTranslations["menu.myshop.detail.left_unlist"] = "左侧: 部分下架"
            zhTranslations["menu.myshop.detail.right_restock"] = "右侧: 从背包补货"
            zhTranslations["menu.myshop.detail.unlist_64"] = "下架 64"
            zhTranslations["menu.myshop.detail.unlist_16"] = "下架 16"
            zhTranslations["menu.myshop.detail.unlist_1"] = "下架 1"
            zhTranslations["menu.myshop.detail.unlist_tip"] = "下架并发送到快递驿站"
            zhTranslations["menu.myshop.detail.restock_1"] = "补货 1"
            zhTranslations["menu.myshop.detail.restock_16"] = "补货 16"
            zhTranslations["menu.myshop.detail.restock_64"] = "补货 64"
            zhTranslations["menu.myshop.detail.restock_tip"] = "从背包扣除并增加库存"
            zhTranslations["menu.myshop.detail.unlist_all"] = "完全下架"
            zhTranslations["menu.myshop.detail.unlist_all_confirm"] = "将下架全部 %d 个"
            zhTranslations["menu.myshop.detail.no_stock"] = "库存不足"
            zhTranslations["menu.myshop.detail.invalid_item"] = "无效物品"
            zhTranslations["menu.myshop.detail.insufficient_items"] = "背包中物品不足（需要 %d 个）"
            zhTranslations["menu.myshop.detail.unlist_success"] = "已下架 %d 个 %s 到快递驿站"
            zhTranslations["menu.myshop.detail.restock_success"] = "成功补货 %d 个 %s"
            zhTranslations["menu.myshop.detail.unlist_reason"] = "部分下架"
            zhTranslations["menu.myshop.detail.unlist_all_success"] = "已完全下架 %s，返还 %d 个到快递驿站"
            zhTranslations["menu.myshop.detail.unlist_all_reason"] = "完全下架"
            zhTranslations["menu.back"] = "返回"

            // 我的收购
            zhTranslations["menu.mypurchase.title"] = "我的收购"
            zhTranslations["menu.mypurchase.tip1"] = "已收购的物品会自动发送到快递驿站"
            zhTranslations["menu.mypurchase.tip2"] = "点击订单可取消收购"
            zhTranslations["menu.mypurchase.count"] = "订单数: %d"
            zhTranslations["menu.mypurchase.progress"] = "进度: %d/%d (%d%%)"
            zhTranslations["menu.mypurchase.remaining"] = "剩余: %d"
            zhTranslations["menu.mypurchase.auto_sent_tip"] = "已收购物品已自动发送至快递驿站"
            zhTranslations["menu.mypurchase.completed"] = "收购完成！"
            zhTranslations["menu.mypurchase.click_cancel"] = "点击取消此收购订单"
            zhTranslations["menu.mypurchase.cancel_success"] = "已取消收购 %s"

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
            enTranslations["command.mpay.insufficient_funds"] = "Insufficient balance"
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
            enTranslations["command.mbuy.not_found"] = "No items available for purchase"
            enTranslations["command.mbuy.insufficient_stock"] = "Insufficient server stock, maximum available: %d"
            enTranslations["command.mbuy.insufficient_funds"] = "Insufficient balance, need %s"
            enTranslations["command.mbuy.limit_exceeded"] = "Daily purchase limit exceeded, remaining: %d"
            enTranslations["command.mbuy.success"] = "Successfully purchased %d %s for %s"
            enTranslations["command.mbuy.error"] = "Error occurred during purchase"
            enTranslations["command.mbuy.cannot_buy_own_item"] = "Cannot buy your own items"

            // MLang 命令
            enTranslations["command.mlang.success"] = "Language changed to %s"
            enTranslations["command.mlang.invalid"] = "Invalid language code, supported languages: zh, en"
            
            // 管理员命令
            enTranslations["command.madd.player_offline"] = "Target player is offline"
            enTranslations["command.madd.success"] = "Successfully added %s to player %s's balance"
            enTranslations["command.madd.received"] = "Administrator added %s to your balance"
            enTranslations["command.madd.failed"] = "Failed to add balance"

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

            // MPurchase 命令
            enTranslations["command.mpurchase.player_only"] = "Only players can execute this command"
            enTranslations["command.mpurchase.hold_item"] = "Please hold the item you want to purchase"
            enTranslations["command.mpurchase.success"] = "Successfully set purchase order for %s, price %s, target amount %d"
            enTranslations["command.mpurchase.failed"] = "Failed to set purchase order"

            // APurchase 命令
            enTranslations["command.apurchase.player_only"] = "Only players can execute this command"
            enTranslations["command.apurchase.hold_item"] = "Please hold the item for system purchase"
            enTranslations["command.apurchase.add_success"] = "Successfully set system purchase for %s, price %s, daily limit %s"
            enTranslations["command.apurchase.update_success"] = "Successfully updated system purchase for %s, price %s, daily limit %s"
            enTranslations["command.apurchase.failed"] = "Failed to set system purchase"
            enTranslations["command.apurchase.unlimited"] = "Unlimited"

            // MSellToPurchase 命令
            enTranslations["command.mselltopurchase.player_only"] = "Only players can execute this command"
            enTranslations["command.mselltopurchase.hold_item"] = "Please hold the item you want to sell"
            enTranslations["command.mselltopurchase.insufficient_items"] = "Insufficient items (need %d)"
            enTranslations["command.mselltopurchase.success"] = "Successfully sold %d %s for %s"
            enTranslations["command.mselltopurchase.not_found"] = "No purchase order found for this item"
            enTranslations["command.mselltopurchase.limit_exceeded"] = "Daily sell limit exceeded, remaining: %d"
            enTranslations["command.mselltopurchase.buyer_no_money"] = "Buyer has insufficient balance, need %s"
            enTranslations["command.mselltopurchase.error"] = "Error occurred during sale"
            enTranslations["command.mselltopurchase.failed"] = "Sale failed"

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
            enTranslations["menu.cannot_buy_own_item"] = "Cannot buy your own items"
            // 首页新增键
            enTranslations["menu.balance"] = "Balance: %s"
            enTranslations["menu.enter_market"] = "Enter Market"
            enTranslations["menu.back_home"] = "Home"
            // 首页/导航新增键
            enTranslations["menu.home.help_title"] = "Market Help"
            enTranslations["menu.home.help1"] = "Click bottom-right to view sellers"
            enTranslations["menu.home.help2"] = "Select a seller to enter the shop"
            enTranslations["menu.home.help3"] = "In shop: Left/Right = 1/64"
            enTranslations["menu.enter_market_sellers"] = "Enter Market (Sellers)"
            // Purchase menu
            enTranslations["menu.enter_purchase"] = "Enter Purchase Page"
            enTranslations["menu.purchase_list.title"] = "Purchase List"
            enTranslations["menu.purchase_list.tip1"] = "Click item to sell to buyer"
            enTranslations["menu.purchase_list.tip2"] = "Use arrows to turn page"
            enTranslations["menu.purchase_list.tip3"] = "Nether Star: Home"
            enTranslations["menu.purchase_list.tip4"] = "Barrier: Close"
            enTranslations["menu.purchase.buyer"] = "Buyer: %s"
            enTranslations["menu.purchase.price"] = "Purchase Price: %s"
            enTranslations["menu.purchase.limit"] = "Limit: %s"
            enTranslations["menu.purchase.click_tip"] = "Left: Sell 1  |  Right: Sell 64"
            enTranslations["menu.purchase.sell_ok"] = "Successfully sold %d %s for %s"
            enTranslations["menu.purchase.not_enough_items"] = "Insufficient items"
            enTranslations["menu.purchase.limit_exceeded"] = "Daily sell limit reached"
            enTranslations["menu.purchase.buyer_no_money"] = "Buyer has insufficient balance"
            enTranslations["menu.purchase.error"] = "Sale failed"
            enTranslations["menu.loading"] = "Loading..."
            // Seller list / entry
            enTranslations["menu.seller.items"] = "Items: %d"
            enTranslations["menu.seller.open_shop"] = "Left click to open shop"
            enTranslations["menu.seller_list.title"] = "Seller List"
            enTranslations["menu.seller_list.tip1"] = "Click seller to open shop"
            enTranslations["menu.seller_list.tip2"] = "Use arrows to turn page"
            enTranslations["menu.seller_list.tip3"] = "Nether Star: Home"
            enTranslations["menu.seller_list.tip4"] = "Barrier: Close"
            // Shop navigation / tips
            enTranslations["menu.shop.title"] = "Shop"
            enTranslations["menu.shop.tip_left"] = "Left: Buy 1"
            enTranslations["menu.shop.tip_right"] = "Right: Buy 64"
            enTranslations["menu.shop.tip_back"] = "Nether Star: Sellers"
            enTranslations["menu.shop.tip_close"] = "Barrier: Close"
            enTranslations["menu.back_sellers"] = "Back to Sellers"
            enTranslations["menu.shop.click_tip"] = "Left: +1  |  Right: +64"

            // Parcel Station
            enTranslations["menu.enter_parcel"] = "Parcel Station"
            enTranslations["menu.parcel.title"] = "Parcel Station"
            enTranslations["menu.parcel.tip1"] = "Click item to receive parcel"
            enTranslations["menu.parcel.tip2"] = "Use arrows to turn page"
            enTranslations["menu.parcel.tip3"] = "Nether Star: Home"
            enTranslations["menu.parcel.tip4"] = "Barrier: Close"
            enTranslations["menu.parcel.reason"] = "From: %s"
            enTranslations["menu.parcel.time"] = "Time: %s"
            enTranslations["menu.parcel.click_tip"] = "Click to receive"
            enTranslations["menu.parcel.received"] = "Received %d %s"
            enTranslations["menu.parcel.empty"] = "No parcels"
            enTranslations["menu.parcel.error"] = "Failed to receive"
            enTranslations["menu.parcel.count"] = "Parcels: %d"

            // My Shop
            enTranslations["menu.myshop.title"] = "My Shop"
            enTranslations["menu.myshop.tip1"] = "Click item to manage"
            enTranslations["menu.myshop.count"] = "Items: %d"
            enTranslations["menu.myshop.click_to_manage"] = "Click to manage"

            // My Shop Detail
            enTranslations["menu.myshop.detail.total_value"] = "Total value: %s"
            enTranslations["menu.myshop.detail.current_stock"] = "Current Stock"
            enTranslations["menu.myshop.detail.left_unlist"] = "Left: Partial unlist"
            enTranslations["menu.myshop.detail.right_restock"] = "Right: Restock from inventory"
            enTranslations["menu.myshop.detail.unlist_64"] = "Unlist 64"
            enTranslations["menu.myshop.detail.unlist_16"] = "Unlist 16"
            enTranslations["menu.myshop.detail.unlist_1"] = "Unlist 1"
            enTranslations["menu.myshop.detail.unlist_tip"] = "Unlist and send to parcel station"
            enTranslations["menu.myshop.detail.restock_1"] = "Restock 1"
            enTranslations["menu.myshop.detail.restock_16"] = "Restock 16"
            enTranslations["menu.myshop.detail.restock_64"] = "Restock 64"
            enTranslations["menu.myshop.detail.restock_tip"] = "Deduct from inventory and add stock"
            enTranslations["menu.myshop.detail.unlist_all"] = "Unlist All"
            enTranslations["menu.myshop.detail.unlist_all_confirm"] = "Unlist all %d items"
            enTranslations["menu.myshop.detail.no_stock"] = "Insufficient stock"
            enTranslations["menu.myshop.detail.invalid_item"] = "Invalid item"
            enTranslations["menu.myshop.detail.insufficient_items"] = "Insufficient items in inventory (need %d)"
            enTranslations["menu.myshop.detail.unlist_success"] = "Unlisted %d %s to parcel station"
            enTranslations["menu.myshop.detail.restock_success"] = "Successfully restocked %d %s"
            enTranslations["menu.myshop.detail.unlist_reason"] = "Partial unlist"
            enTranslations["menu.myshop.detail.unlist_all_success"] = "Fully unlisted %s, returned %d to parcel station"
            enTranslations["menu.myshop.detail.unlist_all_reason"] = "Full unlist"
            enTranslations["menu.back"] = "Back"

            // My Purchase
            enTranslations["menu.mypurchase.title"] = "My Purchase Orders"
            enTranslations["menu.mypurchase.tip1"] = "Shift+Right click to claim purchased items"
            enTranslations["menu.mypurchase.tip2"] = "Click to view details"
            enTranslations["menu.mypurchase.count"] = "Orders: %d"
            enTranslations["menu.mypurchase.progress"] = "Progress: %d/%d (%d%%)"
            enTranslations["menu.mypurchase.remaining"] = "Remaining: %d"
            enTranslations["menu.mypurchase.left_tip"] = "Left: View details"
            enTranslations["menu.mypurchase.shift_right_claim_tip"] = "Shift+Right: Claim purchased"
            enTranslations["menu.mypurchase.order_info"] = "%s | Price: %s | Purchased: %d/%d"
            enTranslations["menu.mypurchase.price_prompt"] = "Please use command to change price"
            enTranslations["menu.mypurchase.use_mpurchase"] = "Use command: /mpurchase <price> <target> (holding %s)"
            enTranslations["menu.mypurchase.cancel_success"] = "Cancelled purchase order for %s"
            enTranslations["menu.mypurchase.claim_success"] = "Claimed %d %s to parcel station"
            enTranslations["menu.mypurchase.nothing_to_claim"] = "Nothing to claim"
            enTranslations["menu.mypurchase.claim_reason"] = "Purchase order"

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
