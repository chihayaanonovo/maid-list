package com.fairymoon3.maidlist.server;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.fairymoon3.maidlist.MaidList;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;

/**
 * 与「车万女仆：狐梦伴岁」（foxdream）的软联动。
 *
 * <p>关键点：**这个类里所有对 foxdream 的直接引用都必须放在被守卫的私有方法里**。
 * JVM 解析符号是懒执行的，只要没装上 foxdream，{@code available()} 就会先短路返回，
 * 永远不会执行到引用 {@code CareData} / {@code CareNetwork} 的那几行，因此不会
 * {@code NoClassDefFoundError}。</p>
 */
public final class FoxdreamCompat {

    public static final String MODID = "foxdream";

    /** 阶段值为 -1 表示「没有被狐梦手册转化过」。 */
    public static final int STAGE_NONE = -1;

    private FoxdreamCompat() {
    }

    public static boolean available() {
        try {
            return ModList.get().isLoaded(MODID);
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * 读取女仆的狐梦成长阶段。
     *
     * @return 0/1/2… 表示已转化；{@link #STAGE_NONE} 表示未转化或没装 mod
     */
    public static int stage(EntityMaid maid) {
        if (maid == null || !available()) {
            return STAGE_NONE;
        }
        try {
            return stage0(maid);
        } catch (Throwable t) {
            MaidList.LOGGER.debug("[女仆名单] 读取狐梦成长阶段失败", t);
            return STAGE_NONE;
        }
    }

    /**
     * 打开狐梦手册面板 —— 等价于「手持狐梦陪伴手册右键这只女仆」。
     *
     * <p>{@code CareNetwork.send(maid, player, true)} 就是 foxdream 手册右键进去的那条
     * 服务端→客户端路径：它会做「是否已转化」的校验，然后推送 {@code State} 包，
     * 客户端收到后自己打开 {@code CareScreen}。</p>
     *
     * @return 是否成功触发
     */
    public static boolean openHandbook(ServerPlayer player, EntityMaid maid) {
        if (player == null || maid == null || !available()) {
            return false;
        }
        try {
            return openHandbook0(player, maid);
        } catch (Throwable t) {
            MaidList.LOGGER.warn("[女仆名单] 打开狐梦手册失败", t);
            return false;
        }
    }

    // ------------------------------------------------------------ 以下方法只在装有 foxdream 时执行

    private static int stage0(EntityMaid maid) {
        if (!cn.blockforge.generated.winefox1801000525.CareData.started(maid)) {
            return STAGE_NONE;
        }
        var data = cn.blockforge.generated.winefox1801000525.CareData.load(maid);
        return data == null ? STAGE_NONE : data.stage;
    }

    /**
     * 读取狐梦成长体系里的生活数值（睡眠 / 满足 / 心情 / 信赖 / 好感 / 相伴天数 / 三餐）。
     *
     * @return 未转化或读取失败时返回 {@code null}
     */
    public static com.fairymoon3.maidlist.network.FoxStats stats(EntityMaid maid) {
        if (maid == null || !available()) {
            return null;
        }
        try {
            return stats0(maid);
        } catch (Throwable t) {
            MaidList.LOGGER.debug("[女仆名单] 读取狐梦成长数值失败", t);
            return null;
        }
    }

    private static com.fairymoon3.maidlist.network.FoxStats stats0(EntityMaid maid) {
        if (!cn.blockforge.generated.winefox1801000525.CareData.started(maid)) {
            return null;
        }
        var data = cn.blockforge.generated.winefox1801000525.CareData.load(maid);
        if (data == null || !data.started) {
            return null;
        }

        // 三餐：meals 是「已吃份数」，>=1 就算吃过
        int mealsMask = 0;
        int[] meals = data.meals;
        if (meals != null) {
            for (int i = 0; i < Math.min(3, meals.length); i++) {
                if (meals[i] >= 1) {
                    mealsMask |= 1 << i;
                }
            }
        }

        // 初始性格（幼年期就带着的那一项，不是青年期累积出来的）
        String innate = "";
        try {
            String[] names = cn.blockforge.generated.winefox1801000525.CareRules.INNATE;
            int index = data.innate;
            if (names != null && index >= 0 && index < names.length) {
                innate = names[index] == null ? "" : names[index];
            }
        } catch (Throwable ignored) {
            // 读不到就不显示
        }

        // 信赖：data.trust 是「档位索引」，data.trustProgress 才是该档内的百分比
        String trustLevel = "";
        int trustPercent = 0;
        try {
            String[] trustNames = cn.blockforge.generated.winefox1801000525.CareRules.TRUST;
            int cap = Math.max(1, cn.blockforge.generated.winefox1801000525.CareRules.trustCap(data.stage));
            int level = Math.max(0, Math.min(cap - 1, data.trust));
            if (trustNames != null && level < trustNames.length) {
                trustLevel = trustNames[level] == null ? "" : trustNames[level];
            }
            trustPercent = clamp100((int) Math.round(data.trustProgress));
        } catch (Throwable ignored) {
            // 同上
        }

        // 好感等级：与狐梦 HUD 的阈值完全一致（<64 级0 / <192 级1 / <384 级2 / 其余级3）
        int favorPoints = Math.max(0, data.favor);
        int favorLevel = favorPoints < 64 ? 0 : favorPoints < 192 ? 1 : favorPoints < 384 ? 2 : 3;
        int favorCap = 64;
        try {
            favorCap = cn.blockforge.generated.winefox1801000525.CareRules.favorCap(data.stage);
        } catch (Throwable ignored) {
            // 用默认值
        }

        // 相伴时长直接借狐梦自己的格式化（它会算成"x天"这种）
        String daysText = "";
        try {
            daysText = cn.blockforge.generated.winefox1801000525.CompanionRules.days(data.companionTicks);
        } catch (Throwable ignored) {
            // 读不到就空着
        }

        return new com.fairymoon3.maidlist.network.FoxStats(
                clamp100((int) Math.round(data.sleep)),
                clamp100((int) Math.round(data.satisfaction)),
                clamp100((int) Math.round(data.mood)),
                trustPercent,
                trustLevel,
                favorLevel,
                favorPoints,
                Math.max(1, favorCap),
                daysText == null ? "" : daysText,
                mealsMask,
                innate);
    }

    private static int clamp100(int value) {
        return Math.max(0, Math.min(100, value));
    }

    private static boolean openHandbook0(ServerPlayer player, EntityMaid maid) {
        if (!cn.blockforge.generated.winefox1801000525.CareData.started(maid)) {
            return false;
        }
        cn.blockforge.generated.winefox1801000525.CareNetwork.send(maid, player, true);
        return true;
    }
}
