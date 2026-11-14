package com.fairymoon3.maidlist.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** 客户端配置。 */
public final class PanelConfig {

    public static final ModConfigSpec CLIENT_SPEC;

    /**
     * 远程管理总开关。
     *
     * <p>开启时（默认）：</p>
     * <ul>
     *   <li>女仆名单打开的女仆背包 / 配置界面不受距离限制</li>
     *   <li>狐梦养成面板（HUD）不受距离限制，也不会因走远而自动关闭</li>
     *   <li>狐梦 HUD 里的动作按钮（打开背包 / 早点睡觉……）可远程触发</li>
     * </ul>
     * <p>关闭后全部恢复各 mod 的原版行为。</p>
     */
    public static final ModConfigSpec.BooleanValue REMOTE_MAID_CONTAINER;

    /** 狐梦 HUD 动作包是否忽略“视线”要求。 */
    public static final ModConfigSpec.BooleanValue IGNORE_FOXDREAM_LINE_OF_SIGHT;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("tlm");
        REMOTE_MAID_CONTAINER = builder
                .comment("远程管理总开关：允许在任意距离打开并保持女仆背包 / 配置界面、狐梦 HUD 及其动作。默认开启。",
                        "Master switch for remote maid management (containers, Foxdream HUD and its actions). Default true.")
                .define("remote_maid_container", true);
        builder.pop();

        builder.push("foxdream_ui");
        IGNORE_FOXDREAM_LINE_OF_SIGHT = builder
                .comment("狐梦 HUD 的动作包忽略『视线』检查（隔着墙 / 远处也能远程操作）。默认开启。",
                        "Ignore line-of-sight requirement for Foxdream HUD actions. Default true.")
                .define("ignore_line_of_sight", true);
        builder.pop();

        CLIENT_SPEC = builder.build();
    }

    private PanelConfig() {
    }
}
