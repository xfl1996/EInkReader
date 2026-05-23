package com.einkreader.ui.reader;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.einkreader.R;

import java.util.ArrayList;

/**
 * 目录界面 - 按钮翻页模式
 * 墨水屏不适合滑动，改用箭头按钮翻页
 * 底部：大箭头(±10页) 小箭头(±1页) 页码
 */
public class TocActivity extends Activity {

    public static final String EXTRA_CHAPTERS = "chapter_titles";
    public static final String EXTRA_CURRENT_CHAPTER = "current_chapter";
    public static final String EXTRA_BOOK_TITLE = "book_title";
    public static final String RESULT_CHAPTER_INDEX = "chapter_index";

    // 每页显示的章节数
    private static final int ITEMS_PER_PAGE = 12;

    private ArrayList<String> chapterTitles;
    private int currentChapter;
    private int totalPages;
    private int currentPage;

    // UI 控件
    private LinearLayout listContainer;
    private TextView tvPageInfo;
    private TextView btnPrev1, btnPrev10;
    private TextView btnNext1, btnNext10;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        // 构建布局
        buildLayout();

        // 获取数据
        chapterTitles = getIntent().getStringArrayListExtra(EXTRA_CHAPTERS);
        currentChapter = getIntent().getIntExtra(EXTRA_CURRENT_CHAPTER, 0);
        String bookTitle = getIntent().getStringExtra(EXTRA_BOOK_TITLE);

