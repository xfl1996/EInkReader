package com.einkreader.core.refresh;

import android.content.Context;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * E-Ink 墨水屏刷新管理器
 * 
 * 支持两种刷新路径：
 * 1. sysfs 硬件控制 — 通过 /sys/devices/platform/epdc/ 等节点直接控制屏幕
 * 2. Android API 回退 — 通过 invalidate/postInvalidate 触发软件层刷新
 * 
 * 自动检测设备支持的刷新模式，并根据场景选择最优刷新策略。
 */
public class EinkRefreshManager {
    private static final String TAG = "EinkRefresh";

    // 常见 AllWinner E-Ink sysfs 路径候选
    private static final String[] EPDC_PATH_CANDIDATES = {
        "/sys/devices/platform/epdc/",
        "/sys/devices/platform/soc/1c00000.eink/",
        "/sys/devices/platform/eink/",
        "/sys/class/eink/",
    };

    private static final String[] FB_PATH_CANDIDATES = {
        "/sys/class/graphics/fb0/",
    };

    private static final String WAVEFORM_FILE = "waveform_mode";
    private static final String DISPLAY_FILE = "update_mode";

    private final Context context;
    private final Handler mainHandler;
    
    private String epdcPath;          // 探测到的 epdc sysfs 路径
    private String fbPath;            // 探测到的 framebuffer sysfs 路径
    private boolean sysfsAvailable;   // sysfs 是否可用
    
    private Set<RefreshMode> supportedModes;  // 设备支持的刷新模式
    private RefreshMode defaultMode;          // 默认刷新模式
    private int fullRefreshInterval = 10;     // 全局刷新间隔（页数）
    private int pageCounter = 0;              // 翻页计数器
    
    private RefreshCallback callback;

    /**
     * 刷新回调接口
     */
    public interface RefreshCallback {
        void onRefreshStart(RefreshMode mode);
        void onRefreshComplete(RefreshMode mode);
        void onModeDetected(Set<RefreshMode> modes);
        void onSysfsUnavailable(); // sysfs 不可用，将使用 Android API
    }

    public EinkRefreshManager(Context context) {
        this.context = context.getApplicationContext();
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.supportedModes = new HashSet<>();
        this.defaultMode = RefreshMode.AUTO;
    }

