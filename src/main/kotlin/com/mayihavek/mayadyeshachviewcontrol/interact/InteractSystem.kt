package com.mayihavek.mayadyeshachviewcontrol.interact

import com.mayihavek.mayadyeshachviewcontrol.MayAdyeshachViewControl
import com.mayihavek.mayadyeshachviewcontrol.config.ConfigManager
import com.mayihavek.mayadyeshachviewcontrol.manager.NpcVisibilityManager
import ink.ptms.adyeshach.core.Adyeshach
import ink.ptms.adyeshach.core.entity.EntityInstance
import ink.ptms.adyeshach.core.entity.manager.ManagerType
import org.bukkit.Bukkit
import org.bukkit.entity.Entity
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.util.Vector
import taboolib.common.platform.event.SubscribeEvent
import taboolib.common.platform.function.info
import taboolib.common.platform.function.pluginId
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

/**
 * 交互 HUD 系统
 * 扫描附近实体 → ArcartX HUD 展示 → 按键模拟右键交互
 * 支持：私有 NPC 兼容、世界限制、ArcartX 区域限制、距离排序
 */
object InteractSystem {

    /** 每个玩家的实体缓存 */
    val playerEntityMap = ConcurrentHashMap<String, PlayerCache>()

    /** 玩家当前所在的 ArcartX 区域名称（null = 不在任何区域） */
    private val playerCurrentArea = ConcurrentHashMap<String, String?>()

    /** 记录每个玩家的 HUD 是否已打开，避免重复 open/close */
    private val playerHudOpen = ConcurrentHashMap<String, Boolean>()

    /** 记录每个玩家上次发送的实体 ID 列表签名，用于判断是否需要重新发包 */
    private val playerLastSignature = ConcurrentHashMap<String, String>()

    private var scanTask: BukkitRunnable? = null
    private var packetTask: BukkitRunnable? = null
    private var arcartxUI: Any? = null
    private var cachedSendMethod: Method? = null
    private var cachedOpenMethod: Method? = null
    private var cachedCloseMethod: Method? = null

    /** 统一的实体条目，已按距离排序 */
    data class EntityEntry(
        val name: String,
        val type: String,       // "bukkit" 或 "ady"
        val id: String,
        val distSq: Double,
        val bukkitEntity: Entity? = null,
        val adyEntity: EntityInstance? = null
    )

    /** 单个玩家的缓存：按距离排序的统一列表 */
    data class PlayerCache(
        val entries: List<EntityEntry>
    )

    fun start() {
        stop()
        val plugin = Bukkit.getPluginManager().getPlugin(pluginId) ?: return
        registerUI()

        scanTask = object : BukkitRunnable() {
            override fun run() { scanAll() }
        }.also { it.runTaskTimer(plugin, 10L, ConfigManager.interactScanPeriod) }

        packetTask = object : BukkitRunnable() {
            override fun run() { sendPackets() }
        }.also { it.runTaskTimer(plugin, 10L, ConfigManager.interactPacketPeriod) }

        info("§a[MAVC] 交互 HUD 系统已启动")
    }

    fun stop() {
        scanTask?.takeIf { !it.isCancelled }?.cancel()
        packetTask?.takeIf { !it.isCancelled }?.cancel()
        scanTask = null
        packetTask = null
        // 关闭所有在线玩家的 HUD
        closeAllHuds()
        playerEntityMap.clear()
        playerCurrentArea.clear()
        playerHudOpen.clear()
        playerLastSignature.clear()
        cachedSendMethod = null
        cachedOpenMethod = null
        cachedCloseMethod = null
    }

