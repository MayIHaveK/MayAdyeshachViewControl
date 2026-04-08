package com.mayihavek.mayadyeshachviewcontrol.storage

import org.jetbrains.exposed.sql.Table

/**
 * Exposed 表结构：玩家私人 NPC 模型。
 */
object PrivateModelTable : Table("private_npc_model") {

    val playerName = varchar("player_name", 64)
    val npcId = varchar("npc_id", 256)
    val modelId = varchar("model_id", 256)
    val scale = double("scale")

    override val primaryKey = PrimaryKey(playerName, npcId)
}
