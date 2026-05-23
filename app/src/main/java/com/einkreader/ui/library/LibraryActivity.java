package com.einkreader.ui.library;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.einkreader.R;
import com.einkreader.core.model.Book;
import com.einkreader.core.parser.EpubParser;
import com.einkreader.core.parser.TxtParser;
import com.einkreader.core.parser.TxtToEpubConverter;
import com.einkreader.ui.reader.ReaderActivity;
import com.einkreader.utils.EncodingDetector;
import com.einkreader.utils.LogExporter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 书库主界面 Activity
 * 显示书籍列表，支持导入 TXT/EPUB、打开阅读、删除书籍
 */
public class LibraryActivity extends Activity {

    private static final int REQUEST_PICK_FILE = 1001;
    private static final int REQUEST_FILE_PICKER = 1002;
    private static final int REQUEST_SYSTEM_PICKER = 1003;
    private static final String PREFS_NAME = "eink_reader_prefs";
    private static final String KEY_LAST_BOOK = "last_book";

    private ListView bookListView;
    private LinearLayout emptyView;
    private View btnImport;
    private BookListAdapter adapter;
    private List<Book> bookList;
    private File booksDir;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 全屏
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.activity_library);

        // 书籍存储目录
        booksDir = new File(getExternalFilesDir(null), "books");
        if (!booksDir.exists()) booksDir.mkdirs();

        // 初始化视图
        bookListView = (ListView) findViewById(R.id.book_list);
        emptyView = (LinearLayout) findViewById(R.id.empty_view);
        btnImport = findViewById(R.id.btn_import);

        // 初始化列表
        bookList = new ArrayList<>();
        adapter = new BookListAdapter(this);
        adapter.setBooks(bookList);
        bookListView.setAdapter(adapter);

        // 点击打开书籍
        adapter.setOnBookClickListener(new BookListAdapter.OnBookClickListener() {
            @Override
            public void onBookClick(Book book, int position) {
                openBook(book);
            }

            @Override
            public void onBookLongClick(Book book, int position) {
                showDeleteDialog(book, position);
            }
        });

        // 导入按钮 — 弹出选择对话框
        btnImport.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showImportMethodDialog();
            }
        });

        // 日志按钮 — 显示二维码
        View btnLog = findViewById(R.id.btn_log);
        btnLog.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 先收集设备信息 + 最近崩溃日志
                StringBuilder sb = new StringBuilder();
                sb.append("=== 设备信息 ===\n");
                sb.append("型号: ").append(android.os.Build.MODEL).append("\n");
                sb.append("Android: ").append(android.os.Build.VERSION.RELEASE).append("\n");
                sb.append("SDK: ").append(android.os.Build.VERSION.SDK_INT).append("\n\n");

                // 读取最近的崩溃日志（SharedPreferences）
                try {
                    android.content.SharedPreferences crashPrefs = getSharedPreferences("crash_log", MODE_PRIVATE);
                    String lastCrash = crashPrefs.getString("last_crash", null);
                    if (lastCrash != null && !lastCrash.isEmpty()) {
                        sb.append("=== 最近崩溃 ===\n");
                        sb.append(lastCrash);
                    } else {
                        sb.append("(无崩溃记录)\n");
                    }
                } catch (Exception e) {
                    sb.append("读取崩溃日志失败: ").append(e.getMessage()).append("\n");
                }

                // 启动二维码页面
                Intent intent = new Intent(LibraryActivity.this,
                        com.einkreader.ui.reader.QrLogActivity.class);
                intent.putExtra("log_text", sb.toString());
                startActivity(intent);
            }
        });

        // 刷新设置按钮
        View btnRefreshSettings = findViewById(R.id.btn_refresh_settings);
        btnRefreshSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(LibraryActivity.this, RefreshSettingsActivity.class);
                startActivity(intent);
            }
        });

        // 退出按钮
        View btnExit = findViewById(R.id.btn_exit);
        btnExit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new AlertDialog.Builder(LibraryActivity.this)
                    .setTitle("退出")
                    .setMessage("确定退出 EInkReader？")
                    .setPositiveButton("退出", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            finish();
                        }
                    })
                    .setNegativeButton("取消", null)
                    .show();
            }
        });

        // 加载已有书籍
        loadBookList();

        // 请求存储权限（高版本安卓需要 ALL_FILES_ACCESS）
        requestStoragePermission();
    }

    private static final int REQUEST_MANAGE_STORAGE = 2001;

    private void requestStoragePermission() {
        // Android 4.4 (API 19) 不需要运行时权限，Manifest 里声明即可
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return; // Android 4.x 直接跳过
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ 需要 MANAGE_EXTERNAL_STORAGE
            try {
                if (!Environment.isExternalStorageManager()) {
                    new AlertDialog.Builder(this)
                        .setTitle("需要存储权限")
                        .setMessage("EInkReader 需要访问所有文件的权限，以便浏览和导入电子书。\n\n请在接下来的设置页面中开启「允许管理所有文件」。")
                        .setPositiveButton("去开启", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                try {
                                    Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                                    intent.setData(Uri.parse("package:" + getPackageName()));
                                    startActivityForResult(intent, REQUEST_MANAGE_STORAGE);
                                } catch (Exception e) {
                                    try {
                                        Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                                        startActivityForResult(intent, REQUEST_MANAGE_STORAGE);
                                    } catch (Exception e2) {
                                        Toast.makeText(LibraryActivity.this,
                                            "无法打开权限设置，请手动在系统设置中授权", Toast.LENGTH_LONG).show();
                                    }
                                }
                            }
                        })
                        .setNegativeButton("跳过", null)
                        .show();
                }
            } catch (Exception e) {
                // 某些 ROM 的 isExternalStorageManager 也可能崩
                // 静默忽略，让后续文件访问自行尝试
            }
        } else {
            // Android 6-10 用传统权限
            try {
                int hasRead = checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE);
                int hasWrite = checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE);
                if (hasRead != android.content.pm.PackageManager.PERMISSION_GRANTED
                        || hasWrite != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{
                        android.Manifest.permission.READ_EXTERNAL_STORAGE,
                        android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                    }, REQUEST_MANAGE_STORAGE);
                }
            } catch (Exception e) {
                // 静默忽略
            }
        }
    }

    /**
     * 弹出导入方式选择对话框
     */
    private void showImportMethodDialog() {
        String[] methods;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            methods = new String[]{
                "📁 内置文件浏览器",
                "📂 系统文件管理器"
            };
        } else {
            // Android 4.4 没有 SAF，只用内置
            methods = new String[]{
                "📁 内置文件浏览器"
            };
        }

        new AlertDialog.Builder(this)
            .setTitle("选择导入方式")
            .setItems(methods, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    if (which == 0) {
                        pickFile();
                    } else if (which == 1) {
                        pickFileWithSystem();
                    }
                }
            })
            .show();
    }

    /**
     * 使用系统文件管理器（Android 5.0+ SAF，需 READ_EXTERNAL_STORAGE）
     */
    private void pickFileWithSystem() {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            // 允许多 MIME 类型
            String[] mimeTypes = { "text/plain", "application/epub+zip" };
            intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
            startActivityForResult(intent, REQUEST_SYSTEM_PICKER);
        } catch (Exception e) {
            Toast.makeText(this, "无法打开系统文件管理器: " + e.getMessage(),
                Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 打开内置文件浏览器（替代系统文件选择器）
     */
    private void pickFile() {
        Intent intent = new Intent(this, FilePickerActivity.class);
        // 不传 start_dir，让 FilePickerActivity 自己探测可访问的存储根目录
        startActivityForResult(intent, REQUEST_FILE_PICKER);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;

        if (requestCode == REQUEST_FILE_PICKER) {
            // 内置文件浏览器返回
            String path = data.getStringExtra(FilePickerActivity.EXTRA_SELECTED_PATH);
            if (path != null) {
                handleFileImport(path);
            }
        } else if (requestCode == REQUEST_SYSTEM_PICKER) {
            // 系统文件管理器返回（SAF Uri）
            Uri uri = data.getData();
            if (uri != null) {
                handleUriImport(uri);
            }
        } else if (requestCode == REQUEST_PICK_FILE) {
            // 旧系统文件选择器兼容
            Uri uri = data.getData();
            if (uri != null) {
                handleUriImport(uri);
            }
        }
    }

    /**
     * 处理文件导入（来自内置文件浏览器的路径）
     */
    private void handleFileImport(String path) {
        File file = new File(path);
        if (!file.exists() || !file.canRead()) {
            Toast.makeText(this, "文件无法读取: " + path, Toast.LENGTH_SHORT).show();
            return;
        }

        String name = file.getName().toLowerCase();
        if (name.endsWith(".txt")) {
            // TXT：提示是否转换为 EPUB
            showConvertDialog(file, file.getName());
        } else if (name.endsWith(".epub")) {
            // EPUB：直接复制到书库
            File destFile = new File(booksDir, file.getName());
            try {
                copyFile(file, destFile);
            } catch (Exception e) {
                Toast.makeText(this, "导入失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                return;
            }
            importBook(destFile, "epub");
        } else {
            Toast.makeText(this, "不支持的文件格式", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 处理文件导入（来自系统 Uri，兼容旧路径）
     */
    private void handleUriImport(Uri uri) {
        // 获取文件名
        String fileName = getFileName(uri);
        if (fileName == null) fileName = "unknown.txt";

        final File destFile = new File(booksDir, fileName);

        // 大文件复制放到后台线程，避免主线程 ANR
        // 先给用户一个反馈
        Toast.makeText(this, "正在导入文件...", Toast.LENGTH_SHORT).show();

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    copyUriToFile(uri, destFile);
                } catch (Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(LibraryActivity.this, "导入失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
                    return;
                }

                final String lowerName = destFile.getName().toLowerCase();

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // TXT 文件：提示是否转换为 EPUB
                        if (lowerName.endsWith(".txt")) {
                            showConvertDialog(destFile, destFile.getName());
                        } else if (lowerName.endsWith(".epub")) {
                            importBook(destFile, "epub");
                        } else {
                            Toast.makeText(LibraryActivity.this, "不支持的文件格式", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }
        }).start();
    }

    /**
     * TXT 转 EPUB 提示对话框
     */
    private void showConvertDialog(final File txtFile, final String fileName) {
        new AlertDialog.Builder(this)
                .setTitle("导入 TXT 文件")
                .setMessage("是否将此文件转换为 EPUB 格式？\n\n转换后可获得更好的排版效果。")
                .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        convertAndImport(txtFile);
                    }
                })
                .setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        importBook(txtFile, "txt");
                    }
                })
                .setCancelable(true)
                .show();
    }

    /**
     * 转换 TXT 为 EPUB 并导入（后台线程 + 列表内嵌进度，不弹窗）
     */
    private void convertAndImport(final File txtFile) {
        // 先临时加入列表（轻量操作）
        final Book tempBook = new Book(txtFile.getName(), txtFile.getAbsolutePath(), "txt");
        bookList.add(0, tempBook);
        adapter.setBooks(bookList);
        updateEmptyView();
        // 立刻显示初始进度
        adapter.setProgress(txtFile.getAbsolutePath(), 0, "准备转换...");

        // 立刻启动后台线程，不等 UI 刷新完成
        final Thread convertThread = new Thread(new Runnable() {
            @Override
            public void run() {
                TxtToEpubConverter.ConvertResult result =
                        TxtToEpubConverter.convert(txtFile, booksDir, null,
                                new TxtToEpubConverter.ProgressListener() {
                            @Override
                            public void onProgress(final int progress, final String message) {
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        adapter.setProgress(txtFile.getAbsolutePath(), progress, message);
                                    }
                                });
                            }
                        });

                final boolean success = result.success;
                final File outFile = result.outputFile;
                final int chapters = result.chapterCount;
                final String error = result.error;

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        adapter.clearProgress(txtFile.getAbsolutePath());
                        // 移除临时条目
                        bookList.remove(tempBook);
                        if (success) {
                            importBook(outFile, "epub");
                            Toast.makeText(LibraryActivity.this,
                                "转换成功！共 " + chapters + " 章",
                                Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(LibraryActivity.this, "转换失败: " + error,
                                    Toast.LENGTH_SHORT).show();
                        }
                        adapter.setBooks(bookList);
                        updateEmptyView();
                    }
                });
            }
        });
        convertThread.start();
    }

    /**
     * 添加书籍到书库并刷新列表
     */
    private void importBook(File file, String format) {
        Book book = new Book(extractTitle(file.getName()), file.getPath(), format);
        book.setFileSize(file.length());

        // 检查是否已存在
        for (Book existing : bookList) {
            if (existing.getFilePath().equals(file.getPath())) {
                Toast.makeText(this, "该书已在书库中", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        bookList.add(0, book); // 新书置顶
        adapter.setBooks(bookList);
        updateEmptyView();
        Toast.makeText(this, "导入成功: " + book.getTitle(), Toast.LENGTH_SHORT).show();
    }

    /**
     * 打开书籍进入阅读器
     * 点击后立刻在列表显示"加载中"状态，然后跳转
     */
    private void openBook(final Book book) {
        final String title = book.getTitle();
        final String path = book.getFilePath();

        // 【立即反馈】在列表项上显示"加载中"
        adapter.setLoadingBook(path, true);

        // 后台验证文件可读，然后跳转
        new Thread(new Runnable() {
            @Override
            public void run() {
                final File file = new File(path);
                final boolean ready = file.exists() && file.canRead() && file.length() > 0;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        adapter.setLoadingBook(path, false);
                        if (ready) {
                            Intent intent = new Intent(LibraryActivity.this, ReaderActivity.class);
                            intent.putExtra("file_path", path);
                            startActivity(intent);
                        } else {
                            Toast.makeText(LibraryActivity.this,
                                "文件不存在或无法读取", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }
        }).start();
    }

    /**
     * 长按删除书籍
     */
    private void showDeleteDialog(final Book book, final int position) {
        new AlertDialog.Builder(this)
                .setTitle("删除书籍")
                .setMessage("确定删除「" + book.getTitle() + "」？")
                .setPositiveButton("删除", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // 删除文件
                        File file = new File(book.getFilePath());
                        if (file.exists()) file.delete();
                        // 从列表移除
                        bookList.remove(position);
                        adapter.setBooks(bookList);
                        updateEmptyView();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /**
     * 加载书库中的书籍列表
     */
    private void loadBookList() {
        bookList.clear();

        File[] files = booksDir.listFiles();
        if (files != null) {
            for (File file : files) {
                String name = file.getName().toLowerCase();
                if (name.endsWith(".txt") || name.endsWith(".epub")) {
                    String format = name.endsWith(".epub") ? "epub" : "txt";
                    Book book = new Book(extractTitle(file.getName()), file.getPath(), format);
                    book.setFileSize(file.length());
                    // 读取上次阅读时间
                    SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
                    if (file.getPath().equals(prefs.getString(KEY_LAST_BOOK, ""))) {
                        book.setLastReadTime(prefs.getLong("last_read_time", 0));
                    }
                    bookList.add(book);
                }
            }
        }

        // 按最近阅读时间排序
        java.util.Collections.sort(bookList, new java.util.Comparator<Book>() {
            @Override
            public int compare(Book a, Book b) {
                return Long.compare(b.getLastReadTime(), a.getLastReadTime());
            }
        });

        adapter.setBooks(bookList);
        updateEmptyView();
    }

    /**
     * 更新空状态视图
     */
    private void updateEmptyView() {
        if (bookList.isEmpty()) {
            bookListView.setVisibility(View.GONE);
            emptyView.setVisibility(View.VISIBLE);
        } else {
            bookListView.setVisibility(View.VISIBLE);
            emptyView.setVisibility(View.GONE);
        }
    }

    /**
     * 从 URI 获取文件名
     */
    private String getFileName(Uri uri) {
        String name = null;
        if ("content".equals(uri.getScheme())) {
            Cursor cursor = getContentResolver().query(uri, null, null, null, null);
            if (cursor != null) {
                try {
                    if (cursor.moveToFirst()) {
                        int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                        if (idx >= 0) name = cursor.getString(idx);
                    }
                } finally {
                    cursor.close();
                }
            }
        }
        if (name == null) {
            name = uri.getPath();
            if (name != null) {
                int slash = name.lastIndexOf('/');
                if (slash >= 0) name = name.substring(slash + 1);
            }
        }
        return name;
    }

    /**
     * 复制 URI 内容到文件
     */
    private void copyUriToFile(Uri uri, File dest) throws Exception {
        InputStream is = getContentResolver().openInputStream(uri);
        FileOutputStream fos = new FileOutputStream(dest);
        try {
            byte[] buf = new byte[8192];
            int len;
            while ((len = is.read(buf)) != -1) {
                fos.write(buf, 0, len);
            }
            fos.flush();
        } finally {
            try { is.close(); } catch (Exception e) {}
            try { fos.close(); } catch (Exception e) {}
        }
    }

    /**
     * 复制文件（File -> File）
     */
    private void copyFile(File src, File dest) throws Exception {
        java.io.FileInputStream fis = new java.io.FileInputStream(src);
        FileOutputStream fos = new FileOutputStream(dest);
        try {
            byte[] buf = new byte[8192];
            int len;
            while ((len = fis.read(buf)) != -1) {
                fos.write(buf, 0, len);
            }
            fos.flush();
        } finally {
            try { fis.close(); } catch (Exception e) {}
            try { fos.close(); } catch (Exception e) {}
        }
    }

    /**
     * 从文件名提取书名
     */
    private String extractTitle(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 从阅读器返回时刷新列表（更新最近阅读时间）
        loadBookList();
    }
}
