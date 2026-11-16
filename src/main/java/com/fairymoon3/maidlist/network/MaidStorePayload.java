package com.fairymoon3.maidlist.network;

import com.fairymoon3.maidlist.MaidList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/** 客户端 -> 服务端：把这只女仆收进收纳空间。 */
public record MaidStorePayload(UUID maidId) implements CustomPacketPayload {

    public static final Type<MaidStorePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MaidList.MODID, "maid_store"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MaidStorePayload> STREAM_CODEC = StreamCodec.of(
            (RegistryFriendlyByteBuf buf, MaidStorePayload payload) -> buf.writeUUID(payload.maidId()),
            (RegistryFriendlyByteBuf buf) -> new MaidStorePayload(buf.readUUID()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
