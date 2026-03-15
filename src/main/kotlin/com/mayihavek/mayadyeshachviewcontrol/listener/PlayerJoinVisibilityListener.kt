package com.mayihavek.mayadyeshachviewcontrol.listener

import org.bukkit.Bukkit
import org.bukkit.event.player.PlayerJoinEvent
import taboolib.common.platform.event.SubscribeEvent
import taboolib.common.platform.function.pluginId

/**
 * 玩家进服：延后执行一次「修复 + 应用可见性」（先刷新实体管理器，再按库内设置对不可见的 NPC 做 removeViewer）。
 *
 * @author MayIHaveK
 * @since 2026/3/15
 */
object PlayerJoinVisibilityListener {

    private const val APPLY_DELAY_TICKS = 60L // 延后 3 秒再应用，确保 NPC 已加载

    @SubscribeEvent
    fun onJoin(e: PlayerJoinEvent) {
        val player = e.player
        val plugin = Bukkit.getPluginManager().getPlugin(pluginId) ?: return
        Bukkit.getScheduler().runTaskLater(
            plugin,
            Runnable { VisibilityApplyHelper.fixAndApplyVisibility(player) },
            APPLY_DELAY_TICKS
        )
    }
}
