package com.einkreader.core.parser;

import android.os.Environment;
import android.util.Log;

import com.einkreader.core.model.Chapter;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * EPUB 文件解析器（带缓存）
 * 解析 EPUB 内部 XHTML 内容，提取目录结构和章节文本
 * EPUB 本质是 ZIP 包，包含 META-INF/container.xml、*.opf 清单、XHTML 内容文件
 */
public class EpubParser {
    private static final String TAG = "EpubParser";
    private static final String CACHE_DIR_NAME = "epub_parse_cache";

    /** 解析结果 */
    public static class EpubResult {
        public String title;
        public String author;
        public String encoding = "UTF-8";
        public List<Chapter> chapters;
        public List<String> spineOrder;

        public EpubResult() {
            chapters = new ArrayList<>();
            spineOrder = new ArrayList<>();
        }
    }

    /**
     * 解析 EPUB 文件（带缓存）
     */
    public static EpubResult parse(File file) throws IOException {
        // 尝试读取缓存
        EpubResult cached = readCache(file);
        if (cached != null) {
            Log.i(TAG, "Cache hit for: " + file.getName() + " (" + cached.chapters.size() + " chapters)");
            return cached;
        }

        // 无缓存，正常解析
        EpubResult result = doParse(file);

        // 写入缓存
        writeCache(file, result);

        return result;
    }

    /**
     * 实际解析逻辑
     */
    private static EpubResult doParse(File file) throws IOException {
        EpubResult result = new EpubResult();
        ZipFile zipFile = null;

        try {
            zipFile = new ZipFile(file);

            // 1. 读取 container.xml 找到 OPF 路径
            String opfPath = parseContainer(zipFile);
            if (opfPath == null) {
                Log.e(TAG, "Cannot find OPF path in container.xml");
                return result;
            }

            // 2. 解析 OPF 文件，获取元数据和 spine 顺序
            parseOpf(zipFile, opfPath, result);

            // 3. 按 spine 顺序读取 XHTML 内容
            String opfDir = opfPath.substring(0, opfPath.lastIndexOf('/') + 1);

            // 尝试解析 NCX 目录文件（获取章节标题映射）
            Map<String, String> ncxTitles = parseNcx(zipFile, opfDir, result);

            parseSpineContent(zipFile, opfDir, result, ncxTitles);

            // Fallback: 用文件名作标题
            if (result.title == null || result.title.isEmpty()) {
                result.title = file.getName();
                if (result.title.endsWith(".epub") || result.title.endsWith(".EPUB")) {
                    result.title = result.title.substring(0, result.title.length() - 5);
                }
            }

            Log.i(TAG, "Parsed EPUB: " + result.title + ", chapters=" + result.chapters.size());

        } finally {
            if (zipFile != null) {
                try { zipFile.close(); } catch (IOException e) {}
            }
        }

        return result;
    }

    // ==================== 缓存机制 ====================

    private static String getCacheKey(File file) {
        return file.getAbsolutePath() + "|" + file.length() + "|" + file.lastModified();
    }

    /** 外部存储缓存目录，运行时由 initCacheDir 设置 */
    private static File sCacheBaseDir = null;

