package com.einkreader.ui.settings;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.einkreader.R;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import org.json.JSONArray;
import org.json.JSONObject;

public class AboutActivity extends Activity {

    private TextView tvVersion, tvUpdateStatus, tvChangelogTitle, tvChangelog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        // 返回按钮
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        // 版本号
        tvVersion = findViewById(R.id.tv_version);
        try {
            PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            tvVersion.setText("版本 v" + pi.versionName);
        } catch (Exception e) {
            tvVersion.setText("版本未知");
        }

        // 更新状态
        tvUpdateStatus = findViewById(R.id.tv_update_status);
        tvChangelogTitle = findViewById(R.id.tv_changelog_title);
        tvChangelog = findViewById(R.id.tv_changelog);

        // 检查更新按钮
        TextView btnCheckUpdate = findViewById(R.id.btn_check_update);
        btnCheckUpdate.setOnClickListener(v -> checkForUpdates());

        // B站二维码点击 - 跳转浏览器
        findViewById(R.id.iv_qrcode).setOnClickListener(v -> {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://space.bilibili.com"));
                startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(this, "无法打开浏览器", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void checkForUpdates() {
        tvUpdateStatus.setText("正在检查更新...");
        tvChangelogTitle.setVisibility(View.GONE);
        tvChangelog.setVisibility(View.GONE);

        new Thread(() -> {
            try {
                URL url = new URL("https://api.github.com/repos/xfl1996/EInkReader/releases/latest");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);

                int code = conn.getResponseCode();
                if (code != 200) {
                    postUpdateStatus("检查失败 (HTTP " + code + ")", null);
                    return;
                }

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                reader.close();

                JSONObject release = new JSONObject(sb.toString());
                String latestTag = release.getString("tag_name");
                String body = release.getString("body");

                // 获取本地版本
                PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
                String localVersion = "v" + pi.versionName;

                if (latestTag.equals(localVersion)) {
                    postUpdateStatus("已是最新版本 (" + localVersion + ")", null);
                } else {
                    // 获取下载链接
                    String downloadUrl = "";
                    JSONArray assets = release.getJSONArray("assets");
                    for (int i = 0; i < assets.length(); i++) {
                        String name = assets.getJSONObject(i).getString("name");
                        if (name.contains("universal")) {
                            downloadUrl = assets.getJSONObject(i).getString("browser_download_url");
                            break;
                        }
                    }
                    if (downloadUrl.isEmpty() && assets.length() > 0) {
                        downloadUrl = assets.getJSONObject(0).getString("browser_download_url");
                    }

                    final String dlUrl = downloadUrl;
                    postUpdateStatus("发现新版本: " + latestTag + " (当前: " + localVersion + ")\n点击此处下载更新",
                            body, dlUrl);
                }

            } catch (Exception e) {
                postUpdateStatus("检查失败: " + e.getMessage(), null);
            }
        }).start();
    }

    private void postUpdateStatus(String status, String changelog) {
        postUpdateStatus(status, changelog, null);
    }

    private void postUpdateStatus(String status, String changelog, String downloadUrl) {
        new Handler(Looper.getMainLooper()).post(() -> {
            tvUpdateStatus.setText(status);

            if (downloadUrl != null) {
                tvUpdateStatus.setOnClickListener(v -> {
                    try {
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl));
                        startActivity(intent);
                    } catch (Exception e) {
                        Toast.makeText(this, "无法打开下载链接", Toast.LENGTH_SHORT).show();
                    }
                });
                tvUpdateStatus.setTextColor(Color.parseColor("#FF0066CC"));
            } else {
                tvUpdateStatus.setOnClickListener(null);
                tvUpdateStatus.setTextColor(Color.parseColor("#FF333333"));
            }

            if (changelog != null && !changelog.isEmpty()) {
                tvChangelogTitle.setVisibility(View.VISIBLE);
                tvChangelog.setVisibility(View.VISIBLE);
                tvChangelog.setText(changelog);
            }
        });
    }
}
