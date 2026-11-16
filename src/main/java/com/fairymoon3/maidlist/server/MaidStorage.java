package com.fairymoon3.maidlist.server;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.fairymoon3.maidlist.MaidList;
import com.fairymoon3.maidlist.network.MaidStoragePayload;
import com.fairymoon3.maidlist.network.StoredMaid;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 女仆收纳空间的服务端逻辑。
 *
 * <p>收纳时用 {@code EntityMaid.saveWithoutId} 生成完整实体 NBT（与原版魂符同一份数据），
 * 然后 {@code discard()} 掉实体；释放时用这份 NBT 重新构造一只女仆。
 * 区别在于一个玩家可以收纳任意多只（原版魂符一张只装一只）。</p>
 */
public final class MaidStorage {

    private MaidStorage() {
    }

    // ------------------------------------------------------------------ 收纳

    public static void store(ServerPlayer player, UUID maidId) {
        if (maidId == null) {
            return;
        }
        EntityMaid maid = findMaid(player, maidId);
        if (maid == null) {
            chat(player, "fairymoon3_maid_list.storage.store.notfound");
            return;
        }
        try {
            CompoundTag tag = new CompoundTag();
            maid.saveWithoutId(tag);
            // 打上「本存档产出」的签章，供日后恢复时校验来源
            MaidOrigin.stamp(tag, player.getServer(), maidId);
            String name = maid.getName().getString();
            int hp = Math.max(0, Math.round(maid.getHealth()));
            int maxHp = Math.max(0, Math.round(maid.getMaxHealth()));

            MaidStorageData data = MaidStorageData.get(player.getServer());
            data.add(player.getUUID(), new MaidStorageData.Stored(maidId, name, hp, maxHp, tag));

            // 语音等客户端状态一并清掉
            maid.discard();

            chat(player, "fairymoon3_maid_list.storage.store.done", name);
            MaidCollector.sendPanel(player);
            sendStorage(player);
        } catch (Exception e) {
            MaidList.LOGGER.warn("[女仆名单] 收纳女仆失败", e);
            chat(player, "fairymoon3_maid_list.storage.store.fail");
        }
    }

    // ------------------------------------------------------------------ 释放

    public static void release(ServerPlayer player, UUID maidId) {
        if (maidId == null) {
            return;
        }
        MaidStorageData data = MaidStorageData.get(player.getServer());
        MaidStorageData.Stored stored = data.remove(player.getUUID(), maidId);
        if (stored == null) {
            chat(player, "fairymoon3_maid_list.storage.release.notfound");
            return;
        }
        if (spawn(player, stored)) {
            chat(player, "fairymoon3_maid_list.storage.release.done", stored.name());
        }
        MaidCollector.sendPanel(player);
        sendStorage(player);
    }

    public static void releaseAll(ServerPlayer player) {
        MaidStorageData data = MaidStorageData.get(player.getServer());
        List<MaidStorageData.Stored> all = data.removeAll(player.getUUID());
        if (all.isEmpty()) {
            chat(player, "fairymoon3_maid_list.storage.release.empty");
            return;
        }
        int ok = 0;
        for (MaidStorageData.Stored stored : all) {
            if (spawn(player, stored)) {
                ok++;
            }
        }
        chat(player, "fairymoon3_maid_list.storage.release.all.done", ok, all.size() - ok);
        MaidCollector.sendPanel(player);
        sendStorage(player);
    }

    /** 从存档 NBT 还原一只女仆并放在玩家身边。 */
    private static boolean spawn(ServerPlayer player, MaidStorageData.Stored stored) {
        try {
            ServerLevel level = player.serverLevel();
            EntityMaid maid = new EntityMaid(level);
            maid.load(MaidOrigin.stripped(stored.tag()));
            double x = player.getX() + (player.getRandom().nextInt(3) - 1);
            double z = player.getZ() + (player.getRandom().nextInt(3) - 1);
            maid.moveTo(x, player.getY(), z, player.getYRot(), 0.0F);
            level.addFreshEntity(maid);
            return true;
        } catch (Exception e) {
            MaidList.LOGGER.warn("[女仆名单] 释放女仆失败：{}", stored.name(), e);
            return false;
        }
    }

    // ------------------------------------------------------------------ 从本地备份恢复

    /**
     * 用客户端备份的完整 NBT 恢复一只女仆。
     *
     * <p>只接受**当前存档**产出的数据：服务端用自己存档的密钥校验签章，
     * 来自其它存档 / 被修改过的数据会被拒绝并告知玩家。</p>
     */
    public static void restore(ServerPlayer player, String name, CompoundTag tag) {
        if (tag == null || tag.isEmpty()) {
            return;
        }
        try {
            UUID maidId;
            try {
                maidId = tag.getUUID("UUID");
            } catch (Exception e) {
                chat(player, "fairymoon3_maid_list.storage.restore.rejected");
                return;
            }
            if (!MaidOrigin.verify(tag, player.getServer(), maidId)) {
                chat(player, "fairymoon3_maid_list.storage.restore.rejected");
                return;
            }
            MaidStorageData data = MaidStorageData.get(player.getServer());
            if (data.isStored(player.getUUID(), maidId)) {
                // 服务端已经有了，不用重复恢复
                return;
            }
            int hp = Math.max(0, Math.round(tag.getFloat("Health")));
            int maxHp = Math.max(hp, 20);
            String display = name == null || name.isBlank() ? "?" : name;
            data.add(player.getUUID(), new MaidStorageData.Stored(maidId, display, hp, maxHp, tag.copy()));
            chat(player, "fairymoon3_maid_list.storage.restore.done", display);
            sendStorage(player);
            MaidCollector.sendPanel(player);
        } catch (Exception e) {
            MaidList.LOGGER.warn("[女仆名单] 恢复收纳女仆失败", e);
            chat(player, "fairymoon3_maid_list.storage.restore.failed", name == null ? "?" : name);
        }
    }

    // ------------------------------------------------------------------ 同步

    public static void sendStorage(ServerPlayer player) {
        MaidStorageData data = MaidStorageData.get(player.getServer());
        List<StoredMaid> list = new ArrayList<>();
        for (MaidStorageData.Stored stored : data.list(player.getUUID())) {
            list.add(new StoredMaid(stored.uuid(), stored.name(), stored.hp(), stored.maxHp(), stored.tag().copy()));
        }
        PacketDistributor.sendToPlayer(player, new MaidStoragePayload(list));
    }

    /** 该女仆是否已被当前玩家收纳（用于从名册里隐藏）。 */
    public static boolean isStored(ServerPlayer player, UUID maidId) {
        try {
            return MaidStorageData.get(player.getServer()).isStored(player.getUUID(), maidId);
        } catch (Exception e) {
            return false;
        }
    }

    private static EntityMaid findMaid(ServerPlayer player, UUID maidId) {
        List<? extends EntityMaid> found = player.serverLevel().getEntities(EntityMaid.TYPE,
                maid -> maidId.equals(maid.getUUID()) && maid.isOwnedBy(player) && maid.isAlive());
        return found.isEmpty() ? null : found.get(0);
    }

    private static void chat(ServerPlayer player, String key, Object... args) {
        player.sendSystemMessage(Component.translatable(key, args));
    }
}
