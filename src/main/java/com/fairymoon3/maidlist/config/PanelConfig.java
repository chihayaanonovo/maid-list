package com.fairymoon3.maidlist.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 客户端配置。
 *
 * <p><b>重要：</b>这是 {@link ModConfig.Type#CLIENT} 配置，专用服务器上根本不会被加载，
 * 此时 {@code ConfigValue#get()} 会抛
 * {@code IllegalStateException: Cannot get config value before config is loaded}。</p>
 *
 * <p>而本模组的几个 Mixin 是**双端生效**的（{@code AbstractMaidContainer#stillValid}、
 * 开界面包校验、狐梦动作包校验都会在服务端跑），所以不能直接读配置。
 * 一律通过本类的 {@code xxx()} 安全读取方法，读不到时退回默认值，
 * 而不是把异常抛给玩家的 tick 循环。</p>
 */
public final class PanelConfig {

    public static final ModConfigSpec CLIENT_SPEC;

    /**
     * 服务端配置。
     *
     * <p>专用服务器上客户端配置不会被加载，所以必须有这份配置，
     * 否则服主无法关闭「远程管理」这个会改变玩法的功能。</p>
     */
    public static final ModConfigSpec SERVER_SPEC;

    /** 远程管理总开关的默认值。 */
    public static final boolean DEFAULT_REMOTE_MAID_CONTAINER = true;
    /** 忽略视线要求的默认值。 */
    public static final boolean DEFAULT_IGNORE_LINE_OF_SIGHT = true;
    /** 夜间主题的默认值。 */
    public static final boolean DEFAULT_DARK_THEME = false;

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

    /** 界面主题：false = 米白（白天），true = 深色（夜间）。 */
    public static final ModConfigSpec.BooleanValue DARK_THEME;

    /** 服务端侧的远程管理开关。 */
    public static final ModConfigSpec.BooleanValue SERVER_REMOTE_MAID_CONTAINER;
    /** 服务端侧的忽略视线开关。 */
    public static final ModConfigSpec.BooleanValue SERVER_IGNORE_LINE_OF_SIGHT;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("tlm");
        REMOTE_MAID_CONTAINER = builder
                .comment("远程管理总开关：允许在任意距离打开并保持女仆背包 / 配置界面、狐梦 HUD 及其动作。默认开启。",
                        "Master switch for remote maid management (containers, Foxdream HUD and its actions). Default true.")
                .define("remote_maid_container", DEFAULT_REMOTE_MAID_CONTAINER);
        builder.pop();

        builder.push("foxdream_ui");
        IGNORE_FOXDREAM_LINE_OF_SIGHT = builder
                .comment("狐梦 HUD 的动作包忽略『视线』检查（隔着墙 / 远处也能远程操作）。默认开启。",
                        "Ignore line-of-sight requirement for Foxdream HUD actions. Default true.")
                .define("ignore_line_of_sight", DEFAULT_IGNORE_LINE_OF_SIGHT);
        builder.pop();

        builder.push("ui");
        DARK_THEME = builder
                .comment("界面主题：false = 米白（白天），true = 深色（夜间）。可在面板里点太阳/月亮切换。",
                        "false = light (cream) theme, true = dark theme.")
                .define("dark_theme", DEFAULT_DARK_THEME);
        builder.pop();

        CLIENT_SPEC = builder.build();

        ModConfigSpec.Builder server = new ModConfigSpec.Builder();
        server.push("tlm");
        SERVER_REMOTE_MAID_CONTAINER = server
                .comment("服务端：是否允许远程打开 / 保持女仆容器界面（无视 4~5 格距离）。默认开启。",
                        "Server side: allow opening/keeping maid containers regardless of distance. Default true.")
                .define("remote_maid_container", DEFAULT_REMOTE_MAID_CONTAINER);
        server.pop();
        server.push("foxdream_ui");
        SERVER_IGNORE_LINE_OF_SIGHT = server
                .comment("服务端：狐梦动作包是否忽略视线要求。默认开启。",
                        "Server side: ignore line-of-sight for Foxdream actions. Default true.")
                .define("ignore_line_of_sight", DEFAULT_IGNORE_LINE_OF_SIGHT);
        server.pop();
        SERVER_SPEC = server.build();
    }

    private PanelConfig() {
    }

    // ------------------------------------------------------------------ 安全读取

    /**
     * 远程管理是否开启。
     *
     * <p>专用服务器上客户端配置未加载，这里返回默认值而不是抛异常 —— 这正是
     * 0.1 版本服务器崩溃的原因。</p>
     */
    public static boolean remoteMaidContainer() {
        Boolean client = read(REMOTE_MAID_CONTAINER);
        if (client != null) {
            return client;
        }
        Boolean server = read(SERVER_REMOTE_MAID_CONTAINER);
        return server != null ? server : DEFAULT_REMOTE_MAID_CONTAINER;
    }

    /** 读取一个布尔配置；未加载时返回 null 而不是抛异常。 */
    private static Boolean read(ModConfigSpec.BooleanValue value) {
        try {
            return value.get();
        } catch (Throwable t) {
            return null;
        }
    }

    /** 狐梦 HUD 动作是否忽略视线要求。 */
    public static boolean ignoreFoxdreamLineOfSight() {
        Boolean client = read(IGNORE_FOXDREAM_LINE_OF_SIGHT);
        if (client != null) {
            return client;
        }
        Boolean server = read(SERVER_IGNORE_LINE_OF_SIGHT);
        return server != null ? server : DEFAULT_IGNORE_LINE_OF_SIGHT;
    }

    /** 当前是否夜间主题。 */
    public static boolean darkTheme() {
        try {
            return DARK_THEME.get();
        } catch (Throwable t) {
            return DEFAULT_DARK_THEME;
        }
    }

    /** 切换夜间主题；配置不可写时只影响本次界面。 */
    public static boolean toggleDarkTheme() {
        boolean next = !darkTheme();
        try {
            DARK_THEME.set(next);
        } catch (Throwable ignored) {
            // 配置未加载：至少让本次界面切过去
        }
        return next;
    }
}
