package com.fairymoon3.maidlist.client;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 被静音的女仆名单（客户端本地，按 UUID 记录）。
 *
 * <p>只影响本机播放：静音后连她的语音包一起拦掉，不影响服务端与其它玩家。</p>
 */
public final class MaidMuteStore {

    private static final Gson GSON = new Gson();
    private static final Type TYPE = new TypeToken<List<String>>() {
    }.getType();
    private static final Path FILE = FMLPaths.CONFIGDIR.get().resolve("maid_list_muted.json");

    private static final Set<UUID> MUTED = new LinkedHashSet<>();
    private static boolean loaded;

    private MaidMuteStore() {
    }

    private static void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        try {
            if (Files.exists(FILE)) {
                String json = Files.readString(FILE, StandardCharsets.UTF_8);
                List<String> raw = GSON.fromJson(json, TYPE);
                if (raw != null) {
                    for (String text : raw) {
                        try {
                            MUTED.add(UUID.fromString(text));
                        } catch (IllegalArgumentException ignored) {
                            // 跳过坏数据
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // 读取失败就用空名单
        }
    }

    public static boolean isMuted(UUID maidId) {
        if (maidId == null) {
            return false;
        }
        ensureLoaded();
        return MUTED.contains(maidId);
    }

    /** 切换静音状态，返回切换后的状态（true = 已静音）。 */
    public static boolean toggle(UUID maidId) {
        if (maidId == null) {
            return false;
        }
        ensureLoaded();
        boolean nowMuted;
        if (MUTED.contains(maidId)) {
            MUTED.remove(maidId);
            nowMuted = false;
        } else {
            MUTED.add(maidId);
            nowMuted = true;
        }
        save();
        return nowMuted;
    }

    private static void save() {
        try {
            List<String> raw = new ArrayList<>();
            for (UUID id : MUTED) {
                raw.add(id.toString());
            }
            Files.writeString(FILE, GSON.toJson(raw, TYPE), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // 写失败不影响本次会话
        }
    }
}
