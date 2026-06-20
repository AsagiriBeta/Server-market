package asagiribeta.serverMarket.integration

import asagiribeta.serverMarket.ServerMarket
import asagiribeta.serverMarket.service.EconomyService
import asagiribeta.serverMarket.util.MoneyFormat
import asagiribeta.serverMarket.util.MoneyUnits
import com.mojang.authlib.GameProfile
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.math.BigInteger
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Registers ServerMarket with Patbox's Common Economy API without compile-time dependency
 * on API types (API JARs ship Mojang mappings; this project uses Yarn).
 *
 * Supports API v1.x (long minor units) and v2.0+ (BigInteger minor units).
 */
object CommonEconomyBridge {
    private const val PROVIDER_ID = "server-market"
    private val lookup = MethodHandles.lookup()

    private enum class MoneyMode { LONG, BIG_INTEGER }

    private lateinit var economyProviderClass: Class<*>
    private lateinit var economyCurrencyClass: Class<*>
    private lateinit var economyAccountClass: Class<*>
    private lateinit var transactionSimpleClass: Class<*>
    private lateinit var moneyMode: MoneyMode

    private lateinit var economy: EconomyService
    private lateinit var database: asagiribeta.serverMarket.repository.Database

    private val providerProxy: Any by lazy { buildProviderProxy() }
    private val currencyProxy: Any by lazy { buildCurrencyProxy() }
    private val accountCache = ConcurrentHashMap<UUID, Any>()

    fun register(economyService: EconomyService) {
        economy = economyService
        database = ServerMarket.instance.database
        try {
            loadApiClasses()
            val commonEconomy = Class.forName("eu.pb4.common.economy.api.CommonEconomy")
            val register = commonEconomy.getMethod(
                "register",
                String::class.java,
                economyProviderClass
            )
            register.invoke(null, PROVIDER_ID, providerProxy)
            ServerMarket.LOGGER.info(
                "Registered Common Economy API provider '{}' (money mode: {})",
                PROVIDER_ID,
                moneyMode
            )
        } catch (t: Throwable) {
            ServerMarket.LOGGER.warn("Common Economy API unavailable; skipping provider registration", t)
        }
    }

    private fun loadApiClasses() {
        economyProviderClass = Class.forName("eu.pb4.common.economy.api.EconomyProvider")
        economyCurrencyClass = Class.forName("eu.pb4.common.economy.api.EconomyCurrency")
        economyAccountClass = Class.forName("eu.pb4.common.economy.api.EconomyAccount")
        transactionSimpleClass = Class.forName("eu.pb4.common.economy.api.EconomyTransaction\$Simple")

        val balanceReturnType = economyAccountClass.getMethod("balance").returnType
        moneyMode = when (balanceReturnType) {
            Long::class.javaPrimitiveType, java.lang.Long::class.java -> MoneyMode.LONG
            BigInteger::class.java -> MoneyMode.BIG_INTEGER
            else -> throw IllegalStateException("Unsupported Common Economy balance type: $balanceReturnType")
        }
    }

    private fun <T> onDbThread(block: () -> T): T = database.supplyAsync0(block).join()

    private fun balanceAsApiUnits(profile: GameProfile): Any = onDbThread {
        toApiUnits(MoneyUnits.toMinorUnits(economy.getBalanceSync(profile.id)))
    }

    private fun toApiUnits(minorUnits: BigInteger): Any = when (moneyMode) {
        MoneyMode.LONG -> minorUnits.longValueExact()
        MoneyMode.BIG_INTEGER -> minorUnits
    }

    private fun fromApiUnits(units: Any): Double = when (moneyMode) {
        MoneyMode.LONG -> MoneyUnits.fromMinorUnits(units as Long)
        MoneyMode.BIG_INTEGER -> MoneyUnits.fromMinorUnits(units as BigInteger)
    }

    private fun toApiUnits(amount: Double): Any = toApiUnits(MoneyUnits.toMinorUnits(amount))

    private fun addUnits(a: Any, b: Any): Any = when (moneyMode) {
        MoneyMode.LONG -> (a as Long) + (b as Long)
        MoneyMode.BIG_INTEGER -> (a as BigInteger).add(b as BigInteger)
    }

