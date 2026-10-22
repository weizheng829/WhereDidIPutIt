package com.nawle.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** 关于页：作者信息、联系方式、QQ 群二维码、赞助、开源协议 */
public class AboutActivity extends Activity {

    private int themeSeqApplied;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(Ui.wrapTheme(base));
        themeSeqApplied = Ui.themeSeq();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (themeSeqApplied != Ui.themeSeq()) recreate();
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_about);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        ((TextView) findViewById(R.id.tvVersion)).setText(
                "v" + About.versionName(this) + " · " + About.LICENSE_NAME + " 开源");
        ((TextView) findViewById(R.id.tvEmail)).setText(About.EMAIL);
        ((TextView) findViewById(R.id.tvCopyright)).setText(About.COPYRIGHT);

        findViewById(R.id.btnCopyEmail).setOnClickListener(v -> copy("邮箱", About.EMAIL));

        findViewById(R.id.btnMail).setOnClickListener(v -> {
            Intent it = new Intent(Intent.ACTION_SENDTO,
                    Uri.fromParts("mailto", About.EMAIL, null));
            it.putExtra(Intent.EXTRA_SUBJECT, "「收哪了」反馈");
            try {
                startActivity(it);
            } catch (Exception e) {
                copy("邮箱", About.EMAIL);
                Ui.toast(this, "没有邮件应用，邮箱已复制");
            }
        });

        ImageView qrQQ = findViewById(R.id.qrQQ);
        ImageView qrDonate = findViewById(R.id.qrDonate);
        qrQQ.setImageResource(R.drawable.qr_qq);
        qrDonate.setImageResource(R.drawable.qr_donate);
        qrQQ.setOnClickListener(v -> showLarge(R.drawable.qr_qq, "QQ 交流群"));
        qrDonate.setOnClickListener(v -> showLarge(R.drawable.qr_donate, "赞助支持"));

        findViewById(R.id.btnLicense).setOnClickListener(v -> showLicense());

        findViewById(R.id.btnSource).setOnClickListener(v -> {
            Intent it = new Intent(Intent.ACTION_VIEW, Uri.parse(About.REPO_URL));
            try {
                startActivity(it);
            } catch (Exception e) {
                copy("仓库地址", About.REPO_URL);
                Ui.toast(this, "没有浏览器，仓库地址已复制");
            }
        });
    }

    private void copy(String label, String text) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText(label, text));
        Ui.toast(this, label + "已复制：" + text);
    }

    /** 全屏放大查看二维码 */
    private void showLarge(int resId, String title) {
        FrameLayout wrap = new FrameLayout(this);
        wrap.setBackgroundColor(Color.BLACK);
        ImageView iv = new ImageView(this);
        iv.setImageResource(resId);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int pad = Ui.dp(this, 24);
        wrap.setPadding(pad, pad, pad, pad);
        wrap.addView(iv, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(wrap)
                .setPositiveButton("关闭", null)
                .show();
    }

    /** 从 assets 读取 LICENSE 全文展示 */
    private void showLicense() {
        String text;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                getAssets().open("license.txt"), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append('\n');
            text = sb.toString();
        } catch (Exception e) {
            text = About.COPYRIGHT;
        }
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(13);
        tv.setTextColor(getResources().getColor(R.color.onSurface));
        int pad = Ui.dp(this, 20);
        tv.setPadding(pad, pad, pad, 0);
        ScrollView sc = new ScrollView(this);
        sc.addView(tv, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.addView(sc, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 380)));
        new AlertDialog.Builder(this)
                .setTitle("MIT License")
                .setView(box)
                .setPositiveButton("关闭", null)
                .show();
    }
}
