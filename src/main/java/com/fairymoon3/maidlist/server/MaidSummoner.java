package com.fairymoon3.maidlist.server;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.world.data.MaidInfo;
import com.github.tartaricacid.touhoulittlemaid.world.data.MaidWorldData;
import com.fairymoon3.maidlist.MaidList;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

/**
 * 传送服务。
 *
 * <p>三种方向：</p>
 * <ul>
 *   <li>召唤：女仆 -> 玩家（音效：仆人铃 {@code BELL_BLOCK}）</li>
 *   <li>前往：玩家 -> 女仆（音效：末影珍珠 {@code PLAYER_TELEPORT}）</li>
 *   <li>全部召唤：所有能传送的女仆一起过来</li>
 * </ul>
 *
 * <p>每一次操作都会在聊天框里留下记录（成功与失败都写）。</p>
 */
public final class MaidSummoner {

    private static final int MAX_WAIT_TICKS = 24;
    private static final int RETRY_STEP = 2;
    private static final int TICKET_RADIUS = 1;

    private MaidSummoner() {
    }

    // ------------------------------------------------------------------ 召唤：女仆 -> 玩家

    public static void summon(ServerPlayer player, UUID maidId) {
        if (maidId == null || throttled(player, "summon")) {
            return;
        }
        EntityMaid live = findMaid(player.serverLevel(), maidId, player);
        if (live != null) {
            summonOne(player, live);
            return;
        }
        MaidInfo info = findInfo(player, maidId);
        if (info == null) {
            chat(player, "fairymoon3_maid_list.tp.chat.not_found", unknownName());
            return;
        }
        ServerLevel target = resolveLevel(player.getServer(), info.getDimension());
        if (target == null) {
            chat(player, "fairymoon3_maid_list.tp.chat.not_found", nameOf(info));
            return;
        }
        ChunkPos pos = chunkOf(info);
        target.getChunkSource().addRegionTicket(
                net.minecraft.server.level.TicketType.FORCED, pos, TICKET_RADIUS, pos);
        waitForMaid(player, target, pos, maidId, info, 0, EntityMaid::isSleeping, (level, maid) -> {
            if (maid.isSleeping()) {
                chat(player, "fairymoon3_maid_list.tp.chat.sleeping", nameOf(maid));
                return;
            }
            if (maid.level() == player.level()) {
                teleportInLevel(player, maid);
            } else {
                pullAcrossDimension(player, maid);
            }
            player.playNotifySound(SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 1.0F, 1.0F);
            chat(player, "fairymoon3_maid_list.tp.chat.success", nameOf(maid));
            MaidCollector.sendPanel(player);
        });
    }

    /** 当前维度已加载时的直接召唤。 */
    private static void summonOne(ServerPlayer player, EntityMaid maid) {
        if (maid.isSleeping()) {
            chat(player, "fairymoon3_maid_list.tp.chat.sleeping", nameOf(maid));
            return;
        }
        teleportInLevel(player, maid);
        player.playNotifySound(SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 1.0F, 1.0F);
        chat(player, "fairymoon3_maid_list.tp.chat.success", nameOf(maid));
        MaidCollector.sendPanel(player);
    }

    // ------------------------------------------------------------------ 全部召唤

    public static void summonAll(ServerPlayer player) {
        if (throttled(player, "all")) {
            return;
        }
        ServerLevel level = player.serverLevel();
        List<? extends EntityMaid> maids = level.getEntities(EntityMaid.TYPE,
                maid -> maid.isOwnedBy(player) && maid.isAlive() && !maid.isSleeping());

        int summoned = 0;
        for (EntityMaid maid : maids) {
            teleportInLevel(player, maid);
            summoned++;
        }
        if (summoned == 0 && countOwned(player, level) == 0) {
            chat(player, "fairymoon3_maid_list.tp.chat.all_none");
            return;
        }

        int total = countOwned(player, level);
        int failed = Math.max(0, total - summoned);
        player.playNotifySound(SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 1.0F, 1.0F);
        chat(player, "fairymoon3_maid_list.tp.chat.all_done", summoned, failed);
        MaidCollector.sendPanel(player);
    }

    // ------------------------------------------------------------------ 前往：玩家 -> 女仆

