package com.einkreader.ui.reader;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.CheckBox;
import android.widget.RadioGroup;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import com.einkreader.R;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * 阅读设置页面 - 纯文字加减按钮风格，适配墨水屏
 */
public class ReadingSettingsActivity extends Activity {

    private static final String PREFS_NAME = "eink_reader_prefs";

    /** 按键映射 SharedPreferences key */
    public static final String KEY_PREV_KEYCODE = "key_prev_keycode";
    public static final String KEY_NEXT_KEYCODE = "key_next_keycode";
    public static final String KEY_PREV_KEY_NAME = "key_prev_key_name";
    public static final String KEY_NEXT_KEY_NAME = "key_next_key_name";
    /** 全刷 SharedPreferences key */
    public static final String KEY_CHAPTER_REFRESH = "full_refresh_chapter";
    public static final String KEY_PAGE_REFRESH_INTERVAL = "page_refresh_interval";
    /** 全刷延时 SharedPreferences key */
    public static final String KEY_REFRESH_DELAY1 = "refresh_delay1";
    public static final String KEY_REFRESH_DELAY2 = "refresh_delay2";

    private static final int REQUEST_PICK_FONT = 3001;

    // 字号 (10-40)
    private int fontSize = 20;
    private TextView tvSizeValue;

    // 行距 (10-25, 实际值/10)
    private int lineHeightInt = 13;
    private TextView tvLineSpacingValue;

    // 段距 (0-50, step 2)
    private int paragraphSpacing = 0;
    private TextView tvParagraphSpacingValue;

    // 字距 (0-10)
    private int letterSpacing = 0;
    private TextView tvLetterSpacingValue;

    // 粗细 (-50 到 +50, step 5)
    private int fontWeight = 0;
    private TextView tvWeightValue;

    // 触屏模式
    private RadioGroup rgTouchMode;

    // 物理按键映射
    private int prevKeycode = KeyEvent.KEYCODE_PAGE_UP;
    private int nextKeycode = KeyEvent.KEYCODE_PAGE_DOWN;
    private String prevKeyName = "PAGE_UP";
    private String nextKeyName = "PAGE_DOWN";
    private boolean waitingForKey = false;
    private int waitingForKeyType = 0;
    private TextView tvKeyMappingHint;
    private TextView tvPrevKeyValue;
    private TextView tvNextKeyValue;

    // 按键日志
    private StringBuilder keyLog = new StringBuilder();
    private TextView tvKeyLog;

    // 全刷
    private CheckBox cbChapterRefresh;
    private int pageRefreshInterval = 0;
    private TextView tvPageRefreshValue;

    // 全刷延时
    private int refreshDelay1 = 400;  // 黑屏→白屏 延迟(ms)
    private int refreshDelay2 = 400;  // 白屏→恢复 延迟(ms)
    private TextView tvRefreshDelay1Value;
    private TextView tvRefreshDelay2Value;

    // 边距
    private int paddingTop = 6;
    private int paddingBottom = 6;
    private int paddingHorizontal = 8;
    private TextView tvPaddingTopValue;
    private TextView tvPaddingBottomValue;
    private TextView tvPaddingHorizontalValue;

