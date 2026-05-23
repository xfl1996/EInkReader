package com.einkreader;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import com.einkreader.ui.reader.QrLogActivity;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 全局 Application：捕获所有未处理异常
 * 闪退前自动保存错误信息并启动二维码页面
 */
public class CrashHandler implements Thread.UncaughtExceptionHandler {

    private static CrashHandler instance;
    private Thread.UncaughtExceptionHandler defaultHandler;
    private Context appContext;

    public static CrashHandler getInstance() {
        if (instance == null) {
            instance = new CrashHandler();
        }
        return instance;
    }

    public void init(Context context) {
        appContext = context.getApplicationContext();
        defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(this);
    }

    @Override
    public void uncaughtException(Thread thread, Throwable ex) {
        try {
            // 收集崩溃信息
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            String ts = sdf.format(new Date());

            StringWriter sw = new StringWriter();
            ex.printStackTrace(new PrintWriter(sw));
            String stackTrace = sw.toString();

            StringBuilder sb = new StringBuilder();
            sb.append("=== EInkReader 崩溃日志 ===\n");
            sb.append("时间: ").append(ts).append("\n");
            sb.append("设备: ").append(Build.MODEL).append("\n");
            sb.append("Android: ").append(Build.VERSION.RELEASE).append("\n");
            sb.append("SDK: ").append(Build.VERSION.SDK_INT).append("\n");
            sb.append("异常: ").append(ex.getClass().getName()).append("\n");
            sb.append("信息: ").append(ex.getMessage()).append("\n\n");
            sb.append("=== 堆栈 ===\n");
            sb.append(stackTrace);

            String logText = sb.toString();

            // 保存到 SharedPreferences，下次首页📋按钮能读到
            SharedPreferences crashPrefs = appContext.getSharedPreferences("crash_log", Context.MODE_PRIVATE);
            crashPrefs.edit().putString("last_crash", logText).apply();

            // 启动二维码页面（带 FLAG_ACTIVITY_NEW_TASK 和 CLEAR_TASK）
            Intent intent = new Intent(appContext, QrLogActivity.class);
            intent.putExtra("log_text", logText);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TASK
                    | Intent.FLAG_ACTIVITY_NO_ANIMATION);
            appContext.startActivity(intent);

            // 等一下让用户看到二维码，再杀进程
            Thread.sleep(8000);

        } catch (Throwable e) {
            // 如果连二维码都启动不了，算了（用 Throwable 而非 Exception，确保 Error 也能被捕获）
            e.printStackTrace();
        }

        // 调用默认处理器（杀进程）
        if (defaultHandler != null) {
            defaultHandler.uncaughtException(thread, ex);
        }
    }
}
