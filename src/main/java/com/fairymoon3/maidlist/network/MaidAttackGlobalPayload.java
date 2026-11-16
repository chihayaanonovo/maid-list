package com.fairymoon3.maidlist.network;

import com.fairymoon3.maidlist.MaidList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.UUID;

/**
 * 客户端 -> 服务端：批量操作。
 *
 * @param action 0 = 把名单同步到该玩家所有女仆；1 = 重置该玩家所有女仆的名单
 */
public record MaidAttackGlobalPayload(int action, UUID maidId, Map<ResourceLocation, String> entries)
        implements CustomPacketPayload {

    public static final int ACTION_SYNC_ALL = 0;
    public static final int ACTION_RESET_ALL = 1;

    public static final Type<MaidAttackGlobalPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MaidList.MODID, "maid_attack_global"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MaidAttackGlobalPayload> STREAM_CODEC = StreamCodec.of(
            (RegistryFriendlyByteBuf buf, MaidAttackGlobalPayload payload) -> {
                buf.writeVarInt(payload.action());
                if (payload.maidId() == null) {
                    buf.writeBoolean(false);
                } else {
                    buf.writeBoolean(true);
                    buf.writeUUID(payload.maidId());
                }
                AttackListCodec.write(buf, payload.entries());
            },
            (RegistryFriendlyByteBuf buf) -> {
                int action = buf.readVarInt();
                UUID maidId = buf.readBoolean() ? buf.readUUID() : null;
                return new MaidAttackGlobalPayload(action, maidId, AttackListCodec.read(buf));
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
