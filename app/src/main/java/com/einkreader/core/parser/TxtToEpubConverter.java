package com.einkreader.core.parser;

import android.util.Log;

import com.einkreader.core.model.Chapter;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * TXT 转 EPUB 转换器
 * 将 TXT 文件转换为符合 EPUB 3.0 规范的 EPUB 文件
 * 自动分章并生成 TOC 目录
 */
public class TxtToEpubConverter {
    private static final String TAG = "TxtToEpubConverter";

    /**
     * 进度回调接口
     */
    public interface ProgressListener {
        /**
         * @param progress 0-100
         * @param message  当前步骤描述
         */
        void onProgress(int progress, String message);
    }

    /**
     * 转换结果
     */
    public static class ConvertResult {
        public boolean success;
        public File outputFile;
        public String error;
        public int chapterCount;

        public ConvertResult(boolean success) {
            this.success = success;
        }
    }

    /**
     * 将 TXT 文件转换为 EPUB（无进度回调）
     */
    public static ConvertResult convert(File sourceFile, File outputDir, String encoding) {
        return convert(sourceFile, outputDir, encoding, null);
    }

    /**
     * 将 TXT 文件转换为 EPUB（带进度回调）
     *
     * @param sourceFile  源 TXT 文件
     * @param outputDir   输出目录（保存转换后的 EPUB 文件）
     * @param encoding    源文件编码（null 自动检测）
     * @param listener    进度回调（null 则不回调）
     * @return 转换结果
     */
    public static ConvertResult convert(File sourceFile, File outputDir, String encoding,
                                        ProgressListener listener) {
        ConvertResult result = new ConvertResult(false);

        try {
            // 1. 解析 TXT 文件 (0-50%)
            if (listener != null) listener.onProgress(5, "正在检测编码...");
            TxtParser.ParseResult parseResult = TxtParser.parse(sourceFile, encoding);
            List<Chapter> chapters = parseResult.chapters;

            if (chapters.isEmpty()) {
                result.error = "文件内容为空或无法解析";
                return result;
            }

            if (listener != null) listener.onProgress(50, "解析完成，共 " + chapters.size() + " 章");

            // 2. 准备输出文件 (50-60%)
            if (!outputDir.exists()) {
                outputDir.mkdirs();
            }
            String epubName = parseResult.bookTitle + ".epub";
            epubName = epubName.replaceAll("[\\\\/:*?\"<>|]", "_");
            File outputFile = new File(outputDir, epubName);

            if (listener != null) listener.onProgress(60, "正在生成 EPUB...");

            // 3. 生成 EPUB (60-95%，内部按章节推进)
            generateEpub(outputFile, parseResult.bookTitle, null, chapters, listener);

            if (listener != null) listener.onProgress(100, "转换完成！");

            result.success = true;
            result.outputFile = outputFile;
            result.chapterCount = chapters.size();

            Log.i(TAG, "Convert success: " + outputFile.getPath() + 
                  ", chapters=" + chapters.size());

        } catch (Exception e) {
            Log.e(TAG, "Convert failed", e);
            result.error = e.getMessage();
        }

        return result;
    }

    /**
     * 生成 EPUB 文件（带进度）
     */
    private static void generateEpub(File outputFile, String title, String author,
                                      List<Chapter> chapters,
                                      ProgressListener listener) throws IOException {
        int totalSteps = 5 + chapters.size(); // 5个基础文件 + 每个章节
        int currentStep = 0;

        ZipOutputStream zos = null;
        try {
            zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(outputFile)));

            // 1. mimetype
            writeMimetype(zos);
            currentStep++;
            if (listener != null)
                listener.onProgress(60 + currentStep * 35 / totalSteps, "写入基础文件...");

            // 2. container.xml
            writeContainer(zos);
            currentStep++;
            if (listener != null)
                listener.onProgress(60 + currentStep * 35 / totalSteps, "生成目录结构...");

            // 3. content.opf
            writeOpf(zos, title, author, chapters.size());
            currentStep++;

            // 4. toc.ncx
            writeTocNcx(zos, title, chapters);
            currentStep++;
            if (listener != null)
                listener.onProgress(60 + currentStep * 35 / totalSteps, "写入章节内容...");

