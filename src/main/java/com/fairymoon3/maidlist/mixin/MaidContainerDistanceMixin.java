package com.fairymoon3.maidlist.mixin;

import com.fairymoon3.maidlist.config.PanelConfig;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 允许远程打开/保持女仆容器界面。
 *
 * <p>车万女仆的 {@code AbstractMaidContainer#stillValid} 结尾是：</p>
 * <pre>
 *   return player.canInteractWithEntity(maid, 4.0D);
 * </pre>
 * <p>玩家走远后这里返回 false，容器界面就会被客户端自动关掉 ——
 * 表现就是「打开一下闪没了」。这里只接管这一个距离判断，
 * 归属（{@code isOwnedBy}）、存活、睡觉等其它条件原样保留。</p>
 */
@Mixin(targets = "com.github.tartaricacid.touhoulittlemaid.inventory.container.AbstractMaidContainer")
public class MaidContainerDistanceMixin {

    private static final org.apache.logging.log4j.Logger LOGGER =
            org.apache.logging.log4j.LogManager.getLogger("fairymoon3_maid_list/mixin");
    private static final java.util.concurrent.atomic.AtomicBoolean LOGGED =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    @Redirect(
            method = "stillValid(Lnet/minecraft/world/entity/player/Player;)Z",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/player/Player;canInteractWithEntity(Lnet/minecraft/world/entity/Entity;D)Z"
            ),
            remap = false)
    private boolean maidList$remoteMaidContainer(Player player, Entity entity, double maxDistance) {
        if (!PanelConfig.remoteMaidContainer()) {
            // 配置关掉 → 保持车万女仆原行为
            return player.canInteractWithEntity(entity, maxDistance);
        }
        if (LOGGED.compareAndSet(false, true)) {
            LOGGER.info("[女仆名单] 已接管女仆容器的距离校验（允许远程打开背包）");
        }
        return true;
    }
}