    fun reload() {
        // 先停掉扫描/发包定时器，但保留 arcartxUI 引用
        scanTask?.takeIf { !it.isCancelled }?.cancel()
        packetTask?.takeIf { !it.isCancelled }?.cancel()
        scanTask = null
        packetTask = null
        // 关闭所有在线玩家的 HUD
        closeAllHuds()
        playerEntityMap.clear()
        playerCurrentArea.clear()
        playerHudOpen.clear()
        playerLastSignature.clear()

        // 热重载 HUD 配置
        try {
            val hudFile = java.io.File(MayAdyeshachViewControl.dataFolder, "Hud.yml")
            if (!hudFile.exists()) {
                info("§c[MAVC] HUD 配置文件不存在: ${hudFile.path}")
                return
            }
            val registryClass = Class.forName("priv.seventeen.artist.arcartx.api.ArcartXAPI")
            val registry = registryClass.getMethod("getUIRegistry").invoke(null)

            if (arcartxUI != null) {
                // 已注册过 → 先 unregister 再重新 register，确保引用刷新
                try {
                    registry.javaClass.getMethod("unregister", String::class.java)
                        .invoke(registry, "InteractEntityHud")
                } catch (_: Exception) {}
            }

            // 重新走完整注册流程（包括回调绑定和方法缓存）
            arcartxUI = null
            cachedSendMethod = null
            cachedOpenMethod = null
            cachedCloseMethod = null
            registerUI()
            info("§a[MAVC] ArcartX HUD 已热重载")
        } catch (e: Exception) {
            info("§c[MAVC] ArcartX HUD 热重载失败: ${e.message}")
            // 兜底：如果 UI 还没注册过，走完整注册
            if (arcartxUI == null) {
                registerUI()
            }
        }

        // 重启定时器
        val plugin = Bukkit.getPluginManager().getPlugin(pluginId) ?: return
        scanTask = object : BukkitRunnable() {
            override fun run() { scanAll() }
        }.also { it.runTaskTimer(plugin, 10L, ConfigManager.interactScanPeriod) }

        packetTask = object : BukkitRunnable() {
            override fun run() { sendPackets() }
        }.also { it.runTaskTimer(plugin, 10L, ConfigManager.interactPacketPeriod) }

        info("§a[MAVC] 交互 HUD 系统已重载")
    }

    fun open(player: Player) {
        try {
            val ui = arcartxUI ?: return
            ui.javaClass.getMethod("open", Player::class.java).invoke(ui, player)
        } catch (_: Exception) {}
    }

    // ==================== ArcartX 区域事件监听 ====================

    /**
     * 监听 ArcartX 区域进入事件
     * 通过 TabooLib @SubscribeEvent 监听，事件类型用反射判断避免编译依赖
     */
    @SubscribeEvent
    fun onAreaEnter(e: org.bukkit.event.Event) {
        val clz = e.javaClass
        if (clz.name != "priv.seventeen.artist.arcartx.event.player.PlayerAreaEnterEvent") return
        try {
            val player = clz.getMethod("getPlayer").invoke(e) as? Player ?: return
            val area = clz.getMethod("getArea").invoke(e) ?: return
            val areaName = area.javaClass.getMethod("getName").invoke(area) as? String ?: return
            playerCurrentArea[player.name] = areaName
        } catch (_: Exception) {}
    }

    @SubscribeEvent
    fun onAreaLeave(e: org.bukkit.event.Event) {
        val clz = e.javaClass
        if (clz.name != "priv.seventeen.artist.arcartx.event.player.PlayerAreaLeaveEvent") return
        try {
            val player = clz.getMethod("getPlayer").invoke(e) as? Player ?: return
            val newArea = clz.getMethod("getNewArea").invoke(e)
            if (newArea == null) {
                playerCurrentArea[player.name] = null
            } else {
                val areaName = newArea.javaClass.getMethod("getName").invoke(newArea) as? String
                playerCurrentArea[player.name] = areaName
            }
        } catch (_: Exception) {}
    }

    // ==================== ArcartX 注册（全反射，避免 Record 编译问题） ====================

