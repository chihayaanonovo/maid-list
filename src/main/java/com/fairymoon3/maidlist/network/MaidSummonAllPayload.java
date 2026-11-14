package com.fairymoon3.maidlist.network;

import com.fairymoon3.maidlist.MaidList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** 客户端 -> 服务端：把所有能传送的女仆一次性召唤到身边。 */
public record MaidSummonAllPayload() implements CustomPacketPayload {

    public static final Type<MaidSummonAllPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MaidList.MODID, "maid_summon_all"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MaidSummonAllPayload> STREAM_CODEC =
            StreamCodec.unit(new MaidSummonAllPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