    /**
     * 初始化缓存目录到外部存储
     * 使用 /sdcard/EInkReader/epub_cache/，重装应用后缓存不丢失
     */
    public static void initCacheDir(File appCacheDir) {
        // 优先使用外部存储（重装不丢失）
        try {
            // 注意：不要用 Environment.getExternalStorageState(File) — 那是 API 21+
            if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
                File extDir = Environment.getExternalStorageDirectory();
                if (extDir != null) {
                    sCacheBaseDir = new File(extDir, "EInkReader/epub_cache");
                    if (!sCacheBaseDir.exists()) sCacheBaseDir.mkdirs();
                    Log.i(TAG, "Cache dir: " + sCacheBaseDir.getAbsolutePath());
                    return;
                }
            }
        } catch (Throwable e) {
            Log.w(TAG, "External storage unavailable, falling back to internal");
        }
        // fallback：应用内部缓存
        sCacheBaseDir = new File(appCacheDir, CACHE_DIR_NAME);
        if (!sCacheBaseDir.exists()) sCacheBaseDir.mkdirs();
    }

    private static File getCacheDir(File epubFile) {
        if (sCacheBaseDir != null && sCacheBaseDir.exists()) {
            return sCacheBaseDir;
        }
        // fallback：尝试在文件同级目录创建
        File cacheDir = new File(epubFile.getParentFile(), CACHE_DIR_NAME);
        if (!cacheDir.exists()) cacheDir.mkdirs();
        return cacheDir;
    }

    private static File getCacheFile(File epubFile) {
        // 用文件名 + 长度做缓存文件名，避免特殊字符
        String cacheName = epubFile.getName() + "_" + epubFile.length() + ".cache";
        return new File(getCacheDir(epubFile), cacheName);
    }

    private static EpubResult readCache(File file) {
        File cacheFile = getCacheFile(file);
        if (!cacheFile.exists()) return null;

        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(
                    new FileInputStream(cacheFile), "UTF-8"));

            // 第一行：缓存 key
            String cachedKey = reader.readLine();
            String currentKey = getCacheKey(file);
            if (!currentKey.equals(cachedKey)) {
                return null; // 文件已变化
            }

            // 第二行：书名
            String title = reader.readLine();
            // 第三行：作者
            String author = reader.readLine();
            // 第四行：章节数量
            int chapterCount = Integer.parseInt(reader.readLine().trim());

            EpubResult result = new EpubResult();
            result.title = title;
            result.author = author;
            result.chapters = new ArrayList<>();

            for (int i = 0; i < chapterCount; i++) {
                String chTitle = reader.readLine();
                // 内容长度
                int contentLen = Integer.parseInt(reader.readLine().trim());
                // 读取内容
                char[] buf = new char[contentLen];
                int read = 0;
                while (read < contentLen) {
                    int n = reader.read(buf, read, contentLen - read);
                    if (n < 0) break;
                    read += n;
                }
                String content = new String(buf, 0, read);
                Chapter chapter = new Chapter(chTitle, content);
                chapter.setIndex(i);
                result.chapters.add(chapter);
            }

            return result;
        } catch (Exception e) {
            Log.e(TAG, "Error reading cache", e);
            return null; // 缓存损坏，忽略
        } finally {
            if (reader != null) {
                try { reader.close(); } catch (IOException e) {}
            }
        }
    }

    private static void writeCache(File file, EpubResult result) {
        if (result == null || result.chapters == null || result.chapters.isEmpty()) return;

        File cacheFile = getCacheFile(file);
        File tmpFile = new File(cacheFile.getAbsolutePath() + ".tmp");
        OutputStreamWriter writer = null;
        try {
            writer = new OutputStreamWriter(new FileOutputStream(tmpFile), "UTF-8");

            // 缓存 key
            writer.write(getCacheKey(file));
            writer.write('\n');
            // 书名
            writer.write(result.title != null ? result.title : "");
            writer.write('\n');
            // 作者
            writer.write(result.author != null ? result.author : "");
            writer.write('\n');
            // 章节数量
            writer.write(String.valueOf(result.chapters.size()));
            writer.write('\n');

            // 每个章节
            for (Chapter ch : result.chapters) {
                writer.write(ch.getTitle() != null ? ch.getTitle() : "");
                writer.write('\n');
                String content = ch.getContent() != null ? ch.getContent() : "";
                writer.write(String.valueOf(content.length()));
                writer.write('\n');
                writer.write(content);
            }

            writer.flush();
            writer.close();
            writer = null;
            // 原子重命名：先删旧文件，再重命名临时文件
            if (cacheFile.exists()) cacheFile.delete();
            if (tmpFile.renameTo(cacheFile)) {
                Log.i(TAG, "Cache written: " + cacheFile.getName());
            } else {
                Log.w(TAG, "Cache rename failed: " + tmpFile.getName());
                tmpFile.delete();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error writing cache", e);
            // 缓存写入失败，清理临时文件
            if (tmpFile.exists()) tmpFile.delete();
            if (cacheFile.exists()) cacheFile.delete();
        } finally {
            if (writer != null) {
                try { writer.close(); } catch (IOException e) {}
            }
        }
    }

    // ==================== 解析逻辑 ====================

    /**
     * 解析 META-INF/container.xml，找到 OPF 文件路径
     */
    private static String parseContainer(ZipFile zipFile) throws IOException {
        ZipEntry entry = zipFile.getEntry("META-INF/container.xml");
        if (entry == null) return null;

        InputStream is = null;
        try {
            is = zipFile.getInputStream(entry);
            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            XmlPullParser parser = factory.newPullParser();
            parser.setInput(is, "UTF-8");

            while (parser.getEventType() != XmlPullParser.END_DOCUMENT) {
                if (parser.getEventType() == XmlPullParser.START_TAG) {
                    String tag = parser.getName();
                    if ("rootfile".equalsIgnoreCase(tag)) {
                        String fullPath = parser.getAttributeValue(null, "full-path");
                        if (fullPath != null) return fullPath.trim();
                    }
                }
                parser.next();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing container.xml", e);
        } finally {
            if (is != null) try { is.close(); } catch (IOException e) {}
        }
        return null;
    }

    /**
     * 解析 OPF 文件，提取元数据和 spine 顺序
     */
    private static void parseOpf(ZipFile zipFile, String opfPath, EpubResult result) throws IOException {
        ZipEntry entry = zipFile.getEntry(opfPath);
        if (entry == null) return;

        Map<String, String> manifest = new HashMap<String, String>();
        List<String> spineIds = new ArrayList<String>();

        InputStream is = null;
        try {
            is = zipFile.getInputStream(entry);
            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            XmlPullParser parser = factory.newPullParser();
            parser.setInput(is, "UTF-8");

            String currentId = null;
            String currentHref = null;
            boolean inMetadata = false;
            boolean inManifest = false;
            boolean inSpine = false;

            while (parser.getEventType() != XmlPullParser.END_DOCUMENT) {
                switch (parser.getEventType()) {
                    case XmlPullParser.START_TAG:
                        String tag = parser.getName();
                        if ("metadata".equalsIgnoreCase(tag)) {
                            inMetadata = true;
                        } else if ("manifest".equalsIgnoreCase(tag)) {
                            inManifest = true;
                        } else if ("spine".equalsIgnoreCase(tag)) {
                            inSpine = true;
                        } else if (inMetadata) {
                            if ("title".equalsIgnoreCase(tag)) {
                                result.title = readText(parser);
                            } else if ("creator".equalsIgnoreCase(tag)) {
                                result.author = readText(parser);
                            }
                        } else if (inManifest && "item".equalsIgnoreCase(tag)) {
                            currentId = parser.getAttributeValue(null, "id");
                            currentHref = parser.getAttributeValue(null, "href");
                            if (currentId != null && currentHref != null) {
                                manifest.put(currentId, currentHref);
                            }
                        } else if (inSpine && "itemref".equalsIgnoreCase(tag)) {
                            String idref = parser.getAttributeValue(null, "idref");
                            if (idref != null) {
                                spineIds.add(idref);
                            }
                        }
                        break;

                    case XmlPullParser.END_TAG:
                        String endTag = parser.getName();
                        if ("metadata".equalsIgnoreCase(endTag)) {
                            inMetadata = false;
                        } else if ("manifest".equalsIgnoreCase(endTag)) {
                            inManifest = false;
                        } else if ("spine".equalsIgnoreCase(endTag)) {
                            inSpine = false;
                        }
                        break;
                }
                parser.next();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing OPF", e);
        } finally {
            if (is != null) try { is.close(); } catch (IOException e) {}
        }

        // 构建 spineOrder
        for (String id : spineIds) {
            String href = manifest.get(id);
            if (href != null) {
                result.spineOrder.add(href);
            }
        }
    }

    /**
     * 解析 NCX 目录文件，提取 href -> 标题映射
     */
    private static Map<String, String> parseNcx(ZipFile zipFile, String opfDir, EpubResult result) throws IOException {
        Map<String, String> ncxTitles = new HashMap<String, String>();

        // 先从 OPF 的 spine toc 属性找 NCX id，再从 manifest 找路径
        String ncxHref = null;
        ZipEntry opfEntry = zipFile.getEntry(opfDir + "content.opf");
        if (opfEntry == null) {
            // 尝试在所有 opf 文件中搜索
            java.util.Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry ze = entries.nextElement();
                String name = ze.getName();
                if (name.endsWith(".opf") && !name.contains("/META-INF/")) {
                    opfEntry = ze;
                    break;
                }
            }
        }

        // 从 OPF 中找 NCX
        if (opfEntry != null) {
            InputStream is = null;
            try {
                is = zipFile.getInputStream(opfEntry);
                XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
                XmlPullParser parser = factory.newPullParser();
                parser.setInput(is, "UTF-8");

                boolean inManifest = false;
                while (parser.getEventType() != XmlPullParser.END_DOCUMENT) {
                    if (parser.getEventType() == XmlPullParser.START_TAG) {
                        String tag = parser.getName();
                        if ("manifest".equalsIgnoreCase(tag)) {
                            inManifest = true;
                        } else if (inManifest && "item".equalsIgnoreCase(tag)) {
                            String mediaType = parser.getAttributeValue(null, "media-type");
                            String href = parser.getAttributeValue(null, "href");
                            if (mediaType != null && mediaType.contains("dtbncx") && href != null) {
                                ncxHref = href;
                                break;
                            }
                        }
                    } else if (parser.getEventType() == XmlPullParser.END_TAG) {
                        if ("manifest".equalsIgnoreCase(parser.getName())) {
                            inManifest = false;
                        }
                    }
                    parser.next();
                }
            } catch (Exception e) {
                Log.e(TAG, "Error finding NCX in OPF", e);
            } finally {
                if (is != null) try { is.close(); } catch (IOException e) {}
            }
        }

        if (ncxHref == null) {
            // Fallback: 尝试常见的 toc.ncx 路径
            String[] candidates = {opfDir + "toc.ncx", "toc.ncx", "OEBPS/toc.ncx", "Text/toc.ncx"};
            for (String path : candidates) {
                if (zipFile.getEntry(path) != null) {
                    ncxHref = path.substring(opfDir.length());
                    break;
                }
            }
        }

        if (ncxHref == null) return ncxTitles;

        String ncxPath = opfDir + ncxHref;
        ZipEntry ncxEntry = zipFile.getEntry(ncxPath);
        if (ncxEntry == null) {
            ncxEntry = zipFile.getEntry(ncxHref);
        }
        if (ncxEntry == null) return ncxTitles;

        InputStream is = null;
        try {
            is = zipFile.getInputStream(ncxEntry);
            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            XmlPullParser parser = factory.newPullParser();
            parser.setInput(is, "UTF-8");

            String currentSrc = null;
            String currentLabel = null;

            while (parser.getEventType() != XmlPullParser.END_DOCUMENT) {
                if (parser.getEventType() == XmlPullParser.START_TAG) {
                    String tag = parser.getName();
                    if ("content".equalsIgnoreCase(tag)) {
                        currentSrc = parser.getAttributeValue(null, "src");
                    } else if ("navlabel".equalsIgnoreCase(tag)) {
                        currentLabel = null;
                    } else if ("text".equalsIgnoreCase(tag)) {
                        currentLabel = readNcxText(parser);
                    }
                } else if (parser.getEventType() == XmlPullParser.END_TAG) {
                    String endTag = parser.getName();
                    if ("navpoint".equalsIgnoreCase(endTag) || "navreference".equalsIgnoreCase(endTag)) {
                        if (currentSrc != null && currentLabel != null && !currentLabel.isEmpty()) {
                            // 去掉 fragment (#xxx)
                            String href = currentSrc;
                            int hashIdx = href.indexOf('#');
                            if (hashIdx > 0) href = href.substring(0, hashIdx);
                            ncxTitles.put(href, currentLabel.trim());
                        }
                        currentSrc = null;
                        currentLabel = null;
                    }
                }
                parser.next();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing NCX", e);
        } finally {
            if (is != null) try { is.close(); } catch (IOException e) {}
        }

        return ncxTitles;
    }

    /**
     * 读取 NCX <navLabel> 下的 <text> 标签内容
     */
    private static String readNcxText(XmlPullParser parser) throws Exception {
        int depth = 0;
        while (parser.getEventType() != XmlPullParser.END_DOCUMENT) {
            int event = parser.getEventType();
            if (event == XmlPullParser.START_TAG) {
                String tag = parser.getName();
                if ("text".equalsIgnoreCase(tag)) {
                    StringBuilder sb = new StringBuilder();
                    int textDepth = 0;
                    while (parser.getEventType() != XmlPullParser.END_DOCUMENT) {
                        int e = parser.next();
                        if (e == XmlPullParser.TEXT) {
                            sb.append(parser.getText());
                        } else if (e == XmlPullParser.START_TAG) {
                            textDepth++;
                        } else if (e == XmlPullParser.END_TAG) {
                            if (textDepth > 0) {
                                textDepth--;
                            } else if ("text".equalsIgnoreCase(parser.getName())) {
                                return sb.toString().trim();
                            }
                        }
                    }
                    return sb.toString().trim();
                } else {
                    depth++;
                }
            } else if (event == XmlPullParser.END_TAG) {
                if ("navLabel".equalsIgnoreCase(parser.getName())) {
                    break;
                }
                if (depth > 0) depth--;
            }
            parser.next();
        }
        return null;
    }

    /**
     * 按 spine 顺序读取 XHTML 内容并生成章节
     */
    private static void parseSpineContent(ZipFile zipFile, String opfDir, EpubResult result, Map<String, String> ncxTitles) throws IOException {
        for (int i = 0; i < result.spineOrder.size(); i++) {
            String href = result.spineOrder.get(i);
            String entryPath = opfDir + href;

            ZipEntry entry = zipFile.getEntry(entryPath);
            if (entry == null) {
                try {
                    entry = zipFile.getEntry(java.net.URLDecoder.decode(entryPath, "UTF-8"));
                } catch (Exception e) {}
            }
            if (entry == null) {
                if (entryPath.startsWith("./")) {
                    entry = zipFile.getEntry(entryPath.substring(2));
                }
            }
            if (entry == null) continue;

            String content = readXhtmlContent(zipFile, entry);
            if (content == null || content.trim().isEmpty()) continue;

            // 标题优先级：NCX > XHTML h1/h2/title > 文件名
            String title = null;
            if (ncxTitles != null) {
                title = ncxTitles.get(href);
                if (title == null) {
                    String nameOnly = href;
                    int ls = nameOnly.lastIndexOf('/');
                    if (ls >= 0) nameOnly = nameOnly.substring(ls + 1);
                    title = ncxTitles.get(nameOnly);
                }
                if (title != null) title = title.trim();
                if (title == null || title.isEmpty()) {
                    title = extractTitleFromHref(href, content, i);
                }
            } else {
                title = extractTitleFromHref(href, content, i);
            }

            Chapter chapter = new Chapter(title, content);
            chapter.setIndex(i);
            result.chapters.add(chapter);
        }
    }

    /**
     * 读取 XHTML 文件并提取纯文本内容
     */
    private static String readXhtmlContent(ZipFile zipFile, ZipEntry entry) throws IOException {
        InputStream is = null;
        try {
            is = zipFile.getInputStream(entry);

            String encoding = "UTF-8";
            byte[] firstBytes = new byte[1024];
            int read = is.read(firstBytes);
            if (read > 0) {
                String header = new String(firstBytes, 0, Math.min(read, 200));
                if (header.contains("charset=gbk") || header.contains("charset=GBK") ||
                    header.contains("charset=gb2312")) {
                    encoding = "GBK";
                } else if (header.contains("charset=big5")) {
                    encoding = "Big5";
                } else if (header.contains("charset=windows-1252")) {
                    encoding = "ISO-8859-1";
                }
            }

            is.close();
            is = zipFile.getInputStream(entry);

            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            XmlPullParser parser = factory.newPullParser();
            parser.setInput(is, encoding);

            StringBuilder sb = new StringBuilder();
            boolean skipTag = false;

            while (parser.getEventType() != XmlPullParser.END_DOCUMENT) {
                switch (parser.getEventType()) {
                    case XmlPullParser.START_TAG:
                        String tag = parser.getName();
                        if ("p".equalsIgnoreCase(tag) || "h1".equalsIgnoreCase(tag) ||
                            "h2".equalsIgnoreCase(tag) || "h3".equalsIgnoreCase(tag) ||
                            "h4".equalsIgnoreCase(tag) || "div".equalsIgnoreCase(tag) ||
                            "br".equalsIgnoreCase(tag)) {
                            if (sb.length() > 0) sb.append("\n");
                        }
                        if ("style".equalsIgnoreCase(tag) || "script".equalsIgnoreCase(tag)) {
                            skipTag = true;
                        }
                        break;

                    case XmlPullParser.TEXT:
                        if (!skipTag) {
                            String text = parser.getText();
                            if (text != null) {
                                sb.append(text.trim());
                            }
                        }
                        break;

                    case XmlPullParser.END_TAG:
                        String endTag = parser.getName();
                        if ("style".equalsIgnoreCase(endTag) || "script".equalsIgnoreCase(endTag)) {
                            skipTag = false;
                        }
                        if ("p".equalsIgnoreCase(endTag) || "div".equalsIgnoreCase(endTag) ||
                            "h1".equalsIgnoreCase(endTag) || "h2".equalsIgnoreCase(endTag)) {
                            sb.append("\n");
                        }
                        break;
                }
                parser.next();
            }

            return sb.toString().trim();

        } catch (Exception e) {
            Log.e(TAG, "Error reading XHTML content", e);
            return readXhtmlContentSimple(zipFile, entry);
        } finally {
            if (is != null) try { is.close(); } catch (IOException e) {}
        }
    }

    /**
     * 简单标签去除方案（fallback）
     */
    private static String readXhtmlContentSimple(ZipFile zipFile, ZipEntry entry) throws IOException {
        InputStream is = null;
        BufferedReader reader = null;
        try {
            is = zipFile.getInputStream(entry);
            reader = new BufferedReader(new InputStreamReader(is, "UTF-8"), 8192);

            StringBuilder sb = new StringBuilder();
            String line;
            boolean inTag = false;

            while ((line = reader.readLine()) != null) {
                for (int i = 0; i < line.length(); i++) {
                    char c = line.charAt(i);
                    if (c == '<') {
                        inTag = true;
                    } else if (c == '>') {
                        inTag = false;
                        sb.append("\n");
                    } else if (!inTag) {
                        sb.append(c);
                    }
                }
                sb.append("\n");
            }

            return sb.toString().replaceAll("\\n{3,}", "\n\n").trim();
        } finally {
            if (reader != null) try { reader.close(); } catch (IOException e) {}
            if (is != null) try { is.close(); } catch (IOException e) {}
        }
    }

    /**
     * 从 href 或内容中提取章节标题
     */
    private static String extractTitleFromHref(String href, String content, int index) {
        String name = href;
        int lastSlash = name.lastIndexOf('/');
        if (lastSlash >= 0) name = name.substring(lastSlash + 1);
        int dotIndex = name.lastIndexOf('.');
        if (dotIndex > 0) name = name.substring(0, dotIndex);

        if (content != null && content.length() < 200000) {
            String[] titleTags = { "h1", "h2", "h3", "title" };
            for (String tag : titleTags) {
                int startIdx = content.indexOf("<" + tag + ">");
                if (startIdx < 0) startIdx = content.indexOf("<" + tag + " ");
                if (startIdx < 0) startIdx = content.toLowerCase().indexOf("<" + tag + ">");
                if (startIdx >= 0) {
                    int openEnd = content.indexOf(">", startIdx);
                    if (openEnd >= 0) {
                        int closeIdx = content.indexOf("</" + tag + ">", openEnd + 1);
                        if (closeIdx > openEnd) {
                            String title = content.substring(openEnd + 1, closeIdx)
                                .replaceAll("<[^>]*>", "")
                                .trim();
                            if (!title.isEmpty() && title.length() < 100) {
                                return title;
                            }
                        }
                    }
                }
            }
        }

        if (!name.isEmpty() && !name.matches("\\d+")) {
            String pretty = name
                .replaceAll("(?i)^(chapter|chap|ch|section|sec|part)\\s*", "")
                .replaceAll("^0+", "");
            if (!pretty.isEmpty() && !pretty.equals(name)) {
                try {
                    int num = Integer.parseInt(pretty);
                    return "第" + num + "章";
                } catch (NumberFormatException e) {}
            }
            return name;
        }

        return "第" + (index + 1) + "章";
    }

    /**
     * 读取 XmlPullParser 当前标签的文本内容
     */
    private static String readText(XmlPullParser parser) throws Exception {
        StringBuilder sb = new StringBuilder();
        int depth = 1;
        while (depth > 0) {
            int event = parser.next();
            if (event == XmlPullParser.TEXT) {
                sb.append(parser.getText());
            } else if (event == XmlPullParser.START_TAG) {
                depth++;
            } else if (event == XmlPullParser.END_TAG) {
                depth--;
            }
        }
        return sb.toString().trim();
    }

    /**
     * 验证文件是否是有效的 EPUB
     */
    public static boolean isValidEpub(File file) {
        if (file == null || !file.exists()) return false;
        String name = file.getName().toLowerCase();
        if (!name.endsWith(".epub")) return false;

        ZipFile zipFile = null;
        try {
            zipFile = new ZipFile(file);
            ZipEntry container = zipFile.getEntry("META-INF/container.xml");
            return container != null;
        } catch (IOException e) {
            return false;
        } finally {
            if (zipFile != null) {
                try { zipFile.close(); } catch (IOException e) {}
            }
        }
    }
}
