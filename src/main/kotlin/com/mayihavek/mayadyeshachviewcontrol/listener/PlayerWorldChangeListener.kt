package com.mayihavek.mayadyeshachviewcontrol.listener

import org.bukkit.Bukkit
import org.bukkit.event.player.PlayerChangedWorldEvent
import taboolib.common.platform.event.SubscribeEvent
import taboolib.common.platform.function.pluginId

/**
 * 玩家换世界/传送后：延后执行一次「修复 + 应用可见性」，与进服逻辑一致。
 *
 * @author MayIHaveK
 * @since 2026/3/15
 */
object PlayerWorldChangeListener {

    private const val APPLY_DELAY_TICKS = 20L

    @SubscribeEvent
    fun onWorldChange(e: PlayerChangedWorldEvent) {
        val player = e.player
        val plugin = Bukkit.getPluginManager().getPlugin(pluginId) ?: return
        Bukkit.getScheduler().runTaskLater(
            plugin,
            Runnable { VisibilityApplyHelper.fixAndApplyVisibility(player) },
            APPLY_DELAY_TICKS
        )
    }
}
