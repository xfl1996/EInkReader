package com.einkreader;

import android.app.Application;

import com.einkreader.core.parser.EpubParser;

/**
 * 自定义 Application：初始化全局崩溃捕获和 EPUB 缓存目录
 */
public class EInkReaderApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        CrashHandler.getInstance().init(this);

        // 初始化 EPUB 解析缓存目录（使用应用内部缓存，保证可写）
        try {
            EpubParser.initCacheDir(getCacheDir());
        } catch (Exception e) {
            // 缓存目录初始化失败不影响主功能
        }
    }
}
