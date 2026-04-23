package com.mayihavek.mayadyeshachviewcontrol

import com.mayihavek.mayadyeshachviewcontrol.auth.AuthManager
import com.mayihavek.mayadyeshachviewcontrol.config.ConfigManager
import com.mayihavek.mayadyeshachviewcontrol.interact.InteractSystem
import com.mayihavek.mayadyeshachviewcontrol.manager.AdyService
import com.mayihavek.mayadyeshachviewcontrol.manager.NpcVisibilityManager
import com.mayihavek.mayadyeshachviewcontrol.storage.SqliteVisibilityRepository
import com.mayihavek.mayadyeshachviewcontrol.utils.ConsoleBanner
import ink.ptms.adyeshach.core.Adyeshach
import ink.ptms.adyeshach.core.AdyeshachAPI
import taboolib.common.platform.Plugin
import taboolib.common.platform.function.disablePlugin
import taboolib.common.platform.function.pluginId
import taboolib.common.platform.function.pluginVersion
import taboolib.common.platform.function.severe
import java.io.File

object MayAdyeshachViewControl : Plugin() {

    val dataFolder: File
        get() = File("plugins", pluginId)

    private var visibilityRepository: SqliteVisibilityRepository? = null

    val sqliteRepository: SqliteVisibilityRepository?
        get() = visibilityRepository

    val adyeshachAPI: AdyeshachAPI
        get() = Adyeshach.api()

    override fun onEnable() {
        if (!AuthManager.performValidation()) {
            severe("MayAdyeshachViewControl 授权验证失败，插件已禁用")
            disablePlugin()
            return
        }

        visibilityRepository = SqliteVisibilityRepository(dataFolder)
        NpcVisibilityManager.init(visibilityRepository!!)
        AdyService.loadPrivateModels()

        val adyConnected = try {
            adyeshachAPI
            true
        } catch (e: Exception) {
            false
        }

        val autoHideCount = ConfigManager.autoHideNpcs.size
        val groupCount = ConfigManager.groupNames.size

        val interactEnabled = try {
            if (ConfigManager.interactEnabled) {
                saveDefaultResource("Hud.yml")
                InteractSystem.start()
                true
            } else false
        } catch (_: Exception) { false }

        ConsoleBanner.print {
            asciiText = "MAVC"
            pluginName = pluginId
            version = pluginVersion
            contact = "QQ 2534226689"

            info("Adyeshach", if (adyConnected) "Connected" else "Not Found")
            info("Database", "SQLite Ready")
            info("Auto-Hide", "$autoHideCount NPCs")
            info("Groups", "$groupCount defined")
            info("Interact HUD", if (interactEnabled) "Enabled" else "Disabled")
            info("Command", "/madvc help")
        }
    }

    override fun onDisable() {
        AuthManager.clearSession()
        InteractSystem.stop()
        NpcVisibilityManager.close()
        visibilityRepository?.close()
        visibilityRepository = null
    }

    private fun saveDefaultResource(name: String) {
        val file = File(dataFolder, name)
        if (!file.exists()) {
            dataFolder.mkdirs()
            javaClass.classLoader?.getResourceAsStream(name)?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
        }
    }
}