    private fun subtractUnits(a: Any, b: Any): Any = when (moneyMode) {
        MoneyMode.LONG -> (a as Long) - (b as Long)
        MoneyMode.BIG_INTEGER -> (a as BigInteger).subtract(b as BigInteger)
    }

    private fun negateUnits(value: Any): Any = when (moneyMode) {
        MoneyMode.LONG -> -(value as Long)
        MoneyMode.BIG_INTEGER -> (value as BigInteger).negate()
    }

    private fun compareUnits(a: Any, b: Any): Int = when (moneyMode) {
        MoneyMode.LONG -> (a as Long).compareTo(b as Long)
        MoneyMode.BIG_INTEGER -> (a as BigInteger).compareTo(b as BigInteger)
    }

    private fun buildProviderProxy(): Any = proxy(economyProviderClass) { _, method, args ->
        when (method.name) {
            "name" -> Text.literal("Server Market")
            "getCurrencies" -> listOf(currencyProxy)
            "getCurrency" -> {
                val currencyId = args[1] as String
                if (currencyId == "coin" || currencyId == "$PROVIDER_ID:coin") currencyProxy else null
            }
            "getAccounts" -> listOf(accountFor(args[1] as GameProfile))
            "getAccount" -> {
                val profile = args[1] as GameProfile
                val accountId = args[2] as String
                if (accountId == profile.id.toString()) accountFor(profile) else null
            }
            "defaultAccount" -> {
                val currency = args[2]
                if (currency === currencyProxy) (args[1] as GameProfile).id.toString() else null
            }
            else -> unsupported(method)
        }
    }

    private fun buildCurrencyProxy(): Any = proxy(economyCurrencyClass) { _, method, args ->
        when (method.name) {
            "name" -> Text.literal("Coin")
            "id" -> Identifier.of(PROVIDER_ID, "coin")
            "provider" -> providerProxy
            "formatValue" -> {
                val amount = fromApiUnits(requireNotNull(args[0]))
                val precise = args[1] as Boolean
                if (precise) MoneyFormat.format(amount, 2) else MoneyFormat.formatShort(amount)
            }
            "parseValue" -> toApiUnits((args[0] as String).trim().removePrefix("$").toDouble())
            else -> unsupported(method)
        }
    }

    private fun accountFor(profile: GameProfile): Any =
        accountCache.computeIfAbsent(profile.id) { buildAccountProxy(profile) }

    private fun buildAccountProxy(profile: GameProfile): Any = proxy(economyAccountClass) { proxy, method, args ->
        when (method.name) {
            "name" -> Text.literal(profile.name)
            "owner" -> profile.id
            "id" -> Identifier.of(PROVIDER_ID, profile.id.toString())
            "provider" -> providerProxy
            "currency" -> currencyProxy
            "balance" -> balanceAsApiUnits(profile)
            "setBalance" -> onDbThread {
                economy.setBalanceSync(
                    profile.id,
                    fromApiUnits(requireNotNull(args[0])),
                    reason = "common_economy"
                )
                null
            }
            "canIncreaseBalance" -> buildTransaction(profile, proxy, requireNotNull(args[0]), increase = true)
            "canDecreaseBalance" -> buildTransaction(profile, proxy, requireNotNull(args[0]), increase = false)
            "increaseBalance" -> onDbThread {
                val delta = requireNotNull(args[0])
                val amount = fromApiUnits(delta)
                val previous = toApiUnits(MoneyUnits.toMinorUnits(economy.getBalanceSync(profile.id)))
                economy.depositSync(profile.id, amount, reason = "common_economy")
                val finalBalance = toApiUnits(MoneyUnits.toMinorUnits(economy.getBalanceSync(profile.id)))
                createTransaction(
                    success = true,
                    message = Text.literal("Success"),
                    finalBalance = finalBalance,
                    previousBalance = previous,
                    transactionAmount = delta,
                    account = proxy
                )
            }
            "decreaseBalance" -> onDbThread {
                val delta = requireNotNull(args[0])
                val amount = fromApiUnits(delta)
                val previous = toApiUnits(MoneyUnits.toMinorUnits(economy.getBalanceSync(profile.id)))
                if (compareUnits(previous, delta) < 0) {
                    createTransaction(
                        success = false,
                        message = Text.literal("Insufficient funds"),
                        finalBalance = previous,
                        previousBalance = previous,
                        transactionAmount = negateUnits(delta),
                        account = proxy
                    )
                } else {
                    val ok = economy.withdrawSync(profile.id, amount, reason = "common_economy")
                    val finalBalance = toApiUnits(MoneyUnits.toMinorUnits(economy.getBalanceSync(profile.id)))
                    createTransaction(
                        success = ok,
                        message = Text.literal(if (ok) "Success" else "Insufficient funds"),
                        finalBalance = finalBalance,
                        previousBalance = previous,
                        transactionAmount = negateUnits(delta),
                        account = proxy
                    )
                }
            }
            else -> unsupported(method)
        }
    }

