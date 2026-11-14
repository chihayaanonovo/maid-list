package com.fairymoon3.maidlist.mixin;

import com.fairymoon3.maidlist.client.MaidSpeechTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 通道二：车万女仆的「按坐标播放语音」包（{@code MaidSoundInstanceAtPos}）不带女仆引用，
 * 这里从包里拿到坐标，按位置挑最近的已加载女仆来认领这次发声。
 */
@Mixin(targets = "com.github.tartaricacid.touhoulittlemaid.network.message.PlayMaidSoundAtPosPackage")
public class MaidSpeechAtPosMixin {

    @Inject(method = "handle", at = @At("HEAD"), remap = false)
    private static void maidList$trackAtPos(
            com.github.tartaricacid.touhoulittlemaid.network.message.PlayMaidSoundAtPosPackage payload,
            net.neoforged.neoforge.network.handling.IPayloadContext context,
            CallbackInfo ci) {
        double x;
        double y;
        double z;
        try {
            x = payload.x();
            y = payload.y();
            z = payload.z();
        } catch (Throwable t) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        // 包处理线程不一定是客户端主线程，切回去再查实体
        minecraft.execute(() -> {
            try {
                if (minecraft.level == null) {
                    return;
                }
                var box = AABB.ofSize(new net.minecraft.world.phys.Vec3(x, y, z), 2.0D, 2.0D, 2.0D);
                Entity nearest = null;
                double best = Double.MAX_VALUE;
                for (Entity entity : minecraft.level.getEntitiesOfClass(Entity.class, box)) {
                    if (!(entity instanceof com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid)) {
                        continue;
                    }
                    double dist = entity.distanceToSqr(x, y, z);
                    if (dist < best) {
                        best = dist;
                        nearest = entity;
                    }
                }
                if (nearest != null) {
                    MaidSpeechTracker.onMaidSpeaking(nearest.getUUID());
                }
            } catch (Throwable ignored) {
                // 认领失败不影响其它功能
            }
        });
    }
}
