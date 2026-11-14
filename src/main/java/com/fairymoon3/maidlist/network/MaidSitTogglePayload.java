package com.fairymoon3.maidlist.network;

import com.fairymoon3.maidlist.MaidList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/** 客户端 -> 服务端：切换女仆的坐下 / 站立（等同玩家右键让她坐下）。 */
public record MaidSitTogglePayload(UUID maidId) implements CustomPacketPayload {

    public static final Type<MaidSitTogglePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MaidList.MODID, "maid_sit_toggle"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MaidSitTogglePayload> STREAM_CODEC = StreamCodec.of(
            (RegistryFriendlyByteBuf buf, MaidSitTogglePayload payload) -> buf.writeUUID(payload.maidId()),
            (RegistryFriendlyByteBuf buf) -> new MaidSitTogglePayload(buf.readUUID()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
