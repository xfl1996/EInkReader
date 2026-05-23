package com.einkreader.ui.library;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.einkreader.R;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 内置文件浏览器（v2 — 修复兼容性）
 *
 * 修复：
 * - 高版本安卓：正确处理 MANAGE_EXTERNAL_STORAGE 权限后的目录访问
 * - 低版本安卓：移除所有高版本 API 调用，避免 4.4 闪退
 * - 主页按钮：使用可靠的存储根路径列表探测
 * - 标题栏：修复多余文件夹名显示
 */
public class FilePickerActivity extends Activity {

    public static final String EXTRA_SELECTED_PATH = "selected_path";
    public static final String EXTRA_START_DIR = "start_dir";

    private static final String PREFS_NAME = "file_picker_prefs";
    private static final String KEY_LAST_DIR = "last_dir";

    private ListView fileList;
    private TextView tvPathDisplay;
    private TextView tvEmptyDir;

    private File currentDir;
    private List<File> items = new ArrayList<File>();
    private List<File> displayFiles = new ArrayList<File>();
    private ArrayAdapter<String> adapter;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_file_picker);

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        fileList = (ListView) findViewById(R.id.file_list);
        tvPathDisplay = (TextView) findViewById(R.id.tv_path_display);
        tvEmptyDir = (TextView) findViewById(R.id.tv_empty_dir);

        // 返回按钮
        findViewById(R.id.btn_file_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { goUp(); }
        });

        // 回主页按钮 — 直接关闭文件浏览器，回到书库首页
        findViewById(R.id.btn_file_home).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setResult(RESULT_CANCELED);
                finish();
            }
        });

        // 文件列表点击
        fileList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position < 0 || position >= displayFiles.size()) return;
                File file = displayFiles.get(position);
                if (file.isDirectory()) {
                    enterDirectory(file);
                } else {
                    selectFile(file);
                }
            }
        });

        // 确定起始目录
        String startDir = getIntent().getStringExtra(EXTRA_START_DIR);
        if (startDir != null && new File(startDir).canRead()) {
            currentDir = new File(startDir);
        } else {
            // 尝试恢复上次目录
            String lastDir = prefs.getString(KEY_LAST_DIR, null);
            if (lastDir != null && new File(lastDir).canRead()) {
                currentDir = new File(lastDir);
            } else {
                currentDir = findAccessibleRoot();
            }
        }

        if (currentDir != null) {
            enterDirectory(currentDir);
        } else {
            Toast.makeText(this, "无法访问存储目录", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    /**
     * 查找一个可读的存储根目录
     * 按优先级尝试多个候选路径，返回第一个可读且有内容的
     */
    private File findAccessibleRoot() {
        // 候选路径，从最通用到最具体
        String[] candidates;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+：需要 MANAGE_EXTERNAL_STORAGE 权限
            // 用 getExternalFilesDir 的上级逐级往上找
            File extDir = getExternalFilesDir(null);
            if (extDir != null) {
                File f = extDir;
                while (f != null && f.getParentFile() != null) {
                    File parent = f.getParentFile();
                    if (isReadableDirectory(parent)) {
                        return parent;
                    }
                    f = parent;
                }
            }
            // 回退：硬编码路径
            candidates = new String[]{
                "/storage/emulated/0",
                "/sdcard",
                "/storage/sdcard0",
            };
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Android 6-10：用 READ_EXTERNAL_STORAGE 权限
            candidates = new String[]{
                Environment.getExternalStorageDirectory().getAbsolutePath(),
                "/storage/emulated/0",
                "/sdcard",
                "/storage/sdcard0",
                "/mnt/sdcard",
            };
        } else {
            // Android 4.x：通常不需要运行时权限，直接试
            candidates = new String[]{
                Environment.getExternalStorageDirectory().getAbsolutePath(),
                "/storage/emulated/0",
                "/sdcard",
                "/mnt/sdcard",
                "/storage/sdcard0",
                "/storage/sdcard1",
            };
        }

        for (String path : candidates) {
            File dir = new File(path);
            if (isReadableDirectory(dir)) {
                return dir;
            }
        }

        return null;
    }

    /**
     * 判断目录是否可读且有内容
     */
    private boolean isReadableDirectory(File dir) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) return false;
        if (!dir.canRead()) return false;
        // 尝试列出文件
        File[] files = dir.listFiles();
        return files != null && files.length > 0;
    }

    /**
     * 进入指定目录
     */
    private void enterDirectory(File dir) {
        if (dir == null || !dir.canRead()) {
            Toast.makeText(this, "无法读取此目录", Toast.LENGTH_SHORT).show();
            return;
        }

        currentDir = dir;
        tvPathDisplay.setText(dir.getAbsolutePath());
        prefs.edit().putString(KEY_LAST_DIR, dir.getAbsolutePath()).apply();

        items.clear();
        displayFiles.clear();

        File[] files = dir.listFiles();
        if (files != null) {
            List<File> dirs = new ArrayList<File>();
            List<File> txtFiles = new ArrayList<File>();
            List<File> epubFiles = new ArrayList<File>();

            for (File f : files) {
                if (f.getName().startsWith(".")) continue;
                if (f.isDirectory()) {
                    dirs.add(f);
                } else {
                    String name = f.getName().toLowerCase();
                    if (name.endsWith(".txt")) txtFiles.add(f);
                    else if (name.endsWith(".epub")) epubFiles.add(f);
                }
            }

            Comparator<File> nameSort = new Comparator<File>() {
                @Override
                public int compare(File a, File b) {
                    return a.getName().compareToIgnoreCase(b.getName());
                }
            };
            Collections.sort(dirs, nameSort);
            Collections.sort(txtFiles, nameSort);
            Collections.sort(epubFiles, nameSort);

            items.addAll(dirs);
            items.addAll(txtFiles);
            items.addAll(epubFiles);
        }

        // 上级目录 ".."
        File parent = dir.getParentFile();
        if (parent != null && !parent.equals(dir)) {
            displayFiles.add(parent);
        }
        displayFiles.addAll(items);

        // 构建显示文本
        List<String> displayNames = new ArrayList<String>();
        for (File f : displayFiles) {
            if (f.equals(parent) && !f.equals(dir)) {
                displayNames.add("📁 .. (上级目录)");
            } else if (f.isDirectory()) {
                int count = f.listFiles() != null ? f.listFiles().length : 0;
                displayNames.add("📁 " + f.getName() + "  (" + count + ")");
            } else {
                String ext = f.getName().toLowerCase().endsWith(".epub") ? "📖" : "📄";
                displayNames.add(ext + " " + f.getName() + "  (" + formatSize(f.length()) + ")");
            }
        }

        adapter = new ArrayAdapter<String>(this,
                R.layout.item_file, R.id.file_name, displayNames);
        fileList.setAdapter(adapter);

        boolean empty = displayFiles.isEmpty();
        tvEmptyDir.setVisibility(empty ? View.VISIBLE : View.GONE);
        fileList.setVisibility(empty ? View.GONE : View.VISIBLE);

        // 滚动回顶部
        fileList.setSelection(0);
    }

    private void goUp() {
        if (currentDir != null && currentDir.getParentFile() != null
                && !currentDir.getParentFile().equals(currentDir)) {
            enterDirectory(currentDir.getParentFile());
        } else {
            finish();
        }
    }

    private void selectFile(File file) {
        Intent result = new Intent();
        result.putExtra(EXTRA_SELECTED_PATH, file.getAbsolutePath());
        setResult(RESULT_OK, result);
        finish();
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024));
    }

    @Override
    public void onBackPressed() { goUp(); }
}