    public static void teleportPlayerTo(ServerPlayer player, UUID maidId) {
        if (maidId == null || throttled(player, "goto")) {
            return;
        }
        EntityMaid live = findMaid(player.serverLevel(), maidId, player);
        if (live != null) {
            goTo(player, live);
            return;
        }
        MaidInfo info = findInfo(player, maidId);
        if (info == null) {
            chat(player, "fairymoon3_maid_list.tp.chat.to_maid_fail", unknownName());
            return;
        }
        ServerLevel target = resolveLevel(player.getServer(), info.getDimension());
        if (target == null) {
            chat(player, "fairymoon3_maid_list.tp.chat.to_maid_fail", nameOf(info));
            return;
        }
        ChunkPos pos = chunkOf(info);
        target.getChunkSource().addRegionTicket(
                net.minecraft.server.level.TicketType.FORCED, pos, TICKET_RADIUS, pos);
        waitForMaid(player, target, pos, maidId, info, 0, maid -> false, (level, maid) -> goTo(player, maid));
    }

    private static void goTo(ServerPlayer player, EntityMaid maid) {
        try {
            if (!(maid.level() instanceof ServerLevel maidLevel)) {
                chat(player, "fairymoon3_maid_list.tp.chat.to_maid_fail", nameOf(maid));
                return;
            }
            player.teleportTo(maidLevel, maid.getX(), maid.getY(), maid.getZ(),
                    player.getYRot(), player.getXRot());
            player.playNotifySound(SoundEvents.PLAYER_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);
            chat(player, "fairymoon3_maid_list.tp.chat.to_maid", nameOf(maid));
        } catch (Exception e) {
            MaidList.LOGGER.warn("[女仆名单] 传送到女仆身边失败", e);
            chat(player, "fairymoon3_maid_list.tp.chat.to_maid_fail", nameOf(maid));
        }
    }

    // ------------------------------------------------------------------ 等待区块装载

    @FunctionalInterface
    private interface MaidConsumer {
        void accept(ServerLevel level, EntityMaid maid);
    }

    @FunctionalInterface
    private interface MaidPredicate {
        boolean test(EntityMaid maid);
    }

    private static void waitForMaid(ServerPlayer player, ServerLevel target, ChunkPos pos, UUID maidId,
                                    MaidInfo info, int elapsed, MaidPredicate guard, MaidConsumer action) {
        MinecraftServer server = player.getServer();
        if (server == null || !player.isAlive() || player.hasDisconnected()) {
            releaseTicket(target, pos);
            return;
        }
        EntityMaid maid = findMaid(target, maidId, player);
        if (maid != null) {
            releaseTicket(target, pos);
            try {
                action.accept(target, maid);
            } catch (Exception e) {
                MaidList.LOGGER.warn("[女仆名单] 传送动作执行失败", e);
            }
            return;
        }
        if (elapsed >= MAX_WAIT_TICKS) {
            releaseTicket(target, pos);
            chat(player, "fairymoon3_maid_list.tp.chat.not_found", nameOf(info));
            return;
        }
        int delay = server.getTickCount() + RETRY_STEP;
        server.tell(new TickTask(delay,
                () -> waitForMaid(player, target, pos, maidId, info, elapsed + RETRY_STEP, guard, action)));
    }

    private static void releaseTicket(ServerLevel level, ChunkPos pos) {
        try {
            level.getChunkSource().removeRegionTicket(
                    net.minecraft.server.level.TicketType.FORCED, pos, TICKET_RADIUS, pos);
        } catch (Exception e) {
            MaidList.LOGGER.debug("[女仆名单] 释放区块 ticket 失败", e);
        }
    }

    // ------------------------------------------------------------------ 传送动作

    private static void teleportInLevel(ServerPlayer player, EntityMaid maid) {
        maid.setHomeModeEnable(false);
        if (maid.isPassenger()) {
            maid.stopRiding();
        }
        maid.addEffect(new MobEffectInstance(MobEffects.GLOWING, 200, 1, true, false));
        maid.teleportTo(
                player.getX() + player.getRandom().nextInt(3) - 1,
                player.getY(),
                player.getZ() + player.getRandom().nextInt(3) - 1
        );
    }

    private static void pullAcrossDimension(ServerPlayer player, EntityMaid maid) {
        try {
            ServerLevel destination = player.serverLevel();
            var transition = new DimensionTransition(destination, player.position(), Vec3.ZERO,
                    player.getYRot(), player.getXRot(), DimensionTransition.DO_NOTHING);
            var moved = maid.changeDimension(transition);
            EntityMaid result = moved instanceof EntityMaid m ? m : maid;
            result.setHomeModeEnable(false);
            result.addEffect(new MobEffectInstance(MobEffects.GLOWING, 200, 1, true, false));
        } catch (Exception e) {
            MaidList.LOGGER.warn("[女仆名单] 跨维度召唤失败", e);
        }
    }

