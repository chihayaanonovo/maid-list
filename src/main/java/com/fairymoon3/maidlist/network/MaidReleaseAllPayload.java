package com.fairymoon3.maidlist.network;

import com.fairymoon3.maidlist.MaidList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** 客户端 -> 服务端：一次性释放收纳空间里的全部女仆。 */
public record MaidReleaseAllPayload() implements CustomPacketPayload {

    public static final Type<MaidReleaseAllPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MaidList.MODID, "maid_release_all"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MaidReleaseAllPayload> STREAM_CODEC =
            StreamCodec.unit(new MaidReleaseAllPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
