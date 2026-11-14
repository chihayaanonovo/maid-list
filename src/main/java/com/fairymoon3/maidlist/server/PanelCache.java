package com.fairymoon3.maidlist.server;

import com.fairymoon3.maidlist.MaidList;
import com.fairymoon3.maidlist.network.MaidEntry;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 女仆信息快照缓存（存档级持久化）。
 *
 * <p>车万女仆只在实体存在时才有血量 / 职业 / 好感等数据，实体一旦随区块卸载就只剩
 * {@code MaidWorldData} 里的名字、维度和区块坐标。这里在每次收集到「活着」的女仆时
 * 顺手存一份完整快照，等女仆离线时就能把上次看到的数据补上。</p>
 *
 * <p>只存轻量字段；物品用注册名重建，避免 NBT 里塞 ItemStack 组件。</p>
 */
public class PanelCache extends SavedData {

    private static final String DATA_NAME = "fairymoon3_maid_list_cache";
    private static final String KEY_MAIDS = "maids";

    public static final SavedData.Factory<PanelCache> FACTORY =
            new SavedData.Factory<>(PanelCache::new, PanelCache::load);

    private final Map<UUID, MaidEntry> entries = new HashMap<>();
    private final Map<UUID, Long> recordedAt = new HashMap<>();

    public static PanelCache get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    /** 记录一次完整快照。 */
    public void record(MaidEntry entry) {
        if (entry == null || !entry.loaded()) {
            return;
        }
        this.entries.put(entry.uuid(), entry);
        this.recordedAt.put(entry.uuid(), System.currentTimeMillis());
        setDirty();
    }

    /** 取快照，并把「多久之前记录的」折算成秒。没有就返回 null。 */
    public MaidEntry snapshot(UUID id, long now) {
        MaidEntry entry = this.entries.get(id);
        if (entry == null) {
            return null;
        }
        Long when = this.recordedAt.get(id);
        long ageMillis = when == null ? 0L : Math.max(0L, now - when);
        int ageSeconds = (int) Math.min(Integer.MAX_VALUE, ageMillis / 1000L);
        return entry.withAge(ageSeconds);
    }

    // ------------------------------------------------------------------ 存档

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (MaidEntry entry : this.entries.values()) {
            CompoundTag t = new CompoundTag();
            t.putUUID("id", entry.uuid());
            t.putString("name", entry.name());
            t.putString("task", entry.taskId() == null ? "" : entry.taskId().toString());
            t.putString("taskIcon", itemId(entry.taskIcon()));
            t.putInt("hp", entry.health());
            t.putInt("maxHp", entry.maxHealth());
            t.putInt("favor", entry.favorability());
            t.putInt("favorLv", entry.favorLevel());
            t.putInt("exp", entry.experience());
            t.putBoolean("home", entry.homeMode());
            t.putBoolean("sitting", entry.sitting());
            t.putBoolean("sleeping", entry.sleeping());
            t.putBoolean("fighting", entry.fighting());
            t.putBoolean("backpack", entry.hasBackpack());
            t.putString("backpackIcon", itemId(entry.backpackIcon()));
            t.putString("backpackName", entry.backpackName() == null ? "" : entry.backpackName());
            t.putString("model", entry.modelId() == null ? "" : entry.modelId());
            t.putBoolean("ysm", entry.ysmModel());
            t.putString("dim", entry.dimension() == null ? "" : entry.dimension().toString());
            t.putDouble("x", entry.x());
            t.putDouble("y", entry.y());
            t.putDouble("z", entry.z());
            t.putInt("cx", entry.chunkX());
            t.putInt("cz", entry.chunkZ());
            t.putInt("foxStage", entry.foxdreamStage());
            t.putLong("acq", entry.acquiredAt());
            t.putLong("at", this.recordedAt.getOrDefault(entry.uuid(), 0L));
            list.add(t);
        }
        tag.put(KEY_MAIDS, list);
        return tag;
    }

    public static PanelCache load(CompoundTag tag, HolderLookup.Provider registries) {
        PanelCache cache = new PanelCache();
        ListTag list = tag.getList(KEY_MAIDS, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            try {
                if (!t.hasUUID("id")) {
                    continue;
                }
                UUID id = t.getUUID("id");
                MaidEntry entry = new MaidEntry(
                        id,
                        t.getString("name"),
                        false,
                        parseId(t.getString("task"), "touhou_little_maid:idle"),
                        itemFrom(t.getString("taskIcon")),
                        t.getInt("hp"),
                        t.getInt("maxHp"),
                        t.getInt("favor"),
                        t.getInt("favorLv"),
                        t.getInt("exp"),
                        t.getBoolean("home"),
                        t.getBoolean("sitting"),
                        t.getBoolean("sleeping"),
                        t.getBoolean("fighting"),
                        t.getBoolean("backpack"),
                        itemFrom(t.getString("backpackIcon")),
                        t.getString("backpackName"),
                        t.getString("model"),
                        t.getBoolean("ysm"),
                        parseId(t.getString("dim"), "minecraft:overworld"),
                        t.getDouble("x"),
                        t.getDouble("y"),
                        t.getDouble("z"),
                        t.getInt("cx"),
                        t.getInt("cz"),
                        t.contains("foxStage") ? t.getInt("foxStage") : FoxdreamCompat.STAGE_NONE,
                        t.getLong("acq"),
                        0
                );
                cache.entries.put(id, entry);
                cache.recordedAt.put(id, t.getLong("at"));
            } catch (Exception e) {
                MaidList.LOGGER.debug("[女仆名单] 读取快照失败，跳过一条", e);
            }
        }
        return cache;
    }

    private static String itemId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return key == null ? "" : key.toString();
    }

    private static ItemStack itemFrom(String id) {
        if (id == null || id.isBlank()) {
            return ItemStack.EMPTY;
        }
        try {
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
            return item == null ? ItemStack.EMPTY : new ItemStack(item);
        } catch (Exception e) {
            return ItemStack.EMPTY;
        }
    }

    private static ResourceLocation parseId(String raw, String fallback) {
        try {
            return raw == null || raw.isBlank()
                    ? ResourceLocation.parse(fallback)
                    : ResourceLocation.parse(raw);
        } catch (Exception e) {
            return ResourceLocation.parse(fallback);
        }
    }
}
