package com.einkreader.ui.reader;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import com.einkreader.R;
import com.einkreader.core.model.Chapter;
import com.einkreader.core.parser.EpubParser;
import com.einkreader.core.parser.TxtParser;
import com.einkreader.core.refresh.EinkRefreshManager;
import com.einkreader.core.refresh.RefreshMode;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 阅读器 Activity
 * 纯文字底部菜单栏、音量键/硬件翻页键翻页、白底黑字顶部状态栏
 */
public class ReaderActivity extends Activity {

    private static final String PREFS_NAME = "eink_reader_prefs";
    private static final int REQUEST_TOC = 2001;
    private static final int REQUEST_READING_SETTINGS = 2002;

    /** 全局错误日志 */
    private static final StringBuilder errorLog = new StringBuilder();
    private static final int MAX_LOG_LENGTH = 8000;

    public static void appendLog(String tag, String message) {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        String ts = sdf.format(new Date());
        errorLog.append("[").append(ts).append("] ").append(tag).append(": ").append(message).append("\n");
        if (errorLog.length() > MAX_LOG_LENGTH) {
            errorLog.delete(0, errorLog.length() - MAX_LOG_LENGTH);
        }
    }

    public static String getLog() {
        return errorLog.toString();
    }

    private ReaderView readerView;
    private View topStatusBar;
    private View bottomMenu;
    private TextView statusTime;
    private TextView statusChapter;
    private TextView statusBattery;
    private TextView btnBack;
    private TextView btnToc;
    private TextView btnFontMinus;
    private TextView btnFontPlus;
    private TextView btnSettings;
    private TextView btnFullRefresh;
    private TextView btnShowLog;
    private TextView btnBrightMinus;
    private TextView btnBrightPlus;

    private EinkRefreshManager refreshManager;
    private List<Chapter> chapters;
    private int currentChapterIndex;
    private SharedPreferences prefs;
    private boolean menuVisible = false;

