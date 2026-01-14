package asagiribeta.serverMarket.util

import asagiribeta.serverMarket.ServerMarket
import java.util.Properties

/**
 * 配置属性加载器工具类
 * 提供类型安全的属性读取和自动补全功能
 */
class PropertyLoader(private val properties: Properties) {
    var changed: Boolean = false
        private set

    /**
     * 读取并验证 Double 类型的配置项
     */
    fun doubleKey(key: String, default: Double): Double {
        val raw = properties.getProperty(key)
        if (raw == null) {
            properties.setProperty(key, default.toString())
            changed = true
            return default
        }
        val v = raw.toDoubleOrNull()
        return if (v == null) {
            ServerMarket.LOGGER.warn("Config key '{}' value '{}' is invalid; using default {}", key, raw, default)
            properties.setProperty(key, default.toString())
            changed = true
            default
        } else v
    }

    /**
     * 读取并验证 Int 类型的配置项
     */
    fun intKey(key: String, default: Int): Int {
        val raw = properties.getProperty(key)
        if (raw == null) {
            properties.setProperty(key, default.toString())
            changed = true
            return default
        }
        val v = raw.toIntOrNull()
        return if (v == null) {
            ServerMarket.LOGGER.warn("Config key '{}' value '{}' is invalid; using default {}", key, raw, default)
            properties.setProperty(key, default.toString())
            changed = true
            default
        } else v
    }

    /**
     * 读取并验证 Boolean 类型的配置项
     */
    fun boolKey(key: String, default: Boolean): Boolean {
        val raw = properties.getProperty(key)
        if (raw == null) {
            properties.setProperty(key, default.toString())
            changed = true
            return default
        }
        return when (raw.lowercase()) {
            "true", "false" -> raw.toBoolean()
            else -> {
                ServerMarket.LOGGER.warn("Config key '{}' value '{}' is invalid; using default {}", key, raw, default)
                properties.setProperty(key, default.toString())
                changed = true
                default
            }
        }
    }

    /**
     * 读取 String 类型的配置项
     */
    fun stringKey(key: String, default: String): String {
        val raw = properties.getProperty(key)
        return if (raw == null) {
            properties.setProperty(key, default)
            changed = true
            default
        } else raw
    }

    /**
     * 设置 Double 类型的配置项
     */
    @Suppress("unused")
    fun setDouble(key: String, value: Double) {
        properties.setProperty(key, value.toString())
    }

    /**
     * 设置 Int 类型的配置项
     */
    @Suppress("unused")
    fun setInt(key: String, value: Int) {
        properties.setProperty(key, value.toString())
    }

    /**
     * 设置 Boolean 类型的配置项
     */
    @Suppress("unused")
    fun setBool(key: String, value: Boolean) {
        properties.setProperty(key, value.toString())
    }

    /**
     * 设置 String 类型的配置项
     */
    @Suppress("unused")
    fun setString(key: String, value: String) {
        properties.setProperty(key, value)
    }
}