    private fun registerUI() {
        try {
            val hudFile = java.io.File(MayAdyeshachViewControl.dataFolder, "Hud.yml")
            if (!hudFile.exists()) return

            val registryClass = Class.forName("priv.seventeen.artist.arcartx.api.ArcartXAPI")
            val registry = registryClass.getMethod("getUIRegistry").invoke(null)
            val ui = registry.javaClass.getMethod("register", String::class.java, java.io.File::class.java)
                .invoke(registry, "InteractEntityHud", hudFile)

            // 动态代理 UICallBack（避免编译时引用 CallData → Record）
            val callBackClass = Class.forName("priv.seventeen.artist.arcartx.util.collections.UICallBack")
            val callBackProxy = java.lang.reflect.Proxy.newProxyInstance(
                callBackClass.classLoader,
                arrayOf(callBackClass)
            ) { _, method, args ->
                if (method.name == "call" && args != null && args.isNotEmpty()) {
                    val callData = args[0]
                    val clz = callData.javaClass
                    val player = clz.getMethod("player").invoke(callData) as Player
                    val identifier = clz.getMethod("identifier").invoke(callData) as String
                    @Suppress("UNCHECKED_CAST")
                    val data = clz.getMethod("data").invoke(callData) as List<String>
                    if (data.isNotEmpty() && identifier.equals("InteractEntityHud", ignoreCase = true)) {
                        if (ConfigManager.interactDebug) {
                            info("§7[MAVC-Debug] 收包 $identifier | $data")
                        }
                        if (data[0] == "select" && data.size == 3) {
                            handleSelect(player, data[1], data[2])
                        }
                    }
                }
                null
            }

            val callBackTypeClass = Class.forName("priv.seventeen.artist.arcartx.core.ui.adapter.CallBackType")
            val packetType = callBackTypeClass.getField("PACKET").get(null)
            ui!!.javaClass.getMethod("registerCallBack", callBackTypeClass, callBackClass)
                .invoke(ui, packetType, callBackProxy)

            // 缓存方法引用，避免每次发包都反射查找
            cachedSendMethod = ui.javaClass.getMethod("sendPacket", Player::class.java, String::class.java, Any::class.java)
            cachedOpenMethod = ui.javaClass.getMethod("open", Player::class.java)
            cachedCloseMethod = ui.javaClass.getMethod("close", Player::class.java)
            arcartxUI = ui
            info("§a[MAVC] ArcartX HUD 已注册")
        } catch (e: Exception) {
            info("§c[MAVC] ArcartX HUD 注册失败: ${e.message}")
        }
    }

    // ==================== 交互处理 ====================

    /** 处理客户端按 F 选中实体的回调，模拟右键交互 */
    private fun handleSelect(player: Player, type: String, entityId: String) {
        val cache = playerEntityMap[player.name] ?: return
        val entry = cache.entries.find { it.type == type && it.id == entityId } ?: return

        when (type) {
            "bukkit" -> {
                val entity = entry.bukkitEntity ?: return
                val evt = PlayerInteractEntityEvent(player, entity)
                Bukkit.getPluginManager().callEvent(evt)
                info("§e[MAVC] 互动 | ${player.name} -> ${entity.customName ?: entity.name} | bukkit")
            }
            "ady" -> {
                val entity = entry.adyEntity ?: return
                try {
                    val evt = ink.ptms.adyeshach.core.event.AdyeshachEntityInteractEvent(
                        entity, player, true, Vector(0, 0, 0)
                    )
                    evt.javaClass.superclass?.getMethod("call")?.invoke(evt)
                } catch (_: Exception) {}
                info("§e[MAVC] 互动 | ${player.name} -> ${entity.getCustomName()} | ady")
            }
        }
    }

    // ==================== 扫描逻辑 ====================

    private var scanCounter = 0L

