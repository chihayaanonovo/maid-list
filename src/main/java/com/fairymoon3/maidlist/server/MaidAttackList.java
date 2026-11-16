package com.fairymoon3.maidlist.server;

import com.github.tartaricacid.touhoulittlemaid.entity.data.inner.AttackListData;
import com.github.tartaricacid.touhoulittlemaid.entity.misc.MonsterType;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitTaskData;
import com.fairymoon3.maidlist.MaidList;
import com.fairymoon3.maidlist.network.MaidAttackListPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 女仆「仇恨名单」的服务端逻辑。
 *
 * <p>直接读写车万女仆自己的 {@code InitTaskData.ATTACK_LIST}（{@link AttackListData}），
 * 所以和本体「任务设置 → 攻击面板」是**同一份数据**：我们改的，本体面板立刻能看到；
 * 本体面板改的，我们这边也读得到。</p>
 */
public final class MaidAttackList {

    private MaidAttackList() {
    }

    // ------------------------------------------------------------------ 读写本体数据

    /** 读出这只女仆的仇恨名单（实体 ID -> 状态名）。 */
    public static Map<ResourceLocation, String> read(EntityMaid maid) {
        Map<ResourceLocation, String> out = new LinkedHashMap<>();
        try {
            AttackListData data = maid.getData(InitTaskData.ATTACK_LIST);
            if (data == null) {
                return out;
            }
            for (Map.Entry<ResourceLocation, MonsterType> entry : data.attackGroups().entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    out.put(entry.getKey(), entry.getValue().name());
                }
            }
        } catch (Throwable t) {
            MaidList.LOGGER.debug("[女仆名单] 读取仇恨名单失败", t);
        }
        return out;
    }

    /** 写回本体数据（会自动同步给客户端并持久化）。 */
    public static void write(EntityMaid maid, Map<ResourceLocation, String> entries) {
        Map<ResourceLocation, MonsterType> map = new LinkedHashMap<>();
        if (entries != null) {
            for (Map.Entry<ResourceLocation, String> entry : entries.entrySet()) {
                MonsterType type = parseType(entry.getValue());
                if (type != null) {
                    map.put(entry.getKey(), type);
                }
            }
        }
        maid.setAndSyncData(InitTaskData.ATTACK_LIST, new AttackListData(map));
    }

    private static MonsterType parseType(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return MonsterType.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ 网络入口

    public static void send(ServerPlayer player, UUID maidId) {
        EntityMaid maid = find(player, maidId);
        if (maid == null) {
            chat(player, "fairymoon3_maid_list.attack.notfound");
            return;
        }
        PacketDistributor.sendToPlayer(player, new MaidAttackListPayload(
                maid.getUUID(), maid.getName().getString(), read(maid)));
    }

    public static void setOne(ServerPlayer player, UUID maidId, ResourceLocation entityType, String mode) {
        EntityMaid maid = find(player, maidId);
        if (maid == null || entityType == null) {
            return;
        }
        Map<ResourceLocation, String> list = read(maid);
        if (mode == null || mode.isBlank()) {
            list.remove(entityType);
        } else {
            list.put(entityType, mode);
        }
        write(maid, list);
        PacketDistributor.sendToPlayer(player, new MaidAttackListPayload(
                maid.getUUID(), maid.getName().getString(), list));
    }

    public static void replace(ServerPlayer player, UUID maidId, Map<ResourceLocation, String> entries) {
        EntityMaid maid = find(player, maidId);
        if (maid == null) {
            chat(player, "fairymoon3_maid_list.attack.notfound");
            return;
        }
        write(maid, entries);
        PacketDistributor.sendToPlayer(player, new MaidAttackListPayload(
                maid.getUUID(), maid.getName().getString(), read(maid)));
    }

    /** 把这份名单套用到该玩家**所有已加载**的女仆上。 */
    public static void syncToAll(ServerPlayer player, UUID maidId, Map<ResourceLocation, String> entries) {
        int count = 0;
        for (EntityMaid maid : ownedLoaded(player)) {
            write(maid, entries);
            count++;
        }
        chat(player, "fairymoon3_maid_list.attack.sync.all", count);
        if (maidId != null) {
            send(player, maidId);
        }
    }

    /** 清空该玩家所有已加载女仆的仇恨名单。 */
    public static void resetAll(ServerPlayer player, UUID maidId) {
        int count = 0;
        for (EntityMaid maid : ownedLoaded(player)) {
            write(maid, Map.of());
            count++;
        }
        chat(player, "fairymoon3_maid_list.attack.reset.all", count);
        if (maidId != null) {
            send(player, maidId);
        }
    }

    // ------------------------------------------------------------------ 工具

    private static List<EntityMaid> ownedLoaded(ServerPlayer player) {
        List<EntityMaid> out = new java.util.ArrayList<>();
        for (ServerLevel level : player.getServer().getAllLevels()) {
            out.addAll(level.getEntities(EntityMaid.TYPE,
                    maid -> maid.isOwnedBy(player) && maid.isAlive()));
        }
        return out;
    }

    private static EntityMaid find(ServerPlayer player, UUID maidId) {
        if (maidId == null) {
            return null;
        }
        List<? extends EntityMaid> found = player.serverLevel().getEntities(EntityMaid.TYPE,
                maid -> maidId.equals(maid.getUUID()) && maid.isOwnedBy(player) && maid.isAlive());
        return found.isEmpty() ? null : found.get(0);
    }

    private static void chat(ServerPlayer player, String key, Object... args) {
        player.sendSystemMessage(Component.translatable(key, args));
    }
}