        if (chapterTitles == null || chapterTitles.isEmpty()) {
            Toast.makeText(this, "无目录数据", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // 计算总页数
        totalPages = Math.max(1, (chapterTitles.size() + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE);

        // 找到当前章节所在的页
        currentPage = currentChapter / ITEMS_PER_PAGE;

        // 渲染第一页
        renderPage();
    }

    /**
     * 使用代码构建 UI（避免频繁修改 XML）
     */
    private void buildLayout() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);

        // ===== 顶部标题栏 =====
        LinearLayout titleBar = new LinearLayout(this);
        titleBar.setOrientation(LinearLayout.HORIZONTAL);
        titleBar.setGravity(Gravity.CENTER_VERTICAL);
        titleBar.setBackgroundColor(0xFF333333);
        int tbPad = dp(12);
        titleBar.setPadding(tbPad, dp(8), tbPad, dp(8));

        TextView btnBack = new TextView(this);
        btnBack.setText("← 返回");
        btnBack.setTextColor(Color.WHITE);
        btnBack.setTextSize(14);
        btnBack.setPadding(dp(4), dp(4), dp(4), dp(4));
        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { finish(); }
        });
        titleBar.addView(btnBack, lpWrap());

        TextView tvTitle = new TextView(this);
        tvTitle.setText("目录");
        tvTitle.setTextColor(Color.WHITE);
        tvTitle.setTextSize(16);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvTitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleLp = lpWrap();
        titleLp.weight = 1;
        titleBar.addView(tvTitle, titleLp);

        // 占位
        TextView placeholder = new TextView(this);
        placeholder.setWidth(dp(48));
        titleBar.addView(placeholder);

        root.addView(titleBar, lpMatchW(dp(48)));

        // ===== 章节列表容器（占满中间空间）=====
        listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams listLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0);
        listLp.weight = 1;
        root.addView(listContainer, listLp);

        // ===== 底部翻页栏 =====
        LinearLayout bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setGravity(Gravity.CENTER_VERTICAL);
        bottomBar.setBackgroundColor(0xFFF5F5F5);
        int bbPad = dp(8);
        bottomBar.setPadding(bbPad, bbPad, bbPad, bbPad);

        // 大左箭头 «
        btnPrev10 = makeArrowButton("«", true);
        bottomBar.addView(btnPrev10, lpWrap());

        // 小左箭头 ‹
        btnPrev1 = makeArrowButton("‹", false);
        bottomBar.addView(btnPrev1, lpWrap());

        // 页码信息
        tvPageInfo = new TextView(this);
        tvPageInfo.setTextSize(15);
        tvPageInfo.setTextColor(Color.BLACK);
        tvPageInfo.setGravity(Gravity.CENTER);
        tvPageInfo.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams pageInfoLp = lpWrap();
        pageInfoLp.weight = 1;
        pageInfoLp.leftMargin = dp(16);
        pageInfoLp.rightMargin = dp(16);
        bottomBar.addView(tvPageInfo, pageInfoLp);

        // 小右箭头 ›
        btnNext1 = makeArrowButton("›", false);
        bottomBar.addView(btnNext1, lpWrap());

        // 大右箭头 »
        btnNext10 = makeArrowButton("»", true);
        bottomBar.addView(btnNext10, lpWrap());

        LinearLayout.LayoutParams bbLp = lpMatchW(dp(64));
        root.addView(bottomBar, bbLp);

        setContentView(root);
    }

    /**
     * 创建箭头按钮
     */
    private TextView makeArrowButton(String text, boolean isBig) {
        TextView btn = new TextView(this);
        btn.setText(text);
        btn.setTextColor(Color.BLACK);
        btn.setTextSize(isBig ? 26 : 32);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setGravity(Gravity.CENTER);
        int pad = dp(isBig ? 14 : 12);
        btn.setPadding(pad, pad, pad, pad);
        btn.setBackgroundColor(0xFFE0E0E0);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onArrowClick(v);
            }
        });
        return btn;
    }

    /**
     * 渲染当前页的章节列表
     */
    private void renderPage() {
        listContainer.removeAllViews();

        int startIdx = currentPage * ITEMS_PER_PAGE;
        int endIdx = Math.min(startIdx + ITEMS_PER_PAGE, chapterTitles.size());

        int pad = dp(12);

        for (int i = startIdx; i < endIdx; i++) {
            final int chapterIndex = i;

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(pad, dp(10), pad, dp(10));

            // 当前章节高亮 - 用深色背景+反白文字，墨水屏也能看清
            if (i == currentChapter) {
                row.setBackgroundColor(Color.BLACK);
            } else {
                row.setBackgroundColor(Color.WHITE);
            }

            // 序号
            TextView tvNum = new TextView(this);
            tvNum.setText(String.format("%d.", i + 1));
            tvNum.setTextSize(14);
            tvNum.setWidth(dp(36));
            row.addView(tvNum);

            // 标题
            TextView tvTitle = new TextView(this);
            String title = chapterTitles.get(i);
            tvTitle.setText(title != null ? title : "第" + (i + 1) + "章");
            tvTitle.setTextSize(15);
            if (i == currentChapter) {
                // 当前章节：黑底白字
                tvTitle.setTextColor(Color.WHITE);
                tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
                tvNum.setTextColor(0xFFCCCCCC);
            } else {
                tvTitle.setTextColor(0xFF333333);
                tvNum.setTextColor(0xFF666666);
            }
            LinearLayout.LayoutParams titleLp = lpWrap();
            titleLp.weight = 1;
            row.addView(tvTitle, titleLp);

            // 点击跳转
            final View clickTarget = row;
            clickTarget.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent resultIntent = new Intent();
                    resultIntent.putExtra(RESULT_CHAPTER_INDEX, chapterIndex);
                    setResult(RESULT_OK, resultIntent);
                    finish();
                }
            });

            // 注意：不能用 lpMatchW(0)，height=0dp 没有 weight 会让行不可见！
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            listContainer.addView(row, rowLp);

            // 分隔线
            if (i < endIdx - 1) {
                View divider = new View(this);
                divider.setBackgroundColor(0xFFEEEEEE);
                LinearLayout.LayoutParams divLp = lpMatchW(1);
                divLp.leftMargin = dp(36);
                listContainer.addView(divider, divLp);
            }
        }

        // 更新页码显示
        tvPageInfo.setText((currentPage + 1) + " / " + totalPages);

        // 更新按钮状态
        btnPrev10.setAlpha(currentPage > 0 ? 1.0f : 0.3f);
        btnPrev1.setAlpha(currentPage > 0 ? 1.0f : 0.3f);
        btnNext1.setAlpha(currentPage < totalPages - 1 ? 1.0f : 0.3f);
        btnNext10.setAlpha(currentPage < totalPages - 1 ? 1.0f : 0.3f);
    }

    /**
     * 箭头按钮点击处理
     */
    private void onArrowClick(View v) {
        if (v == btnPrev10) {
            // 左移10页
            currentPage = Math.max(0, currentPage - 10);
        } else if (v == btnPrev1) {
            // 左移1页
            currentPage = Math.max(0, currentPage - 1);
        } else if (v == btnNext1) {
            // 右移1页
            currentPage = Math.min(totalPages - 1, currentPage + 1);
        } else if (v == btnNext10) {
            // 右移10页
            currentPage = Math.min(totalPages - 1, currentPage + 10);
        }
        renderPage();
    }

    // ===== 工具方法 =====

    private int dp(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
    }

    private LinearLayout.LayoutParams lpMatchW(int heightDp) {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                heightDp < 0 ? LinearLayout.LayoutParams.MATCH_PARENT : dp(heightDp));
    }

    private LinearLayout.LayoutParams lpWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }
}
