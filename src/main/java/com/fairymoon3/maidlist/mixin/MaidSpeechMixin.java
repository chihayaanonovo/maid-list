package com.fairymoon3.maidlist.mixin;

import com.fairymoon3.maidlist.client.MaidSpeechTracker;
import net.minecraft.client.resources.sounds.SoundInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 通道一：客户端播放声音时，登记绑定女仆的语音实例。
 */
@Mixin(targets = "net.minecraft.client.sounds.SoundManager")
public class MaidSpeechMixin {

    @Inject(
            method = "play(Lnet/minecraft/client/resources/sounds/SoundInstance;)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false)
    private void maidList$trackMaidSpeech(SoundInstance sound, CallbackInfo ci) {
        java.util.UUID maidId = MaidSpeechTracker.attribute(sound);
        if (maidId == null) {
            return;
        }
        // 被静音的女仆：拦下这次播放，也就不用标记「正在说话」
        if (com.fairymoon3.maidlist.client.MaidMuteStore.isMuted(maidId)) {
            ci.cancel();
            return;
        }
        var location = sound.getLocation();
        MaidSpeechTracker.markSpeaking(maidId, sound, location == null ? null : location.toString());
    }
}
