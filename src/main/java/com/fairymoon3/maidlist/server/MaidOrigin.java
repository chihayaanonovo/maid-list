package com.fairymoon3.maidlist.server;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

/**
 * 收纳数据的「来源签章」。
 *
 * <p>收纳时给实体 NBT 打上一个标记：{@code MaidListOrigin = { key: 世界密钥, sig: SHA256(key:uuid) }}。
 * 恢复时服务端用自己存档的密钥重新算一遍：</p>
 * <ul>
 *   <li>密钥不一致 → 这份数据来自别的存档，拒绝恢复</li>
 *   <li>签名对不上 → 数据被改过，拒绝恢复</li>
 * </ul>
 *
 * <p>这样多人服务器上，玩家无法把自己单机存档里的女仆「导入」到服务器里。</p>
 */
public final class MaidOrigin {

    private static final String TAG = "MaidListOrigin";
    private static final String KEY = "key";
    private static final String SIG = "sig";

    private MaidOrigin() {
    }

    /** 给实体 NBT 打上来源标记。 */
    public static void stamp(CompoundTag tag, MinecraftServer server, UUID maidId) {
        try {
            String key = MaidWorldKey.get(server);
            CompoundTag origin = new CompoundTag();
            origin.putString(KEY, key);
            origin.putString(SIG, sign(key, maidId));
            tag.put(TAG, origin);
        } catch (Throwable ignored) {
            // 打标记失败不影响收纳本身
        }
    }

    /** 校验这份数据是不是当前存档产出的。 */
    public static boolean verify(CompoundTag tag, MinecraftServer server, UUID maidId) {
        try {
            if (tag == null || !tag.contains(TAG)) {
                return false;
            }
            CompoundTag origin = tag.getCompound(TAG);
            String key = MaidWorldKey.get(server);
            if (!key.equals(origin.getString(KEY))) {
                return false;
            }
            return sign(key, maidId).equals(origin.getString(SIG));
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * 从已有收纳数据里找出曾经用过的世界密钥。
     *
     * <p>用于「密钥文件丢失但数据还在」的补救：把旧密钥继承回来，
     * 之前打在数据上的签章就不会全部失效。</p>
     *
     * @return 找到的密钥，找不到返回 {@code null}
     */
    public static String findExistingKey(MinecraftServer server) {
        try {
            MaidStorageData data = MaidStorageData.get(server);
            // 遍历所有玩家的收纳记录（SavedData 里没有直接暴露全部列表的接口，这里逐个玩家看）
            for (MaidStorageData.Stored stored : data.allStored()) {
                CompoundTag origin = stored.tag().getCompound(TAG);
                String key = origin.getString(KEY);
                if (!key.isBlank()) {
                    return key;
                }
            }
        } catch (Throwable ignored) {
            // 拿不到就算了
        }
        return null;
    }

    /** 去掉标记后的实体 NBT（释放 / 恢复时用，避免污染实体数据）。 */
    public static CompoundTag stripped(CompoundTag tag) {
        CompoundTag copy = tag.copy();
        copy.remove(TAG);
        return copy;
    }

    private static String sign(String key, UUID maidId) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] out = digest.digest((key + ":" + maidId).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(out);
        } catch (Exception e) {
            return "";
        }
    }
}
