package com.fairymoon3.maidlist.client;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.fairymoon3.maidlist.MaidList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 记录「哪些女仆当前正在播语音」，并负责把一次声音播放认领到具体女仆。
 *
 * <p>车万女仆的语音有好几条通道，狐梦伴岁还会自己 mixin 进 {@code MaidSoundInstance}，
 * 所以按优先级依次尝试：</p>
 * <ol>
 *   <li>{@code MaidSoundInstance} —— 直接带 {@code getMaid()}</li>
 *   <li>{@code MaidSoundInstanceAtPos} / 任何 {@code touhou_little_maid}、{@code foxdream}
 *       的 {@code maid.*} 位置音效 —— 按坐标认领最近的已加载女仆</li>
 * </ol>
 */
public final class MaidSpeechTracker {

    private static final long MAX_SPEAK_MS = 4000L;
    private static final double MATCH_RADIUS = 3.0D;
    private static final int LOG_LIMIT = 15;

    private static final Map<UUID, Long> EXPIRES = new ConcurrentHashMap<>();
    private static final Map<UUID, SoundInstance> INSTANCES = new ConcurrentHashMap<>();
    private static final Set<UUID> CONFIRMED = ConcurrentHashMap.newKeySet();
    private static final Set<String> LOGGED = ConcurrentHashMap.newKeySet();

    private MaidSpeechTracker() {
    }

    /**
     * 把一次播放认领给某只女仆（不标记为正在说话）。
     *
     * @return 认领到的女仆 UUID，认不出则为 {@code null}
     */
    public static UUID attribute(SoundInstance sound) {
        if (sound == null) {
            return null;
        }
        UUID bound = boundMaid(sound);
        if (bound != null) {
            return bound;
        }
        return positionalMaid(sound);
    }

    /** 认领并标记为「正在说话」（会跳过已静音的女仆）。 */
    public static void markSpeaking(UUID maidId, SoundInstance instance, String soundId) {
        if (maidId == null || MaidMuteStore.isMuted(maidId)) {
            return;
        }
        EXPIRES.put(maidId, System.currentTimeMillis() + MAX_SPEAK_MS);
        if (instance != null) {
            INSTANCES.put(maidId, instance);
            CONFIRMED.remove(maidId);
        }
        if (soundId != null && LOGGED.size() < LOG_LIMIT && LOGGED.add(soundId)) {
            MaidList.LOGGER.info("[女仆名单] 语音认领：{}", soundId);
        }
    }

    /** 只知道「某只女仆刚发了声」时使用。 */
    public static void onMaidSpeaking(UUID maidId) {
        markSpeaking(maidId, null, null);
    }

    private static UUID boundMaid(SoundInstance sound) {
        try {
            if (sound instanceof com.github.tartaricacid.touhoulittlemaid.client.sound.data.MaidSoundInstance maidSound) {
                var maid = maidSound.getMaid();
                if (maid != null) {
                    return maid.getUUID();
                }
            }
        } catch (Throwable ignored) {
            // 类不存在 / 读取失败
        }
        return null;
    }

    private static UUID positionalMaid(SoundInstance sound) {
        try {
            if (sound.isRelative()) {
                return null;
            }
            ResourceLocation location = sound.getLocation();
            if (location == null) {
                return null;
            }
            String namespace = location.getNamespace();
            String path = location.getPath();
            boolean fromMaidMod = "touhou_little_maid".equals(namespace) || "foxdream".equals(namespace);
            if (!fromMaidMod) {
                return null;
            }
            if (!path.startsWith("maid") && !"foxdream".equals(namespace)) {
                return null;
            }
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level == null) {
                return null;
            }
            Vec3 pos = new Vec3(sound.getX(), sound.getY(), sound.getZ());
            AABB box = AABB.ofSize(pos, MATCH_RADIUS * 2.0D, MATCH_RADIUS * 2.0D, MATCH_RADIUS * 2.0D);
            EntityMaid nearest = null;
            double best = Double.MAX_VALUE;
            for (Entity entity : minecraft.level.getEntitiesOfClass(Entity.class, box)) {
                if (!(entity instanceof EntityMaid maid)) {
                    continue;
                }
                double distance = entity.distanceToSqr(pos.x, pos.y, pos.z);
                if (distance < best) {
                    best = distance;
                    nearest = maid;
                }
            }
            return nearest == null ? null : nearest.getUUID();
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static boolean isSpeaking(UUID maidId) {
        if (maidId == null || MaidMuteStore.isMuted(maidId)) {
            return false;
        }
        Long until = EXPIRES.get(maidId);
        if (until == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (now >= until) {
            clear(maidId);
            return false;
        }
        SoundInstance instance = INSTANCES.get(maidId);
        if (instance != null) {
            try {
                var manager = Minecraft.getInstance().getSoundManager();
                boolean active = manager != null && manager.isActive(instance);
                if (active) {
                    CONFIRMED.add(maidId);
                } else if (CONFIRMED.contains(maidId)) {
                    clear(maidId);
                    return false;
                }
            } catch (Throwable ignored) {
                // 查询失败就退化成超时判定
            }
        }
        return true;
    }

    private static void clear(UUID maidId) {
        EXPIRES.remove(maidId);
        INSTANCES.remove(maidId);
        CONFIRMED.remove(maidId);
    }
}
