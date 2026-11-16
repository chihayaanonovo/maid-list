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
        registrar.playToServer(MaidSitTogglePayload.TYPE, MaidSitTogglePayload.STREAM_CODEC,
                ModNetwork::handleSitToggle);
        registrar.playToServer(MaidStorePayload.TYPE, MaidStorePayload.STREAM_CODEC,
                ModNetwork::handleStore);
        registrar.playToServer(MaidReleasePayload.TYPE, MaidReleasePayload.STREAM_CODEC,
                ModNetwork::handleRelease);
        registrar.playToServer(MaidReleaseAllPayload.TYPE, MaidReleaseAllPayload.STREAM_CODEC,
                ModNetwork::handleReleaseAll);
        registrar.playToServer(MaidStorageRequestPayload.TYPE, MaidStorageRequestPayload.STREAM_CODEC,
                ModNetwork::handleStorageRequest);
        registrar.playToServer(MaidRestorePayload.TYPE, MaidRestorePayload.STREAM_CODEC,
                ModNetwork::handleRestore);
        registrar.playToServer(MaidAttackRequestPayload.TYPE, MaidAttackRequestPayload.STREAM_CODEC,
                ModNetwork::handleAttackRequest);
        registrar.playToServer(MaidAttackSetPayload.TYPE, MaidAttackSetPayload.STREAM_CODEC,
                ModNetwork::handleAttackSet);
        registrar.playToServer(MaidAttackReplacePayload.TYPE, MaidAttackReplacePayload.STREAM_CODEC,
                ModNetwork::handleAttackReplace);
        registrar.playToServer(MaidAttackGlobalPayload.TYPE, MaidAttackGlobalPayload.STREAM_CODEC,
                ModNetwork::handleAttackGlobal);

        // 服务端 -> 客户端
        registrar.playToClient(MaidListPayload.TYPE, MaidListPayload.STREAM_CODEC,
                ModNetwork::handleList);
        registrar.playToClient(MaidStoragePayload.TYPE, MaidStoragePayload.STREAM_CODEC,
                ModNetwork::handleStorage);
        registrar.playToClient(MaidAttackListPayload.TYPE, MaidAttackListPayload.STREAM_CODEC,
                ModNetwork::handleAttackList);
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

    /** 服务端：切换女仆坐下 / 站立。 */
    private static void handleSitToggle(MaidSitTogglePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                MaidCollector.toggleSitting(serverPlayer, payload.maidId());
            }
        });
    }

    /** 服务端：收纳一只女仆。 */
    private static void handleStore(MaidStorePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                com.fairymoon3.maidlist.server.MaidStorage.store(serverPlayer, payload.maidId());
            }
        });
    }

    /** 服务端：释放一只女仆。 */
    private static void handleRelease(MaidReleasePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                com.fairymoon3.maidlist.server.MaidStorage.release(serverPlayer, payload.maidId());
            }
        });
    }

    /** 服务端：全部释放。 */
    private static void handleReleaseAll(MaidReleaseAllPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                com.fairymoon3.maidlist.server.MaidStorage.releaseAll(serverPlayer);
            }
        });
    }

    /** 服务端：回传收纳空间列表。 */
    private static void handleStorageRequest(MaidStorageRequestPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                com.fairymoon3.maidlist.server.MaidStorage.sendStorage(serverPlayer);
            }
        });
    }

    /** 服务端：回传某只女仆的仇恨名单。 */
    private static void handleAttackRequest(MaidAttackRequestPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                com.fairymoon3.maidlist.server.MaidAttackList.send(serverPlayer, payload.maidId());
            }
        });
    }

    /** 服务端：修改名单里的单个实体状态。 */
    private static void handleAttackSet(MaidAttackSetPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                com.fairymoon3.maidlist.server.MaidAttackList.setOne(
                        serverPlayer, payload.maidId(), payload.entityType(), payload.mode());
            }
        });
    }

    /** 服务端：整体替换名单（重置 / 粘贴）。 */
    private static void handleAttackReplace(MaidAttackReplacePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                com.fairymoon3.maidlist.server.MaidAttackList.replace(
                        serverPlayer, payload.maidId(), payload.entries());
            }
        });
    }

    /** 服务端：批量同步 / 重置所有女仆。 */
    private static void handleAttackGlobal(MaidAttackGlobalPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                if (payload.action() == MaidAttackGlobalPayload.ACTION_RESET_ALL) {
                    com.fairymoon3.maidlist.server.MaidAttackList.resetAll(serverPlayer, payload.maidId());
                } else {
                    com.fairymoon3.maidlist.server.MaidAttackList.syncToAll(
                            serverPlayer, payload.maidId(), payload.entries());
                }
            }
        });
    }

    /** 客户端：收到仇恨名单。 */
    private static void handleAttackList(MaidAttackListPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (FMLEnvironment.dist.isClient()) {
                com.fairymoon3.maidlist.client.ClientMaidAttackData.handle(payload);
            }
        });
    }

    /** 服务端：用本地备份恢复一只女仆（会校验来源）。 */
    private static void handleRestore(MaidRestorePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                com.fairymoon3.maidlist.server.MaidStorage.restore(serverPlayer, payload.name(), payload.tag());
            }
        });
    }

    /** 客户端：刷新收纳空间界面。 */
    private static void handleStorage(MaidStoragePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (FMLEnvironment.dist.isClient()) {
                com.fairymoon3.maidlist.client.ClientMaidStorage.handle(payload);
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
