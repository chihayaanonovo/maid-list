package com.fairymoon3.maidlist.client;

import com.fairymoon3.maidlist.MaidList;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.Minecraft;

import java.io.File;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 模型包自带语言文件的解析器。
 *
 * <p>车万女仆的模型包（`tlm_custom_pack/`）里的语言文件用的是**旧版 `.lang` 格式**，例如：</p>
 * <pre>
 * model.geckolib.winfox_saint.name=圣女酒狐
 * </pre>
 *
 * <p>而 Minecraft 的 {@code I18n} 只加载 `.json`，所以这些翻译永远不会出现在
 * {@code I18n.exists()} 里 —— 面板只能显示原始键名。</p>
 *
 * <p>这里自己把模型包里的 {@code assets/<ns>/lang/*.lang|*.json} 全部读一遍，
 * 建一份字典供模型名解析使用。支持目录包与 zip 包。</p>
 */
public final class MaidModelLangStore {

    private static final Gson GSON = new Gson();
    private static final Type JSON_TYPE = new TypeToken<LinkedHashMap<String, String>>() {
    }.getType();

    /** 优先中文，其次英文。 */
    private static final String[] LANG_PRIORITY = {"zh_cn", "zh_tw", "en_us"};

    private static final Map<String, String> DICT = new HashMap<>();
    private static boolean loaded;

    private MaidModelLangStore() {
    }

    /** 查一条翻译；找不到返回 null。 */
    public static String lookup(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        ensureLoaded();
        String value = DICT.get(key);
        if (value != null && !value.isBlank()) {
            return value;
        }
        // 兼容作者把 winefox 误写成 winfox 的情况
        if (key.contains("winfox")) {
            value = DICT.get(key.replace("winfox", "winefox"));
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        try {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft == null) {
                return;
            }
            File gameDir = minecraft.gameDirectory;
            List<Path> roots = List.of(
                    new File(gameDir, "tlm_custom_pack").toPath(),
                    new File(gameDir, "config/touhou_little_maid/custom_packs").toPath(),
                    new File(gameDir, "touhou_little_maid/custom_packs").toPath());
            for (Path root : roots) {
                if (Files.isDirectory(root)) {
                    scanRoot(root);
                }
            }
            MaidList.LOGGER.info("[女仆名单] 已从模型包读取 {} 条模型名翻译", DICT.size());
        } catch (Throwable t) {
            MaidList.LOGGER.debug("[女仆名单] 读取模型包语言文件失败", t);
        }
    }

    private static void scanRoot(Path root) {
        try {
            Files.walk(root, 2).forEach(path -> {
                try {
                    String name = path.getFileName() == null ? "" : path.getFileName().toString();
                    if (Files.isDirectory(path)) {
                        if (!path.equals(root)) {
                            readDirectoryPack(path);
                        }
                    } else if (name.toLowerCase().endsWith(".zip")) {
                        readZipPack(path);
                    }
                } catch (Throwable ignored) {
                    // 单个包失败不影响其它包
                }
            });
        } catch (Throwable ignored) {
            // 目录不可读就跳过
        }
    }

    private static void readDirectoryPack(Path packRoot) {
        Path assets = packRoot.resolve("assets");
        if (!Files.isDirectory(assets)) {
            return;
        }
        try {
            Files.walk(assets, 3).forEach(path -> {
                String fileName = path.getFileName() == null ? "" : path.getFileName().toString().toLowerCase();
                String full = path.toString().replace('\\', '/');
                if (!full.contains("/lang/")) {
                    return;
                }
                try {
                    if (fileName.endsWith(".lang")) {
                        parseLang(Files.readString(path, StandardCharsets.UTF_8), fileName);
                    } else if (fileName.endsWith(".json")) {
                        parseJson(Files.readString(path, StandardCharsets.UTF_8), fileName);
                    }
                } catch (Throwable ignored) {
                    // 单个文件失败跳过
                }
            });
        } catch (Throwable ignored) {
            // 跳过
        }
    }

    private static void readZipPack(Path zipPath) {
        try (ZipFile zip = new ZipFile(zipPath.toFile())) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName().replace('\\', '/');
                if (entry.isDirectory() || !name.contains("/lang/")) {
                    continue;
                }
                String lower = name.toLowerCase();
                if (!lower.endsWith(".lang") && !lower.endsWith(".json")) {
                    continue;
                }
                try (var stream = zip.getInputStream(entry)) {
                    String text = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                    if (lower.endsWith(".lang")) {
                        parseLang(text, lower);
                    } else {
                        parseJson(text, lower);
                    }
                } catch (Throwable ignored) {
                    // 跳过
                }
            }
        } catch (Throwable ignored) {
            // 跳过坏包
        }
    }

    /** 解析 {@code key=value} 形式的旧版 lang。 */
    private static void parseLang(String text, String fileName) {
        int rank = rankOf(fileName);
        for (String raw : text.split("\\R")) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int eq = line.indexOf('=');
            if (eq <= 0 || eq >= line.length() - 1) {
                continue;
            }
            put(line.substring(0, eq).trim(), line.substring(eq + 1).trim(), rank);
        }
    }

    private static void parseJson(String text, String fileName) {
        try {
            Map<String, String> map = GSON.fromJson(text, JSON_TYPE);
            if (map == null) {
                return;
            }
            int rank = rankOf(fileName);
            for (Map.Entry<String, String> entry : map.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    put(entry.getKey(), entry.getValue(), rank);
                }
            }
        } catch (Throwable ignored) {
            // 不是语言文件就算了
        }
    }

    /** 语言优先级：数字越小越优先。 */
    private static int rankOf(String fileName) {
        for (int i = 0; i < LANG_PRIORITY.length; i++) {
            if (fileName.startsWith(LANG_PRIORITY[i])) {
                return i;
            }
        }
        return LANG_PRIORITY.length;
    }

    /** 记录翻译与优先级，供同名键择优。 */
    private static final Map<String, Integer> RANK = new HashMap<>();

    private static void put(String key, String value, int rank) {
        if (key == null || value == null || key.isEmpty() || value.isEmpty()) {
            return;
        }
        Integer existing = RANK.get(key);
        if (existing == null || rank < existing) {
            DICT.put(key, value);
            RANK.put(key, rank);
        }
    }
}
