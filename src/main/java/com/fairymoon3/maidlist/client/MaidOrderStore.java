package com.fairymoon3.maidlist.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.fairymoon3.maidlist.MaidList;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 女仆在面板里的自定义排列顺序（客户端本地持久化）。
 *
 * <p>存在 {@code config/fairymoon3_maid_list_order.json}，只影响本机显示顺序，不参与服务端逻辑。</p>
 *
 * <p>默认没有记录时按「玩家获得女仆的先后」排序；只有玩家用中键手动拖动过之后，
 * 这个文件里才会有明确的顺序。</p>
 */
public final class MaidOrderStore {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FMLPaths.CONFIGDIR.get().resolve("maid_list_order.json");

    private static List<UUID> order;
    private static boolean dirty;

    private MaidOrderStore() {
    }

    /** 女仆在自定义顺序里的位置；不在列表里返回 {@link Integer#MAX_VALUE}。 */
    public static int indexOf(UUID id) {
        ensureLoaded();
        int index = order.indexOf(id);
        return index < 0 ? Integer.MAX_VALUE : index;
    }

    /**
     * 把当前完整顺序写入存储，并把 {@code moving} 挪到 {@code targetIndex}。
     *
     * @param current 当前界面上看到的完整顺序（已排序）
     */
    public static void applyMove(List<UUID> current, UUID moving, int targetIndex) {
        ensureLoaded();
        List<UUID> next = new ArrayList<>(current);
        next.remove(moving);
        int clamp = Math.max(0, Math.min(targetIndex, next.size()));
        next.add(clamp, moving);
        order = next;
        dirty = true;
        save();
    }

    /** 首次看到某些女仆时，把它们补进顺序表尾部（保证默认顺序稳定）。 */
    public static void seed(List<UUID> seenInOrder) {
        ensureLoaded();
        boolean changed = false;
        for (UUID id : seenInOrder) {
            if (!order.contains(id)) {
                order.add(id);
                changed = true;
            }
        }
        if (changed) {
            save();
        }
    }

    private static void ensureLoaded() {
        if (order != null) {
            return;
        }
        order = new ArrayList<>();
        try {
            if (Files.exists(FILE)) {
                String json = Files.readString(FILE, StandardCharsets.UTF_8);
                List<String> raw = GSON.fromJson(json, new TypeToken<List<String>>() {
                }.getType());
                if (raw != null) {
                    for (String s : raw) {
                        try {
                            order.add(UUID.fromString(s));
                        } catch (Exception ignored) {
                            // 跳过损坏条目
                        }
                    }
                }
            }
        } catch (Exception e) {
            MaidList.LOGGER.warn("[女仆名单] 读取排列顺序失败，使用默认顺序", e);
            order = new ArrayList<>();
        }
    }

    private static void save() {
        try {
            List<String> raw = new ArrayList<>(order.size());
            for (UUID id : order) {
                raw.add(id.toString());
            }
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(raw), StandardCharsets.UTF_8);
            dirty = false;
        } catch (Exception e) {
            MaidList.LOGGER.warn("[女仆名单] 保存排列顺序失败", e);
        }
    }
}
