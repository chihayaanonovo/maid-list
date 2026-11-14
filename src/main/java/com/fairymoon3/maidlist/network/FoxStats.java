package com.fairymoon3.maidlist.network;

import net.minecraft.network.RegistryFriendlyByteBuf;

/**
 * 狐梦伴岁（foxdream）成长体系里的生活数值快照。
 *
 * <p>只有被狐梦手册转化过的女仆才会有这份数据；未转化或未安装 foxdream 时为 {@code null}。</p>
 *
 * @param sleep        睡眠 0..100
 * @param satisfaction 满足 0..100
 * @param mood         心情 0..100
 * @param trustPercent 当前信赖档位内的进度百分比 0..100
 * @param trustLevel   信赖档位名（陌生 / 熟悉 / 亲密 / 挚爱 / 生死与共）
 * @param favorLevel   好感等级（<64 为 0 级，<192 为 1 级，<384 为 2 级，其余 3 级，与狐梦 HUD 一致）
 * @param favorPoints  当前好感点数
 * @param favorCap     当前成长阶段的好感上限
 * @param daysText     相伴时长文本（由狐梦自己的 CompanionRules.days 生成）
 * @param mealsMask    三餐是否吃过，bit0=早饭 bit1=午饭 bit2=晚饭
 * @param personality  初始性格（挑食 / 笨拙 / 胆小 …），不是后天成长性格
 */
public record FoxStats(
        int sleep,
        int satisfaction,
        int mood,
        int trustPercent,
        String trustLevel,
        int favorLevel,
        int favorPoints,
        int favorCap,
        String daysText,
        int mealsMask,
        String personality
) {

    public boolean mealEaten(int index) {
        return (mealsMask & (1 << index)) != 0;
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(sleep);
        buf.writeVarInt(satisfaction);
        buf.writeVarInt(mood);
        buf.writeVarInt(trustPercent);
        buf.writeUtf(trustLevel == null ? "" : trustLevel, 32);
        buf.writeVarInt(favorLevel);
        buf.writeVarInt(favorPoints);
        buf.writeVarInt(favorCap);
        buf.writeUtf(daysText == null ? "" : daysText, 48);
        buf.writeByte(mealsMask);
        buf.writeUtf(personality == null ? "" : personality, 32);
    }

    public static FoxStats read(RegistryFriendlyByteBuf buf) {
        return new FoxStats(
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readUtf(32),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readUtf(48),
                buf.readByte(),
                buf.readUtf(32));
    }
}
