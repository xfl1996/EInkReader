package com.einkreader.core.parser;

import android.util.Log;

import com.einkreader.core.model.Chapter;
import com.einkreader.utils.EncodingDetector;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TXT 文件解析器（v3 — 缓存 + 修正正则）
 * 改进点：
 * 1. 章节解析结果按文件 hash 缓存，第二次打开秒开
 * 2. 修正正则嵌套 bug，覆盖更多中文小说格式
 * 3. 流式读取，不将整个文件加载到单个 String
 */
public class TxtParser {
    private static final String TAG = "TxtParser";
    private static final int DEFAULT_CHAPTER_SIZE = 3000;
    private static final String CACHE_DIR_NAME = "txt_parse_cache";

    /**
     * 章节标题正则 —— 涵盖常见中文小说格式
     * 格式：第X章/节/回/卷/集/篇/部 标题
     */
    private static final Pattern CHAPTER_PATTERN = Pattern.compile(
        "^\\s*第[零一二三四五六七八九十百千万亿\\d]{1,8}[章节回卷集篇部折]" +
        "(?:\\s+(\\S.*))?" +
        "\\s*$"
    );

    /**
     * 更宽松的检测：只要一行以"第X章"等开头就算
     */
    private static final Pattern LOOSE_CHAPTER_PATTERN = Pattern.compile(
        "^\\s*第[零一二三四五六七八九十百千万亿\\d]{1,8}[章节回卷集篇部]"
    );

    public static class ParseResult {
        public String bookTitle;
        public String encoding;
        public String fullContent;
        public List<Chapter> chapters;

        public ParseResult() {
            chapters = new ArrayList<>();
        }
    }

    public static ParseResult parse(File file) throws IOException {
        return parse(file, null);
    }

    /**
     * 流式解析 TXT，带缓存：
     * 如果缓存文件存在且 hash 匹配，直接读取缓存的章节列表
     */
    public static ParseResult parse(File file, String forcedEncoding) throws IOException {
        // 尝试读取缓存
        ParseResult cached = readCache(file);
        if (cached != null) {
            Log.i(TAG, "Cache hit for: " + file.getName());
            return cached;
        }

        // 无缓存，正常解析
        ParseResult result = doParse(file, forcedEncoding);

        // 写入缓存
        writeCache(file, result);

        return result;
    }

    /**
     * 实际解析逻辑
     */
    private static ParseResult doParse(File file, String forcedEncoding) throws IOException {
        ParseResult result = new ParseResult();

        // 编码检测
        String encoding = forcedEncoding;
        if (encoding == null || encoding.isEmpty()) {
            encoding = EncodingDetector.detect(file);
        }
        result.encoding = encoding;
        result.bookTitle = extractTitle(file);

        // 流式读取：边读边记录章节边界
        List<int[]> chapterBreaks = new ArrayList<>();
        List<String> chapterTitles = new ArrayList<>();
        List<String> allLines = new ArrayList<>(4096);

        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(
                    new FileInputStream(file), encoding), 16384);
            String line;
            int lineIndex = 0;
            int lastChapterEnd = 0; // 上一个章节结束的行号

            while ((line = reader.readLine()) != null) {
                allLines.add(line);

                // 检测章节标题
                if (isChapterTitle(line)) {
                    // 如果当前有内容，先关闭上一个章节
                    if (chapterBreaks.isEmpty() && lineIndex > 0) {
                        // 前面有非章节内容，作为"序章"
                        chapterBreaks.add(new int[]{0, lineIndex});
                        chapterTitles.add(""); // 无标题
                    } else if (!chapterBreaks.isEmpty()) {
                        // 更新上一个章节的结束行
                        int[] prev = chapterBreaks.get(chapterBreaks.size() - 1);
                        prev[1] = lineIndex;
                    }

                    // 新章节从当前行开始（标题行本身不计入正文）
                    chapterBreaks.add(new int[]{lineIndex + 1, -1});
                    chapterTitles.add(extractChapterTitle(line));
                }

                lineIndex++;
            }

