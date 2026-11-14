package com.fairymoon3.maidlist.mixin;

import com.fairymoon3.maidlist.client.MaidSpeechTracker;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 通道三：车万女仆带 entityId 的语音包（最直接的一条路）。
 *
 * <p>用实体 id 反查女仆，不依赖 {@code MaidSoundInstance} 是否真的开始播放，
 * 因此狐梦转化过的女仆也能被识别。</p>
 */
@Mixin(targets = "com.github.tartaricacid.touhoulittlemaid.network.message.PlayMaidSoundPackage")
public class MaidSpeechEntityMixin {

    @Inject(method = "handle", at = @At("HEAD"), remap = false)
    private static void maidList$trackByEntity(
            com.github.tartaricacid.touhoulittlemaid.network.message.PlayMaidSoundPackage payload,
            net.neoforged.neoforge.network.handling.IPayloadContext context,
            CallbackInfo ci) {
        int entityId;
        try {
            entityId = payload.entityId();
        } catch (Throwable t) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            try {
                if (minecraft.level == null) {
                    return;
                }
                var entity = minecraft.level.getEntity(entityId);
                if (entity instanceof com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid) {
                    MaidSpeechTracker.onMaidSpeaking(entity.getUUID());
                }
            } catch (Throwable ignored) {
                // 认领失败不影响其它功能
            }
        });
    }
}
