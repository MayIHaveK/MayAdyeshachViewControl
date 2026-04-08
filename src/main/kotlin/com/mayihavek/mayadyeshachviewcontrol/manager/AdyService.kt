package com.mayihavek.mayadyeshachviewcontrol.manager

import com.mayihavek.mayadyeshachviewcontrol.MayAdyeshachViewControl
import ink.ptms.adyeshach.core.entity.EntityInstance
import org.bukkit.World
import org.bukkit.entity.Player
import priv.seventeen.artist.arcartx.api.ArcartXAPI
import taboolib.common.platform.function.info
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Adyeshach 服务工具类
 * @author MayIHaveK
 * @since 2026/3/15
 */
object AdyService {

    private const val ARCARTX_MODEL_TAG = "ArcartX_Model"
    private const val ARCARTX_SCALE_TAG = "ArcartX_Scale"

    data class PrivateModelData(
        val modelId: String,
        val scale: Double
    )

    /** 玩家名 -> (npcId -> 私人模型数据) */
    private val privateModelMap = ConcurrentHashMap<String, MutableMap<String, PrivateModelData>>()

    /**
     * 将 String 扩展为获取 Adyeshach 实体的方法
     * 带 try-catch 防护，防止底层 API 在极不稳定的状态下抛错
     */
    fun String.getAdyEntities(): List<EntityInstance> {
        return try {
            MayAdyeshachViewControl.adyeshachAPI.getEntityFinder().getEntitiesFromId(this)
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 获取所有存活的实体 ID 列表（用于补全）
     * 就算加载出来的实体是 0 个，也不会阻断玩家手动敲入 ID
     */
    val allEntityIds: List<String>
        get() = try {
            MayAdyeshachViewControl.adyeshachAPI.getPublicEntityManager().getEntities().map { it.id }.distinct()
        } catch (e: Exception) {
            emptyList()
        }

    /**
     * 获取所有公共实体实例
     */
    val allEntities: List<EntityInstance>
        get() = try {
            MayAdyeshachViewControl.adyeshachAPI.getPublicEntityManager().getEntities()
        } catch (e: Exception) {
            emptyList()
        }

    /**
     * 获取指定世界中的所有 NPC 实体
     */
    fun getEntitiesInWorld(world: World): List<EntityInstance> {
        return try {
            MayAdyeshachViewControl.adyeshachAPI.getEntityFinder().getEntities { entity ->
                entity.world.name == world.name
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 获取玩家附近的 NPC 实体
     */
    fun getEntitiesNearPlayer(player: Player, radius: Double = 128.0): List<EntityInstance> {
        return try {
            val loc = player.location
            MayAdyeshachViewControl.adyeshachAPI.getEntityFinder().getEntities { entity ->
                entity.world.name == player.world.name &&
                entity.position.toLocation().distance(loc) <= radius
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 通过 ArcartX 约定的持久化标签为 Adyeshach NPC 设置模型。
     * ArcartX 会监听 AdyeshachPersistentTagUpdateEvent，并自动同步给可见玩家。
     */
    fun setArcartXModel(npcId: String, modelId: String, scale: Double = 1.0): Int {
        val entities = npcId.getAdyEntities()
        entities.forEach { entity ->
            try {
                // 先写缩放，再写模型，保证 ArcartX 在处理 Model 事件时能读到最新 Scale
                entity.setPersistentTag(ARCARTX_SCALE_TAG, scale.toString())
                entity.setPersistentTag(ARCARTX_MODEL_TAG, modelId)
            } catch (e: Exception) {
                info("§c[MAVC] 设置 ArcartX 模型失败 | npc=$npcId | entity=${entity.uniqueId} | ${e.message}")
            }
        }
        return entities.size
    }

    /**
     * 清除 Adyeshach NPC 上由 ArcartX 挂载的模型。
     */
    fun clearArcartXModel(npcId: String): Int {
        val entities = npcId.getAdyEntities()
        entities.forEach { entity ->
            try {
                entity.removePersistentTag(ARCARTX_MODEL_TAG)
                entity.removePersistentTag(ARCARTX_SCALE_TAG)
            } catch (e: Exception) {
                info("§c[MAVC] 清除 ArcartX 模型失败 | npc=$npcId | entity=${entity.uniqueId} | ${e.message}")
            }
        }
        return entities.size
    }

    /**
     * 设置某玩家看到某个 NPC 的私人模型。
     */
    fun setPrivateArcartXModel(playerName: String, npcId: String, modelId: String, scale: Double = 1.0): Int {
        val entities = npcId.getAdyEntities()
        privateModelMap.computeIfAbsent(playerName) { ConcurrentHashMap() }[npcId] = PrivateModelData(modelId, scale)
        MayAdyeshachViewControl.sqliteRepository?.setPrivateModel(playerName, npcId, modelId, scale)

        val player = org.bukkit.Bukkit.getPlayerExact(playerName)
        if (player != null) {
            entities.forEach { applyEffectiveModelForPlayer(player, it) }
        }
        return entities.size
    }

    /**
     * 清除某玩家看到某个 NPC 的私人模型，并回退到全局模型/默认显示。
     */
    fun clearPrivateArcartXModel(playerName: String, npcId: String): Int {
        val entities = npcId.getAdyEntities()
        privateModelMap[playerName]?.remove(npcId)
        if (privateModelMap[playerName].isNullOrEmpty()) {
            privateModelMap.remove(playerName)
        }
        MayAdyeshachViewControl.sqliteRepository?.removePrivateModel(playerName, npcId)

        val player = org.bukkit.Bukkit.getPlayerExact(playerName)
        if (player != null) {
            entities.forEach { applyEffectiveModelForPlayer(player, it) }
        }
        return entities.size
    }

    fun hasPrivateModel(playerName: String, npcId: String): Boolean {
        return privateModelMap[playerName]?.containsKey(npcId) == true
    }

    /**
     * 玩家客户端重新看到实体时，若存在私人模型则补发。
     */
    fun applyPrivateModelIfPresent(player: Player, entity: EntityInstance): Boolean {
        val data = privateModelMap[player.name]?.get(entity.id) ?: return false
        return try {
            ArcartXAPI.getNetworkSender().sendSetEntityModel(
                player,
                UUID.fromString(entity.uniqueId),
                data.modelId,
                data.scale
            )
            true
        } catch (e: Exception) {
            info("§c[MAVC] 补发私人模型失败 | player=${player.name} | npc=${entity.id} | ${e.message}")
            false
        }
    }

    /**
     * 对某个玩家应用该实体的最终模型效果：私人模型 > 全局模型 > 清空模型。
     */
    fun applyEffectiveModelForPlayer(player: Player, entity: EntityInstance) {
        if (applyPrivateModelIfPresent(player, entity)) {
            return
        }
        try {
            val uuid = UUID.fromString(entity.uniqueId)
            val globalModel = entity.getPersistentTag(ARCARTX_MODEL_TAG)
            val globalScale = entity.getPersistentTag(ARCARTX_SCALE_TAG)?.toDoubleOrNull() ?: 1.0
            if (!globalModel.isNullOrBlank()) {
                ArcartXAPI.getNetworkSender().sendSetEntityModel(player, uuid, globalModel, globalScale)
            } else {
                ArcartXAPI.getNetworkSender().sendSetEntityModel(player, uuid, "", 1.0)
            }
        } catch (e: Exception) {
            info("§c[MAVC] 应用最终模型失败 | player=${player.name} | npc=${entity.id} | ${e.message}")
        }
    }

    fun getPrivateModelsForPlayer(playerName: String): Map<String, PrivateModelData> {
        return privateModelMap[playerName]?.toMap() ?: emptyMap()
    }

    fun clearAllPrivateModelsForNpc(npcId: String): Int {
        var changedPlayers = 0
        val iterator = privateModelMap.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.value.remove(npcId) != null) {
                changedPlayers++
            }
            if (entry.value.isEmpty()) {
                iterator.remove()
            }
        }
        MayAdyeshachViewControl.sqliteRepository?.removeAllPrivateModelsForNpc(npcId)
        return changedPlayers
    }

    fun loadPrivateModels() {
        privateModelMap.clear()
        val all = MayAdyeshachViewControl.sqliteRepository?.getAllPrivateModels() ?: emptyMap()
        all.forEach { (playerName, npcMap) ->
            val modelMap = ConcurrentHashMap<String, PrivateModelData>()
            npcMap.forEach { (npcId, data) ->
                modelMap[npcId] = PrivateModelData(data.first, data.second)
            }
            if (modelMap.isNotEmpty()) {
                privateModelMap[playerName] = modelMap
            }
        }
    }

    fun getEntityByUniqueIdForPlayer(uniqueId: UUID, player: Player): EntityInstance? {
        return try {
            MayAdyeshachViewControl.adyeshachAPI.getEntityFinder().getEntityFromUniqueId(uniqueId.toString(), player)
        } catch (_: Exception) {
            null
        }
    }
}