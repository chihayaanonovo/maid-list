package com.fairymoon3.maidlist.network;

import com.fairymoon3.maidlist.MaidList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.UUID;

/**
 * 客户端 -> 服务端：整体替换某只女仆的仇恨名单。
 *
 * <p>用于「重置该女仆的仇恨名单」（传空表）与「黏贴仇恨名单」。</p>
 */
public record MaidAttackReplacePayload(UUID maidId, Map<ResourceLocation, String> entries)
        implements CustomPacketPayload {

    public static final Type<MaidAttackReplacePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MaidList.MODID, "maid_attack_replace"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MaidAttackReplacePayload> STREAM_CODEC = StreamCodec.of(
            (RegistryFriendlyByteBuf buf, MaidAttackReplacePayload payload) -> {
                buf.writeUUID(payload.maidId());
                AttackListCodec.write(buf, payload.entries());
            },
            (RegistryFriendlyByteBuf buf) -> new MaidAttackReplacePayload(buf.readUUID(), AttackListCodec.read(buf)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
