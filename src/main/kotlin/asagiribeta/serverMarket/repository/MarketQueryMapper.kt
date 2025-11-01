package asagiribeta.serverMarket.repository
import java.sql.ResultSet
/**
 * 市场查询构建器和结果映射
 * 
 * 职责：
 * - SQL 查询构建
 * - 结果集到数据类的映射
 * - 数据类定义
 */
internal class MarketQueryMapper {
    /**
     * 通用结果集映射方法（交易用）
     * 
     * 要求查询结果包含列: item_id, nbt, seller_name, price, quantity
     */
    fun mapToMarketItems(rs: ResultSet): List<MarketItem> = buildList {
        while (rs.next()) {
            add(
                MarketItem(
                    itemId = rs.getString("item_id"),
                    nbt = rs.getString("nbt"),
                    sellerName = rs.getString("seller_name"),
                    price = rs.getDouble("price"),
                    quantity = rs.getInt("quantity")
                )
            )
        }
    }
    /**
     * 通用结果集映射方法（菜单用）
     * 
     * 要求查询结果包含列: item_id, nbt, price, quantity, seller_id, seller_name, is_system
     */
    fun mapToMarketMenuEntries(rs: ResultSet): List<MarketMenuEntry> = buildList {
        while (rs.next()) {
            add(
                MarketMenuEntry(
                    itemId = rs.getString("item_id"),
                    nbt = rs.getString("nbt"),
                    price = rs.getDouble("price"),
                    quantity = rs.getInt("quantity"),
                    sellerId = rs.getString("seller_id"),
                    sellerName = rs.getString("seller_name"),
                    isSystem = rs.getInt("is_system") == 1
                )
            )
        }
    }
    /**
     * 映射卖家列表
     */
    fun mapToSellerEntries(rs: ResultSet): List<SellerMenuEntry> = buildList {
        while (rs.next()) {
            add(
                SellerMenuEntry(
                    sellerId = rs.getString("seller_id"),
                    sellerName = rs.getString("seller_name"),
                    itemCount = rs.getInt("item_count")
                )
            )
        }
    }
    /**
     * 映射卖家名称列表
     */
    fun mapToSellerNames(rs: ResultSet): List<String> = buildList {
        while (rs.next()) {
            add(rs.getString(1))
        }
    }
}
/**
 * 市场商品数据类（交易用）
 * 
 * 用于商品购买、搜索等场景
 */
data class MarketItem(
    val itemId: String,
    val nbt: String,
    val sellerName: String,  // 可能是 'SERVER' 或玩家UUID
    val price: Double,
    val quantity: Int
)
/**
 * 市场商品数据类（菜单用）
 * 
 * 用于GUI菜单显示，包含更详细的卖家信息
 */
data class MarketMenuEntry(
    val itemId: String,
    val nbt: String,
    val price: Double,
    val quantity: Int,
    val sellerId: String,      // 'SERVER' 或玩家UUID
    val sellerName: String,    // 展示名称
    val isSystem: Boolean
)
/**
 * 卖家菜单条目
 * 
 * 用于卖家列表显示
 */
data class SellerMenuEntry(
    val sellerId: String,      // 'SERVER' 或玩家UUID
    val sellerName: String,
    val itemCount: Int
)
