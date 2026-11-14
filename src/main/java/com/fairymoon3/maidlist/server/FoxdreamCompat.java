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

    private static boolean openHandbook0(ServerPlayer player, EntityMaid maid) {
        if (!cn.blockforge.generated.winefox1801000525.CareData.started(maid)) {
            return false;
        }
        cn.blockforge.generated.winefox1801000525.CareNetwork.send(maid, player, true);
        return true;
    }
}
