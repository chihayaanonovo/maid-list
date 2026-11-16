package com.fairymoon3.maidlist.network;

import com.fairymoon3.maidlist.MaidList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.UUID;

/** 服务端 -> 客户端：某只女仆的完整仇恨名单。 */
public record MaidAttackListPayload(UUID maidId, String maidName,
                                    Map<ResourceLocation, String> entries) implements CustomPacketPayload {

    public static final Type<MaidAttackListPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MaidList.MODID, "maid_attack_list"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MaidAttackListPayload> STREAM_CODEC = StreamCodec.of(
            (RegistryFriendlyByteBuf buf, MaidAttackListPayload payload) -> {
                buf.writeUUID(payload.maidId());
                buf.writeUtf(payload.maidName() == null ? "?" : payload.maidName(), 128);
                AttackListCodec.write(buf, payload.entries());
            },
            (RegistryFriendlyByteBuf buf) -> {
                UUID uuid = buf.readUUID();
                String name = buf.readUtf(128);
                return new MaidAttackListPayload(uuid, name, AttackListCodec.read(buf));
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
