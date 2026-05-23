package com.einkreader.ui.settings;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.einkreader.R;
import com.einkreader.core.refresh.EinkRefreshManager;
import com.einkreader.core.refresh.RefreshMode;
import com.einkreader.utils.LogExporter;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 设置界面 Activity
 * 支持字体大小、行距、刷新模式、全局刷新间隔、翻页方式等配置
 * 所有设置通过 SharedPreferences 保存，实时生效
 */
public class SettingsActivity extends Activity {

    public static final String PREFS_NAME = "eink_reader_settings";
    public static final String KEY_FONT_SIZE = "font_size";
    public static final String KEY_LINE_SPACING = "line_spacing";
    public static final String KEY_REFRESH_MODE = "refresh_mode";
    public static final String KEY_REFRESH_INTERVAL = "refresh_interval";
    public static final String KEY_PAGE_MODE = "page_mode";

    private SharedPreferences prefs;

    private SeekBar seekFontSize;
    private TextView textFontSize;
    private SeekBar seekLineSpacing;
    private TextView textLineSpacing;
    private Spinner spinnerRefreshMode;
    private SeekBar seekRefreshInterval;
    private TextView textRefreshInterval;
    private Spinner spinnerPageMode;
    private TextView textDeviceInfo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.activity_settings);

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        initFontSize();
        initLineSpacing();
        initRefreshMode();
        initRefreshInterval();
        initPageMode();
        initDeviceInfo();
        initLogExport();
    }

    // --- 字体大小 ---
    private void initFontSize() {
        seekFontSize = (SeekBar) findViewById(R.id.seek_font_size);
        textFontSize = (TextView) findViewById(R.id.text_font_size);

        float savedSize = prefs.getFloat(KEY_FONT_SIZE, 18f);
        int progress = (int) (savedSize - 10); // 10-40 sp -> 0-30
        seekFontSize.setProgress(Math.max(0, Math.min(30, progress)));
        updateFontSizeLabel(progress);

        seekFontSize.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateFontSizeLabel(progress);
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                float size = seekBar.getProgress() + 10f;
                prefs.edit().putFloat(KEY_FONT_SIZE, size).apply();
            }
        });
    }

    private void updateFontSizeLabel(int progress) {
        float size = progress + 10f;
        textFontSize.setText(String.format("%.0fsp", size));
    }

    // --- 行距 ---
    private void initLineSpacing() {
        seekLineSpacing = (SeekBar) findViewById(R.id.seek_line_spacing);
        textLineSpacing = (TextView) findViewById(R.id.text_line_spacing);

        float savedSpacing = prefs.getFloat(KEY_LINE_SPACING, 1.5f);
        int progress = (int) ((savedSpacing - 1.0f) * 10); // 1.0-3.0 -> 0-20
        seekLineSpacing.setProgress(Math.max(0, Math.min(20, progress)));
        updateLineSpacingLabel(progress);

        seekLineSpacing.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateLineSpacingLabel(progress);
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                float spacing = 1.0f + seekBar.getProgress() / 10.0f;
                prefs.edit().putFloat(KEY_LINE_SPACING, spacing).apply();
            }
        });
    }

    private void updateLineSpacingLabel(int progress) {
        float spacing = 1.0f + progress / 10.0f;
        textLineSpacing.setText(String.format("%.1fx", spacing));
    }

    // --- 刷新模式 ---
    private void initRefreshMode() {
        spinnerRefreshMode = (Spinner) findViewById(R.id.spinner_refresh_mode);

        List<String> modeNames = new ArrayList<>();
        modeNames.add("自动");
        modeNames.add("全局刷新 (GC16)");
        modeNames.add("局部刷新 (DU)");
        modeNames.add("极速刷新 (A2)");
        modeNames.add("局部灰度 (GL16)");

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, modeNames);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerRefreshMode.setAdapter(adapter);

        int savedMode = prefs.getInt(KEY_REFRESH_MODE, 0);
        spinnerRefreshMode.setSelection(savedMode);

        spinnerRefreshMode.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                prefs.edit().putInt(KEY_REFRESH_MODE, position).apply();
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    // --- 全局刷新间隔 ---
    private void initRefreshInterval() {
        seekRefreshInterval = (SeekBar) findViewById(R.id.seek_refresh_interval);
        textRefreshInterval = (TextView) findViewById(R.id.text_refresh_interval);

        int savedInterval = prefs.getInt(KEY_REFRESH_INTERVAL, 10);
        seekRefreshInterval.setProgress(Math.max(0, Math.min(49, savedInterval - 1)));
        updateRefreshIntervalLabel(savedInterval);

        seekRefreshInterval.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateRefreshIntervalLabel(progress + 1);
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                int interval = seekBar.getProgress() + 1;
                prefs.edit().putInt(KEY_REFRESH_INTERVAL, interval).apply();
            }
        });
    }

    private void updateRefreshIntervalLabel(int interval) {
        textRefreshInterval.setText("每 " + interval + " 页");
    }

    // --- 翻页方式 ---
    private void initPageMode() {
        spinnerPageMode = (Spinner) findViewById(R.id.spinner_page_mode);

        List<String> pageModes = new ArrayList<>();
        pageModes.add("点击翻页（推荐）");
        pageModes.add("滑动翻页");
        pageModes.add("音量键翻页");

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, pageModes);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerPageMode.setAdapter(adapter);

        int savedMode = prefs.getInt(KEY_PAGE_MODE, 0);
        spinnerPageMode.setSelection(savedMode);

        spinnerPageMode.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                prefs.edit().putInt(KEY_PAGE_MODE, position).apply();
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    // --- 设备信息 ---
    private void initDeviceInfo() {
        textDeviceInfo = (TextView) findViewById(R.id.text_device_info);

        EinkRefreshManager manager = new EinkRefreshManager(this);
        manager.initialize(new EinkRefreshManager.RefreshCallback() {
            @Override
            public void onRefreshStart(RefreshMode mode) {}
            @Override
            public void onRefreshComplete(RefreshMode mode) {}
            @Override
            public void onModeDetected(Set<RefreshMode> modes) {
                final String info = "检测到的刷新模式: " + modes;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        textDeviceInfo.setText(info);
                    }
                });
            }
            @Override
            public void onSysfsUnavailable() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        textDeviceInfo.setText("sysfs 不可用，使用 Android API 回退模式");
                    }
                });
            }
        });
    }

    // --- 日志导出 ---
    private void initLogExport() {
        Button btnExport = (Button) findViewById(R.id.btn_export_log);
        Button btnClean = (Button) findViewById(R.id.btn_clean_logs);
        TextView textLogPath = (TextView) findViewById(R.id.text_log_path);

        // 显示日志文件位置
        File logDir = LogExporter.getLogDir(this);
        File booksDir = new File(getExternalFilesDir(null), "books");
        textLogPath.setText("书籍文件: " + booksDir.getAbsolutePath()
            + "\n日志文件: " + logDir.getAbsolutePath());

        btnExport.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(SettingsActivity.this, "正在导出日志...", Toast.LENGTH_SHORT).show();
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        final File logFile = LogExporter.exportLogs(SettingsActivity.this, null);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (logFile != null) {
                                    Toast.makeText(SettingsActivity.this,
                                        "日志已保存: " + logFile.getName(),
                                        Toast.LENGTH_LONG).show();
                                } else {
                                    Toast.makeText(SettingsActivity.this,
                                        "日志导出失败", Toast.LENGTH_SHORT).show();
                                }
                            }
                        });
                    }
                }).start();
            }
        });

        btnClean.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int deleted = LogExporter.cleanOldLogs(SettingsActivity.this);
                Toast.makeText(SettingsActivity.this,
                    "已清理 " + deleted + " 个旧日志文件", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // --- 静态工具方法供其他 Activity 读取设置 ---

    public static float getFontSize(SharedPreferences prefs) {
        return prefs.getFloat(KEY_FONT_SIZE, 18f);
    }

    public static float getLineSpacing(SharedPreferences prefs) {
        return prefs.getFloat(KEY_LINE_SPACING, 1.5f);
    }

    public static int getRefreshInterval(SharedPreferences prefs) {
        return prefs.getInt(KEY_REFRESH_INTERVAL, 10);
    }

    public static int getRefreshModeIndex(SharedPreferences prefs) {
        return prefs.getInt(KEY_REFRESH_MODE, 0);
    }

    public static int getPageModeIndex(SharedPreferences prefs) {
        return prefs.getInt(KEY_PAGE_MODE, 0);
    }
}
