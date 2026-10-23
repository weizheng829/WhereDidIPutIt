package com.nawle.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
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

    /** 赞赏码资源，下标对应 donateTab：0 微信 1 支付宝 */
    private static final int[] DONATE_QRS = {
            R.drawable.qr_donate_wechat, R.drawable.qr_donate_alipay};

    private int uiSeqApplied;

    private int donateTab;
    private ImageView qrDonate;
    private TextView tabWechat, tabAlipay;
    /** 赞赏码放大弹窗内的控件引用，弹窗关闭时置空 */
    private ImageView qrDonateLarge;
    private TextView tabWechatLarge, tabAlipayLarge;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(Ui.wrapLocale(Ui.wrapTheme(base)));
        uiSeqApplied = Ui.uiSeq();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (uiSeqApplied != Ui.uiSeq()) recreate();
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setTheme(Ui.themeStyle(this));
        setContentView(R.layout.activity_about);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        ((TextView) findViewById(R.id.tvVersion)).setText(
                "v" + About.versionName(this));
        ((TextView) findViewById(R.id.tvEmail)).setText(About.EMAIL);
        ((TextView) findViewById(R.id.tvCopyright)).setText(About.COPYRIGHT);

        findViewById(R.id.btnCopyEmail).setOnClickListener(v ->
                copy(getString(R.string.label_email_short), About.EMAIL));

        ImageView qrQQ = findViewById(R.id.qrQQ);
        qrQQ.setImageResource(R.drawable.qr_qq);
        qrQQ.setOnClickListener(v -> showLarge(R.drawable.qr_qq,
                getString(R.string.about_qq_title)));

        qrDonate = findViewById(R.id.qrDonate);
        tabWechat = findViewById(R.id.tabWechat);
        tabAlipay = findViewById(R.id.tabAlipay);
        tabWechat.setOnClickListener(v -> selectDonate(0));
        tabAlipay.setOnClickListener(v -> selectDonate(1));
        qrDonate.setOnClickListener(v -> showDonateLarge());
        applyDonateTab();

        findViewById(R.id.btnLicense).setOnClickListener(v -> showLicense());

        findViewById(R.id.btnSource).setOnClickListener(v -> {
            Intent it = new Intent(Intent.ACTION_VIEW, Uri.parse(About.REPO_URL));
            try {
                startActivity(it);
            } catch (Exception e) {
                copy(getString(R.string.label_repo), About.REPO_URL);
                Ui.toast(this, getString(R.string.toast_no_browser_repo_copied));
            }
        });
    }

    private void copy(String label, String text) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText(label, text));
        Ui.toast(this, getString(R.string.toast_copied_fmt, label, text));
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
                .setPositiveButton(getString(R.string.btn_close), null)
                .show();
    }

    /** 切换微信 / 支付宝赞赏码：卡片与放大弹窗（若开着）同步 */
    private void selectDonate(int tab) {
        if (donateTab == tab) return;
        donateTab = tab;
        applyDonateTab();
    }

    private void applyDonateTab() {
        qrDonate.setImageResource(DONATE_QRS[donateTab]);
        styleTab(tabWechat, donateTab == 0);
        styleTab(tabAlipay, donateTab == 1);
        if (qrDonateLarge != null) {
            qrDonateLarge.setImageResource(DONATE_QRS[donateTab]);
            styleTab(tabWechatLarge, donateTab == 0);
            styleTab(tabAlipayLarge, donateTab == 1);
        }
    }

    /** tab 胶囊样式：选中 accent 底白字，未选中与普通胶囊按钮同款 */
    private void styleTab(TextView t, boolean selected) {
        t.setBackgroundResource(selected
                ? R.drawable.bg_seg_selected : R.drawable.bg_seg_unselected);
        t.setTextColor(Ui.color(this, selected ? R.attr.onAccent : R.attr.chipText));
    }

    private TextView largeTab(String label, int tab) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextSize(14);
        t.setGravity(Gravity.CENTER);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setOnClickListener(v -> selectDonate(tab));
        return t;
    }

    /** 全屏放大查看赞赏码，弹窗内可直接切换微信 / 支付宝 */
    private void showDonateLarge() {
        int pad = Ui.dp(this, 24);

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabWechatLarge = largeTab(getString(R.string.donate_tab_wechat), 0);
        tabAlipayLarge = largeTab(getString(R.string.donate_tab_alipay), 1);
        tabs.addView(tabWechatLarge, new LinearLayout.LayoutParams(0, Ui.dp(this, 38), 1));
        LinearLayout.LayoutParams lpAlipay = new LinearLayout.LayoutParams(0, Ui.dp(this, 38), 1);
        lpAlipay.leftMargin = Ui.dp(this, 8);
        tabs.addView(tabAlipayLarge, lpAlipay);

        qrDonateLarge = new ImageView(this);
        qrDonateLarge.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams lpQr = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        lpQr.topMargin = Ui.dp(this, 16);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setPadding(pad, pad, pad, pad);
        box.addView(tabs, new LinearLayout.LayoutParams(Ui.dp(this, 240),
                ViewGroup.LayoutParams.WRAP_CONTENT));
        box.addView(qrDonateLarge, lpQr);

        FrameLayout wrap = new FrameLayout(this);
        wrap.setBackgroundColor(Color.BLACK);
        wrap.addView(box, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.about_donate_title))
                .setView(wrap)
                .setPositiveButton(getString(R.string.btn_close), null)
                .setOnDismissListener(d -> {
                    qrDonateLarge = null;
                    tabWechatLarge = null;
                    tabAlipayLarge = null;
                })
                .show();
        applyDonateTab();
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
        tv.setTextColor(Ui.color(this, R.attr.onSurface));
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
                .setPositiveButton(getString(R.string.btn_close), null)
                .show();
    }
}
