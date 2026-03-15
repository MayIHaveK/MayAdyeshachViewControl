package com.mayihavek.mayadyeshachviewcontrol.storage

import org.jetbrains.exposed.sql.Database

/**
 * MySQL 实现的 NPC 可见性仓储（预留）。
 * 表结构使用 [NpcVisibilityTable]，与 SQLite 一致。
 * 后续实现步骤：
 * 1. 在 build.gradle.kts 中取消注释 mysql-connector-j 依赖
 * 2. 在 init() 中：Database.connect(
 *      url = "jdbc:mysql://$host:$port/$database?useSSL=false&serverTimezone=UTC",
 *      driver = "com.mysql.cj.jdbc.Driver",
 *      user = user,
 *      password = password
 *    )
 * 3. 使用与 [SqliteVisibilityRepository] 相同的 Exposed transaction + NpcVisibilityTable 读写
 *
 * @author MayIHaveK
 * @since 2026/3/15
 */
class MySqlVisibilityRepository(
    private val host: String,
    private val port: Int,
    private val database: String,
    private val user: String,
    private val password: String
) : VisibilityRepository {

    private var db: Database? = null

    override fun init() {
        // TODO: 启用 MySQL 时取消注释并配置
        // db = Database.connect(
        //     url = "jdbc:mysql://$host:$port/$database?useSSL=false&serverTimezone=UTC",
        //     driver = "com.mysql.cj.jdbc.Driver",
        //     user = user,
        //     password = password
        // )
        // transaction(db!!) { SchemaUtils.create(NpcVisibilityTable) }
        throw UnsupportedOperationException("MySQL 暂未实现，请使用 sqlite 存储。表结构见 NpcVisibilityTable。")
    }

    override fun close() {
        // db 不持有 Connection，Exposed 默认不提供单连接关闭；若需关闭可自行持有 DataSource/Connection
        db = null
    }

    override fun getVisibility(npcId: String, playerName: String): Boolean? {
        throw UnsupportedOperationException("MySQL 暂未实现")
    }

    override fun setVisibility(npcId: String, playerName: String, visible: Boolean) {
        throw UnsupportedOperationException("MySQL 暂未实现")
    }

    override fun getPlayersForNpc(npcId: String): Map<String, Boolean> {
        throw UnsupportedOperationException("MySQL 暂未实现")
    }

    override fun getAllVisibility(): Map<String, Map<String, Boolean>> {
        throw UnsupportedOperationException("MySQL 暂未实现")
    }
}
