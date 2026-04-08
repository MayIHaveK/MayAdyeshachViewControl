package com.mayihavek.mayadyeshachviewcontrol.listener

import com.mayihavek.mayadyeshachviewcontrol.manager.AdyService
import priv.seventeen.artist.arcartx.event.client.ClientEntityJoinEvent
import taboolib.common.platform.event.SubscribeEvent

/**
 * 私人模型同步监听器。
 * 当玩家客户端重新看到某个 Adyeshach NPC 时，补发该玩家对应的私人模型。
 */
object PrivateModelSyncListener {

    @SubscribeEvent
    fun onClientEntityJoin(event: ClientEntityJoinEvent) {
        val player = event.player
        val entity = AdyService.getEntityByUniqueIdForPlayer(event.entityUUID, player) ?: return
        AdyService.applyPrivateModelIfPresent(player, entity)
    }
}
