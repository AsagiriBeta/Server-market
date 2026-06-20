package asagiribeta.serverMarket.service

import asagiribeta.serverMarket.ServerMarket
import asagiribeta.serverMarket.api.ServerMarketEvents
import asagiribeta.serverMarket.api.economy.EconomyTransactionResult
import asagiribeta.serverMarket.api.economy.BalanceRankEntry
import asagiribeta.serverMarket.model.TransactionRecord
import asagiribeta.serverMarket.repository.Database
import asagiribeta.serverMarket.util.Config
import asagiribeta.serverMarket.util.MoneyFormat
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.sql.SQLException

/**
 * Unified economy service — all balance mutations go through here.
 *
 * Inspired by XConomy's centralized transaction pipeline and VaultUnlocked's provider model:
 * every write records optional history, fires [ServerMarketEvents.BALANCE_CHANGED], and respects config limits.
 */
class EconomyService(private val database: Database) {

    private val historyRepo = database.historyRepository

    val systemUuid: UUID = UUID(0, 0)

    // ── Queries ──────────────────────────────────────────────────────────────

    fun getBalance(uuid: UUID): CompletableFuture<Double> =
        database.supplyAsync0 { database.getBalance(uuid) }

    fun hasEnough(uuid: UUID, amount: Double): CompletableFuture<Boolean> =
        database.supplyAsync0 { database.getBalance(uuid) >= amount }

    fun getTopBalances(limit: Int): CompletableFuture<List<BalanceRankEntry>> =
        database.supplyAsync0 {
            database.getTopBalances(limit).map { BalanceRankEntry(it.uuid, it.name, it.balance) }
        }

    fun getHistory(uuid: UUID, page: Int, pageSize: Int): CompletableFuture<List<TransactionRecord>> =
        database.supplyAsync0 {
            historyRepo.queryHistory(uuid, page.coerceAtLeast(1), pageSize.coerceIn(1, 50))
        }

    fun format(amount: Double): String = MoneyFormat.format(amount, 2)

    // ── Mutations ────────────────────────────────────────────────────────────

    fun deposit(
        uuid: UUID,
        amount: Double,
        reason: String? = null,
        actor: UUID? = null,
        history: HistoryContext? = null
    ): CompletableFuture<EconomyTransactionResult> = mutate(uuid, amount, reason, actor, history) {
        database.addBalance(uuid, amount)
    }

    fun withdraw(
        uuid: UUID,
        amount: Double,
        reason: String? = null,
        actor: UUID? = null,
        history: HistoryContext? = null
    ): CompletableFuture<EconomyTransactionResult> {
        if (amount <= 0.0 || amount.isNaN() || amount.isInfinite()) {
            return CompletableFuture.completedFuture(EconomyTransactionResult(false, "Invalid amount"))
        }
        return database.supplyAsync { conn ->
            try {
                val ok = database.withdrawIfEnough(conn, uuid, amount)
                if (!ok) {
                    return@supplyAsync EconomyTransactionResult(
                        false, "Insufficient funds", newBalance = database.getBalance(uuid)
                    )
                }
                val newBal = database.getBalance(uuid)
                postMutation(uuid, -amount, reason, actor, history)
                EconomyTransactionResult(true, newBalance = newBal)
            } catch (t: Throwable) {
                EconomyTransactionResult(false, t.message)
            }
        }
    }

    fun setBalance(
        uuid: UUID,
        amount: Double,
        reason: String? = null,
        actor: UUID? = null
    ): CompletableFuture<EconomyTransactionResult> {
        if (amount.isNaN() || amount.isInfinite() || amount < 0.0) {
            return CompletableFuture.completedFuture(EconomyTransactionResult(false, "Invalid amount"))
        }
        return database.supplyAsync0 {
            try {
                val oldBal = database.getBalance(uuid)
                database.setBalance(uuid, amount)
                val delta = amount - oldBal
                if (delta != 0.0) {
                    postMutation(uuid, delta, reason, actor, null)
                }
                EconomyTransactionResult(true, newBalance = amount)
            } catch (t: Throwable) {
                EconomyTransactionResult(false, t.message)
            }
        }
    }

    /**
     * Player-to-player transfer with standard history entry.
     */
    fun transfer(
        fromUuid: UUID,
        fromName: String,
        toUuid: UUID,
        toName: String,
        amount: Double,
        reason: String? = null,
        actor: UUID? = null,
        item: String = "transfer"
    ): CompletableFuture<TransferOutcome> {
        if (amount <= 0.0 || amount.isNaN() || amount.isInfinite()) {
            return CompletableFuture.completedFuture(TransferOutcome.Error("Invalid amount"))
        }
        return database.supplyAsync0 {
            try {
                val resolvedFrom = fromName.ifBlank {
                    database.playerLookupService.getPlayerNameByUuid(fromUuid) ?: fromUuid.toString()
                }
                val resolvedTo = toName.ifBlank {
                    database.playerLookupService.getPlayerNameByUuid(toUuid) ?: toUuid.toString()
                }
                database.transfer(fromUuid, toUuid, amount)
                val ctx = HistoryContext(
                    fromId = fromUuid, fromType = "player", fromName = resolvedFrom,
                    toId = toUuid, toType = "player", toName = resolvedTo,
                    price = amount, item = item
                )
                fireBalanceChanged(fromUuid, -amount, reason, actor)
                fireBalanceChanged(toUuid, amount, reason, actor)
                maybeRecordHistory(ctx)
                TransferOutcome.Success
            } catch (e: SQLException) {
                if (e.message?.contains("余额不足") == true) {
                    TransferOutcome.InsufficientFunds
                } else {
                    TransferOutcome.Error(e.message ?: "Transfer failed")
                }
            } catch (e: Exception) {
                TransferOutcome.Error(e.message ?: "Transfer failed")
            }
        }
    }

