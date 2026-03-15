package com.mayihavek.mayadyeshachviewcontrol.listener

import com.mayihavek.mayadyeshachviewcontrol.manager.AdyService
import com.mayihavek.mayadyeshachviewcontrol.manager.NpcVisibilityManager
import ink.ptms.adyeshach.core.Adyeshach
import org.bukkit.entity.Player
import taboolib.common.platform.function.info

/**
 * 统一流程：先修复（刷新所有 NPC），再按库内设置应用可见性。
 * 用于：进服、换世界/传送后。
 *
 * @author MayIHaveK
 * @since 2026/3/15
 */
object VisibilityApplyHelper {

    /**
     * 对指定玩家执行一次「修复 + 应用可见性」：
     * 1. 对玩家所在世界的所有 NPC 刷新可见性（修复概率看不见的问题）
     * 2. 根据数据库中的设置，对标记为不可见的 NPC 执行 removeViewer
     * 
     * 使用 entity.visible(player, true) 而非完整的 respawn，性能更优
     */
    fun fixAndApplyVisibility(player: Player) {
        // 获取玩家的可见性设置（用于判断哪些 NPC 应该隐藏）
        val visibilityForPlayer = NpcVisibilityManager.getVisibilityForPlayer(player.name)

        // 获取玩家所在世界的所有 NPC
        val entities = AdyService.getEntitiesInWorld(player.world)
        info("§e[MAVC] 为玩家 ${player.name} 刷新 ${entities.size} 个 NPC")

        // 遍历所有 NPC，应用可见性
        entities.forEach { entity ->
            val npcId = entity.id
            val visibilitySetting = visibilityForPlayer[npcId]

            if (visibilitySetting == false) {
                // 明确设置为不可见：移除观察者
                entity.removeViewer(player)
            } else {
                // null（没有设置）或 true（设置为可见）：强制刷新可见性
                // 使用 visible() 方法，比 removeViewer + addViewer 更轻量
                entity.visible(player, true)
            }
        }
    }
}
