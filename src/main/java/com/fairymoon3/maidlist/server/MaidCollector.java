package com.fairymoon3.maidlist.server;

import com.github.tartaricacid.touhoulittlemaid.api.backpack.IMaidBackpack;
import com.github.tartaricacid.touhoulittlemaid.api.task.IMaidTask;
import com.github.tartaricacid.touhoulittlemaid.entity.favorability.FavorabilityManager;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.world.data.MaidInfo;
import com.github.tartaricacid.touhoulittlemaid.world.data.MaidWorldData;
import com.fairymoon3.maidlist.MaidList;
import com.fairymoon3.maidlist.network.MaidEntry;
import com.fairymoon3.maidlist.network.MaidListPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 服务端：女仆名册收集 + 传送。
 *
 * <p>名册分两层来源：</p>
 * <ol>
 *   <li>各维度已加载的 {@code EntityMaid} 实体 —— 信息最全</li>
 *   <li>车万女仆自带的 {@code MaidWorldData} 持久索引 —— 补上没加载的女仆</li>
 * </ol>
 *
 * <p>传送逻辑照搬车万女仆的仆人铃（{@code ItemServantBell.teleportMaid}）：
 * 取消驻守 → 下坐骑 → 挂 10 秒发光 → 传送到玩家身边 ±1 格。</p>
 */
public final class MaidCollector {

    private MaidCollector() {
    }

    // ------------------------------------------------------------------ 名册

