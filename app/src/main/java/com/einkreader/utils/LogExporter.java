package com.einkreader.utils;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 日志导出工具
 * 读取 logcat 输出并保存到文件，方便用户排查问题
 */
public class LogExporter {
    private static final String TAG = "LogExporter";

    /**
     * 导出日志到文件
     *
     * @param context   上下文
     * @param filterTag 只导出包含此 tag 的日志（null 则导出所有 EInkReader 相关日志）
     * @return 导出的文件，失败返回 null
     */
    public static File exportLogs(Context context, String filterTag) {
        try {
            // 输出目录
            File logDir = new File(context.getExternalFilesDir(null), "logs");
            if (!logDir.exists()) logDir.mkdirs();

            // 文件名带时间戳
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            String fileName = "einkreader_log_" + timestamp + ".txt";
            File logFile = new File(logDir, fileName);

            // 构建 logcat 命令
            String tag = (filterTag != null && !filterTag.isEmpty()) ? filterTag : "EInkReader";
            // 收集最近 2000 行日志
            String[] cmd = {"logcat", "-d", "-v", "time", "-s",
                    tag + ":*",
                    "TxtParser:*",
                    "EpubParser:*",
                    "TxtToEpubConverter:*",
                    "EinkRefreshManager:*",
                    "BookListAdapter:*",
                    "AndroidRuntime:*"};

            Process process = Runtime.getRuntime().exec(cmd);
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()), 8192);

            StringBuilder sb = new StringBuilder();
            sb.append("=== EInkReader 日志导出 ===\n");
            sb.append("导出时间: ").append(timestamp).append("\n");
            sb.append("设备: ").append(android.os.Build.MODEL).append("\n");
            sb.append("Android: ").append(android.os.Build.VERSION.RELEASE).append("\n");
            sb.append("API: ").append(android.os.Build.VERSION.SDK_INT).append("\n");
            sb.append("============================\n\n");

            String line;
            int lineCount = 0;
            while ((line = reader.readLine()) != null && lineCount < 2000) {
                sb.append(line).append("\n");
                lineCount++;
            }
            reader.close();

            sb.append("\n=== 共 ").append(lineCount).append(" 行日志 ===\n");

            // 写入文件
            FileOutputStream fos = new FileOutputStream(logFile);
            fos.write(sb.toString().getBytes("UTF-8"));
            fos.close();

            Log.i(TAG, "Log exported: " + logFile.getPath() + " (" + lineCount + " lines)");
            return logFile;

        } catch (Exception e) {
            Log.e(TAG, "Export logs failed", e);
            return null;
        }
    }

    /**
     * 获取日志文件目录
     */
    public static File getLogDir(Context context) {
        File logDir = new File(context.getExternalFilesDir(null), "logs");
        if (!logDir.exists()) logDir.mkdirs();
        return logDir;
    }

    /**
     * 清理 7 天前的日志文件
     */
    public static int cleanOldLogs(Context context) {
        File logDir = getLogDir(context);
        File[] files = logDir.listFiles();
        if (files == null) return 0;

        long cutoff = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L;
        int deleted = 0;
        for (File f : files) {
            if (f.getName().startsWith("einkreader_log_") && f.lastModified() < cutoff) {
                if (f.delete()) deleted++;
            }
        }
        return deleted;
    }
}
