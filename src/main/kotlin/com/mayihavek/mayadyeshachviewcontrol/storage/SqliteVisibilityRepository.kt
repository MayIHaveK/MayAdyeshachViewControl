package com.mayihavek.mayadyeshachviewcontrol.storage

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import taboolib.common.platform.function.info
import java.io.File

/**
 * 基于 Exposed + SQLite 的 NPC 可见性仓储。
 *
 * @author MayIHaveK
 * @since 2026/3/15
 */
class SqliteVisibilityRepository(private val dataFolder: File) : VisibilityRepository {

    private var database: Database? = null

    override fun init() {
        try {
            dataFolder.mkdirs()
            val dbFile = File(dataFolder, "visibility.db")
            
            // 手动加载重定位后的 SQLite JDBC 驱动
            Class.forName("org.sqlite.JDBC")
            
            // 使用 Exposed 的标准连接方式
            database = Database.connect(
                url = "jdbc:sqlite:${dbFile.absolutePath}",
                driver = "org.sqlite.JDBC"
            )
            
            transaction(database!!) {
                SchemaUtils.create(NpcVisibilityTable, PrivateModelTable)
            }
            info("§a[MayAdyeshachViewControl] SQLite 可见性数据库已初始化 (Exposed)")
        } catch (e: Exception) {
            info("§c[MayAdyeshachViewControl] SQLite 初始化失败: ${e.message}")
            e.printStackTrace()
        }
    }

    override fun close() {
        try {
            database = null
        } catch (e: Exception) {
            info("§c[MayAdyeshachViewControl] SQLite 关闭失败: ${e.message}")
        }
    }

    private fun withDb(block: () -> Unit) {
        database?.let { db -> transaction(db) { block() } }
    }

    private fun <T> withDbResult(block: () -> T): T? {
        return database?.let { db -> transaction(db) { block() } }
    }

    override fun getVisibility(npcId: String, playerName: String): Boolean? {
        return withDbResult {
            NpcVisibilityTable
                .selectAll()
                .where { (NpcVisibilityTable.npcId eq npcId) and (NpcVisibilityTable.playerName eq playerName) }
                .singleOrNull()
                ?.get(NpcVisibilityTable.visible)
        }
    }

    override fun setVisibility(npcId: String, playerName: String, visible: Boolean) {
        withDb {
            val exists = NpcVisibilityTable
                .selectAll()
                .where { (NpcVisibilityTable.npcId eq npcId) and (NpcVisibilityTable.playerName eq playerName) }
                .count() > 0
            if (exists) {
                NpcVisibilityTable.update(
                    { (NpcVisibilityTable.npcId eq npcId) and (NpcVisibilityTable.playerName eq playerName) }
                ) {
                    it[NpcVisibilityTable.visible] = visible
                }
            } else {
                NpcVisibilityTable.insert {
                    it[NpcVisibilityTable.npcId] = npcId
                    it[NpcVisibilityTable.playerName] = playerName
                    it[NpcVisibilityTable.visible] = visible
                }
            }
        }
        if (database == null) {
            info("§c[MayAdyeshachViewControl] 保存可见性失败: 数据库未连接")
        }
    }

    override fun getPlayersForNpc(npcId: String): Map<String, Boolean> {
        return withDbResult {
            NpcVisibilityTable
                .selectAll()
                .where { NpcVisibilityTable.npcId eq npcId }
                .associate { it[NpcVisibilityTable.playerName] to it[NpcVisibilityTable.visible] }
        } ?: emptyMap()
    }

    override fun getAllVisibility(): Map<String, Map<String, Boolean>> {
        return withDbResult {
            NpcVisibilityTable
                .selectAll()
                .groupBy { it[NpcVisibilityTable.npcId] }
                .mapValues { (_, rows) ->
                    rows.associate { it[NpcVisibilityTable.playerName] to it[NpcVisibilityTable.visible] }
                }
        } ?: emptyMap()
    }

    fun setPrivateModel(playerName: String, npcId: String, modelId: String, scale: Double) {
        withDb {
            val exists = PrivateModelTable
                .selectAll()
                .where { (PrivateModelTable.playerName eq playerName) and (PrivateModelTable.npcId eq npcId) }
                .count() > 0
            if (exists) {
                PrivateModelTable.update(
                    { (PrivateModelTable.playerName eq playerName) and (PrivateModelTable.npcId eq npcId) }
                ) {
                    it[PrivateModelTable.modelId] = modelId
                    it[PrivateModelTable.scale] = scale
                }
            } else {
                PrivateModelTable.insert {
                    it[PrivateModelTable.playerName] = playerName
                    it[PrivateModelTable.npcId] = npcId
                    it[PrivateModelTable.modelId] = modelId
                    it[PrivateModelTable.scale] = scale
                }
            }
        }
    }

    fun removePrivateModel(playerName: String, npcId: String) {
        withDb {
            PrivateModelTable.deleteWhere {
                (PrivateModelTable.playerName eq playerName) and (PrivateModelTable.npcId eq npcId)
            }
        }
    }

    fun removeAllPrivateModelsForNpc(npcId: String): Int {
        return withDbResult {
            PrivateModelTable.deleteWhere { PrivateModelTable.npcId eq npcId }
        } ?: 0
    }

    fun getPrivateModelsForPlayer(playerName: String): Map<String, Pair<String, Double>> {
        return withDbResult {
            PrivateModelTable
                .selectAll()
                .where { PrivateModelTable.playerName eq playerName }
                .associate { row ->
                    row[PrivateModelTable.npcId] to (row[PrivateModelTable.modelId] to row[PrivateModelTable.scale])
                }
        } ?: emptyMap()
    }

    fun getAllPrivateModels(): Map<String, Map<String, Pair<String, Double>>> {
        return withDbResult {
            PrivateModelTable
                .selectAll()
                .groupBy { it[PrivateModelTable.playerName] }
                .mapValues { (_, rows) ->
                    rows.associate { row ->
                        row[PrivateModelTable.npcId] to (row[PrivateModelTable.modelId] to row[PrivateModelTable.scale])
                    }
                }
        } ?: emptyMap()
    }
}
