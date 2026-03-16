package com.mayihavek.mayadyeshachviewcontrol.listener

import com.mayihavek.mayadyeshachviewcontrol.manager.AdyService
import com.mayihavek.mayadyeshachviewcontrol.manager.NpcVisibilityManager
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
     * 2. 对 auto-hide 列表中的 NPC 默认隐藏（除非数据库中 visible=true）
     * 3. 根据数据库中的设置应用可见性
     * 
     * 使用 entity.visible(player, true) 而非完整的 respawn，性能更优
     */
    fun fixAndApplyVisibility(player: Player) {
        // 获取玩家的可见性设置（用于判断哪些 NPC 应该隐藏/显示）
        val visibilityForPlayer = NpcVisibilityManager.getVisibilityForPlayer(player.name)

        // 获取玩家所在世界的所有 NPC
        val entities = AdyService.getEntitiesInWorld(player.world)
        info("§e[MAVC] 为玩家 ${player.name} 刷新 ${entities.size} 个 NPC")

        // 遍历所有 NPC，应用可见性
        entities.forEach { entity ->
            val npcId = entity.id
            val visibilitySetting = visibilityForPlayer[npcId]
            val isAutoHide = NpcVisibilityManager.isAutoHideNpc(npcId)

            when {
                // 情况1：数据库明确设置为不可见
                visibilitySetting == false -> {
                    entity.removeViewer(player)
                }
                // 情况2：auto-hide NPC，但数据库设置为可见
                isAutoHide && visibilitySetting == true -> {
                    entity.visible(player, true)
                }
                // 情况3：auto-hide NPC，没有数据库记录（默认隐藏）
                isAutoHide && visibilitySetting == null -> {
                    entity.removeViewer(player)
                }
                // 情况4：普通 NPC，刷新可见性
                else -> {
                    entity.visible(player, true)
                }
            }
        }
    }
}
