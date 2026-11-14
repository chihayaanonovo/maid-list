package com.fairymoon3.maidlist.mixin;

import com.fairymoon3.maidlist.config.PanelConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 解除狐梦 HUD 动作包的距离 / 视线限制。
 *
 * <p>狐梦 HUD 里**所有**按钮（打开背包、早点睡觉……）都走同一个动作包，服务端处理时先做：</p>
 * <pre>
 *   if (!(maid.isAlive()
 *         &amp;&amp; maid.isOwnedBy(player)
 *         &amp;&amp; player.distanceToSqr(maid) &lt;= 64.0      // 8 格
 *         &amp;&amp; player.hasLineOfSight(maid))) {
 *       return;                                     // 静默丢弃，玩家看不到任何反馈
 *   }
 * </pre>
 * <p>所以超出 8 格后点任何按钮都没反应。这里只接管距离与视线两项，
 * 归属 / 存活等其它判断原样保留。</p>
 */
@Mixin(targets = "cn.blockforge.generated.winefox1801000525.CareNetwork")
public class CareNetworkActionRangeMixin {

    private static final org.apache.logging.log4j.Logger LOGGER =
            org.apache.logging.log4j.LogManager.getLogger("fairymoon3_maid_list/mixin");
    private static final java.util.concurrent.atomic.AtomicBoolean LOGGED =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    @Redirect(
            method = "lambda$register$17",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerPlayer;distanceToSqr(Lnet/minecraft/world/entity/Entity;)D"
            ),
            remap = false)
    private static double maidList$ignoreActionDistance(ServerPlayer player, Entity entity) {
        if (!PanelConfig.remoteMaidContainer()) {
            return player.distanceToSqr(entity);
        }
        if (LOGGED.compareAndSet(false, true)) {
            LOGGER.info("[女仆名单] 已接管狐梦 HUD 动作包的距离校验（允许远程操作）");
        }
        return 0.0D;
    }

    @Redirect(
            method = "lambda$register$17",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerPlayer;hasLineOfSight(Lnet/minecraft/world/entity/Entity;)Z"
            ),
            remap = false)
    private static boolean maidList$ignoreActionLineOfSight(ServerPlayer player, Entity entity) {
        if (!PanelConfig.ignoreFoxdreamLineOfSight()) {
            return player.hasLineOfSight(entity);
        }
        return true;
    }
}
