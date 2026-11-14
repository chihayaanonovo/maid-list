package com.fairymoon3.maidlist.network;

import com.fairymoon3.maidlist.MaidList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/** 服务端 -> 客户端：一整份女仆名册。 */
public record MaidListPayload(List<MaidEntry> entries) implements CustomPacketPayload {

    public static final Type<MaidListPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MaidList.MODID, "maid_list"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MaidListPayload> STREAM_CODEC = StreamCodec.of(
            (RegistryFriendlyByteBuf buf, MaidListPayload payload) -> {
                buf.writeVarInt(payload.entries().size());
                for (MaidEntry entry : payload.entries()) {
                    entry.write(buf);
                }
            },
            (RegistryFriendlyByteBuf buf) -> {
                int size = buf.readVarInt();
                List<MaidEntry> list = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    list.add(MaidEntry.read(buf));
                }
                return new MaidListPayload(List.copyOf(list));
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
