package asagiribeta.serverMarket.api

import net.fabricmc.fabric.api.event.Event
import net.fabricmc.fabric.api.event.EventFactory
import java.util.UUID

object ServerMarketEvents {
    /**
     * Fired after a successful balance-changing operation executed via ServerMarketApi.
     */
    @JvmField
    val BALANCE_CHANGED: Event<BalanceChanged> = EventFactory.createArrayBacked(
        BalanceChanged::class.java
    ) { listeners ->
        BalanceChanged { uuid, delta, reason, actor ->
            for (l in listeners) l.onBalanceChanged(uuid, delta, reason, actor)
        }
    }

    fun interface BalanceChanged {
        fun onBalanceChanged(uuid: UUID, delta: Double, reason: String?, actor: UUID?)
    }
}
