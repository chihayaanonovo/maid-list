package com.fairymoon3.maidlist.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 仇恨名单（实体 ID -> 状态名）的网络编解码。
 *
 * <p>状态名直接用 {@code MonsterType} 的枚举名：{@code FRIENDLY} / {@code NEUTRAL} / {@code HOSTILE}，
 * 这样即使车万女仆以后增删枚举，也不会因为序号错位而串状态。</p>
 */
public final class AttackListCodec {

    private AttackListCodec() {
    }

    public static void write(RegistryFriendlyByteBuf buf, Map<ResourceLocation, String> map) {
        if (map == null || map.isEmpty()) {
            buf.writeVarInt(0);
            return;
        }
        buf.writeVarInt(map.size());
        for (Map.Entry<ResourceLocation, String> entry : map.entrySet()) {
            buf.writeResourceLocation(entry.getKey());
            buf.writeUtf(entry.getValue() == null ? "" : entry.getValue(), 32);
        }
    }

    public static Map<ResourceLocation, String> read(RegistryFriendlyByteBuf buf) {
        int size = buf.readVarInt();
        Map<ResourceLocation, String> map = new LinkedHashMap<>();
        for (int i = 0; i < size; i++) {
            ResourceLocation id = buf.readResourceLocation();
            String mode = buf.readUtf(32);
            map.put(id, mode);
        }
        return map;
    }
}
