package com.nawle.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

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
        et.setTextSize(15.5f);
        et.setTextColor(c.getResources().getColor(R.color.onSurface));
        et.setInputType(multiline
                ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                : InputType.TYPE_CLASS_TEXT);
        et.setBackgroundResource(R.drawable.bg_input);
        et.setPadding(dp(c, 14), dp(c, 12), dp(c, 14), dp(c, 12));
        if (multiline) {
            et.setMinLines(4);
            et.setGravity(Gravity.TOP);
        }
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
        // 空内容时保持弹窗打开（默认按钮监听会在点击后无条件关闭）
        final AlertDialog dlg = new AlertDialog.Builder(c)
                .setTitle(title)
                .setView(wrap)
                .setPositiveButton("确定", null)
                .setNegativeButton("取消", null)
                .create();
        dlg.setOnShowListener(d -> ((AlertDialog) d).getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String s = et.getText().toString().trim();
                    if (s.isEmpty()) {
                        toast(c, "内容不能为空");
                        return;
                    }
                    dlg.dismiss();
                    cb.onOk(s);
                }));
        dlg.show();
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

    /** 首字母色块用：空名兜底为「·」，避免 substring 越界 */
    public static String firstChar(String s) {
        if (s == null) return "·";
        s = s.trim();
        return s.isEmpty() ? "·" : s.substring(0, 1);
    }

    // ---------------- 底部操作面板 ----------------

    public static Sheet sheet(Activity act, String title, String subtitle) {
        return new Sheet(act, title, subtitle);
    }

    /**
     * 底部滑出的操作面板：长按菜单、添加照片、子项列表等复用。
     * 自绘视图叠加在 Activity 内容上（与侧边栏同机制），点暗区或返回键关闭。
     * 行样式与设置弹窗一致：彩色圆角图标块 + 标题/副标题，危险操作红色。
     */
    public static final class Sheet {

        private final Activity act;
        private final String title, sub;
        private final List<Row> rows = new ArrayList<>();

        private static final class Row {
            String label, sub;
            int iconRes;   // 0 = 首字母色块
            int color;     // ARGB
            int tileBg;    // 0 = color 同色 15% 透明底
            boolean danger;
            Runnable run;
        }

        private Sheet(Activity act, String title, String sub) {
            this.act = act;
            this.title = title;
            this.sub = sub;
        }

        public Sheet item(String label, int iconRes, int colorRes, Runnable r) {
            return item(label, null, iconRes, colorRes, r);
        }

        public Sheet item(String label, String sub, int iconRes, int colorRes, Runnable r) {
            Row w = new Row();
            w.label = label;
            w.sub = sub;
            w.iconRes = iconRes;
            w.color = act.getResources().getColor(colorRes);
            w.run = r;
            rows.add(w);
            return this;
        }

        /** 危险操作：红色图标与文字，必须带说明性 sub */
        public Sheet danger(String label, String sub, Runnable r) {
            Row w = new Row();
            w.label = label;
            w.sub = sub;
            w.iconRes = R.drawable.ic_delete;
            w.color = act.getResources().getColor(R.color.iconDanger);
            w.danger = true;
            w.run = r;
            rows.add(w);
            return this;
        }

        /** 首字母色块行（子项列表用，色块颜色与首页格子一致） */
        public Sheet child(String label, String sub, int tileColor, Runnable r) {
            Row w = new Row();
            w.label = label;
            w.sub = sub;
            w.iconRes = 0;
            w.tileBg = tileColor;
            w.run = r;
            rows.add(w);
            return this;
        }

        public void show() {
            final Context c = act;
            final FrameLayout overlay = new FrameLayout(c);
            overlay.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            final LinearLayout sheet = new LinearLayout(c);
            sheet.setOrientation(LinearLayout.VERTICAL);
            sheet.setBackgroundResource(R.drawable.bg_sheet);
            sheet.setElevation(dp(c, 16));
            sheet.setClickable(true); // 面板空白处消费触摸：避免穿透，也不会误触关闭
            sheet.setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM));

            // 顶部把手
            View handle = new View(c);
            GradientDrawable hd = new GradientDrawable();
            hd.setCornerRadius(dp(c, 2));
            hd.setColor(c.getResources().getColor(R.color.sheetHandle));
            handle.setBackground(hd);
            LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(dp(c, 36), dp(c, 4));
            hlp.gravity = Gravity.CENTER_HORIZONTAL;
            hlp.topMargin = dp(c, 10);
            sheet.addView(handle, hlp);

            if (title != null) {
                TextView t = new TextView(c);
                t.setText(title);
                t.setTextSize(16.5f);
                t.setTypeface(Typeface.DEFAULT_BOLD);
                t.setTextColor(c.getResources().getColor(R.color.onSurface));
                t.setGravity(Gravity.CENTER);
                t.setPadding(dp(c, 24), 0, dp(c, 24), 0);
                t.setMaxLines(1);
                t.setEllipsize(TextUtils.TruncateAt.END);
                LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                tlp.topMargin = dp(c, 14);
                sheet.addView(t, tlp);

                if (sub != null) {
                    TextView s = new TextView(c);
                    s.setText(sub);
                    s.setTextSize(12.5f);
                    s.setTextColor(c.getResources().getColor(R.color.onSurfaceVariant));
                    s.setGravity(Gravity.CENTER);
                    s.setPadding(dp(c, 24), 0, dp(c, 24), 0);
                    LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    slp.topMargin = dp(c, 3);
                    sheet.addView(s, slp);
                }
            }

            final View dim = new View(c);
            dim.setBackgroundColor(c.getResources().getColor(R.color.scrim));
            dim.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            // 关闭：面板滑下、暗层淡出后移除整个叠加层
            final Runnable dismiss = () -> {
                sheet.animate().translationY(sheet.getHeight() + dp(c, 32))
                        .setDuration(200).start();
                dim.animate().alpha(0).setDuration(200)
                        .withEndAction(() -> {
                            ViewGroup p = (ViewGroup) overlay.getParent();
                            if (p != null) p.removeView(overlay);
                        }).start();
            };
            // 暗层可点：关闭面板，同时消费触摸事件避免穿透到下层内容
            dim.setOnClickListener(v -> dismiss.run());

            // 选项行（过多时面板内部滚动）
            LinearLayout list = new LinearLayout(c);
            list.setOrientation(LinearLayout.VERTICAL);
            int hpad = dp(c, 10);
            list.setPadding(hpad, dp(c, 10), hpad, dp(c, 4));
            for (int i = 0; i < rows.size(); i++) {
                list.addView(rowView(c, rows.get(i), dismiss));
            }
            CappedScrollView scroller = new CappedScrollView(c,
                    c.getResources().getDisplayMetrics().heightPixels * 5 / 8);
            scroller.setVerticalScrollBarEnabled(false);
            scroller.addView(list, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            sheet.addView(scroller, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            // 底部取消
            View dv = new View(c);
            dv.setBackgroundColor(c.getResources().getColor(R.color.divider));
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(c, 1));
            dlp.leftMargin = dp(c, 18);
            dlp.rightMargin = dp(c, 18);
            dlp.topMargin = dp(c, 8);
            sheet.addView(dv, dlp);

            final TextView cancel = new TextView(c);
            cancel.setText("取消");
            cancel.setTextSize(15);
            cancel.setTypeface(Typeface.DEFAULT_BOLD);
            cancel.setTextColor(c.getResources().getColor(R.color.primary));
            cancel.setGravity(Gravity.CENTER);
            cancel.setPadding(0, dp(c, 15), 0, dp(c, 20));
            cancel.setBackgroundResource(R.drawable.bg_row);
            cancel.setOnClickListener(v -> dismiss.run());
            sheet.addView(cancel, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            // 手势导航设备上给取消键留出底部安全距离（insets 已被消费时无影响）
            sheet.setOnApplyWindowInsetsListener((v, ins) -> {
                int bottom = ins.getSystemWindowInsetBottom();
                if (bottom > 0) cancel.setPadding(0, dp(c, 15), 0, dp(c, 20) + bottom);
                return ins;
            });

            overlay.addView(dim);
            overlay.addView(sheet);
            ViewGroup content = (ViewGroup) act.findViewById(android.R.id.content);
            content.addView(overlay);
            overlay.bringToFront();

            // 滑入动画（先置于屏外，避免首帧闪现）
            sheet.setTranslationY(c.getResources().getDisplayMetrics().heightPixels + dp(c, 32));
            dim.setAlpha(0);
            sheet.post(() -> {
                sheet.animate().translationY(0).setDuration(240).start();
                dim.animate().alpha(1).setDuration(240).start();
            });

            // 返回键关闭
            sheet.setFocusableInTouchMode(true);
            sheet.requestFocus();
            sheet.setOnKeyListener((v, keyCode, event) -> {
                if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
                    dismiss.run();
                    return true;
                }
                return false;
            });
        }

        private View rowView(Context c, Row r, final Runnable onPick) {
            LinearLayout row = new LinearLayout(c);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(c, 6), dp(c, 10), dp(c, 6), dp(c, 10));
            row.setBackgroundResource(R.drawable.bg_row);

            int tileColor = r.tileBg != 0 ? r.tileBg : (r.color & 0x00FFFFFF) | 0x26000000;
            GradientDrawable tile = new GradientDrawable();
            tile.setShape(GradientDrawable.RECTANGLE);
            tile.setCornerRadius(dp(c, 13));
            tile.setColor(tileColor);

            if (r.iconRes != 0) {
                ImageView ic = new ImageView(c);
                ic.setImageResource(r.iconRes);
                ic.setImageTintList(ColorStateList.valueOf(r.color));
                ic.setBackground(tile);
                ic.setPadding(dp(c, 9), dp(c, 9), dp(c, 9), dp(c, 9));
                LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(c, 40), dp(c, 40));
                ilp.rightMargin = dp(c, 14);
                row.addView(ic, ilp);
            } else {
                TextView letter = new TextView(c);
                letter.setText(firstChar(r.label));
                letter.setTextSize(19);
                letter.setTypeface(Typeface.DEFAULT_BOLD);
                letter.setTextColor(c.getResources().getColor(R.color.tileLetter));
                letter.setGravity(Gravity.CENTER);
                letter.setBackground(tile);
                LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(c, 40), dp(c, 40));
                ilp.rightMargin = dp(c, 14);
                row.addView(letter, ilp);
            }

            LinearLayout txt = new LinearLayout(c);
            txt.setOrientation(LinearLayout.VERTICAL);
            TextView label = new TextView(c);
            label.setText(r.label);
            label.setTextSize(15.5f);
            label.setTextColor(c.getResources().getColor(
                    r.danger ? R.color.iconDanger : R.color.onSurface));
            if (r.danger) label.setTypeface(Typeface.DEFAULT_BOLD);
            txt.addView(label);
            if (r.sub != null) {
                TextView s = new TextView(c);
                s.setText(r.sub);
                s.setTextSize(12);
                s.setTextColor(c.getResources().getColor(R.color.onSurfaceVariant));
                s.setPadding(0, dp(c, 2), 0, 0);
                txt.addView(s);
            }
            row.addView(txt, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            row.setOnClickListener(v -> {
                onPick.run();
                if (r.run != null) r.run.run();
            });
            return row;
        }
    }

    /** 高度不超过 maxPx 的 ScrollView：选项过多时操作面板内部滚动 */
    private static final class CappedScrollView extends ScrollView {
        private final int maxPx;

        CappedScrollView(Context c, int maxPx) {
            super(c);
            this.maxPx = maxPx;
        }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            super.onMeasure(widthSpec,
                    View.MeasureSpec.makeMeasureSpec(maxPx, View.MeasureSpec.AT_MOST));
        }
    }
}