    /** 收集并回传给指定玩家。 */
    public static void sendPanel(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new MaidListPayload(collect(player)));
    }

    /** 收集该玩家拥有的女仆信息。必须在服务端主线程调用。 */
    public static List<MaidEntry> collect(ServerPlayer player) {
        Map<UUID, MaidEntry> result = new LinkedHashMap<>();
        UUID ownerId = player.getUUID();
        MinecraftServer server = player.getServer();
        if (server == null) {
            return new ArrayList<>();
        }

        // 0) 先拿持久索引：里面带着「玩家获得女仆的时间戳」，用于默认排序
        Map<UUID, MaidInfo> infos = new LinkedHashMap<>();
        try {
            MaidWorldData data = MaidWorldData.get(player.level());
            if (data != null) {
                List<MaidInfo> list = data.getPlayerMaidInfos(player);
                if (list != null) {
                    for (MaidInfo info : list) {
                        if (info != null && info.getEntityId() != null) {
                            infos.put(info.getEntityId(), info);
                        }
                    }
                }
            }
        } catch (Exception e) {
            MaidList.LOGGER.debug("[女仆名单] 读取女仆持久索引失败", e);
        }

        // 1) 已加载实体：信息最全，顺手写进快照缓存
        PanelCache cache = null;
        try {
            cache = PanelCache.get(server);
        } catch (Exception e) {
            MaidList.LOGGER.debug("[女仆名单] 快照缓存不可用", e);
        }
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getEntities().getAll()) {
                if (entity instanceof EntityMaid maid && ownerId.equals(maid.getOwnerUUID())) {
                    MaidInfo info = infos.get(maid.getUUID());
                    long acquiredAt = info == null ? 0L : info.getTimestamp();
                    MaidEntry entry = fromEntity(maid, level, acquiredAt);
                    result.put(maid.getUUID(), entry);
                    if (cache != null) {
                        cache.record(entry);
                    }
                }
            }
        }

        // 2) 未加载的女仆：优先快照，其次索引里的基本信息
        try {
            long now = System.currentTimeMillis();
            for (MaidInfo info : infos.values()) {
                MaidEntry cached = cache == null ? null : cache.snapshot(info.getEntityId(), now);
                result.computeIfAbsent(info.getEntityId(),
                        id -> cached != null ? cached : fromInfo(info));
            }
        } catch (Exception e) {
            MaidList.LOGGER.debug("[女仆名单] 合并女仆索引失败", e);
        }

        return new ArrayList<>(result.values());
    }

    private static MaidEntry fromEntity(EntityMaid maid, ServerLevel level, long acquiredAt) {
        IMaidTask task = safeTask(maid);
        ResourceLocation taskId = task != null && task.getUid() != null
                ? task.getUid()
                : ResourceLocation.fromNamespaceAndPath("touhou_little_maid", "idle");
        ItemStack taskIcon = task != null ? safeIcon(task) : ItemStack.EMPTY;

        int favorLevel = 0;
        FavorabilityManager favorability = maid.getFavorabilityManager();
        if (favorability != null) {
            favorLevel = favorability.getLevel();
        }

        IMaidBackpack backpack = maid.getMaidBackpackType();
        ItemStack backpackIcon = ItemStack.EMPTY;
        String backpackName = "";
        if (backpack != null) {
            Item item = backpack.getItem();
            if (item != null && item != Items.AIR) {
                backpackIcon = new ItemStack(item);
                try {
                    backpackName = backpackIcon.getHoverName().getString();
                } catch (Exception ignored) {
                    backpackName = "";
                }
            }
        }

        BlockPos pos = maid.blockPosition();
        return new MaidEntry(
                maid.getUUID(),
                maid.getName().getString(),
                true,
                taskId,
                taskIcon,
                Math.max(0, Math.round(maid.getHealth())),
                Math.max(0, Math.round(maid.getMaxHealth())),
                maid.getFavorability(),
                favorLevel,
                maid.getExperience(),
                safe(maid::isHomeModeEnable, false),
                safe(maid::isMaidInSittingPose, false),
                safe(maid::isSleeping, false),
                safe(() -> maid.getTarget() != null, false),
                safe(maid::hasBackpack, false),
                backpackIcon,
                backpackName,
                safe(maid::getModelId, ""),
                safe(maid::isYsmModel, false),
                level.dimension().location(),
                maid.getX(),
                maid.getY(),
                maid.getZ(),
                pos.getX() >> 4,
                pos.getZ() >> 4,
                FoxdreamCompat.stage(maid),
                acquiredAt,
                0,
                FoxdreamCompat.stats(maid)
        );
    }

    private static MaidEntry fromInfo(MaidInfo info) {
        ResourceLocation dim = parseDimension(info.getDimension());
        BlockPos pos = info.getChunkPos();
        String name;
        try {
            name = info.getName() == null ? "?" : info.getName().getString();
        } catch (Exception e) {
            name = "?";
        }
        return new MaidEntry(
                info.getEntityId(),
                name,
                false,
                ResourceLocation.fromNamespaceAndPath("touhou_little_maid", "idle"),
                ItemStack.EMPTY,
                0, 0,
                0, 0, 0,
                false, false, false, false,
                false,
                ItemStack.EMPTY,
                "",
                "",
                false,
                dim,
                0.0D, 0.0D, 0.0D,
                pos.getX(), pos.getZ(), FoxdreamCompat.STAGE_NONE, info.getTimestamp(), 0, null
        );
    }

    private static ResourceLocation parseDimension(String raw) {
        if (raw == null || raw.isBlank()) {
            return ResourceLocation.fromNamespaceAndPath("minecraft", "overworld");
        }
        try {
            return ResourceLocation.parse(raw);
        } catch (Exception e) {
            return ResourceLocation.fromNamespaceAndPath("minecraft", "overworld");
        }
    }

    // ------------------------------------------------------------------ 传送

    /**
     * 打开这只女仆自己的界面 —— 等价于玩家直接右键女仆。
     *
     * <p>走的是车万女仆自己的入口 {@code EntityMaid#openMaidGui(Player)}，所以标签页、
     * 背包、装备、任务配置这些和右键打开时完全一致。</p>
     *
     * <p>注意：女仆在睡觉时原版入口会直接返回 false（不肯被打扰）。</p>
     *
     * @return 是否成功打开
     */
    public static boolean openMaidGui(ServerPlayer player, UUID maidId) {
        if (maidId == null) {
            return false;
        }
        List<? extends EntityMaid> maids = player.serverLevel().getEntities(EntityMaid.TYPE,
                maid -> maidId.equals(maid.getUUID()) && maid.isOwnedBy(player));
        if (maids.isEmpty()) {
            displayFeedback(player, "fairymoon3_maid_list.gui.notfound");
            return false;
        }

        EntityMaid maid = maids.get(0);
        if (maid.isSleeping()) {
            displayFeedback(player, "fairymoon3_maid_list.gui.sleeping", maid.getName().getString());
            return false;
        }
        if (!maid.openMaidGui(player)) {
            displayFeedback(player, "fairymoon3_maid_list.gui.notfound");
            return false;
        }
        return true;
    }

    /**
     * 远程切换女仆的坐下 / 站立状态。
     *
     * <p>用车万女仆自己的 {@code EntityMaid#setInSittingPose(boolean)}，
     * 它内部同时处理姿势与 {@code orderedToSit}，等价于玩家右键让她坐下。</p>
     */
    public static void toggleSitting(ServerPlayer player, UUID maidId) {
        if (maidId == null || throttled(player, "sit", 20)) {
            return;
        }
        List<? extends EntityMaid> found = player.serverLevel().getEntities(EntityMaid.TYPE,
                maid -> maidId.equals(maid.getUUID()) && maid.isOwnedBy(player) && maid.isAlive());
        if (found.isEmpty()) {
            displayFeedback(player, "fairymoon3_maid_list.sit.notfound");
            return;
        }
        EntityMaid maid = found.get(0);
        boolean sitting = maid.isMaidInSittingPose();
        boolean next = !sitting;
        try {
            maid.setInSittingPose(next);
            if (next) {
                maid.getNavigation().stop();
            }
        } catch (Exception e) {
            MaidList.LOGGER.warn("[女仆名单] 切换坐下状态失败", e);            return;
        }
        player.sendSystemMessage(Component.translatable(
                next ? "fairymoon3_maid_list.sit.done.sit" : "fairymoon3_maid_list.sit.done.stand",
                maid.getName().getString()));
        sendPanel(player);
    }

    /** 聊天框提示（左下角），保证玩家能看到失败原因。 */
    private static void displayFeedback(ServerPlayer player, String key, Object... args) {
        player.sendSystemMessage(Component.translatable(key, args));
    }

    /** 服务端冷却（tick），防止客户端被绕过时刷包。 */
    private static boolean throttled(ServerPlayer player, String kind, int ticks) {
        try {
            var data = player.getPersistentData();
            String tag = "MaidListCd_" + kind;
            long now = player.serverLevel().getGameTime();
            long last = data.getLong(tag);
            if (last != 0L && now - last < ticks) {
                return true;
            }
            data.putLong(tag, now);
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 打开狐梦陪伴手册面板（软联动；没装 foxdream 时直接失败）。
     *
     * @return 是否成功
     */
    public static boolean openFoxdream(ServerPlayer player, UUID maidId) {
        if (maidId == null || !FoxdreamCompat.available()) {
            return false;
        }
        List<? extends EntityMaid> maids = player.serverLevel().getEntities(EntityMaid.TYPE,
                maid -> maidId.equals(maid.getUUID()) && maid.isOwnedBy(player));
        if (maids.isEmpty()) {
            displayFeedback(player, "fairymoon3_maid_list.gui.notfound");
            return false;
        }
        EntityMaid maid = maids.get(0);
        if (!FoxdreamCompat.openHandbook(player, maid)) {
            displayFeedback(player, "fairymoon3_maid_list.foxdream.notconverted");
            return false;
        }
        return true;
    }

    /** 传送失败时给玩家一句提示。 */    public static void sendTeleportFailure(ServerPlayer player, UUID maidId) {
        String name = null;
        try {
            MaidWorldData data = MaidWorldData.get(player.level());
            if (data != null) {
                List<MaidInfo> infos = data.getPlayerMaidInfos(player);
                if (infos != null) {
                    name = infos.stream()
                            .filter(info -> info != null && maidId.equals(info.getEntityId()))
                            .findFirst()
                            .map(info -> info.getName() == null ? null : info.getName().getString())
                            .orElse(null);
                }
            }
        } catch (Exception ignored) {
            // 拿不到名字也无所谓
        }
        Component message = name == null
                ? Component.translatable("fairymoon3_maid_list.tp.notfound")
                : Component.translatable("fairymoon3_maid_list.tp.notfound.named", name);
        player.displayClientMessage(message, true);
    }

    // ------------------------------------------------------------------ 兜底

    private static IMaidTask safeTask(EntityMaid maid) {
        try {
            return maid.getTask();
        } catch (Exception e) {
            MaidList.LOGGER.debug("[女仆名单] 读取女仆任务失败: {}", maid.getUUID(), e);
            return null;
        }
    }

    private static ItemStack safeIcon(IMaidTask task) {
        try {
            ItemStack icon = task.getIcon();
            return icon == null ? ItemStack.EMPTY : icon;
        } catch (Exception e) {
            return ItemStack.EMPTY;
        }
    }

    /** 女仆数据可能在初始化早期不完整，取不到就给默认值，避免整份名册失败。 */
    private static <T> T safe(Supplier<T> supplier, T fallback) {
        try {
            T value = supplier.get();
            return value == null ? fallback : value;
        } catch (Exception e) {
            return fallback;
        }
    }

    @FunctionalInterface
    private interface Supplier<T> {
        T get();
    }
}
