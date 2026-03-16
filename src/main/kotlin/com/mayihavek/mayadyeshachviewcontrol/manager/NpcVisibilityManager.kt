package com.mayihavek.mayadyeshachviewcontrol.manager

import com.mayihavek.mayadyeshachviewcontrol.config.ConfigManager
import com.mayihavek.mayadyeshachviewcontrol.storage.VisibilityRepository
import taboolib.common.platform.function.info
import java.util.concurrent.ConcurrentHashMap

/**
 * NPC 可见性业务层：数据库 + 内存双写。
 * - set true/false：先写数据库，再更新内存，再对当前在线玩家生效（由指令侧调用 addViewer/removeViewer）。
 * - 玩家进服：从内存（已从 DB 加载）查出该玩家「不允许看见」的 NPC，逐个 removeViewer；允许看见的 addViewer。
 *
 * @author MayIHaveK
 * @since 2026/3/15
 */
object NpcVisibilityManager {

    /** 内存缓存: npcId -> (playerName -> visible)，与数据库保持一致 */
    private val cache = ConcurrentHashMap<String, MutableMap<String, Boolean>>()

    private var repository: VisibilityRepository? = null

    /**
     * 插件启用时调用：注入仓储并从 DB 加载到内存。
     */
    fun init(repo: VisibilityRepository) {
        repository = repo
        repo.init()
        loadFromDb()
    }

    /**
     * 插件禁用时调用：关闭仓储。
     */
    fun close() {
        repository?.close()
        repository = null
        cache.clear()
    }

    /**
     * 从数据库加载到内存（启动时或需要时调用）。
     */
    fun loadFromDb() {
        val repo = repository ?: return
        try {
            val all = repo.getAllVisibility()
            cache.clear()
            all.forEach { (npcId, players) ->
                if (players.isNotEmpty()) {
                    cache[npcId] = ConcurrentHashMap(players)
                }
            }
        } catch (e: Exception) {
            info("§c[MayAdyeshachViewControl] 从数据库加载可见性失败: ${e.message}")
        }
    }

    /**
     * 获取玩家对某 NPC 的可见性设置。
     * @return true=允许看见, false=禁止看见, null=未设置（使用默认）
     */
    fun getVisibility(npcId: String, playerName: String): Boolean? {
        return cache[npcId]?.get(playerName)
    }

    /**
     * 设置玩家对某 NPC 的可见性：先写数据库，再更新内存。
     * 调用方需自行对在线玩家执行 addViewer/removeViewer。
     */
    fun setVisibility(npcId: String, playerName: String, visible: Boolean) {
        repository?.setVisibility(npcId, playerName, visible)
        cache.getOrPut(npcId) { ConcurrentHashMap() }[playerName] = visible
    }

    /**
     * 批量设置组内所有 NPC 的可见性
     * @return 设置的 NPC 数量
     */
    fun setGroupVisibility(groupName: String, playerName: String, visible: Boolean): Int {
        val npcs = ConfigManager.getGroupNpcs(groupName)
        npcs.forEach { npcId ->
            setVisibility(npcId, playerName, visible)
        }
        return npcs.size
    }

    /**
     * 获取某 NPC 下所有玩家的可见性设置。
     */
    fun getPlayersForNpc(npcId: String): Map<String, Boolean> {
        return cache[npcId]?.toMap() ?: emptyMap()
    }

    /**
     * 获取所有 NPC 的可见性数据（用于玩家加入时恢复）。
     */
    fun getAllVisibility(): Map<String, Map<String, Boolean>> {
        return cache.mapValues { it.value.toMap() }
    }

    /**
     * 获取某玩家对应的「NPC -> 是否可见」映射，用于进服时应用可见性。
     */
    fun getVisibilityForPlayer(playerName: String): Map<String, Boolean> {
        return cache.mapNotNull { (npcId, players) ->
            players[playerName]?.let { npcId to it }
        }.toMap()
    }

    /**
     * 判断 NPC 是否在自动隐藏列表中
     */
    fun isAutoHideNpc(npcId: String): Boolean {
        return ConfigManager.isAutoHideNpc(npcId)
    }
}
