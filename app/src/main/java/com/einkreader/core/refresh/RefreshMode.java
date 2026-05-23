package com.einkreader.core.refresh;

/**
 * E-Ink 刷新波形模式
 * 不同模式在刷新速度和显示质量之间取舍
 */
public enum RefreshMode {
    /** 全局刷新，16级灰度，无残影，最慢 */
    GC16(0, "GC16", "全局刷新", true, false),
    /** 局部刷新，仅黑白，速度快 */
    DU(1, "DU", "局部刷新(黑白)", false, true),
    /** 极速刷新，仅黑白，有残影，最快 */
    A2(2, "A2", "极速刷新", false, true),
    /** 局部刷新，16级灰度 */
    GL16(3, "GL16", "局部刷新(灰度)", false, true),
    /** 自动选择最佳模式 */
    AUTO(-1, "AUTO", "自动", false, false);

    private final int sysfsValue;
    private final String code;
    private final String displayName;
    private final boolean isFullRefresh;
    private final boolean isPartialRefresh;

    RefreshMode(int sysfsValue, String code, String displayName,
                boolean isFullRefresh, boolean isPartialRefresh) {
        this.sysfsValue = sysfsValue;
        this.code = code;
        this.displayName = displayName;
        this.isFullRefresh = isFullRefresh;
        this.isPartialRefresh = isPartialRefresh;
    }

    public int getSysfsValue() { return sysfsValue; }
    public String getCode() { return code; }
    public String getDisplayName() { return displayName; }
    public boolean isFullRefresh() { return isFullRefresh; }
    public boolean isPartialRefresh() { return isPartialRefresh; }

    /** 根据 sysfs 值查找对应模式 */
    public static RefreshMode fromSysfsValue(int value) {
        for (RefreshMode mode : values()) {
            if (mode.sysfsValue == value) return mode;
        }
        return null;
    }

    /** 根据代码字符串查找 */
    public static RefreshMode fromCode(String code) {
        for (RefreshMode mode : values()) {
            if (mode.code.equalsIgnoreCase(code)) return mode;
        }
        return AUTO;
    }
}
