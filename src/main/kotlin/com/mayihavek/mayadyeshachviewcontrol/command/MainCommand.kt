package com.mayihavek.mayadyeshachviewcontrol.command

import com.mayihavek.mayadyeshachviewcontrol.config.ConfigManager
import com.mayihavek.mayadyeshachviewcontrol.manager.AdyService.allEntityIds
import com.mayihavek.mayadyeshachviewcontrol.manager.AdyService.getAdyEntities
import com.mayihavek.mayadyeshachviewcontrol.manager.NpcVisibilityManager
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

    @CommandBody
    val group = subCommand {
        dynamic("player") {
            suggestionUncheck<ProxyCommandSender> { _, _ ->
                taboolib.common.platform.function.onlinePlayers().map { it.name }
            }
            dynamic("groupName") {
                suggestionUncheck<ProxyCommandSender> { _, _ ->
                    ConfigManager.groupNames.toList()
                }
                bool("visible") {
                    execute<ProxyCommandSender> { sender, context, argument ->
                        val playerName = context["player"]
                        val groupName = context["groupName"]
                        val isVisible = argument.toBoolean()
                        
                        if (!ConfigManager.hasGroup(groupName)) {
                            sender.sendMessage("&c[MayAdyeshachViewControl] &7组 &f$groupName &7不存在。".colored())
                            return@execute
                        }
                        
                        handleGroupVisibility(sender, groupName, playerName, isVisible)
                    }
                }
            }
        }
    }

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

    @CommandBody
    val reload = subCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            ConfigManager.reload()
            sender.sendMessage("&a[MayAdyeshachViewControl] &7配置文件已重载。".colored())
        }
    }

    private fun handleNpcVisibility(sender: ProxyCommandSender, npcId: String, playerName: String, visible: Boolean) {
        NpcVisibilityManager.setVisibility(npcId, playerName, visible)
        
        val player = Bukkit.getPlayerExact(playerName)
        player?.let { p ->
            val entities = npcId.getAdyEntities()
            if (visible) {
                entities.forEach { entity -> entity.visible(p, true) }
            } else {
                entities.forEach { entity -> entity.removeViewer(p) }
            }
        }

        val actionMsg = if (visible) "&a允许" else "&c禁止"
        sender.sendMessage("&a[MayAdyeshachViewControl] &7已成功设置: $actionMsg &f$playerName &7看到实体 &f$npcId".colored())
    }

    private fun handleGroupVisibility(sender: ProxyCommandSender, groupName: String, playerName: String, visible: Boolean) {
        val count = NpcVisibilityManager.setGroupVisibility(groupName, playerName, visible)
        
        val player = Bukkit.getPlayerExact(playerName)
        player?.let { p ->
            ConfigManager.getGroupNpcs(groupName).forEach { npcId ->
                val entities = npcId.getAdyEntities()
                if (visible) {
                    entities.forEach { entity -> entity.visible(p, true) }
                } else {
                    entities.forEach { entity -> entity.removeViewer(p) }
                }
            }
        }

        val actionMsg = if (visible) "&a允许" else "&c禁止"
        sender.sendMessage("&a[MayAdyeshachViewControl] &7已成功设置: $actionMsg &f$playerName &7看到组 &e$groupName &7中的 &f$count &7个实体".colored())
    }

    @CommandBody
    val help = subCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            sender.sendMessage("""
                &r
                &a&lMayAdyeshachViewControl &7帮助菜单
                &f/madvc set <player> <entity> <true/false> &8- &7设置指定玩家是否能看到该实体
                &f/madvc group <player> <group> <true/false> &8- &7批量设置组内实体可见性
                &f/madvc fix <player> &8- &7对指定玩家执行一次修复并应用可见性
                &f/madvc reload &8- &7重载配置文件
                &r
            """.trimIndent().colored())
        }
    }
}