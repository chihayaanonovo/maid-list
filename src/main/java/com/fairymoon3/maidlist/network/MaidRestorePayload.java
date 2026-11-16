package com.fairymoon3.maidlist.network;

import com.fairymoon3.maidlist.MaidList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 客户端 -> 服务端：用本地备份的完整实体 NBT 恢复一只女仆。
 *
 * <p>服务端会用存档密钥校验来源，来自别的存档的数据会被拒绝。</p>
 */
public record MaidRestorePayload(String name, CompoundTag tag) implements CustomPacketPayload {

    public static final Type<MaidRestorePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MaidList.MODID, "maid_restore"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MaidRestorePayload> STREAM_CODEC = StreamCodec.of(
            (RegistryFriendlyByteBuf buf, MaidRestorePayload payload) -> {
                buf.writeUtf(payload.name() == null ? "?" : payload.name(), 128);
                buf.writeNbt(payload.tag() == null ? new CompoundTag() : payload.tag());
            },
            (RegistryFriendlyByteBuf buf) -> {
                String name = buf.readUtf(128);
                CompoundTag tag = buf.readNbt();
                return new MaidRestorePayload(name, tag == null ? new CompoundTag() : tag);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
