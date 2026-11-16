package com.fairymoon3.maidlist.server;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 女仆收纳空间的持久化存储（存档级）。
 *
 * <p>格式借用「魂符」：每个收纳的女仆存一份 {@code EntityMaid.saveWithoutId} 的完整实体 NBT，
 * 这样释放时能原样还原（血量、好感、背包、模型、狐梦数据都在）。</p>
 *
 * <p>与原版魂符的区别：魂符一张只装一个，这里每个玩家可以装任意多个。</p>
 */
public class MaidStorageData extends SavedData {

    private static final String DATA_NAME = "maid_list_storage";
    private static final String KEY_PLAYERS = "players";
    private static final String KEY_UUID = "uuid";
    private static final String KEY_NAME = "name";
    private static final String KEY_HP = "hp";
    private static final String KEY_MAX_HP = "maxHp";
    private static final String KEY_TAG = "tag";

    public static final SavedData.Factory<MaidStorageData> FACTORY =
            new SavedData.Factory<>(MaidStorageData::new, MaidStorageData::load);

    /** 一个被收纳的女仆。 */
    public record Stored(UUID uuid, String name, int hp, int maxHp, CompoundTag tag) {
    }

    private final Map<UUID, List<Stored>> byPlayer = new LinkedHashMap<>();

    public static MaidStorageData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public List<Stored> list(UUID playerId) {
        List<Stored> list = this.byPlayer.get(playerId);
        return list == null ? List.of() : List.copyOf(list);
    }

    /** 所有玩家所有收纳记录（仅供内部补救逻辑使用）。 */
    public List<Stored> allStored() {
        List<Stored> out = new ArrayList<>();
        for (List<Stored> list : this.byPlayer.values()) {
            out.addAll(list);
        }
        return out;
    }

    public boolean isStored(UUID playerId, UUID maidId) {
        for (Stored stored : list(playerId)) {
            if (stored.uuid().equals(maidId)) {
                return true;
            }
        }
        return false;
    }

    public void add(UUID playerId, Stored stored) {
        this.byPlayer.computeIfAbsent(playerId, key -> new ArrayList<>()).add(stored);
        setDirty();
    }

    /** 取出并移除一只，找不到返回 null。 */
    public Stored remove(UUID playerId, UUID maidId) {
        List<Stored> list = this.byPlayer.get(playerId);
        if (list == null) {
            return null;
        }
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).uuid().equals(maidId)) {
                Stored removed = list.remove(i);
                setDirty();
                return removed;
            }
        }
        return null;
    }

    /** 取出并清空该玩家所有收纳的女仆。 */
    public List<Stored> removeAll(UUID playerId) {
        List<Stored> list = this.byPlayer.remove(playerId);
        if (list == null) {
            return List.of();
        }
        setDirty();
        return List.copyOf(list);
    }

    // ------------------------------------------------------------------ 存档

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag players = new CompoundTag();
        for (Map.Entry<UUID, List<Stored>> entry : this.byPlayer.entrySet()) {
            ListTag list = new ListTag();
            for (Stored stored : entry.getValue()) {
                CompoundTag one = new CompoundTag();
                one.putUUID(KEY_UUID, stored.uuid());
                one.putString(KEY_NAME, stored.name());
                one.putInt(KEY_HP, stored.hp());
                one.putInt(KEY_MAX_HP, stored.maxHp());
                one.put(KEY_TAG, stored.tag().copy());
                list.add(one);
            }
            players.put(entry.getKey().toString(), list);
        }
        tag.put(KEY_PLAYERS, players);
        return tag;
    }

    public static MaidStorageData load(CompoundTag tag, HolderLookup.Provider registries) {
        MaidStorageData data = new MaidStorageData();
        CompoundTag players = tag.getCompound(KEY_PLAYERS);
        for (String key : players.getAllKeys()) {
            UUID playerId;
            try {
                playerId = UUID.fromString(key);
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            ListTag list = players.getList(key, Tag.TAG_COMPOUND);
            List<Stored> storedList = new ArrayList<>();
            for (int i = 0; i < list.size(); i++) {
                CompoundTag one = list.getCompound(i);
                storedList.add(new Stored(
                        one.getUUID(KEY_UUID),
                        one.getString(KEY_NAME),
                        one.getInt(KEY_HP),
                        one.getInt(KEY_MAX_HP),
                        one.getCompound(KEY_TAG)));
            }
            data.byPlayer.put(playerId, storedList);
        }
        return data;
    }
}
