package com.fairymoon3.maidlist.network;

import com.fairymoon3.maidlist.server.MaidCollector;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** 网络通道注册与两侧处理入口。 */
public final class ModNetwork {

    private static final String PROTOCOL_VERSION = "1";

    private ModNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);

        // 客户端 -> 服务端
        registrar.playToServer(MaidListRequestPayload.TYPE, MaidListRequestPayload.STREAM_CODEC,
                ModNetwork::handleRequest);
        registrar.playToServer(MaidTeleportPayload.TYPE, MaidTeleportPayload.STREAM_CODEC,
                ModNetwork::handleTeleport);
        registrar.playToServer(MaidOpenGuiPayload.TYPE, MaidOpenGuiPayload.STREAM_CODEC,
                ModNetwork::handleOpenGui);
        registrar.playToServer(MaidFoxdreamPayload.TYPE, MaidFoxdreamPayload.STREAM_CODEC,
                ModNetwork::handleFoxdream);
        registrar.playToServer(MaidTeleportToPayload.TYPE, MaidTeleportToPayload.STREAM_CODEC,
                ModNetwork::handleTeleportTo);
        registrar.playToServer(MaidSummonAllPayload.TYPE, MaidSummonAllPayload.STREAM_CODEC,
                ModNetwork::handleSummonAll);

        // 服务端 -> 客户端
        registrar.playToClient(MaidListPayload.TYPE, MaidListPayload.STREAM_CODEC,
                ModNetwork::handleList);
    }

    /** 服务端：收到请求后收集名册并回传。 */
    private static void handleRequest(MaidListRequestPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                MaidCollector.sendPanel(serverPlayer);
            }
        });
    }

    /** 服务端：召唤女仆（支持未加载区块与跨维度）。 */
    private static void handleTeleport(MaidTeleportPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                com.fairymoon3.maidlist.server.MaidSummoner.summon(serverPlayer, payload.maidId());
            }
        });
    }

    /** 服务端：打开女仆自己的界面（等同右键女仆）。 */
    private static void handleOpenGui(MaidOpenGuiPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                MaidCollector.openMaidGui(serverPlayer, payload.maidId());
            }
        });
    }

    /** 服务端：打开狐梦陪伴手册面板（软联动）。 */
    private static void handleFoxdream(MaidFoxdreamPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                MaidCollector.openFoxdream(serverPlayer, payload.maidId());
            }
        });
    }

    /** 服务端：把玩家自己传送到女仆身边。 */
    private static void handleTeleportTo(MaidTeleportToPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                com.fairymoon3.maidlist.server.MaidSummoner.teleportPlayerTo(serverPlayer, payload.maidId());
            }
        });
    }

    /** 服务端：召唤所有能传送的女仆。 */
    private static void handleSummonAll(MaidSummonAllPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                com.fairymoon3.maidlist.server.MaidSummoner.summonAll(serverPlayer);
            }
        });
    }

    /** 客户端：把名册交给界面逻辑，打开或刷新面板。 */
    private static void handleList(MaidListPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (FMLEnvironment.dist.isClient()) {
                com.fairymoon3.maidlist.client.ClientMaidPanel.handle(payload);
            }
        });
    }
}
