package com.mayihavek.mayadyeshachviewcontrol.storage

import org.jetbrains.exposed.sql.Index
import org.jetbrains.exposed.sql.Table

/**
 * Exposed 表结构：NPC 可见性。
 * SQLite / MySQL 共用同一套 DSL，仅连接与方言由各自 Repository 提供。
 *
 * @author MayIHaveK
 * @since 2026/3/15
 */
object NpcVisibilityTable : Table("npc_visibility") {

    val npcId = varchar("npc_id", 256)
    val playerName = varchar("player_name", 64).index("idx_player_name")
    val visible = bool("visible")

    override val primaryKey = PrimaryKey(npcId, playerName)
}