    /**
     * 初始化：探测 sysfs 路径并检测支持的刷新模式
     */
    public void initialize(RefreshCallback callback) {
        this.callback = callback;
        
        new Thread(new Runnable() {
            @Override
            public void run() {
                detectSysfsPaths();
                detectSupportedModes();
                
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (sysfsAvailable && callback != null) {
                            callback.onModeDetected(supportedModes);
                        } else if (!sysfsAvailable && callback != null) {
                            callback.onSysfsUnavailable();
                        }
                    }
                });
            }
        }).start();
    }

    /**
     * 探测 sysfs 路径
     */
    private void detectSysfsPaths() {
        sysfsAvailable = false;
        
        // 探测 epdc 路径
        for (String path : EPDC_PATH_CANDIDATES) {
            File dir = new File(path);
            if (dir.exists() && dir.canRead()) {
                epdcPath = path;
                Log.i(TAG, "Found EPDC path: " + path);
                break;
            }
        }
        
        // 探测 fb 路径
        for (String path : FB_PATH_CANDIDATES) {
            File dir = new File(path);
            if (dir.exists() && dir.canRead()) {
                fbPath = path;
                Log.i(TAG, "Found FB path: " + path);
                break;
            }
        }
        
        // 尝试写入测试，确认可写
        if (epdcPath != null || fbPath != null) {
            String testPath = epdcPath != null ? epdcPath : fbPath;
            File waveformFile = new File(testPath, WAVEFORM_FILE);
            if (waveformFile.exists()) {
                sysfsAvailable = waveformFile.canWrite();
                if (!sysfsAvailable) {
                    // 有些设备需要 root 权限才能写入
                    Log.w(TAG, "sysfs path exists but not writable: " + testPath);
                }
            } else {
                // 路径存在但没有 waveform 文件，尝试其他文件名
                File updateFile = new File(testPath, DISPLAY_FILE);
                sysfsAvailable = updateFile.exists() && updateFile.canWrite();
            }
        }
        
        Log.i(TAG, "sysfsAvailable=" + sysfsAvailable + 
              ", epdcPath=" + epdcPath + ", fbPath=" + fbPath);
    }

    /**
     * 检测设备支持的刷新模式
     * 通过读取 sysfs 中的可用波形模式信息
     */
    private void detectSupportedModes() {
        supportedModes.clear();
        
        if (!sysfsAvailable) {
            // sysfs 不可用，假设支持所有常用模式（通过 Android API 回退）
            supportedModes.add(RefreshMode.GC16);
            supportedModes.add(RefreshMode.DU);
            supportedModes.add(RefreshMode.A2);
            supportedModes.add(RefreshMode.GL16);
            defaultMode = RefreshMode.GC16;
            return;
        }
        
        // 尝试读取可用模式列表
        String modeString = readFileValue(epdcPath, "available_modes");
        if (modeString == null) {
            modeString = readFileValue(fbPath, "available_modes");
        }
        
        if (modeString != null && !modeString.isEmpty()) {
            // 解析模式列表，格式可能是 "0 1 2 3" 或 "GC16 DU A2 GL16"
            String[] parts = modeString.trim().split("[\\s,]+");
            for (String part : parts) {
                part = part.trim();
                if (part.isEmpty()) continue;
                
                // 尝试按数字解析
                try {
                    int value = Integer.parseInt(part);
                    RefreshMode mode = RefreshMode.fromSysfsValue(value);
                    if (mode != null) supportedModes.add(mode);
                } catch (NumberFormatException e) {
                    // 尝试按字符串解析
                    RefreshMode mode = RefreshMode.fromCode(part);
                    if (mode != RefreshMode.AUTO) supportedModes.add(mode);
                }
            }
        }
        
        // 如果没读到任何模式，默认支持所有
        if (supportedModes.isEmpty()) {
            supportedModes.add(RefreshMode.GC16);
            supportedModes.add(RefreshMode.DU);
            supportedModes.add(RefreshMode.A2);
        }
        
        // 设置默认模式：优先 GL16 > DU > GC16
        if (supportedModes.contains(RefreshMode.GL16)) {
            defaultMode = RefreshMode.GL16;
        } else if (supportedModes.contains(RefreshMode.DU)) {
            defaultMode = RefreshMode.DU;
        } else {
            defaultMode = RefreshMode.GC16;
        }
        
        Log.i(TAG, "Supported modes: " + supportedModes + ", default: " + defaultMode);
    }

    /**
     * 执行翻页刷新（核心方法）
     * 根据翻页计数自动选择局部或全局刷新
     */
    public void performPageRefresh(View targetView) {
        pageCounter++;
        
        RefreshMode mode;
        if (pageCounter % fullRefreshInterval == 0) {
            // 到达全局刷新间隔，执行 GC16
            mode = RefreshMode.GC16;
        } else {
            // 否则使用默认的局部刷新模式
            mode = getDefaultPartialMode();
        }
        
        performRefresh(targetView, mode);
    }

    /**
     * 执行指定模式的刷新
     */
    public void performRefresh(final View targetView, final RefreshMode mode) {
        if (callback != null) callback.onRefreshStart(mode);
        
        // 1. 尝试 sysfs 硬件刷新
        boolean sysfsSuccess = false;
        if (sysfsAvailable) {
            sysfsSuccess = writeSysfsRefresh(mode);
        }
        
        // 2. sysfs 不可用或失败时，使用 Android API
        if (!sysfsSuccess && targetView != null) {
            targetView.post(new Runnable() {
                @Override
                public void run() {
                    targetView.invalidate();
                    if (mode == RefreshMode.GC16) {
                        // 全局刷新时强制重绘整个视图树
                        targetView.getRootView().invalidate();
                    }
                    if (callback != null) callback.onRefreshComplete(mode);
                }
            });
        } else {
            if (callback != null) callback.onRefreshComplete(mode);
        }
    }

    /**
     * 手动触发全局刷新
     */
    public void forceFullRefresh(View targetView) {
        pageCounter = 0; // 重置计数器
        performRefresh(targetView, RefreshMode.GC16);
    }

    /**
     * 手动触发局部刷新
     */
    public void forcePartialRefresh(View targetView) {
        performRefresh(targetView, getDefaultPartialMode());
    }

    /**
     * 通过 sysfs 写入刷新指令
     */
    private boolean writeSysfsRefresh(RefreshMode mode) {
        if (mode == RefreshMode.AUTO) {
            mode = defaultMode;
        }
        
        // 尝试写入 waveform_mode
        String targetPath = epdcPath != null ? epdcPath : fbPath;
        if (targetPath == null) return false;
        
        boolean success = writeFileValue(targetPath, WAVEFORM_FILE, 
                                          String.valueOf(mode.getSysfsValue()));
        
        if (!success) {
            // 尝试其他文件名
            success = writeFileValue(targetPath, DISPLAY_FILE, 
                                     String.valueOf(mode.getSysfsValue()));
        }
        
        // 某些设备需要触发刷新
        if (success) {
            writeFileValue(targetPath, "trigger", "1");
        }
        
        return success;
    }

    /**
     * 读取 sysfs 文件内容
     */
    private String readFileValue(String path, String filename) {
        File file = new File(path, filename);
        if (!file.exists()) return null;
        
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(file));
            return reader.readLine();
        } catch (IOException e) {
            Log.w(TAG, "Failed to read " + file.getPath(), e);
            return null;
        } finally {
            if (reader != null) {
                try { reader.close(); } catch (IOException e) {}
            }
        }
    }

    /**
     * 写入 sysfs 文件
     */
    private boolean writeFileValue(String path, String filename, String value) {
        File file = new File(path, filename);
        if (!file.exists()) return false;
        
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(file);
            fos.write(value.getBytes());
            fos.flush();
            return true;
        } catch (IOException e) {
            Log.w(TAG, "Failed to write " + file.getPath() + " = " + value, e);
            return false;
        } finally {
            if (fos != null) {
                try { fos.close(); } catch (IOException e) {}
            }
        }
    }

    /**
     * 获取适合局部刷新的默认模式
     */
    private RefreshMode getDefaultPartialMode() {
        if (supportedModes.contains(RefreshMode.GL16)) return RefreshMode.GL16;
        if (supportedModes.contains(RefreshMode.DU)) return RefreshMode.DU;
        if (supportedModes.contains(RefreshMode.A2)) return RefreshMode.A2;
        return RefreshMode.GC16;
    }

    // --- Getters & Setters ---

    public boolean isSysfsAvailable() { return sysfsAvailable; }
    
    public Set<RefreshMode> getSupportedModes() { 
        return new HashSet<>(supportedModes); 
    }
    
    public RefreshMode getDefaultMode() { return defaultMode; }
    
    public void setDefaultMode(RefreshMode mode) {
        if (mode == RefreshMode.AUTO || supportedModes.contains(mode)) {
            this.defaultMode = mode;
        }
    }
    
    public int getFullRefreshInterval() { return fullRefreshInterval; }
    
    public void setFullRefreshInterval(int interval) {
        this.fullRefreshInterval = Math.max(1, interval);
    }
    
    public int getPageCounter() { return pageCounter; }
    
    public void resetPageCounter() { this.pageCounter = 0; }

    /**
     * 获取设备信息摘要（用于调试/设置界面显示）
     */
    public String getDeviceInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("EPDC: ").append(epdcPath != null ? epdcPath : "未找到").append("\n");
        sb.append("FB: ").append(fbPath != null ? fbPath : "未找到").append("\n");
        sb.append("Sysfs: ").append(sysfsAvailable ? "可用" : "不可用").append("\n");
        sb.append("支持模式: ").append(supportedModes).append("\n");
        sb.append("默认模式: ").append(defaultMode.getDisplayName());
        return sb.toString();
    }
}