            // 5. 章节 XHTML 文件
            for (int i = 0; i < chapters.size(); i++) {
                writeChapterXhtml(zos, i, chapters.get(i));
                currentStep++;
                if (listener != null && (i % 10 == 0 || i == chapters.size() - 1)) {
                    int pct = 60 + currentStep * 35 / totalSteps;
                    listener.onProgress(Math.min(pct, 95),
                        "写入章节 " + (i + 1) + "/" + chapters.size());
                }
            }

        } finally {
            if (zos != null) {
                try { zos.close(); } catch (IOException e) {}
            }
        }
    }

    /**
     * 写入 mimetype 文件（EPUB 规范要求不压缩）
     */
    private static void writeMimetype(ZipOutputStream zos) throws IOException {
        ZipEntry entry = new ZipEntry("mimetype");
        entry.setMethod(ZipEntry.DEFLATED); // Android ZipOutputStream 不支持 STORED
        zos.putNextEntry(entry);
        zos.write("application/epub+zip".getBytes("UTF-8"));
        zos.closeEntry();
    }

    /**
     * 写入 META-INF/container.xml
     */
    private static void writeContainer(ZipOutputStream zos) throws IOException {
        ZipEntry entry = new ZipEntry("META-INF/container.xml");
        zos.putNextEntry(entry);

        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<container version=\"1.0\" xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\">\n" +
            "  <rootfiles>\n" +
            "    <rootfile full-path=\"content.opf\" media-type=\"application/oebps-package+xml\"/>\n" +
            "  </rootfiles>\n" +
            "</container>\n";

        zos.write(xml.getBytes("UTF-8"));
        zos.closeEntry();
    }

    /**
     * 写入 content.opf（OPF 包描述文件）
     */
    private static void writeOpf(ZipOutputStream zos, String title, String author,
                                  int chapterCount) throws IOException {
        ZipEntry entry = new ZipEntry("content.opf");
        zos.putNextEntry(entry);

        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<package xmlns=\"http://www.idpf.org/2007/opf\" version=\"2.0\" unique-identifier=\"BookId\">\n");
        sb.append("  <metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\">\n");
        sb.append("    <dc:title>").append(escapeXml(title)).append("</dc:title>\n");
        if (author != null && !author.isEmpty()) {
            sb.append("    <dc:creator>").append(escapeXml(author)).append("</dc:creator>\n");
        }
        sb.append("    <dc:language>zh-CN</dc:language>\n");
        sb.append("    <dc:identifier id=\"BookId\">urn:uuid:einkreader-</dc:identifier>\n");
        sb.append("  </metadata>\n");

        // manifest
        sb.append("  <manifest>\n");
        sb.append("    <item id=\"ncx\" href=\"toc.ncx\" media-type=\"application/x-dtbncx+xml\"/>\n");
        for (int i = 0; i < chapterCount; i++) {
            sb.append("    <item id=\"ch").append(i).append("\" href=\"chapter_")
              .append(i).append(".xhtml\" media-type=\"application/xhtml+xml\"/>\n");
        }
        sb.append("  </manifest>\n");

        // spine
        sb.append("  <spine toc=\"ncx\">\n");
        for (int i = 0; i < chapterCount; i++) {
            sb.append("    <itemref idref=\"ch").append(i).append("\"/>\n");
        }
        sb.append("  </spine>\n");

        sb.append("</package>\n");

        zos.write(sb.toString().getBytes("UTF-8"));
        zos.closeEntry();
    }

    /**
     * 写入 toc.ncx（目录导航文件，兼容旧阅读器）
     */
    private static void writeTocNcx(ZipOutputStream zos, String title,
                                     List<Chapter> chapters) throws IOException {
        ZipEntry entry = new ZipEntry("toc.ncx");
        zos.putNextEntry(entry);

        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<ncx xmlns=\"http://www.daisy.org/z3986/2005/ncx/\" version=\"2005-1\">\n");
        sb.append("  <head>\n");
        sb.append("    <meta name=\"dtb:uid\" content=\"urn:uuid:einkreader\"/>\n");
        sb.append("  </head>\n");
        sb.append("  <docTitle><text>").append(escapeXml(title)).append("</text></docTitle>\n");
        sb.append("  <navMap>\n");

        for (int i = 0; i < chapters.size(); i++) {
            Chapter ch = chapters.get(i);
            sb.append("    <navPoint id=\"navPoint-").append(i).append("\" playOrder=\"").append(i + 1).append("\">\n");
            sb.append("      <navLabel><text>").append(escapeXml(ch.getTitle())).append("</text></navLabel>\n");
            sb.append("      <content src=\"chapter_").append(i).append(".xhtml\"/>\n");
            sb.append("    </navPoint>\n");
        }

        sb.append("  </navMap>\n");
        sb.append("</ncx>\n");

        zos.write(sb.toString().getBytes("UTF-8"));
        zos.closeEntry();
    }

    /**
     * 写入单个章节的 XHTML 文件（流式写入，减少内存占用）
     */
    private static void writeChapterXhtml(ZipOutputStream zos, int index,
                                            Chapter chapter) throws IOException {
        ZipEntry entry = new ZipEntry("chapter_" + index + ".xhtml");
        zos.putNextEntry(entry);

        // 用 byte[] 常量避免 StringBuilder 拼接大字符串
        byte[] header = ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<!DOCTYPE html>\n" +
            "<html xmlns=\"http://www.w3.org/1999/xhtml\">\n" +
            "<head>\n" +
            "  <title>" + escapeXml(chapter.getTitle()) + "</title>\n" +
            "</head>\n" +
            "<body>\n" +
            "  <h1>" + escapeXml(chapter.getTitle()) + "</h1>\n").getBytes("UTF-8");
        byte[] footer = "</body>\n</html>\n".getBytes("UTF-8");
        byte[] paraOpen = "      <p>".getBytes("UTF-8");
        byte[] paraClose = "</p>\n".getBytes("UTF-8");
        byte[] newline = "\n".getBytes("UTF-8");

        zos.write(header);

        // 按行写入段落
        String content = chapter.getContent();
        int len = content.length();
        int lineStart = 0;

        while (lineStart < len) {
            int lineEnd = content.indexOf('\n', lineStart);
            if (lineEnd < 0) lineEnd = len;
            String line = content.substring(lineStart, lineEnd).trim();
            lineStart = lineEnd + 1;

            if (!line.isEmpty()) {
                zos.write(paraOpen);
                zos.write(escapeXml(line).getBytes("UTF-8"));
                zos.write(paraClose);
            }
        }

        zos.write(footer);
        zos.closeEntry();
    }

    /**
     * XML 转义
     */
    private static String escapeXml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&apos;");
    }
}
