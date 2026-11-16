package com.fairymoon3.maidlist.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;

import java.util.UUID;

/**
 * 收纳空间里的一条记录。
 *
 * @param tag 用于客户端渲染小头像的实体数据（完整实体 NBT，收纳时录制）
 */
public record StoredMaid(UUID uuid, String name, int hp, int maxHp, CompoundTag tag) {

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeUUID(uuid);
        buf.writeUtf(name == null ? "?" : name, 128);
        buf.writeVarInt(hp);
        buf.writeVarInt(maxHp);
        buf.writeNbt(tag == null ? new CompoundTag() : tag);
    }

    public static StoredMaid read(RegistryFriendlyByteBuf buf) {
        UUID uuid = buf.readUUID();
        String name = buf.readUtf(128);
        int hp = buf.readVarInt();
        int maxHp = buf.readVarInt();
        CompoundTag tag = buf.readNbt();
        return new StoredMaid(uuid, name, hp, maxHp, tag == null ? new CompoundTag() : tag);
    }
}
