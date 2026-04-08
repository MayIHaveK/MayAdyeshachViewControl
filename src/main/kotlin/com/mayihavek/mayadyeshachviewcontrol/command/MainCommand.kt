package com.mayihavek.mayadyeshachviewcontrol.command

import com.mayihavek.mayadyeshachviewcontrol.config.ConfigManager
import com.mayihavek.mayadyeshachviewcontrol.interact.InteractSystem
import com.mayihavek.mayadyeshachviewcontrol.manager.AdyService.allEntityIds
import com.mayihavek.mayadyeshachviewcontrol.manager.AdyService.clearAllPrivateModelsForNpc
import com.mayihavek.mayadyeshachviewcontrol.manager.AdyService.clearArcartXModel
import com.mayihavek.mayadyeshachviewcontrol.manager.AdyService.clearPrivateArcartXModel
import com.mayihavek.mayadyeshachviewcontrol.manager.AdyService.getAdyEntities
import com.mayihavek.mayadyeshachviewcontrol.manager.AdyService.getPrivateModelsForPlayer
import com.mayihavek.mayadyeshachviewcontrol.manager.AdyService.setArcartXModel
import com.mayihavek.mayadyeshachviewcontrol.manager.AdyService.setPrivateArcartXModel
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
            if (ConfigManager.interactEnabled) {
                InteractSystem.reload()
            } else {
                InteractSystem.stop()
            }
            sender.sendMessage("&a[MayAdyeshachViewControl] &7配置文件已重载。".colored())
        }
    }

    @CommandBody
    val open = subCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            val player = Bukkit.getPlayerExact(sender.name)
            if (player == null) {
                sender.sendMessage("&c[MayAdyeshachViewControl] &7该命令只能由玩家执行。".colored())
                return@execute
            }
            InteractSystem.open(player)
        }
    }

    @CommandBody
    val model = subCommand {
        dynamic("entity") {
            suggestionUncheck<ProxyCommandSender> { _, _ -> allEntityIds }
            dynamic("modelId") {
                execute<ProxyCommandSender> { sender, context, _ ->
                    handleArcartXModel(sender, context["entity"], context["modelId"], 1.0)
                }
                dynamic("scale") {
                    execute<ProxyCommandSender> { sender, context, argument ->
                        val scale = argument.toDoubleOrNull()
                        if (scale == null || scale <= 0) {
                            sender.sendMessage("&c[MayAdyeshachViewControl] &7缩放值必须是大于 0 的数字。".colored())
                            return@execute
                        }
                        handleArcartXModel(sender, context["entity"], context["modelId"], scale)
                    }
                }
            }
        }
    }

    @CommandBody
    val unmodel = subCommand {
        dynamic("entity") {
            suggestionUncheck<ProxyCommandSender> { _, _ -> allEntityIds }
            execute<ProxyCommandSender> { sender, context, _ ->
                handleClearArcartXModel(sender, context["entity"])
            }
        }
    }

    @CommandBody
    val pmodel = subCommand {
        dynamic("player") {
            suggestionUncheck<ProxyCommandSender> { _, _ ->
                taboolib.common.platform.function.onlinePlayers().map { it.name }
            }
            dynamic("entity") {
                suggestionUncheck<ProxyCommandSender> { _, _ -> allEntityIds }
                dynamic("modelId") {
                    execute<ProxyCommandSender> { sender, context, _ ->
                        handlePrivateArcartXModel(sender, context["player"], context["entity"], context["modelId"], 1.0)
                    }
                    dynamic("scale") {
                        execute<ProxyCommandSender> { sender, context, argument ->
                            val scale = argument.toDoubleOrNull()
                            if (scale == null || scale <= 0) {
                                sender.sendMessage("&c[MayAdyeshachViewControl] &7缩放值必须是大于 0 的数字。".colored())
                                return@execute
                            }
                            handlePrivateArcartXModel(sender, context["player"], context["entity"], context["modelId"], scale)
                        }
                    }
                }
            }
        }
    }

    @CommandBody
    val unpmodel = subCommand {
        dynamic("player") {
            suggestionUncheck<ProxyCommandSender> { _, _ ->
                taboolib.common.platform.function.onlinePlayers().map { it.name }
            }
            dynamic("entity") {
                suggestionUncheck<ProxyCommandSender> { _, _ -> allEntityIds }
                execute<ProxyCommandSender> { sender, context, _ ->
                    handleClearPrivateArcartXModel(sender, context["player"], context["entity"])
                }
            }
        }
    }

    @CommandBody
    val plistmodel = subCommand {
        dynamic("player") {
            suggestionUncheck<ProxyCommandSender> { _, _ ->
                taboolib.common.platform.function.onlinePlayers().map { it.name }
            }
            execute<ProxyCommandSender> { sender, context, _ ->
                handleListPrivateModels(sender, context["player"])
            }
        }
    }

    @CommandBody
    val clearmodelall = subCommand {
        dynamic("entity") {
            suggestionUncheck<ProxyCommandSender> { _, _ -> allEntityIds }
            execute<ProxyCommandSender> { sender, context, _ ->
                handleClearAllPrivateModels(sender, context["entity"])
            }
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

    private fun handleArcartXModel(sender: ProxyCommandSender, npcId: String, modelId: String, scale: Double) {
        val count = setArcartXModel(npcId, modelId, scale)
        if (count <= 0) {
            sender.sendMessage("&c[MayAdyeshachViewControl] &7未找到 ID 为 &f$npcId &7的 Adyeshach NPC。".colored())
            return
        }
        sender.sendMessage("&a[MayAdyeshachViewControl] &7已为 &f$npcId &7的 &f$count &7个实体设置 ArcartX 模型为 &f$modelId &7，缩放 &f$scale&7。".colored())
    }

    private fun handleClearArcartXModel(sender: ProxyCommandSender, npcId: String) {
        val count = clearArcartXModel(npcId)
        if (count <= 0) {
            sender.sendMessage("&c[MayAdyeshachViewControl] &7未找到 ID 为 &f$npcId &7的 Adyeshach NPC。".colored())
            return
        }
        sender.sendMessage("&a[MayAdyeshachViewControl] &7已清除 &f$npcId &7的 &f$count &7个实体上的 ArcartX 模型。".colored())
    }

    private fun handlePrivateArcartXModel(sender: ProxyCommandSender, playerName: String, npcId: String, modelId: String, scale: Double) {
        val count = setPrivateArcartXModel(playerName, npcId, modelId, scale)
        if (count <= 0) {
            sender.sendMessage("&c[MayAdyeshachViewControl] &7未找到 ID 为 &f$npcId &7的 Adyeshach NPC。".colored())
            return
        }
        sender.sendMessage("&a[MayAdyeshachViewControl] &7已设置玩家 &f$playerName &7看到 &f$npcId &7时使用私人模型 &f$modelId &7，缩放 &f$scale&7。".colored())
    }

    private fun handleClearPrivateArcartXModel(sender: ProxyCommandSender, playerName: String, npcId: String) {
        val count = clearPrivateArcartXModel(playerName, npcId)
        if (count <= 0) {
            sender.sendMessage("&c[MayAdyeshachViewControl] &7未找到 ID 为 &f$npcId &7的 Adyeshach NPC。".colored())
            return
        }
        sender.sendMessage("&a[MayAdyeshachViewControl] &7已清除玩家 &f$playerName &7看到 &f$npcId &7时的私人模型，并回退到全局/默认显示。".colored())
    }

    private fun handleListPrivateModels(sender: ProxyCommandSender, playerName: String) {
        val models = getPrivateModelsForPlayer(playerName)
        if (models.isEmpty()) {
            sender.sendMessage("&e[MayAdyeshachViewControl] &7玩家 &f$playerName &7当前没有私人模型配置。".colored())
            return
        }
        val lines = models.entries
            .sortedBy { it.key }
            .joinToString("\n") { (npcId, data) ->
                "&f$npcId &8-> &a${data.modelId} &7(scale=&f${data.scale}&7)"
            }
        sender.sendMessage("&a[MayAdyeshachViewControl] &7玩家 &f$playerName &7的私人模型列表:\n$lines".colored())
    }

    private fun handleClearAllPrivateModels(sender: ProxyCommandSender, npcId: String) {
        val count = clearAllPrivateModelsForNpc(npcId)
        sender.sendMessage("&a[MayAdyeshachViewControl] &7已清除 NPC &f$npcId &7在 &f$count &7个玩家上的私人模型配置。".colored())
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
                &f/madvc model <entity> <modelId> [scale] &8- &7通过 ArcartX 为 Ady NPC 挂载全局客户端模型
                &f/madvc unmodel <entity> &8- &7清除 Ady NPC 上的 ArcartX 全局客户端模型
                &f/madvc pmodel <player> <entity> <modelId> [scale] &8- &7给指定玩家设置该 NPC 的私人模型
                &f/madvc unpmodel <player> <entity> &8- &7清除指定玩家看到该 NPC 的私人模型
                &f/madvc plistmodel <player> &8- &7查看该玩家所有私人模型配置
                &f/madvc clearmodelall <entity> &8- &7清除该 NPC 在所有玩家上的私人模型配置
                &f/madvc open &8- &7打开交互 HUD（需启用 interact）
                &f/madvc reload &8- &7重载配置文件和 HUD 材质
                &r
            """.trimIndent().colored())
        }
    }
}