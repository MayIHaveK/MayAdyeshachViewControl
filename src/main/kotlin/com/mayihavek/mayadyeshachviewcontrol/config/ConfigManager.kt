package com.mayihavek.mayadyeshachviewcontrol.config

import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration

/**
 * 配置文件管理器
 * 管理 auto-hide-npcs 和 groups 配置
 *
 * @author MayIHaveK
 * @since 2026/3/15
 */
object ConfigManager {

    @Config("config.yml")
    lateinit var config: Configuration
        private set

    /**
     * 获取进服自动隐藏的 NPC 列表
     */
    val autoHideNpcs: List<String>
        get() = config.getStringList("auto-hide-npcs")

    /**
     * 获取所有隐藏组名称
     */
    val groupNames: Set<String>
        get() = config.getConfigurationSection("groups")?.getKeys(false) ?: emptySet()

    /**
     * 获取指定组内的 NPC 列表
     */
    fun getGroupNpcs(groupName: String): List<String> {
        return config.getStringList("groups.$groupName")
    }

    /**
     * 判断 NPC 是否在自动隐藏列表中
     */
    fun isAutoHideNpc(npcId: String): Boolean {
        return npcId in autoHideNpcs
    }

    /**
     * 判断组是否存在
     */
    fun hasGroup(groupName: String): Boolean {
        return groupName in groupNames
    }

    /**
     * 重载配置文件
     */
    fun reload() {
        config.reload()
    }

    // ==================== 交互 HUD 配置 ====================

    val interactEnabled: Boolean
        get() = config.getBoolean("interact.enabled", false)

    val interactDistance: Double
        get() = config.getDouble("interact.distance", 2.5)

    val interactScanPeriod: Long
        get() = config.getLong("interact.scan-period", 2L)

    val interactPacketPeriod: Long
        get() = config.getLong("interact.packet-period", 3L)

    /** Bukkit 实体名称匹配（空=不过滤名称，只过滤距离和排除类型） */
    val interactMatchNames: List<String>
        get() = config.getStringList("interact.match-names")

    /** Adyeshach NPC ID 匹配（空=不过滤ID，显示距离内所有可见NPC） */
    val interactMatchAdyIds: List<String>
        get() = config.getStringList("interact.match-ady-ids")

    val interactDebug: Boolean
        get() = config.getBoolean("interact.debug", false)

    /** 世界白名单（空=所有世界都启用） */
    val interactAllowedWorlds: List<String>
        get() = config.getStringList("interact.allowed-worlds")

    /** ArcartX 区域白名单（空=所有区域都启用） */
    val interactAllowedAreas: List<String>
        get() = config.getStringList("interact.allowed-areas")
}
