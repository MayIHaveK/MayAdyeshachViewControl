package com.mayihavek.mayadyeshachviewcontrol.storage

/**
 * NPC 可见性持久化仓储抽象层。
 * 屏蔽底层数据库差异，便于兼容 SQLite / MySQL 等多种实现。
 *
 * @author MayIHaveK
 * @since 2026/3/15
 */
interface VisibilityRepository {

    /**
     * 初始化连接/表结构。
     * 插件启用时调用。
     */
    fun init()

    /**
     * 关闭连接，释放资源。
     * 插件禁用时调用。
     */
    fun close()

    /**
     * 获取玩家对某 NPC 的可见性设置。
     * @return true=允许看见, false=禁止看见, null=未设置（使用默认）
     */
    fun getVisibility(npcId: String, playerName: String): Boolean?

    /**
     * 设置玩家对某 NPC 的可见性并持久化。
     */
    fun setVisibility(npcId: String, playerName: String, visible: Boolean)

    /**
     * 获取某 NPC 下所有玩家的可见性设置。
     */
    fun getPlayersForNpc(npcId: String): Map<String, Boolean>

    /**
     * 获取所有 NPC 的可见性数据（用于玩家加入时恢复）。
     * 返回 Map<npcId, Map<playerName, visible>>
     */
    fun getAllVisibility(): Map<String, Map<String, Boolean>>
}
