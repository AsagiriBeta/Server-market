package asagiribeta.serverMarket.service

import asagiribeta.serverMarket.model.ParcelEntry
import asagiribeta.serverMarket.repository.Database
import java.util.UUID
import java.util.concurrent.CompletableFuture

/**
 * 快递包裹服务
 *
 * 职责：处理玩家包裹的添加、查询、领取等业务逻辑
 */
class ParcelService(private val database: Database) {

    private val parcelRepo = database.parcelRepository

    /**
     * 添加包裹（异步）
     *
     * 注意：此方法供外部调用。内部事务中应直接使用 repository
     */
    @Suppress("unused")
    fun addParcelAsync(
        recipientUuid: UUID,
        recipientName: String,
        itemId: String,
        nbt: String,
        quantity: Int,
        reason: String
    ): CompletableFuture<Long> {
        return database.supplyAsync {
            parcelRepo.addParcel(recipientUuid, recipientName, itemId, nbt, quantity, reason)
        }
    }

    /**
     * 获取玩家的所有包裹（异步）
     */
    @Suppress("unused")
    fun getParcelsForPlayerAsync(uuid: UUID): CompletableFuture<List<ParcelEntry>> {
        return database.supplyAsync {
            parcelRepo.getParcelsForPlayer(uuid)
        }
    }

    /**
     * 获取玩家的所有包裹（合并相同物品，异步）
     */
    fun getParcelsForPlayerMergedAsync(uuid: UUID): CompletableFuture<List<ParcelEntry>> {
        return database.supplyAsync {
            parcelRepo.getParcelsForPlayerMerged(uuid)
        }
    }

    /**
     * 获取玩家包裹数量（异步）
     */
    fun getParcelCountForPlayerAsync(uuid: UUID): CompletableFuture<Int> {
        return database.supplyAsync {
            parcelRepo.getParcelCountForPlayer(uuid)
        }
    }

    /**
     * 删除包裹（异步）
     */
    @Suppress("unused")
    fun removeParcelAsync(id: Long): CompletableFuture<Boolean> {
        return database.supplyAsync {
            parcelRepo.removeParcel(id)
        }
    }

    /**
     * 删除指定物品的所有包裹（异步）
     */
    fun removeParcelsByItemAsync(uuid: UUID, itemId: String, nbt: String): CompletableFuture<Int> {
        return database.supplyAsync {
            parcelRepo.removeParcelsByItem(uuid, itemId, nbt)
        }
    }

    /**
     * 批量删除包裹（异步）
     */
    @Suppress("unused")
    fun removeParcelsAsync(ids: List<Long>): CompletableFuture<Int> {
        return database.supplyAsync {
            parcelRepo.removeParcels(ids)
        }
    }
}

