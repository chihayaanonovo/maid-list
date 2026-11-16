package com.fairymoon3.maidlist.client;

import com.fairymoon3.maidlist.network.MaidListRequestPayload;
import com.fairymoon3.maidlist.network.MaidRestorePayload;
import com.fairymoon3.maidlist.network.MaidStoragePayload;
import com.fairymoon3.maidlist.network.MaidStorageRequestPayload;
import com.fairymoon3.maidlist.network.StoredMaid;
import com.google.gson.Gson;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.network.PacketDistributor;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 客户端收纳数据入口 + 本地完整 NBT 备份。
 *
 * <p>备份文件 {@code config/maid_list_storage_backup.json} 保存每只收纳女仆的完整实体 NBT。
 * 恢复只在**玩家主动打开收纳空间**时检查一次，避免收纳 / 释放时突然弹出恢复提示。</p>
 */
public final class ClientMaidStorage {

    private static final Gson GSON = new Gson();
    private static final Path BACKUP =
            FMLPaths.CONFIGDIR.get().resolve("maid_list_storage_backup.json");
    private static final int MAX_RESTORE_PER_SESSION = 32;
    /** 同一条备份最多尝试恢复几次，超过就放弃，避免每次上线刷拒绝提示。 */
    private static final int MAX_ATTEMPTS = 3;
    private static final String ORIGIN_TAG = "MaidListOrigin";

    private static List<StoredMaid> entries = List.of();
    /** 是否等待服务端回包后打开界面（只有右键神龛才会置位）。 */
    private static boolean pendingOpen;
    private static boolean restoreChecked;
    private static boolean warnedMissing;
    /** 本次会话里已发出恢复请求、还没成功的条目。 */
    private static final Map<String, BackupEntry> PENDING = new LinkedHashMap<>();

    private ClientMaidStorage() {
    }

    public static List<StoredMaid> entries() {
        return entries;
    }

    public static void handle(MaidStoragePayload payload) {
        entries = new ArrayList<>(payload.entries());
        Minecraft minecraft = Minecraft.getInstance();

        // 只有「玩家主动打开收纳空间」才做一次恢复检查；
        // 收纳 / 释放引起的推送不做，避免突然刷出恢复提示。
        boolean openRequested = pendingOpen;
        if (openRequested && !restoreChecked) {
            restoreChecked = true;
            tryRestoreMissing(entries);
        }
        writeBackup(entries);

        if (minecraft.screen instanceof MaidStorageScreen screen) {
            screen.refresh();
            pendingOpen = false;
            return;
        }
        if (openRequested) {
            pendingOpen = false;
            minecraft.setScreen(new MaidStorageScreen());
        }
    }

    /** 玩家右键神龛：请求列表并标记「收到后要打开界面」。 */
    public static void requestOpen() {
        pendingOpen = true;
        PacketDistributor.sendToServer(new MaidStorageRequestPayload());
    }

    /** 关闭收纳空间后：请求名册，服务端回包会自动打开主面板。 */
    public static void reopenPanel() {
        PacketDistributor.sendToServer(new MaidListRequestPayload());
    }

    // ------------------------------------------------------------------ 备份与恢复

