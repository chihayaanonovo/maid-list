package com.fairymoon3.maidlist.mixin;

import com.fairymoon3.maidlist.config.PanelConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 解除狐梦 HUD「早点睡觉」按钮的距离 / 视线限制。
 *
 * <p>{@code CareOrderedSleep#begin} 开头是独立的一份校验（和动作包里的那份不共享）：</p>
 * <pre>
 *   if (!CareData.started(maid) || !maid.isAlive() || !maid.isOwnedBy(player)
 *       || player.isSpectator()
 *       || player.level() != maid.level()
 *       || player.distanceToSqr(maid) &gt; 64.0        // 8 格
 *       || !player.hasLineOfSight(maid)) {
 *       return false;
 *   }
 * </pre>
 * <p>所以动作包放开之后，「早点睡觉」仍然会在 8 格外静默失败。这里只接管距离与视线，
 * 已转化 / 存活 / 归属 / 旁观 / 同维度等条件原样保留。</p>
 */
@Mixin(targets = "cn.blockforge.generated.winefox1801000525.CareOrderedSleep")
public class CareOrderedSleepRangeMixin {

    private static final org.apache.logging.log4j.Logger LOGGER =
            org.apache.logging.log4j.LogManager.getLogger("fairymoon3_maid_list/mixin");
    private static final java.util.concurrent.atomic.AtomicBoolean LOGGED =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    @Redirect(
            method = "begin",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerPlayer;distanceToSqr(Lnet/minecraft/world/entity/Entity;)D"
            ),
            remap = false)
    private static double maidList$ignoreSleepOrderDistance(ServerPlayer player, Entity entity) {
        if (!PanelConfig.remoteMaidContainer()) {
            return player.distanceToSqr(entity);
        }
        if (LOGGED.compareAndSet(false, true)) {
            LOGGER.info("[女仆名单] 已接管狐梦『早点睡觉』的距离校验（允许远程下达）");
        }
        return 0.0D;
    }

    @Redirect(
            method = "begin",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerPlayer;hasLineOfSight(Lnet/minecraft/world/entity/Entity;)Z"
            ),
            remap = false)
    private static boolean maidList$ignoreSleepOrderLineOfSight(ServerPlayer player, Entity entity) {
        if (!PanelConfig.ignoreFoxdreamLineOfSight()) {
            return player.hasLineOfSight(entity);
        }
        return true;
    }
}
