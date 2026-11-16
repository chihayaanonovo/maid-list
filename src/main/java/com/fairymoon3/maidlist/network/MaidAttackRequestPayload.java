package com.fairymoon3.maidlist.network;

import com.fairymoon3.maidlist.MaidList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/** 客户端 -> 服务端：请求某只女仆的仇恨名单。 */
public record MaidAttackRequestPayload(UUID maidId) implements CustomPacketPayload {

    public static final Type<MaidAttackRequestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MaidList.MODID, "maid_attack_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MaidAttackRequestPayload> STREAM_CODEC = StreamCodec.of(
            (RegistryFriendlyByteBuf buf, MaidAttackRequestPayload payload) -> buf.writeUUID(payload.maidId()),
            (RegistryFriendlyByteBuf buf) -> new MaidAttackRequestPayload(buf.readUUID()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