    private Handler uiHandler;
    private View loadingOverlay;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 全屏沉浸模式
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.activity_reader);

        uiHandler = new Handler(Looper.getMainLooper());
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        // 应用保存的亮度
        float savedBrightness = safeGetFloat(prefs, "screen_brightness", 0.5f);
        applyBrightness(savedBrightness);

        // 初始化 E-Ink 刷新管理器
        try {
            refreshManager = new EinkRefreshManager(this);
            refreshManager.initialize(new EinkRefreshManager.RefreshCallback() {
                @Override
                public void onRefreshStart(RefreshMode mode) {}
                @Override
                public void onRefreshComplete(RefreshMode mode) {}
                @Override
                public void onModeDetected(java.util.Set<RefreshMode> modes) {}
                @Override
                public void onSysfsUnavailable() {}
            });
        } catch (Exception e) {
            refreshManager = null;
        }

        // 初始化视图
        readerView = (ReaderView) findViewById(R.id.reader_view);
        topStatusBar = findViewById(R.id.top_status_bar);
        bottomMenu = findViewById(R.id.bottom_menu);
        statusTime = (TextView) findViewById(R.id.status_time);
        statusChapter = (TextView) findViewById(R.id.status_chapter);
        statusBattery = (TextView) findViewById(R.id.status_battery);
        btnBack = (TextView) findViewById(R.id.btn_back);
        btnToc = (TextView) findViewById(R.id.btn_toc);
        btnFontMinus = (TextView) findViewById(R.id.btn_font_minus);
        btnFontPlus = (TextView) findViewById(R.id.btn_font_plus);
        btnSettings = (TextView) findViewById(R.id.btn_settings);
        btnFullRefresh = (TextView) findViewById(R.id.btn_full_refresh);
        btnShowLog = (TextView) findViewById(R.id.btn_show_log);
        btnBrightMinus = (TextView) findViewById(R.id.btn_bright_minus);
        btnBrightPlus = (TextView) findViewById(R.id.btn_bright_plus);
        loadingOverlay = findViewById(R.id.loading_overlay);

        // 初始化亮度按钮文字
        float initBrightness = safeGetFloat(prefs, "screen_brightness", 0.5f);
        int initPercent = (int) (initBrightness * 100);
        btnBrightMinus.setText("亮-\n" + initPercent + "%");

        if (readerView == null) {
            showError("严重错误", "ReaderView 初始化失败");
            return;
        }

        // 底部菜单按钮点击（纯文字，无图标）
        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        btnToc.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleMenu(false);
                openToc();
            }
        });
        btnFontMinus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                adjustFontSize(-1f);
            }
        });
        btnFontPlus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                adjustFontSize(1f);
            }
        });
        btnSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleMenu(false);
                if (readerView != null) readerView.cancelFullRefresh();
                Intent intent = new Intent(ReaderActivity.this, ReadingSettingsActivity.class);
                startActivityForResult(intent, REQUEST_READING_SETTINGS);
            }
        });
        btnFullRefresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleMenu(false);
                if (readerView != null) {
                    readerView.performFullRefresh();
                }
            }
        });
        btnShowLog.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleMenu(false);
                showLogAsQr();
            }
        });
        btnBrightMinus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                adjustBrightness(-0.02f);
            }
        });
        btnBrightPlus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                adjustBrightness(0.02f);
            }
        });

        // 阅读器回调
        readerView.setOnPageChangeListener(new ReaderView.OnPageChangeListener() {
            @Override
            public void onPageChanged(int pageIndex, int totalPages) {
                saveProgress();
                updateStatusBar();
            }
            @Override
            public void onChapterChanged(int chapterIndex) {
                switchChapter(chapterIndex);
            }
            @Override
            public void onTapCenter() {
                toggleMenu(!menuVisible);
            }
            @Override
            public void onSwipeDown() {
                toggleMenu(true);
            }
        });

        // 获取传递的书籍信息并加载
        loadBookData();
    }

    /**
     * 显示错误信息弹窗
     */
    private void showError(String title, String message) {
        appendLog("ERROR", title + " | " + message);
        try {
            new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("返回", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        finish();
                    }
                })
                .setCancelable(false)
                .show();
        } catch (Exception e) {
            finish();
        }
    }

    /**
     * 隐藏全屏加载覆盖层
     */
    private void hideLoadingOverlay() {
        if (loadingOverlay != null) {
            loadingOverlay.setVisibility(View.GONE);
        }
    }

    private String exceptionToString(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    /**
     * 加载书籍数据（后台解析章节）
     */
    private void loadBookData() {
        String bookPath = getIntent().getStringExtra("file_path");
        if (bookPath == null) {
            bookPath = getIntent().getStringExtra("book_path");
        }

        if (bookPath == null || bookPath.isEmpty()) {
            showError("错误",
                "书籍路径为空\n\nfile_path=" + getIntent().getStringExtra("file_path")
                + "\nbook_path=" + getIntent().getStringExtra("book_path"));
            return;
        }

        this.filePath = bookPath;
        final String filePath = bookPath;
        final File bookFile = new File(filePath);

        if (!bookFile.exists()) {
            showError("错误", "文件不存在:\n" + filePath);
            return;
        }

        if (!bookFile.canRead()) {
            showError("错误", "文件不可读:\n" + filePath + "\n大小: " + bookFile.length());
            return;
        }

        // 显示全屏加载覆盖层
        if (loadingOverlay != null) {
            TextView loadingFile = (TextView) findViewById(R.id.loading_filename);
            if (loadingFile != null) loadingFile.setText(bookFile.getName());
            loadingOverlay.setVisibility(View.VISIBLE);
        }

        // 后台线程解析章节
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final List<Chapter> parsedChapters;
                    String nameLower = filePath.toLowerCase();

                    if (nameLower.endsWith(".epub")) {
                        EpubParser.EpubResult result = EpubParser.parse(bookFile);
                        parsedChapters = (result != null) ? result.chapters : null;
                    } else if (nameLower.endsWith(".txt")) {
                        TxtParser.ParseResult result = TxtParser.parse(bookFile);
                        parsedChapters = (result != null) ? result.chapters : null;
                    } else {
                        uiHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                hideLoadingOverlay();
                                showError("错误", "不支持的文件格式:\n" + filePath);
                            }
                        });
                        return;
                    }

                    uiHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            hideLoadingOverlay();

                            if (parsedChapters == null || parsedChapters.isEmpty()) {
                                showError("解析失败",
                                    "解析返回空\n文件: " + filePath);
                                return;
                            }

                            chapters = parsedChapters;
                            initBook(filePath);
                        }
                    });
                } catch (final Exception e) {
                    uiHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            hideLoadingOverlay();
                            showError("加载异常",
                                "文件: " + filePath
                                + "\n异常: " + e.getClass().getSimpleName()
                                + "\n信息: " + e.getMessage()
                                + "\n\n" + exceptionToString(e));
                        }
                    });
                }
            }
        }).start();
    }

    /**
     * 初始化阅读（UI线程）
     */
    private void initBook(String filePath) {
        currentChapterIndex = 0;

        // 恢复上次阅读位置
        int savedChapter = prefs.getInt("last_chapter_" + filePath, 0);
        int savedPage = prefs.getInt("last_page_" + filePath, 0);
        if (savedChapter >= 0 && savedChapter < chapters.size()) {
            currentChapterIndex = savedChapter;
        }

        readerView.setChapter(chapters.get(currentChapterIndex));

        // 恢复页码
        if (savedPage > 0) {
            readerView.goToPage(savedPage);
        }

        updateStatusBar();
    }

    /**
     * 切换章节
     */
    private void switchChapter(int delta) {
        if (chapters == null || chapters.isEmpty()) return;

        int newIndex = currentChapterIndex + delta;
        if (newIndex < 0 || newIndex >= chapters.size()) {
            Toast.makeText(this,
                delta < 0 ? "已经是第一章了" : "已经是最后一章了",
                Toast.LENGTH_SHORT).show();
            return;
        }

        currentChapterIndex = newIndex;
        readerView.setChapter(chapters.get(currentChapterIndex));
        updateStatusBar();
    }

    /**
     * 切换菜单显示/隐藏
     */
    private void toggleMenu(boolean show) {
        menuVisible = show;
        if (show) {
            updateStatusBar();
            topStatusBar.setVisibility(View.VISIBLE);
            bottomMenu.setVisibility(View.VISIBLE);
        } else {
            topStatusBar.setVisibility(View.GONE);
            bottomMenu.setVisibility(View.GONE);
        }
    }

    /**
     * 更新顶部状态栏
     */
    private void updateStatusBar() {
        if (statusTime != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
            statusTime.setText(sdf.format(new Date()));
        }
        if (statusChapter != null && chapters != null && currentChapterIndex < chapters.size()) {
            Chapter ch = chapters.get(currentChapterIndex);
            String title = ch.getTitle();
            if (title == null || title.isEmpty()) {
                title = "第" + (currentChapterIndex + 1) + "章";
            }
            statusChapter.setText(title);
        }
        if (statusBattery != null) {
            int level = getBatteryLevel();
            statusBattery.setText(level + "%");
        }
    }

    private int getBatteryLevel() {
        try {
            Intent batteryIntent = registerReceiver(null,
                new android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (batteryIntent != null) {
                int level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                if (level >= 0 && scale > 0) {
                    return (int) ((level / (float) scale) * 100);
                }
            }
        } catch (Exception e) {}
        return -1;
    }

    /**
     * 打开目录
     */
    private void openToc() {
        if (chapters == null || chapters.isEmpty()) return;
        // 取消可能正在进行的全刷，防止残留回调搞黑屏幕
        if (readerView != null) readerView.cancelFullRefresh();
        Intent intent = new Intent(this, TocActivity.class);
        ArrayList<String> titles = new ArrayList<>();
        for (Chapter ch : chapters) {
            String t = ch.getTitle();
            titles.add(t != null ? t : "无标题");
        }
        intent.putStringArrayListExtra("chapter_titles", titles);
        intent.putExtra("current_index", currentChapterIndex);
        startActivityForResult(intent, REQUEST_TOC);
    }

    /**
     * 调整字体大小
     */
    private void adjustFontSize(float delta) {
        float current = safeGetFloat(prefs, "text_size", 20f);
        current = Math.max(12f, Math.min(40f, current + delta));
        prefs.edit().putFloat("text_size", current).apply();
        if (readerView != null) {
            readerView.applySettings();
        }
        Toast.makeText(this, "字号: " + (int) current, Toast.LENGTH_SHORT).show();
    }

    /**
     * 调整屏幕亮度
     * @param delta 亮度增量，-0.02 减亮，+0.02 加亮
     */
    private void adjustBrightness(float delta) {
        float current = safeGetFloat(prefs, "screen_brightness", 0.5f);
        current = Math.max(0.01f, Math.min(1.0f, current + delta));
        prefs.edit().putFloat("screen_brightness", current).apply();
        applyBrightness(current);
        // 在按钮上直接显示亮度值，不用Toast（系统Toast在墨水屏上会一直闪）
        int percent = (int) (current * 100);
        if (btnBrightMinus != null) btnBrightMinus.setText("亮-\n" + percent + "%");
    }

    /** 安全读 float，兼容旧版 int/boolean 存储 */
    private static float safeGetFloat(SharedPreferences p, String key, float def) {
        try { return p.getFloat(key, def); } catch (ClassCastException e) {
            try { return p.getInt(key, (int) def); } catch (Exception e2) {
                try { return p.getBoolean(key, def > 0.5f) ? 1f : 0f; } catch (Exception e3) { return def; }
            }
        }
    }

    private void applyBrightness(float brightness) {
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness = brightness;
        getWindow().setAttributes(lp);
    }

    /**
     * 保存阅读进度
     */
    private void saveProgress() {
        if (chapters == null || filePath == null) return;
        prefs.edit()
            .putInt("last_chapter_" + filePath, currentChapterIndex)
            .putInt("last_page_" + filePath, readerView.getCurrentPage())
            .apply();
    }

    private String filePath;

    /**
     * 显示日志二维码
     */
    private void showLogAsQr() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== EInkReader Log ===\n");
        sb.append("时间: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date())).append("\n");
        sb.append("设备: ").append(android.os.Build.MODEL).append("\n");
        sb.append("系统: Android ").append(android.os.Build.VERSION.RELEASE).append("\n");
        sb.append("API: ").append(android.os.Build.VERSION.SDK_INT).append("\n\n");

        String log = getLog();
        if (log.isEmpty()) {
            sb.append("(暂无日志)\n");
        } else {
            sb.append(log);
        }

        Intent intent = new Intent(this, QrLogActivity.class);
        intent.putExtra("log_text", sb.toString());
        startActivity(intent);
    }

    // ==================== 生命周期 ====================

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_TOC && resultCode == RESULT_OK && data != null) {
            int chapterIndex = data.getIntExtra("chapter_index", -1);
            if (chapterIndex >= 0 && chapterIndex < chapters.size()) {
                currentChapterIndex = chapterIndex;
                readerView.setChapter(chapters.get(currentChapterIndex));
                updateStatusBar();
            }
        } else if (requestCode == REQUEST_READING_SETTINGS) {
            // 从设置页面返回，重新应用设置
            if (readerView != null) {
                readerView.applySettings();
            }
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // 读取自定义按键映射
        int customPrev = prefs.getInt(ReadingSettingsActivity.KEY_PREV_KEYCODE, KeyEvent.KEYCODE_PAGE_UP);
        int customNext = prefs.getInt(ReadingSettingsActivity.KEY_NEXT_KEYCODE, KeyEvent.KEYCODE_PAGE_DOWN);

        // 自定义上一页键
        if (keyCode == customPrev) {
            if (readerView != null && !readerView.prevPage()) {
                switchChapter(-1);  // 章节首页再按→上一章
            }
            return true;
        }
        // 自定义下一页键
        if (keyCode == customNext) {
            if (readerView != null && !readerView.nextPage()) {
                switchChapter(1);  // 章节末页再按→下一章
            }
            return true;
        }
        // 音量键翻页（兼容）
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            if (readerView != null && !readerView.prevPage()) {
                switchChapter(-1);
            }
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (readerView != null && !readerView.nextPage()) {
                switchChapter(1);
            }
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 从设置页面返回后刷新
        if (readerView != null) {
            readerView.applySettings();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveProgress();
        // 取消全刷回调，防止在其他页面时残留回调修改 DecorView
        if (readerView != null) readerView.cancelFullRefresh();
    }
}
