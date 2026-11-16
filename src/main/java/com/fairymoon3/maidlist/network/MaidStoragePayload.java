package com.fairymoon3.maidlist.network;

import com.fairymoon3.maidlist.MaidList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/** 服务端 -> 客户端：收纳空间的一份列表。 */
public record MaidStoragePayload(List<StoredMaid> entries) implements CustomPacketPayload {

    public static final Type<MaidStoragePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MaidList.MODID, "maid_storage"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MaidStoragePayload> STREAM_CODEC = StreamCodec.of(
            (RegistryFriendlyByteBuf buf, MaidStoragePayload payload) -> {
                buf.writeVarInt(payload.entries().size());
                for (StoredMaid entry : payload.entries()) {
                    entry.write(buf);
                }
            },
            (RegistryFriendlyByteBuf buf) -> {
                int size = buf.readVarInt();
                List<StoredMaid> list = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    list.add(StoredMaid.read(buf));
                }
                return new MaidStoragePayload(List.copyOf(list));
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
