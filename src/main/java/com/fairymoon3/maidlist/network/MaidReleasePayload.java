package com.fairymoon3.maidlist.network;

import com.fairymoon3.maidlist.MaidList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/** 客户端 -> 服务端：释放一只被收纳的女仆。 */
public record MaidReleasePayload(UUID maidId) implements CustomPacketPayload {

    public static final Type<MaidReleasePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MaidList.MODID, "maid_release"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MaidReleasePayload> STREAM_CODEC = StreamCodec.of(
            (RegistryFriendlyByteBuf buf, MaidReleasePayload payload) -> buf.writeUUID(payload.maidId()),
            (RegistryFriendlyByteBuf buf) -> new MaidReleasePayload(buf.readUUID()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
