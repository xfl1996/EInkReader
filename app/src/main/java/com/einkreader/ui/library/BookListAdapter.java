package com.einkreader.ui.library;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.einkreader.R;
import com.einkreader.core.model.Book;

import java.util.ArrayList;
import java.util.List;

/**
 * 书籍列表适配器（纯文字进度，墨水屏友好）
 */
public class BookListAdapter extends BaseAdapter {

    private Context context;
    private List<Book> books;
    private LayoutInflater inflater;

    // 进度追踪：filePath -> progress
    private java.util.Map<String, Integer> progressMap = new java.util.HashMap<String, Integer>();
    private java.util.Map<String, String> progressMsgMap = new java.util.HashMap<String, String>();
    // 加载状态追踪
    private java.util.Set<String> loadingBooks = new java.util.HashSet<String>();

    public interface OnBookClickListener {
        void onBookClick(Book book, int position);
        void onBookLongClick(Book book, int position);
    }

    private OnBookClickListener listener;

    public BookListAdapter(Context context) {
        this.context = context;
        this.books = new ArrayList<>();
        this.inflater = LayoutInflater.from(context);
    }

    public void setBooks(List<Book> books) {
        this.books = books != null ? books : new ArrayList<Book>();
        notifyDataSetChanged();
    }

    public void setOnBookClickListener(OnBookClickListener listener) {
        this.listener = listener;
    }

    /**
     * 设置某本书的转换进度
     */
    public void setProgress(String filePath, int progress, String message) {
        progressMap.put(filePath, progress);
        if (message != null) progressMsgMap.put(filePath, message);
        notifyDataSetChanged();
    }

    /**
     * 清除某本书的进度显示
     */
    public void clearProgress(String filePath) {
        progressMap.remove(filePath);
        progressMsgMap.remove(filePath);
        notifyDataSetChanged();
    }

    /**
     * 设置某本书的加载状态（打开时的视觉反馈）
     */
    public void setLoadingBook(String filePath, boolean loading) {
        if (loading) {
            loadingBooks.add(filePath);
        } else {
            loadingBooks.remove(filePath);
        }
        notifyDataSetChanged();
    }

    public void removeBook(int position) {
        if (position >= 0 && position < books.size()) {
            books.remove(position);
            notifyDataSetChanged();
        }
    }

    @Override
    public int getCount() { return books.size(); }

    @Override
    public Book getItem(int position) { return books.get(position); }

    @Override
    public long getItemId(int position) { return position; }

    static class ViewHolder {
        TextView title;
        TextView info;
        TextView progressText;
    }

    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.item_book, parent, false);
            holder = new ViewHolder();
            holder.title = (TextView) convertView.findViewById(R.id.book_title);
            holder.info = (TextView) convertView.findViewById(R.id.book_info);
            holder.progressText = (TextView) convertView.findViewById(R.id.book_progress_text);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        final Book book = getItem(position);

        // 标题（null 保护）
        String title = book.getTitle();
        holder.title.setText(title != null ? title : "未知标题");

        // 显示格式、大小、最近阅读时间
        String format = book.getFileFormat() != null ? book.getFileFormat().toUpperCase() : "TXT";
        String size = formatFileSize(book.getFileSize());
        String time = formatTime(book.getLastReadTime());
        String info = format + "  " + size;
        if (!time.isEmpty()) {
            info += "  " + time;
        }
        holder.info.setText(info);

        // 纯文字进度显示
        Integer progress = progressMap.get(book.getFilePath());
        String progressMsg = progressMsgMap.get(book.getFilePath());
        if (loadingBooks.contains(book.getFilePath())) {
            // 加载状态（打开书籍时）
            holder.progressText.setVisibility(View.VISIBLE);
            holder.progressText.setText("▶ 正在打开...");
        } else if (progress != null && progress >= 0 && progress < 100) {
            // 转换进度
            holder.progressText.setVisibility(View.VISIBLE);
            String msg = progressMsg != null ? progressMsg : "处理中...";
            holder.progressText.setText(msg + " [" + progress + "%]");
        } else {
            holder.progressText.setVisibility(View.GONE);
        }

        // 点击事件
        convertView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) listener.onBookClick(book, position);
            }
        });

        convertView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (listener != null) listener.onBookLongClick(book, position);
                return true;
            }
        });

        return convertView;
    }

    private String formatFileSize(long bytes) {
        if (bytes <= 0) return "";
        if (bytes < 1024) return bytes + "B";
        if (bytes < 1024 * 1024) return String.format("%.1fKB", bytes / 1024.0);
        return String.format("%.1fMB", bytes / 1024.0 / 1024.0);
    }

    private String formatTime(long timeMs) {
        if (timeMs <= 0) return "";
        long diff = System.currentTimeMillis() - timeMs;
        if (diff < 60000) return "刚刚";
        if (diff < 3600000) return (diff / 60000) + "分钟前";
        if (diff < 86400000) return (diff / 3600000) + "小时前";
        return (diff / 86400000) + "天前";
    }
}
