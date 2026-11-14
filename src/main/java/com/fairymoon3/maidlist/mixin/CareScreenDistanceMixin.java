package com.fairymoon3.maidlist.mixin;

import com.fairymoon3.maidlist.config.PanelConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 解除狐梦养成面板自身的距离关闭逻辑。
 *
 * <p>foxdream 在 {@code CareScreen#tick()} 里写死：</p>
 * <pre>
 *   if (entity == null || player.distanceToSqr(entity) &gt; 64.0) { onClose(); return; }
 * </pre>
 *
 * <p>这里直接让那次 {@code distanceToSqr} 返回 0，**完全不设距离上限**
 * （1.7.2 及之前误用了可配置上限，默认 64 格，会让 HUD 在 64 格外被关掉）。</p>
 */
@Mixin(targets = "cn.blockforge.generated.winefox1801000525.client.CareScreen")
public class CareScreenDistanceMixin {

    private static final org.apache.logging.log4j.Logger LOGGER =
            org.apache.logging.log4j.LogManager.getLogger("fairymoon3_maid_list/mixin");
    private static final java.util.concurrent.atomic.AtomicBoolean LOGGED =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    @Redirect(
            method = "tick()V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/player/LocalPlayer;distanceToSqr(Lnet/minecraft/world/entity/Entity;)D"
            ),
            remap = false)
    private double maidList$removeCareRange(LocalPlayer player, Entity entity) {
        if (!PanelConfig.remoteMaidContainer()) {
            return player.distanceToSqr(entity);
        }
        if (LOGGED.compareAndSet(false, true)) {
            LOGGER.info("[女仆名单] 已解除狐梦面板的距离限制（无上限）");
        }
        return 0.0D;
    }

    /**
     * 客户端拿不到那只女仆时（超出实体追踪范围 / 换了维度），别让 HUD 自己关掉。
     *
     * <p>{@code tick()} 里除了距离判断还有一处 {@code getEntity(entityId) == null → onClose()}。
     * 该实体在守卫之后就不再被使用（面板内容全部来自服务端下发的 NBT），
     * 所以这里在取不到时返回玩家自己，纯粹是为了让守卫通过。</p>
     */
    @Redirect(
            method = "tick()V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/multiplayer/ClientLevel;getEntity(I)Lnet/minecraft/world/entity/Entity;"
            ),
            remap = false)
    private Entity maidList$keepAliveWithoutEntity(ClientLevel level, int entityId) {
        Entity entity = level.getEntity(entityId);
        if (entity != null || !PanelConfig.remoteMaidContainer()) {
            return entity;
        }
        return Minecraft.getInstance().player;
    }
}
