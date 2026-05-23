package com.einkreader.ui.reader;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.einkreader.R;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 二维码日志展示页面
 *
 * 接收错误日志文本，分页生成全屏二维码，方便手机扫码读取。
 * 每个二维码包含一段文本（带页码标记），底部有翻页按钮。
 *
 * Intent Extra:
 *   "log_text"  - 完整日志文本 (String)
 *   "log_pages" - 分页后的文本列表 (ArrayList<String>)，优先使用
 *   "page_index" - 初始显示第几页（从0开始）
 */
public class QrLogActivity extends Activity {

    private ImageView qrImageView;
    private TextView pageInfoText;
    private Button btnPrev;
    private Button btnNext;
    private Button btnClose;

    private List<String> pages;
    private int currentPage = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 全屏
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        // 白底黑色内容，适合墨水屏
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(Color.WHITE);
        root.setPadding(0, 0, 0, 0);

        // 二维码图片（尽可能大）
        qrImageView = new ImageView(this);
        LinearLayout.LayoutParams qrLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        qrImageView.setLayoutParams(qrLp);
        qrImageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        qrImageView.setBackgroundColor(Color.WHITE);
        root.addView(qrImageView);

        // 页码提示
        pageInfoText = new TextView(this);
        pageInfoText.setTextSize(16);
        pageInfoText.setTextColor(Color.BLACK);
        pageInfoText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams pageInfoLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        pageInfoLp.setMargins(0, 8, 0, 4);
        root.addView(pageInfoText, pageInfoLp);

        // 按钮栏
        LinearLayout btnBar = new LinearLayout(this);
        btnBar.setOrientation(LinearLayout.HORIZONTAL);
        btnBar.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams btnBarLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        btnBarLp.setMargins(0, 4, 0, 8);
        root.addView(btnBar, btnBarLp);

        btnPrev = makeButton("◀ 上一页");
        btnBar.addView(btnPrev);

        btnNext = makeButton("下一页 ▶");
        btnBar.addView(btnNext);

        btnClose = makeButton("关闭");
        btnBar.addView(btnClose);

        setContentView(root);

        // 获取数据
        pages = getIntent().getStringArrayListExtra("log_pages");
        if (pages == null || pages.isEmpty()) {
            String logText = getIntent().getStringExtra("log_text");
            if (logText == null || logText.isEmpty()) {
                logText = "(空日志)";
            }
            pages = paginateLog(logText);
        }

        currentPage = getIntent().getIntExtra("page_index", 0);
        if (currentPage < 0 || currentPage >= pages.size()) {
            currentPage = 0;
        }

        // 按钮事件
        btnPrev.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (currentPage > 0) {
                    currentPage--;
                    showCurrentPage();
                }
            }
        });

        btnNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (currentPage < pages.size() - 1) {
                    currentPage++;
                    showCurrentPage();
                }
            }
        });

        btnClose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        showCurrentPage();
    }

    private void showCurrentPage() {
        if (pages == null || pages.isEmpty()) return;

        String pageContent = pages.get(currentPage);

        // 生成二维码
        Bitmap qr = generateQrBitmap(pageContent);
        if (qr != null) {
            qrImageView.setImageBitmap(qr);
        } else {
            qrImageView.setImageBitmap(null);
            pageInfoText.setText("二维码生成失败！文本可能过长");
        }

        // 更新页码
        pageInfoText.setText("第 " + (currentPage + 1) + " / " + pages.size() + " 页");

        // 更新按钮状态
        btnPrev.setEnabled(currentPage > 0);
        btnPrev.setTextColor(currentPage > 0 ? Color.BLACK : Color.GRAY);
        btnNext.setEnabled(currentPage < pages.size() - 1);
        btnNext.setTextColor(currentPage < pages.size() - 1 ? Color.BLACK : Color.GRAY);
    }

    /**
     * 将长日志分页，每页尽量多塞内容但不超过 QR 码容量限制
     * QR Code 版本40-L 最大约 7089 个数字 / 4296 个字母 / 2953 字节
     * 中文 UTF-8 每字3字节，安全上限约 800-900 个中文字符
     * 加上页码标记后，每页控制在 700 字符以内（含UTF-8）
     */
    private List<String> paginateLog(String logText) {
        java.util.ArrayList<String> result = new java.util.ArrayList<>();

        // 先按 600 字符分块
        int chunkSize = 600;
        int total = logText.length();
        int totalPages = (total + chunkSize - 1) / chunkSize;

        for (int i = 0; i < total; i += chunkSize) {
            int end = Math.min(i + chunkSize, total);
            String chunk = logText.substring(i, end);
            // 加页码标记
            String tagged = "[" + (result.size() + 1) + "/" + Math.max(totalPages, 1) + "]\n" + chunk;
            result.add(tagged);
        }

        if (result.isEmpty()) {
            result.add("[1/1]\n(空日志)");
        }

        return result;
    }

    /**
     * 生成二维码 Bitmap — 直接编码原始文本，不压缩
     */
    private Bitmap generateQrBitmap(String text) {
        try {
            // 如果文本太长，截断（QR 版本40-L 最大约 2953 字节）
            if (text.getBytes("UTF-8").length > 2900) {
                // 逐字截断，确保 UTF-8 不会被截一半
                int maxChars = 800;
                while (maxChars > 100 && text.substring(0, Math.min(maxChars, text.length()))
                        .getBytes("UTF-8").length > 2850) {
                    maxChars -= 50;
                }
                text = text.substring(0, Math.min(maxChars, text.length()));
                text += "\n...(已截断，完整日志请看后续页)";
            }

            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.L);
            hints.put(EncodeHintType.MARGIN, 0);

            QRCodeWriter writer = new QRCodeWriter();
            // 直接编码原始文本（UTF-8），不压缩
            BitMatrix matrix = writer.encode(text, BarcodeFormat.QR_CODE, 0, 0, hints);

            int width = matrix.getWidth();
            int height = matrix.getHeight();
            int[] pixels = new int[width * height];

            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    pixels[y * width + x] = matrix.get(x, y) ? Color.BLACK : Color.WHITE;
                }
            }

            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
            return bitmap;

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private Button makeButton(String text) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextSize(14);
        btn.setTextColor(Color.BLACK);
        btn.setBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(4, 0, 4, 0);
        btn.setLayoutParams(lp);
        return btn;
    }

    /**
     * 静态工具方法：供其他 Activity 调用，直接打开二维码日志页面
     */
    public static void showLog(Activity from, String logText) {
        android.content.Intent intent = new android.content.Intent(from, QrLogActivity.class);
        intent.putExtra("log_text", logText != null ? logText : "(空日志)");
        from.startActivity(intent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}
