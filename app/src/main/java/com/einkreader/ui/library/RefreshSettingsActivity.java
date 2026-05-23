package com.einkreader.ui.library;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.einkreader.R;
import com.einkreader.core.refresh.EinkRefreshManager;
import com.einkreader.core.refresh.RefreshMode;

/**
 * 墨水屏刷新设置界面
 * 配置自动全刷间隔、刷新模式、手动全刷
 */
public class RefreshSettingsActivity extends Activity {

    private static final String PREFS_NAME = "eink_reader_prefs";
    private static final String KEY_FULL_REFRESH_INTERVAL = "full_refresh_interval";
    private static final String KEY_REFRESH_MODE = "refresh_mode"; // "auto" or "manual"

    private static final int[] INTERVAL_VALUES = {5, 10, 15, 20, 0}; // 0 = 每章
    private static final String[] INTERVAL_LABELS = {"每 5 页", "每 10 页", "每 15 页", "每 20 页", "每章"};

    private Spinner spinnerInterval;
    private RadioGroup rgMode;
    private RadioButton rbAuto, rbManual;
    private TextView tvDeviceStatus;
    private SharedPreferences prefs;
    private EinkRefreshManager refreshManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.activity_refresh_settings);

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        // 初始化视图
        spinnerInterval = (Spinner) findViewById(R.id.spinner_full_refresh_interval);
        rgMode = (RadioGroup) findViewById(R.id.rg_refresh_mode);
        rbAuto = (RadioButton) findViewById(R.id.rb_auto);
        rbManual = (RadioButton) findViewById(R.id.rb_manual);
        tvDeviceStatus = (TextView) findViewById(R.id.tv_device_status);

        // 设置间隔选项
        ArrayAdapter<String> intervalAdapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, INTERVAL_LABELS);
        intervalAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerInterval.setAdapter(intervalAdapter);

        // 检测设备刷新能力
        refreshManager = new EinkRefreshManager(this);
        refreshManager.initialize(new EinkRefreshManager.RefreshCallback() {
            @Override
            public void onRefreshStart(RefreshMode mode) {}

            @Override
            public void onRefreshComplete(RefreshMode mode) {}

            @Override
            public void onModeDetected(java.util.Set<RefreshMode> modes) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        updateDeviceStatus(modes);
                    }
                });
            }

            @Override
            public void onSysfsUnavailable() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        tvDeviceStatus.setText("❌ 未检测到 sysfs 节点，使用 Android API 回退模式");
                    }
                });
            }
        });

        // 返回按钮
        findViewById(R.id.btn_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveSettings();
                finish();
            }
        });

        // 加载已保存的设置
        loadSettings();

        // 手动全刷按钮
        findViewById(R.id.btn_manual_full_refresh).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                refreshManager.forceFullRefresh(new View(RefreshSettingsActivity.this));
                Toast.makeText(RefreshSettingsActivity.this, "已执行全屏刷新", Toast.LENGTH_SHORT).show();
            }
        });

        // 恢复默认按钮
        findViewById(R.id.btn_reset_defaults).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                resetDefaults();
                Toast.makeText(RefreshSettingsActivity.this, "已恢复默认设置", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadSettings() {
        int interval = prefs.getInt(KEY_FULL_REFRESH_INTERVAL, 10); // 默认 10 页
        String mode = prefs.getString(KEY_REFRESH_MODE, "auto");

        // 设置 Spinner 位置
        for (int i = 0; i < INTERVAL_VALUES.length; i++) {
            if (INTERVAL_VALUES[i] == interval) {
                spinnerInterval.setSelection(i);
                break;
            }
        }

        // 设置 RadioGroup
        if ("manual".equals(mode)) {
            rbManual.setChecked(true);
        } else {
            rbAuto.setChecked(true);
        }
    }

    private void saveSettings() {
        int intervalIndex = spinnerInterval.getSelectedItemPosition();
        int interval = INTERVAL_VALUES[intervalIndex];
        String mode = rbManual.isChecked() ? "manual" : "auto";

        prefs.edit()
                .putInt(KEY_FULL_REFRESH_INTERVAL, interval)
                .putString(KEY_REFRESH_MODE, mode)
                .apply();
    }

    private void resetDefaults() {
        spinnerInterval.setSelection(1); // 每 10 页
        rbAuto.setChecked(true);
        saveSettings();
    }

    private void updateDeviceStatus(java.util.Set<RefreshMode> modes) {
        StringBuilder sb = new StringBuilder();
        sb.append("✅ 检测到刷新模式：");
        if (modes.isEmpty()) {
            sb.append("无（使用默认）");
        } else {
            boolean first = true;
            for (RefreshMode mode : modes) {
                if (!first) sb.append(", ");
                sb.append(mode.getDisplayName());
                first = false;
            }
        }
        tvDeviceStatus.setText(sb.toString());
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveSettings();
    }

    @Override
    public void onBackPressed() {
        saveSettings();
        super.onBackPressed();
    }
}