    // ------------------------------------------------------------------ 查询与工具

    private static EntityMaid findMaid(ServerLevel level, UUID maidId, ServerPlayer owner) {
        List<? extends EntityMaid> found = level.getEntities(EntityMaid.TYPE,
                maid -> maidId.equals(maid.getUUID()) && maid.isOwnedBy(owner));
        return found.isEmpty() ? null : found.get(0);
    }

    private static MaidInfo findInfo(ServerPlayer player, UUID maidId) {
        try {
            MaidWorldData data = MaidWorldData.get(player.level());
            if (data == null) {
                return null;
            }
            List<MaidInfo> infos = data.getPlayerMaidInfos(player);
            if (infos == null) {
                return null;
            }
            for (MaidInfo info : infos) {
                if (info != null && maidId.equals(info.getEntityId())) {
                    return info;
                }
            }
        } catch (Exception e) {
            MaidList.LOGGER.debug("[女仆名单] 查询女仆索引失败", e);
        }
        return null;
    }

    /**
     * 统计该玩家名下全世界的女仆数（索引 ∪ 当前维度已加载实体，按 UUID 去重）。
     *
     * <p>只查索引会漏掉「索引里没有但实体确实存在」的女仆（例如刚被召唤/刚加载），
     * 那样睡觉中的女仆就会被算漏，导致「无法传送」的数量偏小。</p>
     */
    private static int countOwned(ServerPlayer player, ServerLevel level) {
        java.util.Set<UUID> ids = new java.util.HashSet<>();
        try {
            MaidWorldData data = MaidWorldData.get(player.level());
            if (data != null) {
                List<MaidInfo> infos = data.getPlayerMaidInfos(player);
                if (infos != null) {
                    for (MaidInfo info : infos) {
                        if (info != null && info.getEntityId() != null) {
                            ids.add(info.getEntityId());
                        }
                    }
                }
            }
        } catch (Exception e) {
            MaidList.LOGGER.debug("[女仆名单] 统计女仆索引失败", e);
        }
        try {
            List<ServerLevel> levels = new java.util.ArrayList<>();
            MinecraftServer server = player.getServer();
            if (server != null) {
                for (ServerLevel lvl : server.getAllLevels()) {
                    levels.add(lvl);
                }
            }
            if (levels.isEmpty()) {
                levels.add(level);
            }
            for (ServerLevel lvl : levels) {
                for (EntityMaid maid : lvl.getEntities(EntityMaid.TYPE, m -> m.isOwnedBy(player))) {
                    ids.add(maid.getUUID());
                }
            }
        } catch (Exception e) {
            MaidList.LOGGER.debug("[女仆名单] 统计已加载女仆失败", e);
        }
        return ids.size();
    }

    private static ServerLevel resolveLevel(MinecraftServer server, String dimension) {
        if (server == null) {
            return null;
        }
        try {
            ResourceLocation id = ResourceLocation.parse(dimension);
            return server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
        } catch (Exception e) {
            return null;
        }
    }

    private static ChunkPos chunkOf(MaidInfo info) {
        var pos = info.getChunkPos();
        int x = Math.abs(pos.getX()) > 32 ? pos.getX() >> 4 : pos.getX();
        int z = Math.abs(pos.getZ()) > 32 ? pos.getZ() >> 4 : pos.getZ();
        return new ChunkPos(x, z);
    }

    private static String nameOf(EntityMaid maid) {
        try {
            return maid.getName().getString();
        } catch (Exception e) {
            return "?";
        }
    }

    private static String nameOf(MaidInfo info) {
        if (info == null || info.getName() == null) {
            return unknownName();
        }
        try {
            return info.getName().getString();
        } catch (Exception e) {
            return unknownName();
        }
    }

    private static String unknownName() {
        return Component.translatable("fairymoon3_maid_list.unknown").getString();
    }

    /** 所有传送反馈都走聊天框（左下角），按要求成功/失败都写。 */
    private static void chat(ServerPlayer player, String key, Object... args) {
        player.sendSystemMessage(Component.translatable(key, args));
    }

    /** 服务端侧的 2 秒冷却（40 tick），防止客户端被绕过时刷命令。 */
    private static boolean throttled(ServerPlayer player, String kind) {
        try {
            var data = player.getPersistentData();
            String tag = "MaidListCd_" + kind;
            long now = player.serverLevel().getGameTime();
            long last = data.getLong(tag);
            if (last != 0L && now - last < 40L) {
                return true;
            }
            data.putLong(tag, now);
            return false;
        } catch (Exception e) {
            return false;
        }
    }
}
