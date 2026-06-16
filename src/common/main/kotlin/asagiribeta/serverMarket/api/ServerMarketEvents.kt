package asagiribeta.serverMarket.api

import net.fabricmc.fabric.api.event.Event
import net.fabricmc.fabric.api.event.EventFactory
import java.util.UUID

object ServerMarketEvents {
    /**
     * Fired after any successful balance-changing operation (commands, market, API).
     */
    @JvmField
    val BALANCE_CHANGED: Event<BalanceChanged> = EventFactory.createArrayBacked(
        BalanceChanged::class.java
    ) { listeners ->
        BalanceChanged { uuid, delta, reason, actor ->
            for (l in listeners) l.onBalanceChanged(uuid, delta, reason, actor)
        }
    }

    /**
     * Fired before a market purchase executes. Return false to cancel.
     * Inspired by QuickShop-Hikari's pre-transaction hooks.
     */
    @JvmField
    val PRE_PURCHASE: Event<PrePurchase> = EventFactory.createArrayBacked(
        PrePurchase::class.java
    ) { listeners ->
        PrePurchase { buyer, itemId, nbt, quantity, totalCost ->
            for (l in listeners) {
                if (!l.allowPurchase(buyer, itemId, nbt, quantity, totalCost)) return@PrePurchase false
            }
            true
        }
    }

    /**
     * Fired after a market purchase attempt (success or failure).
     */
    @JvmField
    val POST_PURCHASE: Event<PostPurchase> = EventFactory.createArrayBacked(
        PostPurchase::class.java
    ) { listeners ->
        PostPurchase { buyer, itemId, quantity, totalCost, success ->
            for (l in listeners) l.onPurchase(buyer, itemId, quantity, totalCost, success)
        }
    }

    fun interface BalanceChanged {
        fun onBalanceChanged(uuid: UUID, delta: Double, reason: String?, actor: UUID?)
    }

    fun interface PrePurchase {
        /** @return true to allow the purchase, false to cancel */
        fun allowPurchase(buyer: UUID, itemId: String, nbt: String, quantity: Int, totalCost: Double): Boolean
    }

    fun interface PostPurchase {
        fun onPurchase(buyer: UUID, itemId: String, quantity: Int, totalCost: Double, success: Boolean)
    }
}