    private fun scanAll() {
        scanCounter++
        val distSq = ConfigManager.interactDistance * ConfigManager.interactDistance
        val matchAdySet = ConfigManager.interactMatchAdyIds.toHashSet()
        val matchNameSet = ConfigManager.interactMatchNames.toHashSet()
        val allowedWorlds = ConfigManager.interactAllowedWorlds.toHashSet()
        val allowedAreas = ConfigManager.interactAllowedAreas.toHashSet()
        val debug = ConfigManager.interactDebug

        for (online in Bukkit.getOnlinePlayers()) {
            val player = online.player ?: continue
            val loc = player.location

            // 世界限制：配置了白名单且当前世界不在其中，跳过并清空缓存
            if (allowedWorlds.isNotEmpty() && player.world.name !in allowedWorlds) {
                playerEntityMap[player.name] = PlayerCache(emptyList())
                continue
            }

            // 区域限制：配置了白名单且玩家不在允许的区域内，跳过并清空缓存
            if (allowedAreas.isNotEmpty()) {
                val currentArea = playerCurrentArea[player.name]
                if (currentArea == null || currentArea !in allowedAreas) {
                    playerEntityMap[player.name] = PlayerCache(emptyList())
                    continue
                }
            }

            // Bukkit 实体：过滤
            val bukkitEntries = player.getNearbyEntities(8.0, 4.0, 8.0)
                .filter { e ->
                    e.type !in EXCLUDED_TYPES
                            && loc.distanceSquared(e.location) <= distSq
                            && (matchNameSet.isEmpty() || (e.customName in matchNameSet) || (e.name in matchNameSet))
                }
                .map { e ->
                    val rawName = e.customName ?: e.name
                    val cleanName = rawName.replace(Regex("§[0-9a-fk-or]", RegexOption.IGNORE_CASE), "")
                    EntityEntry(
                        name = cleanName.ifBlank { e.name },
                        type = "bukkit",
                        id = e.uniqueId.toString(),
                        distSq = loc.distanceSquared(e.location),
                        bukkitEntity = e
                    )
                }

            // Adyeshach NPC：过滤
            val allPersistent: List<EntityInstance> = try {
                Adyeshach.api().getPublicEntityManager(ManagerType.PERSISTENT).getEntities()
            } catch (_: Exception) { emptyList() }

            val adyEntries = allPersistent
                .filter { e ->
                    val base = e as ink.ptms.adyeshach.core.entity.EntityBase
                    val eLoc = org.bukkit.Location(player.world, base.x, base.y, base.z)
                    val customName = try { e.getCustomName() } catch (_: Exception) { null }
                    loc.distanceSquared(eLoc) <= distSq
                            && (matchAdySet.isEmpty() || e.id in matchAdySet || customName in matchAdySet)
                            && isNpcVisibleToPlayer(e.id, player.name)
                }
                .map { e ->
                    val base = e as ink.ptms.adyeshach.core.entity.EntityBase
                    val eLoc = org.bukkit.Location(player.world, base.x, base.y, base.z)
                    EntityEntry(
                        name = e.id,
                        type = "ady",
                        id = e.uniqueId,
                        distSq = loc.distanceSquared(eLoc),
                        adyEntity = e
                    )
                }

            // 合并并按距离排序（最近的在前）
            val merged = (bukkitEntries + adyEntries).sortedBy { it.distSq }

            // debug: 每 200 次扫描打印一次详细信息
            if (debug && scanCounter % 200 == 1L) {
                val nearby = allPersistent.filter {
                    val base = it as ink.ptms.adyeshach.core.entity.EntityBase
                    it.world.name == player.world.name
                            && loc.distanceSquared(org.bukkit.Location(player.world, base.x, base.y, base.z)) <= 100 * 100
                }
                info("§7[MAVC-Debug] PERSISTENT 总数=${allPersistent.size} | 100格内=${nearby.size} | matchSet=$matchAdySet")
                val dbCache = NpcVisibilityManager.getVisibilityForPlayer(player.name)
                info("§7[MAVC-Debug] DB缓存 ${player.name}: $dbCache")
                nearby.take(10).forEach { e ->
                    val base = e as ink.ptms.adyeshach.core.entity.EntityBase
                    val dist = loc.distance(org.bukkit.Location(player.world, base.x, base.y, base.z))
                    val isAutoHide = NpcVisibilityManager.isAutoHideNpc(e.id)
                    val dbVal = NpcVisibilityManager.getVisibility(e.id, player.name)
                    info("§7  id=${e.id} | name=${e.getCustomName()} | dist=%.1f | autoHide=$isAutoHide | db=$dbVal | npcVisible=${isNpcVisibleToPlayer(e.id, player.name)} | idMatch=${e.id in matchAdySet}".format(dist))
                }
                info("§7[MAVC-Debug] ${player.name} | merged=${merged.size} (bukkit=${bukkitEntries.size}, ady=${adyEntries.size})")
            }

            playerEntityMap[player.name] = PlayerCache(merged)
        }

        // 清理离线玩家
        val onlineNames = Bukkit.getOnlinePlayers().mapTo(HashSet()) { it.name }
        playerEntityMap.keys.removeAll { it !in onlineNames }
        playerCurrentArea.keys.removeAll { it !in onlineNames }
        playerHudOpen.keys.removeAll { it !in onlineNames }
        playerLastSignature.keys.removeAll { it !in onlineNames }
    }

    // ==================== 发包逻辑 ====================

    /** 最大显示条目数 */
    private const val MAX_ITEMS = 8

