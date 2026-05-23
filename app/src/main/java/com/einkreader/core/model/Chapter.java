package com.einkreader.core.model;

/**
 * 章节数据模型
 */
public class Chapter {
    private int index;
    private String title;
    private String content;
    private int lineStart; // 起始行号（TXT 解析用，用于缓存）
    private int lineEnd;   // 结束行号（TXT 解析用，用于缓存）

    public Chapter(String title, String content) {
        this.title = title != null ? title : "";
        this.content = content != null ? content : "";
    }

    public Chapter(String title, String content, int lineStart, int lineEnd) {
        this.title = title != null ? title : "";
        this.content = content != null ? content : "";
        this.lineStart = lineStart;
        this.lineEnd = lineEnd;
    }

    public int getIndex() { return index; }
    public void setIndex(int index) { this.index = index; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public int getLineStart() { return lineStart; }
    public void setLineStart(int lineStart) { this.lineStart = lineStart; }

    public int getLineEnd() { return lineEnd; }
    public void setLineEnd(int lineEnd) { this.lineEnd = lineEnd; }

    /** 获取章节内容的字符数 */
    public int getCharCount() {
        return content.length();
    }

    @Override
    public String toString() {
        return "Chapter{" + index + ": " + title + ", chars=" + getCharCount() + "}";
    }
}
