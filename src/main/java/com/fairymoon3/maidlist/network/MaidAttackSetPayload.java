package com.fairymoon3.maidlist.network;

import com.fairymoon3.maidlist.MaidList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * 客户端 -> 服务端：修改某只女仆仇恨名单里的**一个**实体。
 *
 * @param mode {@code FRIENDLY} / {@code NEUTRAL} / {@code HOSTILE}；空字符串表示从名单里移除
 */
public record MaidAttackSetPayload(UUID maidId, ResourceLocation entityType, String mode)
        implements CustomPacketPayload {

    public static final Type<MaidAttackSetPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MaidList.MODID, "maid_attack_set"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MaidAttackSetPayload> STREAM_CODEC = StreamCodec.of(
            (RegistryFriendlyByteBuf buf, MaidAttackSetPayload payload) -> {
                buf.writeUUID(payload.maidId());
                buf.writeResourceLocation(payload.entityType());
                buf.writeUtf(payload.mode() == null ? "" : payload.mode(), 32);
            },
            (RegistryFriendlyByteBuf buf) -> new MaidAttackSetPayload(
                    buf.readUUID(), buf.readResourceLocation(), buf.readUtf(32)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
