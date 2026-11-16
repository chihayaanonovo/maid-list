package com.fairymoon3.maidlist.config;

/**
 * 面板主题。
 *
 * <p>通过标题栏的太阳 / 月亮 / 玻璃按钮循环切换：
 * 日间 → 夜间 → 半透明 → 日间 …</p>
 */
public enum ThemeMode {

    /** 米白（日间）。 */
    LIGHT("light"),
    /** 深色（夜间）。 */
    DARK("dark"),
    /** 整体半透明（能看见后面的世界）。 */
    GLASS("glass");

    private final String id;

    ThemeMode(String id) {
        this.id = id;
    }

    public String id() {
        return this.id;
    }

    /** 下一个主题。 */
    public ThemeMode next() {
        ThemeMode[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public static ThemeMode fromId(String raw) {
        if (raw != null) {
            for (ThemeMode mode : values()) {
                if (mode.id.equalsIgnoreCase(raw)) {
                    return mode;
                }
            }
        }
        return LIGHT;
    }
}
