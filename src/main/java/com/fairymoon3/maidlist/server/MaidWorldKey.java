package com.fairymoon3.maidlist.server;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.UUID;

/**
 * 每个存档独有的随机密钥，用于给收纳的女仆数据签名。
 *
 * <p>**故意和收纳数据分开存**（不同的 SavedData 文件）：这样即使收纳数据文件被删 / 损坏，
 * 密钥还在，之前备份到客户端的女仆仍然能通过来源校验被恢复。</p>
 *
 * <p>注意：新建的 {@link SavedData} 默认不是「脏」的，不调用 {@code setDirty()} 就不会落盘，
 * 结果每次重启都换一把新密钥、之前打的签章全部失效。所以这里第一次取用时会主动标脏。</p>
 */
public class MaidWorldKey extends SavedData {

    private static final String DATA_NAME = "maid_list_world_key";
    private static final String KEY = "key";

    public static final SavedData.Factory<MaidWorldKey> FACTORY =
            new SavedData.Factory<>(MaidWorldKey::new, MaidWorldKey::load);

    private String key = UUID.randomUUID().toString();
    /** 是否已经落过盘（加载进来的算已落盘）。 */
    private boolean persisted;
    /** 本次是否已经处理过「继承旧密钥 / 标脏」。 */
    private boolean adopted;

    public static String get(MinecraftServer server) {
        MaidWorldKey data = server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
        if (!data.adopted) {
            data.adopted = true;
            if (!data.persisted) {
                // 密钥文件不存在（第一次生成 / 旧版本没落盘）：
                // 如果已有收纳数据带着旧签章，就继承那把旧密钥，避免旧数据全部失效。
                String inherited = MaidOrigin.findExistingKey(server);
                if (inherited != null) {
                    data.key = inherited;
                }
            }
            data.setDirty();
        }
        return data.key;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        this.persisted = true;
        tag.putString(KEY, this.key);
        return tag;
    }

    public static MaidWorldKey load(CompoundTag tag, HolderLookup.Provider registries) {
        MaidWorldKey data = new MaidWorldKey();
        String stored = tag.getString(KEY);
        if (!stored.isBlank()) {
            data.key = stored;
        }
        data.persisted = true;
        return data;
    }
}
