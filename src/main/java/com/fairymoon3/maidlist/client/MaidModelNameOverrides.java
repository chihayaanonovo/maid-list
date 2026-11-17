package com.fairymoon3.maidlist.client;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 模型名手动映射表。
 *
 * <p>多人服务器下发的模型包可能只同步了模型本体、没有带语言文件，
 * 此时客户端拿不到翻译，只能显示模型 ID。这个文件允许玩家自己补一条映射：</p>
 *
 * <pre>
 * {
 *   "model.geckolib.winfox_saint.name": "圣女酒狐"
 * }
 * </pre>
 *
 * <p>文件位置：{@code config/maid_list_model_names.json}。改完重进游戏或重新载入语言即可生效。</p>
 */
public final class MaidModelNameOverrides {

    private static final Gson GSON = new Gson();
    private static final Type TYPE = new TypeToken<LinkedHashMap<String, String>>() {
    }.getType();
    private static final Path FILE = FMLPaths.CONFIGDIR.get().resolve("maid_list_model_names.json");

    private static Map<String, String> overrides;
    private static boolean loaded;

    private MaidModelNameOverrides() {
    }

    private static void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        overrides = new LinkedHashMap<>();
        try {
            if (Files.exists(FILE)) {
                String json = Files.readString(FILE, StandardCharsets.UTF_8);
                Map<String, String> parsed = GSON.fromJson(json, TYPE);
                if (parsed != null) {
                    overrides.putAll(parsed);
                }
            } else {
                // 首次生成一个空的模板，方便玩家照着填
                Files.writeString(FILE, "{\n  \"model.geckolib.winfox_saint.name\": \"圣女酒狐\"\n}\n",
                        StandardCharsets.UTF_8);
            }
        } catch (IOException ignored) {
            // 读不到就当没有
        }
    }

    /**
     * 查映射表。
     *
     * @param keys 依次尝试的键（原始键、抽出的模型 ID 等）
     * @return 命中且非空的名字；没命中返回 {@code null}
     */
    public static String lookup(String... keys) {
        ensureLoaded();
        if (overrides == null || overrides.isEmpty()) {
            return null;
        }
        for (String key : keys) {
            if (key == null || key.isBlank()) {
                continue;
            }
            String value = overrides.get(key);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
