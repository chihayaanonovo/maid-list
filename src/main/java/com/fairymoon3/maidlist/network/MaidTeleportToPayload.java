package com.fairymoon3.maidlist.network;

import com.fairymoon3.maidlist.MaidList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/** 客户端 -> 服务端：把玩家自己传送到这只女仆身边。 */
public record MaidTeleportToPayload(UUID maidId) implements CustomPacketPayload {

    public static final Type<MaidTeleportToPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MaidList.MODID, "maid_teleport_to"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MaidTeleportToPayload> STREAM_CODEC = StreamCodec.of(
            (RegistryFriendlyByteBuf buf, MaidTeleportToPayload payload) -> buf.writeUUID(payload.maidId()),
            (RegistryFriendlyByteBuf buf) -> new MaidTeleportToPayload(buf.readUUID()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
