package com.fairymoon3.maidlist.network;

import com.fairymoon3.maidlist.MaidList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** 客户端 -> 服务端：请求一份收纳空间列表。 */
public record MaidStorageRequestPayload() implements CustomPacketPayload {

    public static final Type<MaidStorageRequestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MaidList.MODID, "maid_storage_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MaidStorageRequestPayload> STREAM_CODEC =
            StreamCodec.unit(new MaidStorageRequestPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