    private fun sendPackets() {
        val ui = arcartxUI ?: return
        val sendMethod = cachedSendMethod ?: return
        val openMethod = cachedOpenMethod ?: return
        val closeMethod = cachedCloseMethod ?: return

        for (online in Bukkit.getOnlinePlayers()) {
            val player = online.player ?: continue
            val cache = playerEntityMap[player.name]
            val entries = cache?.entries ?: emptyList()
            val wasOpen = playerHudOpen[player.name] == true

            // 没有实体 → 关闭 HUD
            if (entries.isEmpty()) {
                if (wasOpen) {
                    try { closeMethod.invoke(ui, player) } catch (_: Exception) {}
                    playerHudOpen[player.name] = false
                    playerLastSignature.remove(player.name)
                }
                continue
            }

            // 构建实体列表数据（已按距离排序）
            val items = ArrayList<Map<String, Any>>(MAX_ITEMS)
            val sigBuilder = StringBuilder()

            for (entry in entries) {
                if (items.size >= MAX_ITEMS) break
                items.add(mapOf("name" to entry.name, "type" to entry.type, "id" to entry.id))
                sigBuilder.append(entry.id).append(';')
            }

            val signature = sigBuilder.toString()
            val lastSig = playerLastSignature[player.name]

            // 签名相同 → 实体列表没变，跳过发包
            if (wasOpen && signature == lastSig) continue

            // 有实体但 HUD 没开 → 先打开
            if (!wasOpen) {
                try { openMethod.invoke(ui, player) } catch (_: Exception) {}
                playerHudOpen[player.name] = true
            }

            // 发送数据
            try { sendMethod.invoke(ui, player, "InteractHudPacket", items) } catch (_: Exception) {}
            playerLastSignature[player.name] = signature
        }
    }

    /** 关闭所有在线玩家的 HUD */
    private fun closeAllHuds() {
        val ui = arcartxUI ?: return
        val closeMethod = cachedCloseMethod ?: return
        for ((name, open) in playerHudOpen) {
            if (!open) continue
            val player = Bukkit.getPlayerExact(name) ?: continue
            try { closeMethod.invoke(ui, player) } catch (_: Exception) {}
        }
    }

    // ==================== 工具方法 ====================

    /**
     * 判断 NPC 对玩家是否可见（用于交互 HUD 过滤）
     * - auto-hide 的 NPC：需要数据库里明确 visible=true 才显示
     * - 普通 NPC：没有被明确设为 false 就显示
     */
    private fun isNpcVisibleToPlayer(npcId: String, playerName: String): Boolean {
        val isAutoHide = NpcVisibilityManager.isAutoHideNpc(npcId)
        val dbVisible = NpcVisibilityManager.getVisibility(npcId, playerName)
        return if (isAutoHide) dbVisible == true else dbVisible != false
    }

    private val EXCLUDED_TYPES = setOf(
        EntityType.PLAYER, EntityType.DROPPED_ITEM, EntityType.ITEM_FRAME, EntityType.GLOW_ITEM_FRAME,
        EntityType.ARROW, EntityType.SPECTRAL_ARROW, EntityType.TRIDENT, EntityType.EGG,
        EntityType.ENDER_PEARL, EntityType.SNOWBALL, EntityType.SPLASH_POTION, EntityType.THROWN_EXP_BOTTLE,
        EntityType.FIREBALL, EntityType.SMALL_FIREBALL, EntityType.DRAGON_FIREBALL, EntityType.WITHER_SKULL,
        EntityType.SHULKER_BULLET, EntityType.LLAMA_SPIT, EntityType.PRIMED_TNT, EntityType.MINECART_TNT,
        EntityType.ENDER_CRYSTAL, EntityType.BOAT, EntityType.CHEST_BOAT, EntityType.MINECART,
        EntityType.MINECART_CHEST, EntityType.MINECART_FURNACE, EntityType.MINECART_HOPPER,
        EntityType.MINECART_COMMAND, EntityType.ARMOR_STAND, EntityType.TEXT_DISPLAY, EntityType.BLOCK_DISPLAY,
        EntityType.ITEM_DISPLAY, EntityType.INTERACTION, EntityType.PAINTING, EntityType.LEASH_HITCH,
        EntityType.FALLING_BLOCK, EntityType.LIGHTNING, EntityType.EVOKER_FANGS, EntityType.ENDER_SIGNAL,
        EntityType.AREA_EFFECT_CLOUD, EntityType.MARKER
    )
}
