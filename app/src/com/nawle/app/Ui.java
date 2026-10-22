package com.nawle.app;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

public final class Ui {
    private Ui() {}

    // ---------------- 主题偏好（跟随系统 / 浅色 / 深色） ----------------

    public static final int THEME_SYSTEM = 0;
    public static final int THEME_LIGHT = 1;
    public static final int THEME_DARK = 2;

    /** 偏好变更序号：各 Activity 在 onResume 对比，不一致则 recreate 换肤 */
    private static volatile int themeSeq = 0;

    public static int themePref(Context c) {
        return c.getSharedPreferences("app", Context.MODE_PRIVATE).getInt("theme", THEME_SYSTEM);
    }

    public static void setThemePref(Context c, int mode) {
        c.getSharedPreferences("app", Context.MODE_PRIVATE).edit().putInt("theme", mode).apply();
        themeSeq++;
    }

    public static int themeSeq() {
        return themeSeq;
    }

    /**
     * 在 Activity.attachBaseContext 里调用：按用户偏好强制昼夜资源配置，
     * 使「深色」在不支持系统深色的旧系统上也能生效。
     */
    public static Context wrapTheme(Context base) {
        int mode = themePref(base);
        if (mode == THEME_SYSTEM) return base;
        Configuration cfg = new Configuration(base.getResources().getConfiguration());
        int night = mode == THEME_DARK
                ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO;
        if ((cfg.uiMode & Configuration.UI_MODE_NIGHT_MASK) == night) return base;
        cfg.uiMode = (cfg.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | night;
        return base.createConfigurationContext(cfg);
    }

    // ---------------- 通用工具 ----------------

    public static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    /** 跟随系统字体缩放的 sp 尺寸（用于画布绘制文字） */
    public static int sp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().scaledDensity);
    }

    public static void toast(Context c, String msg) {
        Toast.makeText(c.getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
    }

    public static interface TextCallback {
        void onOk(String text);
    }

    public static void promptText(Context c, String title, String hint, String initial,
                                  boolean multiline, final TextCallback cb) {
        final EditText et = new EditText(c);
        et.setHint(hint);
        et.setText(initial);
        if (initial != null) et.setSelection(initial.length());
        et.setTextSize(16);
        et.setInputType(multiline
                ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                : InputType.TYPE_CLASS_TEXT);
        int pad = dp(c, 20);
        FrameLayout wrap = new FrameLayout(c);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = pad;
        lp.rightMargin = pad;
        lp.topMargin = dp(c, 8);
        lp.bottomMargin = dp(c, 8);
        et.setLayoutParams(lp);
        wrap.addView(et);
        new AlertDialog.Builder(c)
                .setTitle(title)
                .setView(wrap)
                .setPositiveButton("确定", (d, w) -> {
                    String s = et.getText().toString().trim();
                    if (s.isEmpty()) {
                        toast(c, "内容不能为空");
                        return;
                    }
                    cb.onOk(s);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    public static void confirm(Context c, String title, String msg, String okLabel, Runnable onOk) {
        new AlertDialog.Builder(c)
                .setTitle(title)
                .setMessage(msg)
                .setPositiveButton(okLabel, (d, w) -> onOk.run())
                .setNegativeButton("取消", null)
                .show();
    }

    /** 替代已废弃的 ProgressDialog：不可取消的加载对话框（调用方 show/dismiss） */
    public static AlertDialog progress(Context c, String msg) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        int pad = dp(c, 24);
        box.setPadding(pad, pad, pad, pad);
        ProgressBar pb = new ProgressBar(c);
        box.addView(pb);
        TextView tv = new TextView(c);
        tv.setText(msg);
        tv.setTextSize(15);
        tv.setTextColor(c.getResources().getColor(R.color.onSurfaceVariant));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = dp(c, 20);
        box.addView(tv, lp);
        return new AlertDialog.Builder(c)
                .setView(box)
                .setCancelable(false)
                .create();
    }

    private static final int[] TILE_COLORS = {
            Color.rgb(0x4D, 0xB6, 0xAC), Color.rgb(0x7E, 0x9E, 0xE3),
            Color.rgb(0xFF, 0xB7, 0x4D), Color.rgb(0x81, 0xC7, 0x84),
            Color.rgb(0xF0, 0x62, 0x92), Color.rgb(0x95, 0x75, 0xCD),
            Color.rgb(0x4F, 0xC3, 0xF7), Color.rgb(0xFF, 0xD5, 0x4F),
    };

    public static int tileColor(long id) {
        return TILE_COLORS[(int) Math.abs(id % TILE_COLORS.length)];
    }
}