    private fun buildTransaction(
        profile: GameProfile,
        account: Any,
        delta: Any,
        increase: Boolean
    ): Any = onDbThread {
        val previous = toApiUnits(MoneyUnits.toMinorUnits(economy.getBalanceSync(profile.id)))
        if (increase) {
            createTransaction(
                success = true,
                message = Text.literal("Success"),
                finalBalance = addUnits(previous, delta),
                previousBalance = previous,
                transactionAmount = delta,
                account = account
            )
        } else if (compareUnits(previous, delta) >= 0) {
            createTransaction(
                success = true,
                message = Text.literal("Success"),
                finalBalance = subtractUnits(previous, delta),
                previousBalance = previous,
                transactionAmount = negateUnits(delta),
                account = account
            )
        } else {
            createTransaction(
                success = false,
                message = Text.literal("Insufficient funds"),
                finalBalance = previous,
                previousBalance = previous,
                transactionAmount = negateUnits(delta),
                account = account
            )
        }
    }

    private fun createTransaction(
        success: Boolean,
        message: Text,
        finalBalance: Any,
        previousBalance: Any,
        transactionAmount: Any,
        account: Any
    ): Any {
        val ctor = when (moneyMode) {
            MoneyMode.LONG -> transactionSimpleClass.getConstructor(
                Boolean::class.javaPrimitiveType,
                Text::class.java,
                Long::class.javaPrimitiveType,
                Long::class.javaPrimitiveType,
                Long::class.javaPrimitiveType,
                economyAccountClass
            )
            MoneyMode.BIG_INTEGER -> transactionSimpleClass.getConstructor(
                Boolean::class.javaPrimitiveType,
                Text::class.java,
                BigInteger::class.java,
                BigInteger::class.java,
                BigInteger::class.java,
                economyAccountClass
            )
        }
        return ctor.newInstance(success, message, finalBalance, previousBalance, transactionAmount, account)
    }

    private inline fun proxy(
        iface: Class<*>,
        crossinline handler: (Any, Method, Array<Any?>) -> Any?
    ): Any {
        val invocationHandler = java.lang.reflect.InvocationHandler { proxy, method, args ->
            val safeArgs = args ?: emptyArray()
            if (method.declaringClass == Any::class.java) {
                return@InvocationHandler when (method.name) {
                    "toString" -> "ServerMarketCommonEconomyProxy"
                    "hashCode" -> System.identityHashCode(proxy)
                    "equals" -> proxy === safeArgs.getOrNull(0)
                    else -> null
                }
            }
            if (method.isDefault) {
                return@InvocationHandler invokeDefaultMethod(proxy, method, safeArgs)
            }
            handler(proxy, method, safeArgs)
        }
        return Proxy.newProxyInstance(iface.classLoader, arrayOf(iface), invocationHandler)
    }

    private fun invokeDefaultMethod(proxy: Any, method: Method, args: Array<Any?>): Any? {
        val handle = lookup.findSpecial(
            method.declaringClass,
            method.name,
            MethodType.methodType(method.returnType, method.parameterTypes),
            method.declaringClass
        )
        return handle.bindTo(proxy).invokeWithArguments(*args)
    }

    private fun unsupported(method: Method): Nothing {
        throw UnsupportedOperationException("CommonEconomyBridge: ${method.declaringClass.simpleName}.${method.name}")
    }
}
