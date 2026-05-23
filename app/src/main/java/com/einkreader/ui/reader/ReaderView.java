package com.einkreader.ui.reader;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Build;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.widget.Scroller;

import com.einkreader.core.model.Chapter;

import android.os.Handler;
import android.os.Looper;

/**
 * 阅读器自定义 View
 * 负责文本渲染、分页、翻页手势处理
 * 支持三种触屏翻页模式
 * 支持字体/行距/段距/字距/粗细设置（粗细为连续可调）
 */
public class ReaderView extends View {

    // --- 触屏翻页模式 ---
    public static final int TOUCH_TAP_LEFT_RIGHT = 0;
    public static final int TOUCH_TAP_ANYWHERE   = 1;
    public static final int TOUCH_SWIPE           = 2;

    // --- 翻页回调 ---
    public interface OnPageChangeListener {
        void onPageChanged(int pageIndex, int totalPages);
        void onChapterChanged(int chapterIndex);
        void onTapCenter();
        void onSwipeDown();
    }

    // --- 配置 ---
    private float textSize = 20f;
    private float lineSpacing = 1.4f;
    private float paragraphSpacing = 10f;  // dp
    private float letterSpacing = 0f;
    private int fontWeight = 0;            // -50 ~ +50
    private int touchMode = TOUCH_TAP_LEFT_RIGHT;
    private int paddingLeft = 8;
    private int paddingRight = 8;
    private int paddingTop = 6;
    private int paddingBottom = 6;
    private int textColor = Color.BLACK;
    private int bgColor = Color.WHITE;

    // --- 画笔 ---
    private Paint textPaint;

    // --- 布局 ---
    private int viewWidth;
    private int viewHeight;
    private String[] lines;
    private int currentPage = 0;
    private int totalPages = 0;

    // --- 章节 ---
    private Chapter currentChapter;
    private String chapterText;
    private String[] allLines;
    private int chapterLineOffset = 0;

    // --- 手势 ---
    private GestureDetector gestureDetector;
    private Scroller scroller;

    // --- 回调 ---
    private OnPageChangeListener pageChangeListener;

    // --- 全屏刷新 ---
    private boolean isRefreshing = false;
    private int refreshPhase = 0;
    private Handler refreshHandler;

    // --- 自动全刷设置 ---
    private boolean chapterRefresh = true;   // 切换章节时全刷
    private int pageRefreshInterval = 0;     // 隔N页全刷，0=关闭
    private int pagesSinceLastRefresh = 0;   // 距上次全刷已翻页数

    // --- 全刷延时设置 ---
    private int refreshDelay1 = 400;  // 黑屏→白屏 延迟(ms)
    private int refreshDelay2 = 400;  // 白屏→恢复 延迟(ms)

    // --- 翻页重叠 ---
    private int overlapLines = 0;     // 下一页顶部保留上页底部N行

    // --- dp 转换 ---
    private float density;

    // --- SharedPreferences ---
    private SharedPreferences prefs;

    public ReaderView(Context context) {
        super(context);
        init(context);
    }

    public ReaderView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public ReaderView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        density = context.getResources().getDisplayMetrics().density;
        refreshHandler = new Handler(Looper.getMainLooper());

        // 加载设置
        prefs = context.getSharedPreferences("eink_reader_prefs", Context.MODE_PRIVATE);
        loadSettings();

        // 初始化画笔
        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(textColor);
        textPaint.setTextSize(textSize * density);
        applyTextPaintSettings();

