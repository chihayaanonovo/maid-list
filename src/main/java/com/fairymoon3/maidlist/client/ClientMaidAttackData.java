package com.fairymoon3.maidlist.client;

import com.fairymoon3.maidlist.network.MaidAttackListPayload;
import com.fairymoon3.maidlist.network.MaidAttackRequestPayload;
import com.google.gson.Gson;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.network.PacketDistributor;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 客户端仇恨名单数据：当前编辑的女仆、名单内容、剪贴板，以及本地备份。
 *
 * <p>本地备份 {@code config/maid_list_attack_backup.json} 只是留档，方便模组更新后对照，
 * 真正的数据始终在女仆实体上（和本体攻击面板同一份）。</p>
 */
public final class ClientMaidAttackData {

    private static final Gson GSON = new Gson();
    private static final Path BACKUP = FMLPaths.CONFIGDIR.get().resolve("maid_list_attack_backup.json");

    private static UUID maidId;
    private static String maidName = "";
    private static Map<ResourceLocation, String> entries = new LinkedHashMap<>();
    private static Map<ResourceLocation, String> clipboard = new LinkedHashMap<>();
    private static boolean pendingOpen;

    private ClientMaidAttackData() {
    }

    public static UUID maidId() {
        return maidId;
    }

    public static String maidName() {
        return maidName;
    }

    public static Map<ResourceLocation, String> entries() {
        return entries;
    }

    public static boolean hasClipboard() {
        return !clipboard.isEmpty();
    }

    public static Map<ResourceLocation, String> clipboard() {
        return clipboard;
    }

    public static void copyAll() {
        clipboard = new LinkedHashMap<>(entries);
    }

    /** 最近修改过的实体（**最新在最前**），用于把刚改过的实体排到列表最上面。 */
    private static final java.util.LinkedList<ResourceLocation> RECENT = new java.util.LinkedList<>();
    private static final int RECENT_LIMIT = 256;

    /** 记录一次「改成某个状态」。取消修改（移除）时不应该调用它。 */
    public static void markRecent(ResourceLocation id) {
        if (id == null) {
            return;
        }
        RECENT.remove(id);
        RECENT.addFirst(id);
        while (RECENT.size() > RECENT_LIMIT) {
            RECENT.removeLast();
        }
    }

    /** 返回该实体的「最近修改」排名，0 = 最近一次修改；没改过返回 Integer.MAX_VALUE。 */
    public static int recentRank(ResourceLocation id) {
        if (id == null || RECENT.isEmpty()) {
            return Integer.MAX_VALUE;
        }
        int rank = RECENT.indexOf(id);
        return rank < 0 ? Integer.MAX_VALUE : rank;
    }

    public static void requestOpen(UUID id) {
        pendingOpen = true;
        PacketDistributor.sendToServer(new MaidAttackRequestPayload(id));
    }

    public static void handle(MaidAttackListPayload payload) {
        maidId = payload.maidId();
        maidName = payload.maidName();
        entries = new LinkedHashMap<>(payload.entries());
        writeBackup();

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof MaidAttackListScreen screen) {
            screen.refresh();
            pendingOpen = false;
            return;
        }
        if (pendingOpen) {
            pendingOpen = false;
            minecraft.setScreen(new MaidAttackListScreen());
        }
    }

    /** 把当前女仆的名单写进本地备份（按女仆 UUID 归档）。 */
    private static void writeBackup() {
        try {
            BackupData data = readBackup();
            if (data == null) {
                data = new BackupData();
                data.maids = new LinkedHashMap<>();
            }
            if (data.maids == null) {
                data.maids = new LinkedHashMap<>();
            }
            if (maidId != null) {
                BackupEntry entry = new BackupEntry();
                entry.name = maidName;
                entry.updated = System.currentTimeMillis();
                entry.entries = new LinkedHashMap<>();
                for (Map.Entry<ResourceLocation, String> e : entries.entrySet()) {
                    entry.entries.put(e.getKey().toString(), e.getValue());
                }
                data.maids.put(maidId.toString(), entry);
            }
            Files.writeString(BACKUP, GSON.toJson(data), StandardCharsets.UTF_8);
        } catch (Throwable ignored) {
            // 备份失败不影响使用
        }
    }

    private static BackupData readBackup() {
        try {
            if (!Files.exists(BACKUP)) {
                return null;
            }
            return GSON.fromJson(Files.readString(BACKUP, StandardCharsets.UTF_8), BackupData.class);
        } catch (Throwable t) {
            return null;
        }
    }

    private static final class BackupData {
        Map<String, BackupEntry> maids;
    }

    private static final class BackupEntry {
        String name;
        long updated;
        Map<String, String> entries;
    }
}
