package com.mayihavek.mayadyeshachviewcontrol.command

import com.mayihavek.mayadyeshachviewcontrol.manager.AdyService.allEntityIds
import com.mayihavek.mayadyeshachviewcontrol.manager.AdyService.getAdyEntities
import org.bukkit.Bukkit
import taboolib.common.platform.ProxyCommandSender
import taboolib.common.platform.command.*
import taboolib.module.chat.colored

/**
 * MayAdyeshachViewControl 插件的主指令处理类。
 *
 * @author MayIHaveK
 * @since 2026/3/15
 */
@CommandHeader(
    name = "mayadyeshachviewcontrol",
    aliases = ["madvc"],
    permission = "mayadyeshachviewcontrol.command.main"
)
object MainCommand {

    @CommandBody
    val main = mainCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            sender.sendMessage("&a[MayAdyeshachViewControl] &7使用 &f/madvc help &7查看帮助".colored())
        }
    }

    @CommandBody
    val set = subCommand {
        dynamic("player") {
            suggestionUncheck<ProxyCommandSender> { _, _ ->
                taboolib.common.platform.function.onlinePlayers().map { it.name }
            }
            dynamic("entity") {
                suggestionUncheck<ProxyCommandSender> { _, _ -> allEntityIds }
                bool("visible") {
                    execute<ProxyCommandSender> { sender, context, argument ->
                        val isVisible = argument.toBoolean()
                        handleNpcVisibility(
                            sender = sender,
                            npcId = context["entity"],
                            playerName = context["player"],
                            visible = isVisible
                        )
                    }
                }
            }
        }
    }

    // 对指定玩家执行一次「修复 + 应用可见性」（与进服/换世界自动逻辑相同）
    @CommandBody
    val fix = subCommand {
        dynamic("player") {
            suggestionUncheck<ProxyCommandSender> { _, _ ->
                taboolib.common.platform.function.onlinePlayers().map { it.name }
            }
            execute<ProxyCommandSender> { sender, context, _ ->
                val playerName = context["player"]
                val player = Bukkit.getPlayerExact(playerName)
                if (player == null) {
                    sender.sendMessage("&c[MayAdyeshachViewControl] &7玩家 &f$playerName &7当前不在线。".colored())
                    return@execute
                }
                com.mayihavek.mayadyeshachviewcontrol.listener.VisibilityApplyHelper.fixAndApplyVisibility(player)
                sender.sendMessage("&a[MayAdyeshachViewControl] &7已为 &f$playerName &7执行修复并应用可见性。".colored())
            }
        }
    }

    private fun handleNpcVisibility(sender: ProxyCommandSender, npcId: String, playerName: String, visible: Boolean) {
        // 先写数据库并更新内存
        com.mayihavek.mayadyeshachviewcontrol.manager.NpcVisibilityManager.setVisibility(npcId, playerName, visible)
        
        // 对在线玩家立即应用可见性变更
        val player = Bukkit.getPlayerExact(playerName)
        player?.let { p ->
            val entities = npcId.getAdyEntities()
            if (visible) {
                // 设置为可见：使用 visible() 刷新可见性
                entities.forEach { entity -> entity.visible(p, true) }
            } else {
                // 设置为不可见：移除观察者
                entities.forEach { entity -> entity.removeViewer(p) }
            }
        }

        val actionMsg = if (visible) "&a允许" else "&c禁止"
        sender.sendMessage("&a[MayAdyeshachViewControl] &7已成功设置: $actionMsg &f$playerName &7看到实体 &f$npcId".colored())
    }

    @CommandBody
    val help = subCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            sender.sendMessage("""
                &r
                &a&lMayAdyeshachViewControl &7帮助菜单
                &f/madvc set <player> <entity> <true/false> &8- &7设置指定玩家是否能看到该实体
                &f/madvc fix <player> &8- &7对指定玩家执行一次修复并应用可见性
                &r
            """.trimIndent().colored())
        }
    }
}