        // 手势检测
        gestureDetector = new GestureDetector(context, new SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                if (isRefreshing) return true;
                handleTap(e.getX(), e.getY());
                return true;
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (isRefreshing) return true;
                if (touchMode == TOUCH_SWIPE) {
                    float diffX = e2.getX() - e1.getX();
                    float diffY = e2.getY() - e1.getY();
                    float minSwipe = 50 * density;

                    if (Math.abs(diffY) > Math.abs(diffX) && diffY > minSwipe) {
                        if (pageChangeListener != null) pageChangeListener.onSwipeDown();
                        return true;
                    }

                    if (Math.abs(diffX) > minSwipe) {
                        if (diffX < 0) {
                            if (!nextPage()) {
                                if (pageChangeListener != null) pageChangeListener.onChapterChanged(1);
                            }
                        } else {
                            if (!prevPage()) {
                                if (pageChangeListener != null) pageChangeListener.onChapterChanged(-1);
                            }
                        }
                        return true;
                    }
                }
                return false;
            }

            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }
        });

        scroller = new Scroller(context);
    }

    // ==================== 设置加载 ====================

    /**
     * 安全读取 float，兼容旧版 int/boolean 存储的值
     */
    private float safeGetFloat(String key, float def) {
        try {
            return prefs.getFloat(key, def);
        } catch (ClassCastException e) {
            try { return prefs.getInt(key, (int) def); } catch (Exception e2) {
                try { return prefs.getBoolean(key, def > 0.5f) ? 1f : 0f; } catch (Exception e3) {
                    return def;
                }
            }
        }
    }

    /**
     * 安全读取 int，兼容旧版 float/boolean 存储的值
     */
    private int safeGetInt(String key, int def) {
        try {
            return prefs.getInt(key, def);
        } catch (ClassCastException e) {
            try { return (int) prefs.getFloat(key, def); } catch (Exception e2) {
                try { return prefs.getBoolean(key, def != 0) ? 1 : 0; } catch (Exception e3) {
                    return def;
                }
            }
        }
    }

    /**
     * 安全读取 boolean，兼容旧版 int/float 存储的值
     */
    private boolean safeGetBoolean(String key, boolean def) {
        try {
            return prefs.getBoolean(key, def);
        } catch (ClassCastException e) {
            try { return prefs.getFloat(key, def ? 1f : 0f) > 0.5f; } catch (Exception e2) {
                try { return prefs.getInt(key, def ? 1 : 0) != 0; } catch (Exception e3) {
                    return def;
                }
            }
        }
    }

    public void loadSettings() {
        if (prefs == null) return;
        textSize = safeGetFloat("text_size", 20f);
        lineSpacing = safeGetFloat("line_spacing", 1.4f);
        paragraphSpacing = safeGetFloat("paragraph_spacing", 10f);
        letterSpacing = safeGetFloat("letter_spacing", 0f);
        fontWeight = safeGetInt("font_weight", 0);
        touchMode = safeGetInt("touch_mode", TOUCH_TAP_LEFT_RIGHT);
        chapterRefresh = safeGetBoolean(ReadingSettingsActivity.KEY_CHAPTER_REFRESH, true);
        pageRefreshInterval = safeGetInt(ReadingSettingsActivity.KEY_PAGE_REFRESH_INTERVAL, 0);
        paddingTop = safeGetInt("padding_top", 6);
        paddingBottom = safeGetInt("padding_bottom", 6);
        paddingLeft = safeGetInt("padding_horizontal", 8);
        paddingRight = safeGetInt("padding_horizontal", 8);
        overlapLines = safeGetInt("overlap_lines", 0);
        refreshDelay1 = safeGetInt(ReadingSettingsActivity.KEY_REFRESH_DELAY1, 400);
        refreshDelay2 = safeGetInt(ReadingSettingsActivity.KEY_REFRESH_DELAY2, 400);
    }

    public void applySettings() {
        loadSettings();
        applyTextPaintSettings();
        if (chapterText != null && !chapterText.isEmpty()) {
            paginated = false;
            requestLayout();
            invalidate();
        }
    }

    private void applyTextPaintSettings() {
        if (textPaint == null) return;
        textPaint.setTextSize(textSize * density);

        // 字体粗细：用 fakeBoldText 实现连续可调
        // weight: -50 ~ +50, 0=标准
        if (fontWeight == 0) {
            textPaint.setTypeface(Typeface.DEFAULT);
            textPaint.setFakeBoldText(false);
        } else if (fontWeight > 0) {
            // 正数：加粗，用 fakeBoldText 模拟
            textPaint.setTypeface(Typeface.DEFAULT);
            textPaint.setFakeBoldText(true);
        } else {
            // 负数：变细
            textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
            textPaint.setFakeBoldText(false);
        }

        // letterSpacing 在 API 21+ 才支持
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            textPaint.setLetterSpacing(letterSpacing);
        }
    }

    // ==================== 翻页方法 ====================

    public boolean nextPage() {
        if (lines == null || totalPages == 0) return false;
        if (currentPage < totalPages - 1) {
            currentPage++;
            invalidate();
            if (pageChangeListener != null) {
                pageChangeListener.onPageChanged(currentPage, totalPages);
            }
            checkAutoFullRefresh();
            return true;
        }
        return false;
    }

    public boolean prevPage() {
        if (lines == null || totalPages == 0) return false;
        if (currentPage > 0) {
            currentPage--;
            invalidate();
            if (pageChangeListener != null) {
                pageChangeListener.onPageChanged(currentPage, totalPages);
            }
            checkAutoFullRefresh();
            return true;
        }
        return false;
    }

    /**
     * 检查是否需要触发自动全刷
     */
    private void checkAutoFullRefresh() {
        if (pageRefreshInterval <= 0) return;
        pagesSinceLastRefresh++;
        if (pagesSinceLastRefresh >= pageRefreshInterval) {
            pagesSinceLastRefresh = 0;
            // 延迟触发全刷，等当前页绘制完成
            refreshHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    performFullRefresh();
                }
            }, 50);
        }
    }

    /**
     * 重置全刷计数器（切换章节时调用）
     */
    public void resetFullRefreshCounter() {
        pagesSinceLastRefresh = 0;
    }

    /**
     * 切换章节时检查是否需要全刷
     */
    private void checkChapterFullRefresh() {
        if (chapterRefresh) {
            pagesSinceLastRefresh = 0;
            refreshHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    performFullRefresh();
                }
            }, 100);
        }
    }

    public int getCurrentPage() { return currentPage; }
    public int getTotalPages() { return totalPages; }

    public void goToPage(int page) {
        if (paginated && totalPages > 0) {
            currentPage = Math.max(0, Math.min(page, totalPages - 1));
            invalidate();
        } else {
            // 分页还没完成，先存着，paginate 完成后恢复
            pendingRestorePage = page;
        }
    }

    // 待恢复的页码（setChapter 后 pagination 完成前 goToPage 无效，用这个暂存）
    private int pendingRestorePage = -1;

    public void setChapter(Chapter chapter) {
        this.currentChapter = chapter;
        if (chapter != null) {
            this.chapterText = chapter.getContent();
        } else {
            this.chapterText = "";
        }
        this.allLines = null;
        this.currentPage = 0;
        this.pendingRestorePage = -1;
        this.paginated = false;
        requestLayout();
        invalidate();
        // 切换章节时检查全刷
        checkChapterFullRefresh();
    }

    public void setChapterText(String text) {
        this.chapterText = text;
        this.allLines = null;
        this.currentPage = 0;
        this.paginated = false;
        requestLayout();
        invalidate();
        checkChapterFullRefresh();
    }

    public void setOnPageChangeListener(OnPageChangeListener listener) {
        this.pageChangeListener = listener;
    }

    // ==================== 触摸事件 ====================

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (isRefreshing) return true;
        return gestureDetector.onTouchEvent(event);
    }

    // ==================== 测量与布局 ====================

    private boolean paginated = false;

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = MeasureSpec.getSize(widthMeasureSpec);
        int h = MeasureSpec.getSize(heightMeasureSpec);
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        viewWidth = w;
        viewHeight = h;
        paginated = false;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (isRefreshing) {
            drawRefreshPhase(canvas);
            return;
        }

        canvas.drawColor(bgColor);

        if (viewWidth == 0 || viewHeight == 0) return;
        if (chapterText == null || chapterText.isEmpty()) {
            drawEmptyPage(canvas);
            return;
        }

        if (!paginated || lines == null) {
            paginate();
        }

        if (lines == null || lines.length == 0) {
            drawEmptyPage(canvas);
            return;
        }

        drawPage(canvas);
    }

    private void drawEmptyPage(Canvas canvas) {
        Paint hintPaint = new Paint(textPaint);
        hintPaint.setColor(Color.GRAY);
        hintPaint.setTextSize(16 * density);
        hintPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("空白页", viewWidth / 2f, viewHeight / 2f, hintPaint);
    }

    // ==================== 分页算法 ====================

    private int linesPerPage;

    private void paginate() {
        if (chapterText == null || chapterText.isEmpty()) {
            lines = new String[0];
            totalPages = 0;
            paginated = true;
            return;
        }

        String[] paragraphs = chapterText.split("\\n");
        java.util.List<String> lineList = new java.util.ArrayList<>();

        float contentWidth = viewWidth - (paddingLeft + paddingRight) * density;
        float indent = textSize * density * 2f;  // 段首缩进：2个字宽

        // 使用 Paint.FontMetrics 精确计算行高，避免翻页重叠
        Paint.FontMetrics fm = textPaint.getFontMetrics();
        float realLineHeight = fm.descent - fm.ascent;  // 真实行高（含 internal leading）
        float lineHeight = realLineHeight * lineSpacing;  // 行距倍数应用到真实行高
        float paraGap = paragraphSpacing * density;

        for (int p = 0; p < paragraphs.length; p++) {
            String para = paragraphs[p].trim();
            if (para.isEmpty()) {
                lineList.add("");
                continue;
            }

            // 自动换行（段首行考虑缩进）
            int start = 0;
            char[] chars = para.toCharArray();
            boolean firstLine = true;
            while (start < chars.length) {
                float lineWidth = firstLine ? contentWidth - indent : contentWidth;
                int count;
                try {
                    count = textPaint.breakText(chars, start, chars.length - start,
                            lineWidth, null);
                } catch (Exception e) {
                    count = 0;
                }
                if (count <= 0) {
                    count = 1;
                }
                if (start + count > chars.length) {
                    count = chars.length - start;
                }
                lineList.add(new String(chars, start, count));
                start += count;
                firstLine = false;
            }

            // 段落间加空行
            if (p < paragraphs.length - 1 && !para.isEmpty()) {
                lineList.add("");
            }
        }

        lines = lineList.toArray(new String[0]);

        // 计算每页行数
        float totalContentHeight = viewHeight - (paddingTop + paddingBottom) * density;
        float exactLinesPerPage = totalContentHeight / lineHeight;
        linesPerPage = Math.max(1, (int) exactLinesPerPage);

        // 翻页重叠：每页实际前进 linesPerPage - overlapLines 行
        int stride = Math.max(1, linesPerPage - overlapLines);
        totalPages = Math.max(1, (int) Math.ceil((double) lines.length / stride));
        paginated = true;

        // 恢复待恢复的页码
        if (pendingRestorePage >= 0) {
            currentPage = Math.min(pendingRestorePage, totalPages - 1);
            pendingRestorePage = -1;
        }

        if (currentPage >= totalPages) {
            currentPage = totalPages - 1;
        }
        if (currentPage < 0) currentPage = 0;
    }

    // ==================== 绘制 ====================

    private void drawPage(Canvas canvas) {
        float x = paddingLeft * density;
        float startY = paddingTop * density;
        // 使用与 paginate() 一致的真实行高计算
        Paint.FontMetrics fm = textPaint.getFontMetrics();
        float realLineHeight = fm.descent - fm.ascent;
        float lineHeight = realLineHeight * lineSpacing;
        float paraGap = paragraphSpacing * density;
        float bottomLimit = viewHeight - paddingBottom * density;
        float indent = textSize * density * 2f;  // 段首缩进：2个字宽

        int stride = Math.max(1, linesPerPage - overlapLines);
        int startLine = currentPage * stride;

        float y = startY;
        boolean needIndent = true;
        int drawnLines = 0;
        for (int i = startLine; i < lines.length; i++) {
            if (y + lineHeight > bottomLimit || drawnLines >= linesPerPage) break;

            if (lines[i].isEmpty()) {
                y += lineHeight;  // 空行与文本行等高，与 paginate() 一致
                needIndent = true;
                drawnLines++;
                continue;
            }
            float lineX = needIndent ? x + indent : x;
            canvas.drawText(lines[i], lineX, y + textSize * density, textPaint);
            y += lineHeight;
            drawnLines++;
            needIndent = false;
        }

        // 页码（底部居中，浅灰色）
        Paint pagePaint = new Paint(textPaint);
        pagePaint.setTextSize(10 * density);
        pagePaint.setColor(Color.GRAY);
        pagePaint.setTextAlign(Paint.Align.CENTER);
        pagePaint.setFakeBoldText(false);
        String pageStr = (currentPage + 1) + " / " + totalPages;
        canvas.drawText(pageStr, viewWidth / 2f, viewHeight - 2 * density, pagePaint);
    }

    // ==================== 全屏刷新 ====================

    private void drawRefreshPhase(Canvas canvas) {
        switch (refreshPhase) {
            case 1:
                canvas.drawColor(Color.BLACK);
                break;
            case 2:
                canvas.drawColor(Color.WHITE);
                break;
            default:
                canvas.drawColor(bgColor);
                break;
        }
    }

    /**
     * 取消正在进行的全刷，恢复正常显示
     * 在打开目录/设置页面前调用，防止残留回调搞黑屏幕
     */
    public void cancelFullRefresh() {
        refreshHandler.removeCallbacksAndMessages(null);
        if (isRefreshing) {
            isRefreshing = false;
            refreshPhase = 0;
            // 恢复 DecorView 为白色
            try {
                Context ctx = getContext();
                while (ctx instanceof android.content.ContextWrapper) {
                    if (ctx instanceof android.app.Activity) {
                        android.view.Window w = ((android.app.Activity) ctx).getWindow();
                        if (w != null) w.getDecorView().setBackgroundColor(Color.WHITE);
                        break;
                    }
                    ctx = ((android.content.ContextWrapper) ctx).getBaseContext();
                }
            } catch (Exception e) { /* ignore */ }
            invalidate();
        }
    }

    public void performFullRefresh() {
        if (isRefreshing) return;
        isRefreshing = true;
        refreshPhase = 1;
        invalidate();

        trySystemFullRefresh();

        refreshHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                refreshPhase = 2;
                invalidate();
                refreshHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        refreshPhase = 0;
                        isRefreshing = false;
                        invalidate();
                    }
                }, refreshDelay2);
            }
        }, refreshDelay1);
    }

    /**
     * 尝试通过 Window 背景色触发硬件全刷（SurfaceFlinger 路径）
     */
    private void trySystemFullRefresh() {
        try {
            android.app.Activity activity = null;
            Context ctx = getContext();
            while (ctx instanceof android.content.ContextWrapper) {
                if (ctx instanceof android.app.Activity) {
                    activity = (android.app.Activity) ctx;
                    break;
                }
                ctx = ((android.content.ContextWrapper) ctx).getBaseContext();
            }
            if (activity != null) {
                final android.view.Window window = activity.getWindow();
                if (window != null) {
                    final android.view.View decorView = window.getDecorView();
                    // 黑
                    decorView.setBackgroundColor(Color.BLACK);
                    refreshHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            // 白
                            decorView.setBackgroundColor(Color.WHITE);
                            refreshHandler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    // 恢复
                                    decorView.setBackgroundColor(Color.WHITE);
                                }
                            }, refreshDelay2);
                        }
                    }, refreshDelay1);
                }
            }
        } catch (Exception e) {
            // 忽略
        }
    }

    // ==================== 点击处理 ====================

    private void handleTap(float x, float y) {
        // 所有模式下，点击中间区域都弹出菜单
        float ratioX = x / viewWidth;
        float ratioY = y / viewHeight;
        if (ratioX > 0.25f && ratioX < 0.75f && ratioY > 0.15f && ratioY < 0.85f) {
            if (pageChangeListener != null) pageChangeListener.onTapCenter();
            return;
        }

        // 非中间区域，根据模式处理翻页
        switch (touchMode) {
            case TOUCH_TAP_LEFT_RIGHT:
                if (ratioX < 0.3f) {
                    if (!prevPage()) {
                        if (pageChangeListener != null) pageChangeListener.onChapterChanged(-1);
                    }
                } else if (ratioX > 0.7f) {
                    if (!nextPage()) {
                        if (pageChangeListener != null) pageChangeListener.onChapterChanged(1);
                    }
                }
                break;

            case TOUCH_TAP_ANYWHERE:
                if (ratioX < 0.5f) {
                    if (!prevPage()) {
                        if (pageChangeListener != null) pageChangeListener.onChapterChanged(-1);
                    }
                } else {
                    if (!nextPage()) {
                        if (pageChangeListener != null) pageChangeListener.onChapterChanged(1);
                    }
                }
                break;

            case TOUCH_SWIPE:
                // 滑动模式下，非中间区域的点击不做处理（翻页靠滑动手势）
                break;
        }
    }

    /**
     * 获取当前画笔（供外部读取设置状态）
     */
    public Paint getTextPaint() {
        return textPaint;
    }
}