    // 翻页重叠行数
    private int overlapLines = 0;
    private TextView tvOverlapLinesValue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(R.layout.activity_reading_settings);
            loadCurrentSettings();
            initUI();
        } catch (Exception e) {
            e.printStackTrace();
            finish();
        }
    }

    private void loadCurrentSettings() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        fontSize = (int) safeGetFloat(prefs, "font_size", 20f);
        lineHeightInt = (int) safeGetFloat(prefs, "line_spacing", 1.3f) * 10;
        paragraphSpacing = (int) safeGetFloat(prefs, "paragraph_spacing", 0f);
        letterSpacing = (int) safeGetFloat(prefs, "letter_spacing", 0f);
        fontWeight = (int) safeGetFloat(prefs, "font_weight", 0f);
        paddingTop = (int) safeGetFloat(prefs, "padding_top", 6f);
        paddingBottom = (int) safeGetFloat(prefs, "padding_bottom", 6f);
        paddingHorizontal = (int) safeGetFloat(prefs, "padding_horizontal", 8f);
        pageRefreshInterval = (int) safeGetFloat(prefs, "page_refresh_interval", 0f);
        refreshDelay1 = (int) safeGetFloat(prefs, KEY_REFRESH_DELAY1, 400f);
        refreshDelay2 = (int) safeGetFloat(prefs, KEY_REFRESH_DELAY2, 400f);
        overlapLines = (int) safeGetFloat(prefs, "overlap_lines", 0f);
        prevKeycode = prefs.getInt(KEY_PREV_KEYCODE, KeyEvent.KEYCODE_PAGE_UP);
        nextKeycode = prefs.getInt(KEY_NEXT_KEYCODE, KeyEvent.KEYCODE_PAGE_DOWN);
        prevKeyName = prefs.getString(KEY_PREV_KEY_NAME, "PAGE_UP");
        nextKeyName = prefs.getString(KEY_NEXT_KEY_NAME, "PAGE_DOWN");
    }

    private void initUI() {
        // 返回
        findViewById(R.id.btn_back).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { saveAndFinish(); }
        });

        // 恢复默认
        findViewById(R.id.btn_reset_defaults).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { resetToDefaults(); }
        });

        // ===== 字号 =====
        tvSizeValue = findViewById(R.id.tv_size_value);
        tvSizeValue.setText(String.valueOf(fontSize));
        setupStepper(R.id.btn_size_minus, R.id.btn_size_plus, 10, 40, 1, fontSize, new StepperCallback() {
            @Override public void onValueChange(int v) { fontSize = v; tvSizeValue.setText(String.valueOf(v)); }
        });

        // ===== 行距 =====
        tvLineSpacingValue = findViewById(R.id.tv_line_spacing_value);
        tvLineSpacingValue.setText(String.format("%.1f", lineHeightInt / 10f));
        setupStepper(R.id.btn_line_spacing_minus, R.id.btn_line_spacing_plus, 10, 25, 1, lineHeightInt, new StepperCallback() {
            @Override public void onValueChange(int v) { lineHeightInt = v; tvLineSpacingValue.setText(String.format("%.1f", v / 10f)); }
        });

        // ===== 段距 =====
        tvParagraphSpacingValue = findViewById(R.id.tv_paragraph_spacing_value);
        tvParagraphSpacingValue.setText(String.valueOf(paragraphSpacing));
        setupStepper(R.id.btn_paragraph_spacing_minus, R.id.btn_paragraph_spacing_plus, 0, 50, 2, paragraphSpacing, new StepperCallback() {
            @Override public void onValueChange(int v) { paragraphSpacing = v; tvParagraphSpacingValue.setText(String.valueOf(v)); }
        });

        // ===== 字距 =====
        tvLetterSpacingValue = findViewById(R.id.tv_letter_spacing_value);
        tvLetterSpacingValue.setText(String.valueOf(letterSpacing));
        setupStepper(R.id.btn_letter_spacing_minus, R.id.btn_letter_spacing_plus, 0, 10, 1, letterSpacing, new StepperCallback() {
            @Override public void onValueChange(int v) { letterSpacing = v; tvLetterSpacingValue.setText(String.valueOf(v)); }
        });

        // ===== 粗细 =====
        tvWeightValue = findViewById(R.id.tv_weight_value);
        tvWeightValue.setText(String.valueOf(fontWeight));
        setupStepper(R.id.btn_weight_minus, R.id.btn_weight_plus, -50, 50, 5, fontWeight, new StepperCallback() {
            @Override public void onValueChange(int v) {
                fontWeight = v;
                String label = v == 0 ? "标准" : (v > 0 ? "粗 (+" + v + ")" : "细 (" + v + ")");
                tvWeightValue.setText(label);
            }
        });

        // ===== 自定义字体 =====
        final TextView tvCurrentFont = findViewById(R.id.tv_current_font);
        String savedFontPath = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString("custom_font_path", null);
        if (savedFontPath != null && !savedFontPath.isEmpty()) {
            tvCurrentFont.setText(new File(savedFontPath).getName());
        }
        findViewById(R.id.btn_import_font).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Intent intent = new Intent(ReadingSettingsActivity.this, com.einkreader.ui.library.FilePickerActivity.class);
                intent.putExtra("filter_extension", ".ttf");
                startActivityForResult(intent, REQUEST_PICK_FONT);
            }
        });
        findViewById(R.id.btn_reset_font).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().remove("custom_font_path").apply();
                tvCurrentFont.setText("系统默认");
                Toast.makeText(ReadingSettingsActivity.this, "已恢复默认字体", Toast.LENGTH_SHORT).show();
            }
        });

        // ===== 触屏翻页模式 =====
        rgTouchMode = findViewById(R.id.rg_touch_mode);
        int savedMode = (int) safeGetFloat(getSharedPreferences(PREFS_NAME, MODE_PRIVATE), "touch_mode", 0f);
        if (savedMode == 1) rgTouchMode.check(R.id.rb_tap_anywhere);
        else if (savedMode == 2) rgTouchMode.check(R.id.rb_swipe);
        else rgTouchMode.check(R.id.rb_tap_left_right);

        // ===== 物理按键映射 =====
        tvKeyMappingHint = findViewById(R.id.tv_key_mapping_hint);
        tvPrevKeyValue = findViewById(R.id.tv_prev_key_value);
        tvNextKeyValue = findViewById(R.id.tv_next_key_value);
        tvPrevKeyValue.setText("当前: " + prevKeyName + " (" + prevKeycode + ")");
        tvNextKeyValue.setText("当前: " + nextKeyName + " (" + nextKeycode + ")");

        findViewById(R.id.btn_set_prev_key).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                waitingForKey = true; waitingForKeyType = 1;
                tvKeyMappingHint.setText("请按上一页键..."); tvKeyMappingHint.setVisibility(View.VISIBLE);
            }
        });
        findViewById(R.id.btn_set_next_key).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                waitingForKey = true; waitingForKeyType = 2;
                tvKeyMappingHint.setText("请按下一页键..."); tvKeyMappingHint.setVisibility(View.VISIBLE);
            }
        });
        findViewById(R.id.btn_clear_key_mapping).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                prevKeycode = KeyEvent.KEYCODE_PAGE_UP; nextKeycode = KeyEvent.KEYCODE_PAGE_DOWN;
                prevKeyName = "PAGE_UP"; nextKeyName = "PAGE_DOWN";
                tvPrevKeyValue.setText("当前: PAGE_UP (" + prevKeycode + ")");
                tvNextKeyValue.setText("当前: PAGE_DOWN (" + nextKeycode + ")");
                tvKeyMappingHint.setText("已清除，恢复默认"); tvKeyMappingHint.setVisibility(View.VISIBLE);
            }
        });

        // 按键日志
        tvKeyLog = new TextView(this);
        tvKeyLog.setTextSize(11);
        tvKeyLog.setTextColor(Color.parseColor("#333333"));
        tvKeyLog.setPadding(dp2px(12), dp2px(8), dp2px(12), dp2px(8));
        tvKeyLog.setTypeface(android.graphics.Typeface.MONOSPACE);
        tvKeyLog.setText("按键日志:\n等待按键...");
        ((android.widget.LinearLayout) findViewById(R.id.settings_content)).addView(tvKeyLog);

        // ===== 边距 =====
        tvPaddingTopValue = findViewById(R.id.tv_padding_top_value);
        tvPaddingBottomValue = findViewById(R.id.tv_padding_bottom_value);
        tvPaddingHorizontalValue = findViewById(R.id.tv_padding_horizontal_value);
        tvPaddingTopValue.setText(String.valueOf(paddingTop));
        tvPaddingBottomValue.setText(String.valueOf(paddingBottom));
        tvPaddingHorizontalValue.setText(String.valueOf(paddingHorizontal));

        setupStepper(R.id.btn_padding_top_minus, R.id.btn_padding_top_plus, 0, 50, 1, paddingTop, new StepperCallback() {
            @Override public void onValueChange(int v) { paddingTop = v; tvPaddingTopValue.setText(String.valueOf(v)); }
        });
        setupStepper(R.id.btn_padding_bottom_minus, R.id.btn_padding_bottom_plus, 0, 50, 1, paddingBottom, new StepperCallback() {
            @Override public void onValueChange(int v) { paddingBottom = v; tvPaddingBottomValue.setText(String.valueOf(v)); }
        });
        setupStepper(R.id.btn_padding_horizontal_minus, R.id.btn_padding_horizontal_plus, 0, 50, 1, paddingHorizontal, new StepperCallback() {
            @Override public void onValueChange(int v) { paddingHorizontal = v; tvPaddingHorizontalValue.setText(String.valueOf(v)); }
        });

        // ===== 翻页重叠行数 =====
        tvOverlapLinesValue = findViewById(R.id.tv_overlap_lines_value);
        tvOverlapLinesValue.setText(String.valueOf(overlapLines));
        setupStepper(R.id.btn_overlap_lines_minus, R.id.btn_overlap_lines_plus, 0, 20, 1, overlapLines, new StepperCallback() {
            @Override public void onValueChange(int v) { overlapLines = v; tvOverlapLinesValue.setText(String.valueOf(v)); }
        });

        // ===== 全刷 =====
        cbChapterRefresh = findViewById(R.id.cb_chapter_refresh);
        cbChapterRefresh.setChecked(safeGetFloat(getSharedPreferences(PREFS_NAME, MODE_PRIVATE), "full_refresh_chapter", 1f) > 0.5f);

        tvPageRefreshValue = findViewById(R.id.tv_page_refresh_value);
        updatePageRefreshDisplay();
        setupStepper(R.id.btn_page_refresh_minus, R.id.btn_page_refresh_plus, 0, 50, 1, pageRefreshInterval, new StepperCallback() {
            @Override public void onValueChange(int v) { pageRefreshInterval = v; updatePageRefreshDisplay(); }
        });

        // ===== 全刷延时 =====
        tvRefreshDelay1Value = findViewById(R.id.tv_refresh_delay1_value);
        tvRefreshDelay2Value = findViewById(R.id.tv_refresh_delay2_value);
        tvRefreshDelay1Value.setText(refreshDelay1 + "ms");
        tvRefreshDelay2Value.setText(refreshDelay2 + "ms");
        setupStepper(R.id.btn_refresh_delay1_minus, R.id.btn_refresh_delay1_plus, 100, 2000, 50, refreshDelay1, new StepperCallback() {
            @Override public void onValueChange(int v) { refreshDelay1 = v; tvRefreshDelay1Value.setText(v + "ms"); }
        });
        setupStepper(R.id.btn_refresh_delay2_minus, R.id.btn_refresh_delay2_plus, 100, 2000, 50, refreshDelay2, new StepperCallback() {
            @Override public void onValueChange(int v) { refreshDelay2 = v; tvRefreshDelay2Value.setText(v + "ms"); }
        });
    }

    private void updatePageRefreshDisplay() {
        tvPageRefreshValue.setText(pageRefreshInterval == 0 ? "关闭" : "每" + pageRefreshInterval + "页");
    }

    // ===== 按键处理 =====

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // 按键日志
        String name = getKeyName(keyCode);
        if (keyLog.length() > 500) keyLog.setLength(0);
        keyLog.append(name).append(" (").append(keyCode).append(")\n");
        if (tvKeyLog != null) tvKeyLog.setText("按键日志:\n" + keyLog.toString());

        if (waitingForKey) {
            if (waitingForKeyType == 1) {
                prevKeycode = keyCode; prevKeyName = name;
                tvPrevKeyValue.setText("当前: " + name + " (" + keyCode + ")");
                tvKeyMappingHint.setText("已设置上一页键: " + name); tvKeyMappingHint.setVisibility(View.VISIBLE);
            } else {
                nextKeycode = keyCode; nextKeyName = name;
                tvNextKeyValue.setText("当前: " + name + " (" + keyCode + ")");
                tvKeyMappingHint.setText("已设置下一页键: " + name); tvKeyMappingHint.setVisibility(View.VISIBLE);
            }
            waitingForKey = false; waitingForKeyType = 0;
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private String getKeyName(int keyCode) {
        String name = KeyEvent.keyCodeToString(keyCode);
        if (name != null && name.startsWith("KEYCODE_")) name = name.substring(8);
        if (keyCode >= 190 && keyCode <= 201) return "F" + (keyCode - 189);
        if (keyCode == KeyEvent.KEYCODE_PAGE_UP) return "PAGE_UP";
        if (keyCode == KeyEvent.KEYCODE_PAGE_DOWN) return "PAGE_DOWN";
        return name;
    }

    private int dp2px(float dp) {
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void resetToDefaults() {
        fontSize = 20; lineHeightInt = 13; paragraphSpacing = 0; letterSpacing = 0;
        fontWeight = 0; paddingTop = 6; paddingBottom = 6; paddingHorizontal = 8;
        pageRefreshInterval = 0; refreshDelay1 = 400; refreshDelay2 = 400; overlapLines = 0;

        tvSizeValue.setText(String.valueOf(fontSize));
        tvLineSpacingValue.setText(String.format("%.1f", lineHeightInt / 10f));
        tvParagraphSpacingValue.setText(String.valueOf(paragraphSpacing));
        tvLetterSpacingValue.setText(String.valueOf(letterSpacing));
        tvWeightValue.setText(String.valueOf(fontWeight));
        tvPaddingTopValue.setText(String.valueOf(paddingTop));
        tvPaddingBottomValue.setText(String.valueOf(paddingBottom));
        tvPaddingHorizontalValue.setText(String.valueOf(paddingHorizontal));
        tvOverlapLinesValue.setText(String.valueOf(overlapLines));
        updatePageRefreshDisplay();
        tvRefreshDelay1Value.setText(refreshDelay1 + "ms");
        tvRefreshDelay2Value.setText(refreshDelay2 + "ms");
        ((RadioButton) findViewById(R.id.rb_tap_left_right)).setChecked(true);
        cbChapterRefresh.setChecked(true);
        tvKeyMappingHint.setText("已恢复默认设置"); tvKeyMappingHint.setVisibility(View.VISIBLE);
    }

    private void saveAndFinish() {
        SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();
        editor.putFloat("font_size", fontSize);
        editor.putFloat("line_height", lineHeightInt / 10f);
        editor.putFloat("paragraph_spacing", paragraphSpacing);
        editor.putFloat("letter_spacing", letterSpacing);
        editor.putFloat("font_weight", fontWeight);
        editor.putFloat("padding_top", paddingTop);
        editor.putFloat("padding_bottom", paddingBottom);
        editor.putFloat("padding_horizontal", paddingHorizontal);
        editor.putFloat("full_refresh_chapter", cbChapterRefresh.isChecked() ? 1f : 0f);
        editor.putFloat("page_refresh_interval", pageRefreshInterval);
        editor.putFloat(KEY_REFRESH_DELAY1, refreshDelay1);
        editor.putFloat(KEY_REFRESH_DELAY2, refreshDelay2);
        editor.putFloat("overlap_lines", overlapLines);
        int checkedId = rgTouchMode.getCheckedRadioButtonId();
        int touchMode = 0;
        if (checkedId == R.id.rb_tap_anywhere) touchMode = 1;
        else if (checkedId == R.id.rb_swipe) touchMode = 2;
        editor.putFloat("touch_mode", touchMode);
        editor.putInt(KEY_PREV_KEYCODE, prevKeycode);
        editor.putInt(KEY_NEXT_KEYCODE, nextKeycode);
        editor.putString(KEY_PREV_KEY_NAME, prevKeyName);
        editor.putString(KEY_NEXT_KEY_NAME, nextKeyName);
        editor.apply();
        finish();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PICK_FONT && resultCode == RESULT_OK && data != null) {
            String fontPath = data.getStringExtra(com.einkreader.ui.library.FilePickerActivity.EXTRA_SELECTED_PATH);
            if (fontPath != null && fontPath.toLowerCase().endsWith(".ttf")) {
                // 复制字体到应用私有目录，保证重装后也能用
                try {
                    File srcFile = new File(fontPath);
                    File destDir = new File(getFilesDir(), "fonts");
                    destDir.mkdirs();
                    File destFile = new File(destDir, srcFile.getName());
                    copyFile(srcFile, destFile);
                    // 保存路径
                    getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                        .putString("custom_font_path", destFile.getAbsolutePath()).apply();
                    TextView tvCurrentFont = findViewById(R.id.tv_current_font);
                    tvCurrentFont.setText(destFile.getName());
                    Toast.makeText(this, "字体已导入: " + destFile.getName(), Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Toast.makeText(this, "字体导入失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private void copyFile(File src, File dst) throws Exception {
        InputStream in = new FileInputStream(src);
        OutputStream out = new FileOutputStream(dst);
        byte[] buf = new byte[4096];
        int len;
        while ((len = in.read(buf)) > 0) {
            out.write(buf, 0, len);
        }
        in.close();
        out.close();
    }

    @Override
    public void onBackPressed() { saveAndFinish(); }

    // ===== 通用 Stepper =====

    private interface StepperCallback {
        void onValueChange(int value);
    }

    private void setupStepper(int minusId, int plusId, final int min, final int max, final int step,
                              int initial, final StepperCallback callback) {
        final TextView tvMinus = findViewById(minusId);
        final TextView tvPlus = findViewById(plusId);
        if (tvMinus == null || tvPlus == null) return;

        final int[] current = {initial};

        tvMinus.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                int newVal = Math.max(min, current[0] - step);
                current[0] = newVal;
                callback.onValueChange(newVal);
            }
        });
        tvPlus.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                int newVal = Math.min(max, current[0] + step);
                current[0] = newVal;
                callback.onValueChange(newVal);
            }
        });
    }

    private float safeGetFloat(SharedPreferences prefs, String key, float def) {
        try {
            return prefs.getFloat(key, def);
        } catch (ClassCastException e) {
            try { return prefs.getInt(key, (int) def); } catch (Exception e2) {
                try { return prefs.getBoolean(key, def > 0.5f) ? 1f : 0f; } catch (Exception e3) {
                    return def;
                }
            }
        }
    }
}
