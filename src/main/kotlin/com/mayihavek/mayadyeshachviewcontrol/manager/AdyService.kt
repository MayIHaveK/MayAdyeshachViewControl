package com.mayihavek.mayadyeshachviewcontrol.manager

import com.mayihavek.mayadyeshachviewcontrol.MayAdyeshachViewControl
import ink.ptms.adyeshach.core.entity.EntityInstance
import org.bukkit.World
import org.bukkit.entity.Player

/**
 * Adyeshach 服务工具类
 * @author MayIHaveK
 * @since 2026/3/15
 */
object AdyService {

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
}