    private static void tryRestoreMissing(List<StoredMaid> current) {
        try {
            BackupData backup = readBackup();
            if (backup == null || backup.entries == null || backup.entries.isEmpty()) {
                return;
            }
            Set<String> currentIds = new LinkedHashSet<>();
            for (StoredMaid stored : current) {
                currentIds.add(stored.uuid().toString());
            }

            int sent = 0;
            int legacy = 0;
            for (BackupEntry entry : backup.entries) {
                if (entry == null || entry.uuid == null) {
                    continue;
                }
                if (currentIds.contains(entry.uuid)) {
                    // 已经在服务端列表里了，不用恢复
                    PENDING.remove(entry.uuid);
                    continue;
                }
                if (!hasOriginStamp(entry.tag)) {
                    // 旧版本记录的备份没有存档签名，永远无法通过校验 —— 直接放弃，
                    // 否则每次上线都会收到「恢复被拒绝」。
                    legacy++;
                    continue;
                }
                if (entry.attempts >= MAX_ATTEMPTS) {
                    continue;
                }
                if (sent >= MAX_RESTORE_PER_SESSION) {
                    continue;
                }
                CompoundTag tag = decodeTag(entry.tag);
                if (tag == null) {
                    continue;
                }
                entry.attempts++;
                PENDING.put(entry.uuid, entry);
                PacketDistributor.sendToServer(new MaidRestorePayload(entry.name, tag));
                sent++;
            }

            var player = Minecraft.getInstance().player;
            if (player != null) {
                if (sent > 0 && !warnedMissing) {
                    warnedMissing = true;
                    player.displayClientMessage(Component.translatable(
                            "fairymoon3_maid_list.storage.restore.missing", sent), false);
                } else if (legacy > 0 && !warnedMissing) {
                    warnedMissing = true;
                    player.displayClientMessage(Component.translatable(
                            "fairymoon3_maid_list.storage.restore.legacy", legacy), false);
                }
            }
        } catch (Throwable ignored) {
            // 备份只是保险，失败不影响功能
        }
    }

    /** 备份 = 服务端当前列表 ∪ 本次还没恢复成功的条目（其余一律丢弃，避免备份无限膨胀）。 */
    private static void writeBackup(List<StoredMaid> current) {
        try {
            Map<String, BackupEntry> merged = new LinkedHashMap<>();
            for (StoredMaid stored : current) {
                BackupEntry entry = new BackupEntry();
                entry.uuid = stored.uuid().toString();
                entry.name = stored.name();
                entry.hp = stored.hp();
                entry.maxHp = stored.maxHp();
                entry.tag = encodeTag(stored.tag());
                entry.attempts = 0;
                merged.put(entry.uuid, entry);
            }
            merged.putAll(PENDING);

            BackupData data = new BackupData();
            data.updated = System.currentTimeMillis();
            data.entries = new ArrayList<>(merged.values());
            Files.writeString(BACKUP, GSON.toJson(data), StandardCharsets.UTF_8);
        } catch (Throwable ignored) {
            // 写失败不影响游戏
        }
    }

    private static boolean hasOriginStamp(String encodedTag) {
        try {
            if (encodedTag == null || encodedTag.isBlank()) {
                return false;
            }
            byte[] raw = Base64.getDecoder().decode(encodedTag);
            String text = new String(raw, StandardCharsets.ISO_8859_1);
            return text.contains(ORIGIN_TAG);
        } catch (Throwable t) {
            return false;
        }
    }

    private static BackupData readBackup() {
        try {
            if (!Files.exists(BACKUP)) {
                return null;
            }
            String json = Files.readString(BACKUP, StandardCharsets.UTF_8);
            return GSON.fromJson(json, BackupData.class);
        } catch (Throwable t) {
            return null;
        }
    }

    private static String encodeTag(CompoundTag tag) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            NbtIo.write(tag == null ? new CompoundTag() : tag, new DataOutputStream(bytes));
            return Base64.getEncoder().encodeToString(bytes.toByteArray());
        } catch (Throwable t) {
            return "";
        }
    }

    private static CompoundTag decodeTag(String encoded) {
        try {
            if (encoded == null || encoded.isBlank()) {
                return null;
            }
            byte[] raw = Base64.getDecoder().decode(encoded);
            return NbtIo.read(new DataInputStream(new ByteArrayInputStream(raw)));
        } catch (Throwable t) {
            return null;
        }
    }

    private static final class BackupData {
        long updated;
        List<BackupEntry> entries;
    }

    private static final class BackupEntry {
        String uuid;
        String name;
        int hp;
        int maxHp;
        String tag;
        int attempts;
    }
}
