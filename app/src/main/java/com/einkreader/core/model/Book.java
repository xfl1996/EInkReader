package com.einkreader.core.model;

import java.util.List;

/**
 * 书籍数据模型
 */
public class Book {
    private String title;
    private String filePath;
    private String fileFormat; // "txt" or "epub"
    private long fileSize;
    private long lastReadTime;
    private int lastReadChapter;
    private int lastReadPosition;
    private List<Chapter> chapters;

    public Book() {}

    public Book(String title, String filePath, String fileFormat) {
        this.title = title;
        this.filePath = filePath;
        this.fileFormat = fileFormat;
    }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    public String getFileFormat() { return fileFormat; }
    public void setFileFormat(String fileFormat) { this.fileFormat = fileFormat; }

    public long getFileSize() { return fileSize; }
    public void setFileSize(long fileSize) { this.fileSize = fileSize; }

    public long getLastReadTime() { return lastReadTime; }
    public void setLastReadTime(long lastReadTime) { this.lastReadTime = lastReadTime; }

    public int getLastReadChapter() { return lastReadChapter; }
    public void setLastReadChapter(int chapter) { this.lastReadChapter = chapter; }

    public int getLastReadPosition() { return lastReadPosition; }
    public void setLastReadPosition(int position) { this.lastReadPosition = position; }

    public List<Chapter> getChapters() { return chapters; }
    public void setChapters(List<Chapter> chapters) { this.chapters = chapters; }

    public int getChapterCount() {
        return chapters != null ? chapters.size() : 0;
    }
}
