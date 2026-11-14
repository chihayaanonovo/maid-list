package com.fairymoon3.maidlist.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

/**
 * 一条女仆记录 —— 面板里的一行。
 *
 * <p>数据来源有两层：</p>
 * <ul>
 *   <li>女仆实体已加载：从 {@code EntityMaid} 上读全量信息（血量、职业、好感、背包……）</li>
 *   <li>实体未加载：从车万女仆的 {@code MaidWorldData} 持久索引里读到名字 / 维度 / 上次区块，
 *       标记为 {@code loaded = false}</li>
 * </ul>
 *
 * <p>注意：车万女仆 1.5.x 的 {@code getHunger()} 是一个**只剩读写、没有任何逻辑会写入**的
 * 遗留字段，永远返回 0，喂食也不会变化，因此面板不再展示"饱食度"。</p>
 */
public record MaidEntry(
        UUID uuid,
        String name,
        boolean loaded,
        ResourceLocation taskId,
        ItemStack taskIcon,
        int health,
        int maxHealth,
        int favorability,
        int favorLevel,
        int experience,
        boolean homeMode,
        boolean sitting,
        boolean sleeping,
        boolean fighting,
        boolean hasBackpack,
        ItemStack backpackIcon,
        String backpackName,
        String modelId,
        boolean ysmModel,
        ResourceLocation dimension,
        double x,
        double y,
        double z,
        int chunkX,
        int chunkZ,
        /** 狐梦伴岁的成长阶段，-1 表示未转化（或没装该 mod）。 */
        int foxdreamStage,
        /** 玩家「获得」这只女仆的时间戳（毫秒），来自 MaidWorldData 的索引记录；0 表示未知。 */
        long acquiredAt,
        /** 仅对「未加载、来自快照」的记录有意义：多少秒之前记录的，0 表示实时。 */
        int ageSeconds,
        /** 狐梦成长数值；未转化 / 未安装 foxdream / 来自缓存快照时为 null。 */
        FoxStats foxStats
) {

    /** 复制一份并设置「多久之前」。 */
    public MaidEntry withAge(int seconds) {
        return new MaidEntry(uuid, name, loaded, taskId, taskIcon, health, maxHealth, favorability,
                favorLevel, experience, homeMode, sitting, sleeping, fighting, hasBackpack,
                backpackIcon, backpackName, modelId, ysmModel, dimension, x, y, z, chunkX, chunkZ,
                foxdreamStage, acquiredAt, seconds, foxStats);
    }

    /**
     * 语义化状态，供界面显示。
     *
     * <p>优先级：睡觉 &gt; 坐下（待机中） &gt; 战斗 &gt; 驻守 &gt; 自由。</p>
     *
     * <p>「坐下」排到战斗之前，是因为玩家可以用右键让正在战斗的女仆坐下；
     * 此时她已经不攻击了，再显示「战斗中」会误导玩家。</p>
     */
    public Status status() {
        if (!loaded) {
            return Status.UNLOADED;
        }
        if (sleeping) {
            return Status.SLEEPING;
        }
        if (sitting) {
            return Status.SITTING;
        }
        if (fighting) {
            return Status.FIGHTING;
        }
        if (homeMode) {
            return Status.GUARDING;
        }
        return Status.FREE;
    }

    /** 坐下待机，但身上还挂着攻击目标（用于提示玩家她仍在吸引仇恨）。 */
    public boolean sittingWithAggro() {
        return loaded && sitting && fighting;
    }

    public float healthRatio() {
        return maxHealth <= 0 ? 0.0F : Math.max(0.0F, Math.min(1.0F, health / (float) maxHealth));
    }

    // ------------------------------------------------------------------ codec

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeUUID(uuid);
        buf.writeUtf(name, 128);
        buf.writeBoolean(loaded);
        buf.writeResourceLocation(taskId);
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, taskIcon);
        buf.writeVarInt(health);
        buf.writeVarInt(maxHealth);
        buf.writeVarInt(favorability);
        buf.writeVarInt(favorLevel);
        buf.writeVarInt(experience);
        buf.writeBoolean(homeMode);
        buf.writeBoolean(sitting);
        buf.writeBoolean(sleeping);
        buf.writeBoolean(fighting);
        buf.writeBoolean(hasBackpack);
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, backpackIcon);
        buf.writeUtf(backpackName == null ? "" : backpackName, 128);
        buf.writeUtf(modelId == null ? "" : modelId, 256);
        buf.writeBoolean(ysmModel);
        buf.writeResourceLocation(dimension);
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        buf.writeVarInt(chunkX);
        buf.writeVarInt(chunkZ);
        buf.writeVarInt(foxdreamStage);
        buf.writeLong(acquiredAt);
        buf.writeVarInt(ageSeconds);
        buf.writeBoolean(foxStats != null);
        if (foxStats != null) {
            foxStats.write(buf);
        }
    }

    public static MaidEntry read(RegistryFriendlyByteBuf buf) {
        UUID uuid = buf.readUUID();
        String name = buf.readUtf();
        boolean loaded = buf.readBoolean();
        ResourceLocation taskId = buf.readResourceLocation();
        ItemStack taskIcon = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
        int health = buf.readVarInt();
        int maxHealth = buf.readVarInt();
        int favorability = buf.readVarInt();
        int favorLevel = buf.readVarInt();
        int experience = buf.readVarInt();
        boolean homeMode = buf.readBoolean();
        boolean sitting = buf.readBoolean();
        boolean sleeping = buf.readBoolean();
        boolean fighting = buf.readBoolean();
        boolean hasBackpack = buf.readBoolean();
        ItemStack backpackIcon = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
        String backpackName = buf.readUtf();
        String modelId = buf.readUtf();
        boolean ysmModel = buf.readBoolean();
        ResourceLocation dimension = buf.readResourceLocation();
        double x = buf.readDouble();
        double y = buf.readDouble();
        double z = buf.readDouble();
        int chunkX = buf.readVarInt();
        int chunkZ = buf.readVarInt();
        int foxdreamStage = buf.readVarInt();
        long acquiredAt = buf.readLong();
        int ageSeconds = buf.readVarInt();
        FoxStats foxStats = buf.readBoolean() ? FoxStats.read(buf) : null;

        return new MaidEntry(uuid, name, loaded, taskId, taskIcon, health, maxHealth,
                favorability, favorLevel, experience, homeMode, sitting, sleeping, fighting,
                hasBackpack, backpackIcon, backpackName, modelId, ysmModel,
                dimension, x, y, z, chunkX, chunkZ, foxdreamStage, acquiredAt, ageSeconds, foxStats);
    }

    /** 女仆当前状态。 */
    public enum Status {
        SLEEPING("fairymoon3_maid_list.status.sleeping", 0xFFB39DFF),
        FIGHTING("fairymoon3_maid_list.status.fighting", 0xFFFF6B6B),
        SITTING("fairymoon3_maid_list.status.sitting", 0xFF6FA8FF),
        GUARDING("fairymoon3_maid_list.status.guarding", 0xFFE8A33D),
        FREE("fairymoon3_maid_list.status.free", 0xFF7ED07E),
        UNLOADED("fairymoon3_maid_list.status.unloaded", 0xFF6C7484);

        public final String translationKey;
        public final int color;

        Status(String translationKey, int color) {
            this.translationKey = translationKey;
            this.color = color;
        }
    }
}
