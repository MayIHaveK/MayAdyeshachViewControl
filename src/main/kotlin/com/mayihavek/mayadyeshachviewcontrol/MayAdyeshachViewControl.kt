package com.mayihavek.mayadyeshachviewcontrol

import com.mayihavek.mayadyeshachviewcontrol.manager.NpcVisibilityManager
import com.mayihavek.mayadyeshachviewcontrol.storage.SqliteVisibilityRepository
import com.mayihavek.mayadyeshachviewcontrol.utils.ConsoleBanner
import ink.ptms.adyeshach.core.Adyeshach
import ink.ptms.adyeshach.core.AdyeshachAPI
import taboolib.common.platform.Plugin
import taboolib.common.platform.function.info
import taboolib.common.platform.function.pluginId
import taboolib.common.platform.function.pluginVersion
import java.io.File

object MayAdyeshachViewControl : Plugin() {

    /** 插件数据目录（配置与 SQLite 等）。Bukkit 下等价于 getDataFolder() */
    val dataFolder: File
        get() = File("plugins", pluginId)

    /** 可见性仓储（SQLite），插件启用时创建，禁用时关闭 */
    private var visibilityRepository: SqliteVisibilityRepository? = null

    // 不要直接在顶层声明具体类型，使用 getter 动态获取
    // 这样只有在实际调用 adyeshachAPI 时，才会触发类加载，完美避开启动器扫描雷区
    val adyeshachAPI: AdyeshachAPI
        get() = Adyeshach.api()

    override fun onEnable() {
        // 初始化数据库
        visibilityRepository = SqliteVisibilityRepository(dataFolder)
        NpcVisibilityManager.init(visibilityRepository!!)

        // 检测 Adyeshach API
        val adyConnected = try {
            adyeshachAPI
            true
        } catch (e: Exception) {
            false
        }

        // 打印启动横幅
        ConsoleBanner.print {
            asciiText = "MAVC"
            pluginName = pluginId
            version = pluginVersion
            contact = "QQ 2534226689"

            info("Adyeshach", if (adyConnected) "Connected" else "Not Found")
            info("Database", "SQLite Ready")
            info("Command", "/madvc help")
        }
    }

    override fun onDisable() {
        NpcVisibilityManager.close()
        visibilityRepository?.close()
        visibilityRepository = null
    }
}