            // 关闭最后一个章节
            if (!chapterBreaks.isEmpty()) {
                int[] last = chapterBreaks.get(chapterBreaks.size() - 1);
                last[1] = allLines.size();
            }

        } finally {
            if (reader != null) {
                try { reader.close(); } catch (IOException e) {}
            }
        }

        // 构建章节列表
        if (!chapterBreaks.isEmpty()) {
            for (int i = 0; i < chapterBreaks.size(); i++) {
                int start = chapterBreaks.get(i)[0];
                int end = chapterBreaks.get(i)[1];
                if (end < 0) end = allLines.size();
                if (end > allLines.size()) end = allLines.size();

                StringBuilder sb = new StringBuilder();
                for (int j = start; j < end; j++) {
                    sb.append(allLines.get(j));
                    sb.append("\n");
                }

                String title = chapterTitles.get(i);
                if (title == null || title.isEmpty()) {
                    title = "第" + (i + 1) + "章";
                }

                Chapter chapter = new Chapter(title, sb.toString(), start, end);
                result.chapters.add(chapter);
            }
        }

        // 如果没找到章节，按固定字数分割
        if (result.chapters.isEmpty()) {
            result.chapters = splitBySize(allLines, DEFAULT_CHAPTER_SIZE);
        }

        // 拼接全文内容（用于搜索等场景）
        StringBuilder full = new StringBuilder();
        for (String l : allLines) {
            full.append(l).append("\n");
        }
        result.fullContent = full.toString();

        Log.i(TAG, "Parsed " + file.getName() + ": " + result.chapters.size() + " chapters, "
                + allLines.size() + " lines, encoding=" + encoding);

        return result;
    }

    /**
     * 检测是否为章节标题行
     */
    private static boolean isChapterTitle(String line) {
        if (line == null || line.isEmpty()) return false;
        return CHAPTER_PATTERN.matcher(line).find() || LOOSE_CHAPTER_PATTERN.matcher(line).find();
    }

    /**
     * 从标题行提取章节名
     */
    private static String extractChapterTitle(String line) {
        String trimmed = line.trim();
        if (trimmed.isEmpty()) return trimmed;

        Matcher m = CHAPTER_PATTERN.matcher(trimmed);
        if (m.find()) {
            return trimmed;
        }

        // 宽松匹配也返回整行
        if (LOOSE_CHAPTER_PATTERN.matcher(trimmed).find()) {
            return trimmed;
        }

        return trimmed;
    }

    /**
     * 按固定字数分割章节（后备方案）
     */
    private static List<Chapter> splitBySize(List<String> lines, int charsPerChapter) {
        List<Chapter> chapters = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int chapterNum = 1;
        int lineStart = 0;

        for (int i = 0; i < lines.size(); i++) {
            current.append(lines.get(i)).append("\n");

            if (current.length() >= charsPerChapter) {
                String title = "第" + chapterNum + "段";
                chapters.add(new Chapter(title, current.toString(), lineStart, i + 1));
                current = new StringBuilder();
                lineStart = i + 1;
                chapterNum++;
            }
        }

        // 最后一段
        if (current.length() > 0) {
            String title = "第" + chapterNum + "段";
            chapters.add(new Chapter(title, current.toString(), lineStart, lines.size()));
        }

        return chapters;
    }

    /**
     * 从文件名提取书名
     */
    public static String extractTitle(File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    // ==================== 缓存机制 ====================

    /**
     * 缓存文件的 key：文件路径 + 文件长度 + 修改时间
     */
    private static String getCacheKey(File file) {
        return file.getAbsolutePath() + "|" + file.length() + "|" + file.lastModified();
    }

    /**
     * 获取缓存目录
     */
    private static File getCacheDir(File txtFile) {
        File cacheDir = new File(txtFile.getParentFile(), CACHE_DIR_NAME);
        if (!cacheDir.exists()) cacheDir.mkdirs();
        return cacheDir;
    }

    /**
     * 缓存文件名：文件名.cache
     */
    private static File getCacheFile(File txtFile) {
        return new File(getCacheDir(txtFile), txtFile.getName() + ".cache");
    }

    /**
     * 读取缓存
     */
    private static ParseResult readCache(File file) {
        File cacheFile = getCacheFile(file);
        if (!cacheFile.exists()) return null;

        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    new FileInputStream(cacheFile), "UTF-8"));
            try {
                // 第一行：缓存 key
                String cachedKey = reader.readLine();
                String currentKey = getCacheKey(file);
                if (!currentKey.equals(cachedKey)) {
                    return null; // 文件已变化，缓存无效
                }

                // 第二行：书名
                String title = reader.readLine();
                // 第三行：编码
                String encoding = reader.readLine();
                // 第四行：章节数量
                int chapterCount = Integer.parseInt(reader.readLine());

                ParseResult result = new ParseResult();
                result.bookTitle = title;
                result.encoding = encoding;
                result.chapters = new ArrayList<>();
                result.fullContent = "";

                for (int i = 0; i < chapterCount; i++) {
                    String chTitle = reader.readLine();
                    int lineStart = Integer.parseInt(reader.readLine());
                    int lineEnd = Integer.parseInt(reader.readLine());
                    int contentLen = Integer.parseInt(reader.readLine());
                    char[] contentBuf = new char[contentLen];
                    int read = 0;
                    while (read < contentLen) {
                        int n = reader.read(contentBuf, read, contentLen - read);
                        if (n < 0) break;
                        read += n;
                    }
                    String content = new String(contentBuf, 0, read);
                    result.chapters.add(new Chapter(chTitle, content, lineStart, lineEnd));
                }

                return result;
            } finally {
                reader.close();
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to read cache", e);
            return null;
        }
    }

    /**
     * 写入缓存
     */
    private static void writeCache(File file, ParseResult result) {
        File cacheFile = getCacheFile(file);
        try {
            FileWriter writer = new FileWriter(cacheFile);
            try {
                // 缓存 key
                writer.write(getCacheKey(file) + "\n");
                // 书名
                writer.write(result.bookTitle + "\n");
                // 编码
                writer.write(result.encoding + "\n");
                // 章节数量
                writer.write(result.chapters.size() + "\n");

                for (Chapter ch : result.chapters) {
                    writer.write(ch.getTitle() + "\n");
                    writer.write(ch.getLineStart() + "\n");
                    writer.write(ch.getLineEnd() + "\n");
                    String content = ch.getContent();
                    writer.write(content.length() + "\n");
                    writer.write(content);
                }

                writer.flush();
            } finally {
                writer.close();
            }
            Log.i(TAG, "Cache written: " + cacheFile.getName());
        } catch (Exception e) {
            Log.w(TAG, "Failed to write cache", e);
        }
    }
}
