package com.fairymoon3.maidlist.network;

import com.fairymoon3.maidlist.MaidList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 客户端 -> 服务端：请求一份自己的女仆名册。
 * 无载荷，用 {@link StreamCodec#unit} 实现。
 */
public record MaidListRequestPayload() implements CustomPacketPayload {

    public static final Type<MaidListRequestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MaidList.MODID, "maid_list_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MaidListRequestPayload> STREAM_CODEC =
            StreamCodec.unit(new MaidListRequestPayload());

    public static MaidListRequestPayload create() {
        return new MaidListRequestPayload();
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