    // ── DB-thread sync helpers (for in-transaction / blocking API bridges) ───

    /** Must run on the database executor thread. */
    internal fun getBalanceSync(uuid: UUID): Double = database.getBalance(uuid)

    /** Must run on the database executor thread. */
    internal fun depositSync(
        uuid: UUID,
        amount: Double,
        reason: String? = null,
        history: HistoryContext? = null
    ) {
        database.addBalance(uuid, amount)
        postMutation(uuid, amount, reason, null, history)
    }

    /** Must run on the database executor thread. Returns false when funds are insufficient. */
    internal fun withdrawSync(
        uuid: UUID,
        amount: Double,
        reason: String? = null,
        history: HistoryContext? = null
    ): Boolean {
        if (!database.withdrawIfEnough(database.connection, uuid, amount)) return false
        postMutation(uuid, -amount, reason, null, history)
        return true
    }

    /** Must run on the database executor thread. */
    internal fun setBalanceSync(uuid: UUID, amount: Double, reason: String? = null) {
        val oldBal = database.getBalance(uuid)
        database.setBalance(uuid, amount)
        val delta = amount - oldBal
        if (delta != 0.0) {
            postMutation(uuid, delta, reason, null, null)
        }
    }

    /**
     * Generic fund movement (system/player) used by market operations.
     * Does not fire duplicate events when both parties are non-player system accounts.
     */
    fun transferFunds(
        fromUuid: UUID,
        toUuid: UUID,
        amount: Double,
        reason: String? = null,
        actor: UUID? = null,
        history: HistoryContext? = null
    ): CompletableFuture<EconomyTransactionResult> {
        if (amount <= 0.0 || amount.isNaN() || amount.isInfinite()) {
            return CompletableFuture.completedFuture(EconomyTransactionResult(false, "Invalid amount"))
        }
        return database.supplyAsync0 {
            try {
                database.transfer(fromUuid, toUuid, amount)
                fireBalanceChanged(fromUuid, -amount, reason, actor)
                fireBalanceChanged(toUuid, amount, reason, actor)
                maybeRecordHistory(history)
                EconomyTransactionResult(true)
            } catch (t: Throwable) {
                EconomyTransactionResult(false, t.message)
            }
        }
    }

    /** Record a history entry without changing balances (e.g. market tax audit). */
    fun recordHistorySync(ctx: HistoryContext) {
        maybeRecordHistory(ctx)
    }

    /** Synchronous fund movement for use inside [Database.supplyAsync] blocks. */
    fun transferFundsSync(
        fromUuid: UUID,
        toUuid: UUID,
        amount: Double,
        reason: String? = null,
        actor: UUID? = null,
        history: HistoryContext? = null
    ) {
        database.transfer(fromUuid, toUuid, amount)
        fireBalanceChanged(fromUuid, -amount, reason, actor)
        fireBalanceChanged(toUuid, amount, reason, actor)
        maybeRecordHistory(history)
    }

    /** Compute seller payout after market tax (tax stays in system account). */
    fun computeSellerPayout(gross: Double): Pair<Double, Double> {
        if (!Config.enableTax || gross <= 0.0) return gross to 0.0
        val rate = Config.marketTaxRate.coerceIn(0.0, 1.0)
        val tax = gross * rate
        return (gross - tax) to tax
    }

    // ── Internal helpers ───────────────────────────────────────────────────────

    private fun mutate(
        uuid: UUID,
        amount: Double,
        reason: String?,
        actor: UUID?,
        history: HistoryContext?,
        block: () -> Unit
    ): CompletableFuture<EconomyTransactionResult> {
        if (amount.isNaN() || amount.isInfinite()) {
            return CompletableFuture.completedFuture(EconomyTransactionResult(false, "Invalid amount"))
        }
        return database.supplyAsync0 {
            try {
                block()
                val newBal = database.getBalance(uuid)
                postMutation(uuid, amount, reason, actor, history)
                EconomyTransactionResult(true, newBalance = newBal)
            } catch (t: Throwable) {
                EconomyTransactionResult(false, t.message)
            }
        }
    }

    private fun postMutation(uuid: UUID, delta: Double, reason: String?, actor: UUID?, history: HistoryContext?) {
        fireBalanceChanged(uuid, delta, reason, actor)
        maybeRecordHistory(history)
    }

    private fun fireBalanceChanged(uuid: UUID, delta: Double, reason: String?, actor: UUID?) {
        ServerMarket.instance.server?.execute {
            ServerMarketEvents.BALANCE_CHANGED.invoker().onBalanceChanged(uuid, delta, reason, actor)
        }
    }

    private fun maybeRecordHistory(ctx: HistoryContext?) {
        if (ctx == null || !Config.enableTransactionHistory) return
        historyRepo.postHistory(
            dtg = System.currentTimeMillis(),
            fromId = ctx.fromId,
            fromType = ctx.fromType,
            fromName = ctx.fromName,
            toId = ctx.toId,
            toType = ctx.toType,
            toName = ctx.toName,
            price = ctx.price,
            item = ctx.item
        )
        historyRepo.pruneOldRecords(Config.maxHistoryRecords)
    }

    data class HistoryContext(
        val fromId: UUID,
        val fromType: String,
        val fromName: String,
        val toId: UUID,
        val toType: String,
        val toName: String,
        val price: Double,
        val item: String
    )

    sealed class TransferOutcome {
        object Success : TransferOutcome()
        object InsufficientFunds : TransferOutcome()
        data class Error(val message: String) : TransferOutcome()
    }
}
