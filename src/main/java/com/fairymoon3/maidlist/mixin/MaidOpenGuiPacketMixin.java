package com.fairymoon3.maidlist.mixin;

import com.fairymoon3.maidlist.config.PanelConfig;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 接管「打开女仆界面」网络包的距离校验。
 *
 * <p>车万女仆的 {@code OpenMaidGuiPackage#stillValid} 长这样：</p>
 * <pre>
 *   return maid.isOwnedBy(player)
 *       &amp;&amp; !maid.isSleeping()
 *       &amp;&amp; maid.isAlive()
 *       &amp;&amp; player.distanceTo(maid) &lt; 5.0F;   // ← 5 格
 * </pre>
 * <p>这是客户端请求打开女仆界面时服务端做的校验。只接管最后那个距离比较，
 * 归属 / 存活 / 睡觉三项原样保留。</p>
 */
@Mixin(targets = "com.github.tartaricacid.touhoulittlemaid.network.message.OpenMaidGuiPackage")
public class MaidOpenGuiPacketMixin {

    private static final org.apache.logging.log4j.Logger LOGGER =
            org.apache.logging.log4j.LogManager.getLogger("fairymoon3_maid_list/mixin");
    private static final java.util.concurrent.atomic.AtomicBoolean LOGGED =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    @Redirect(
            method = "stillValid",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/github/tartaricacid/touhoulittlemaid/entity/passive/EntityMaid;distanceTo(Lnet/minecraft/world/entity/Entity;)F"
            ),
            remap = false)
    private static float maidList$ignoreOpenGuiDistance(EntityMaid maid, Entity player) {
        if (!PanelConfig.remoteMaidContainer()) {
            return maid.distanceTo(player);
        }
        if (LOGGED.compareAndSet(false, true)) {
            LOGGER.info("[女仆名单] 已接管女仆开界面的 5 格校验（允许远程打开）");
        }
        return 0.0F;
    }
}
