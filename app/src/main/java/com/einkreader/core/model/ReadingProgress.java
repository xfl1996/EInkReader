package com.einkreader.core.model;

/**
 * 阅读进度数据模型
 * 记录书籍的阅读位置，用于恢复阅读
 */
public class ReadingProgress {
    private String bookPath;
    private int chapterIndex;
    private int charOffset;      // 当前章节内的字符偏移
    private int scrollY;         // 滚动位置
    private long lastReadTime;
    private int percent;         // 阅读百分比 0-100

    public ReadingProgress() {}

    public ReadingProgress(String bookPath) {
        this.bookPath = bookPath;
        this.lastReadTime = System.currentTimeMillis();
    }

    public String getBookPath() { return bookPath; }
    public void setBookPath(String bookPath) { this.bookPath = bookPath; }

    public int getChapterIndex() { return chapterIndex; }
    public void setChapterIndex(int chapterIndex) { this.chapterIndex = chapterIndex; }

    public int getCharOffset() { return charOffset; }
    public void setCharOffset(int charOffset) { this.charOffset = charOffset; }

    public int getScrollY() { return scrollY; }
    public void setScrollY(int scrollY) { this.scrollY = scrollY; }

    public long getLastReadTime() { return lastReadTime; }
    public void setLastReadTime(long lastReadTime) { this.lastReadTime = lastReadTime; }

    public int getPercent() { return percent; }
    public void setPercent(int percent) { this.percent = percent; }

    public void touch() {
        this.lastReadTime = System.currentTimeMillis();
    }

    @Override
    public String toString() {
        return "Progress{ch=" + chapterIndex + ", offset=" + charOffset + 
               ", percent=" + percent + "%}";
    }
}
