package com.nawle.app;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.net.Uri;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 侧边栏：叠加在 Activity 内容视图上的左侧滑出面板。
 * 纯框架 API 实现（无依赖），首页右上角 ☰ 与层级页菜单均可唤起。
 */
public final class Sidebar {
    private Sidebar() {}

    public static void show(final Activity act) {
        final FrameLayout overlay = new FrameLayout(act);
        overlay.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        final LinearLayout panel = panel(act);
        final View dim = new View(act);
        dim.setBackgroundColor(Ui.color(act, R.attr.scrim));
        dim.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        dim.setOnClickListener(v -> dismissPanel(panel));
        overlay.addView(dim);
        overlay.addView(panel);

        ((ViewGroup) act.findViewById(android.R.id.content)).addView(overlay);
        overlay.bringToFront();
        overlay.bringChildToFront(panel);

        // 滑入动画
        final int w = Ui.dp(act, 300);
        panel.setTranslationX(-w);
        dim.setAlpha(0);
        panel.post(() -> {
            panel.animate().translationX(0).setDuration(220).start();
            dim.animate().alpha(1).setDuration(220).start();
        });

        // 返回键关闭
        panel.setFocusableInTouchMode(true);
        panel.requestFocus();
        panel.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
                dismissPanel(panel);
                return true;
            }
            return false;
        });
    }

    private static void dismissPanel(final View panel) {
        final ViewGroup overlay = (ViewGroup) panel.getParent();
        final View dim = overlay.getChildAt(0);
        int w = Ui.dp(panel.getContext(), 300);
        panel.animate().translationX(-w).setDuration(200).start();
        dim.animate().alpha(0).setDuration(200)
                .withEndAction(() -> ((ViewGroup) overlay.getParent()).removeView(overlay))
                .start();
    }

    private static LinearLayout panel(final Activity act) {
        Context c = act;
        LinearLayout p = new LinearLayout(c);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setBackgroundColor(Ui.color(c, R.attr.cardBg));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                Ui.dp(c, 300), ViewGroup.LayoutParams.MATCH_PARENT);
        p.setLayoutParams(lp);

        // 头部
        LinearLayout head = new LinearLayout(c);
        head.setOrientation(LinearLayout.VERTICAL);
        head.setBackgroundColor(Ui.color(c, R.attr.headerBg));
        head.setPadding(Ui.dp(c, 20), Ui.dp(c, 30), Ui.dp(c, 20), Ui.dp(c, 22));
        TextView app = new TextView(c);
        app.setText(R.string.app_name);
        app.setTextColor(Ui.color(c, R.attr.onHeader));
        app.setTextSize(22);
        app.setTypeface(Typeface.DEFAULT_BOLD);
        TextView slogan = new TextView(c);
        slogan.setText(R.string.app_slogan);
        slogan.setTextColor(Ui.color(c, R.attr.onHeaderDim));
        slogan.setTextSize(12);
        slogan.setPadding(0, Ui.dp(c, 4), 0, 0);
        TextView ver = new TextView(c);
        ver.setText(c.getString(R.string.sidebar_ver_fmt, About.versionName(c)));
        ver.setTextColor(Ui.color(c, R.attr.onHeaderFaint));
        ver.setTextSize(11);
        ver.setPadding(0, Ui.dp(c, 6), 0, 0);
        head.addView(app);
        head.addView(slogan);
        head.addView(ver);
        p.addView(head);

        // 菜单项
        p.addView(item(c, c.getString(R.string.menu_backup), c.getString(R.string.menu_backup_sub),
                R.drawable.ic_backup, R.color.iconBackup, () -> {
            dismissPanel(p);
            Backup.showMenu(act);
        }));
        p.addView(item(c, c.getString(R.string.menu_update), c.getString(R.string.menu_update_sub),
                R.drawable.ic_open, R.color.iconUpdate, () -> {
            dismissPanel(p);
            String url = About.REPO_URL + "/releases";
            try {
                act.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            } catch (Exception e) {
                copyLink(act, url);
            }
        }));
        // 语言：弹窗选择 跟随系统 / 中文 / English；标题双语固定显示，副标题附当前选项
        p.addView(item(c, c.getString(R.string.menu_language),
                c.getString(R.string.menu_language_en) + " · " + langLabel(c),
                R.drawable.ic_lang, R.color.iconLanguage, () -> showLangPicker(act)));
        // 主题：弹窗选择配色方案（芒果橙 / 经典青 / 烧橙 / 琥珀 / 陶土红 / 可可棕）
        p.addView(item(c, c.getString(R.string.menu_theme), paletteLabel(c),
                R.drawable.ic_palette, R.color.iconAppearance, () -> showThemePicker(act)));
        p.addView(item(c, c.getString(R.string.menu_about), c.getString(R.string.menu_about_sub),
                R.drawable.ic_info, R.color.iconAbout, () -> {
            dismissPanel(p);
            act.startActivity(new Intent(act, AboutActivity.class));
        }));

        // 外观快捷切换（深浅色直达，无需进设置）
        p.addView(divider(c));
        p.addView(appearanceSwitch(act));

        // 底部版权
        TextView foot = new TextView(c);
        foot.setText(About.COPYRIGHT);
        foot.setTextColor(Ui.color(c, R.attr.onSurfaceVariant));
        foot.setTextSize(11);
        LinearLayout fl = new LinearLayout(c);
        fl.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        fl.setPadding(0, 0, 0, Ui.dp(c, 16));
        fl.addView(foot);
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        fl.setLayoutParams(flp);
        p.addView(fl);
        return p;
    }

    private static View item(Context c, String title, String sub, int iconRes, int iconColorRes,
                             final Runnable r) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int pad = Ui.dp(c, 18);
        row.setPadding(pad, Ui.dp(c, 14), pad, Ui.dp(c, 14));
        row.setBackgroundResource(R.drawable.bg_menu_item);

        ImageView ic = new ImageView(c);
        ic.setImageResource(iconRes);
        ic.setImageTintList(ColorStateList.valueOf(c.getResources().getColor(iconColorRes)));
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                Ui.dp(c, 24), Ui.dp(c, 24));
        ilp.rightMargin = Ui.dp(c, 14);
        ic.setLayoutParams(ilp);
        row.addView(ic);

        LinearLayout txt = new LinearLayout(c);
        txt.setOrientation(LinearLayout.VERTICAL);
        // 文字列占满剩余宽度，保证右侧箭头统一贴右对齐
        txt.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView t = new TextView(c);
        t.setText(title);
        t.setTextColor(Ui.color(c, R.attr.onSurface));
        t.setTextSize(16);
        TextView s = new TextView(c);
        s.setText(sub);
        s.setTextColor(Ui.color(c, R.attr.onSurfaceVariant));
        s.setTextSize(12);
        s.setPadding(0, Ui.dp(c, 2), 0, 0);
        txt.addView(t);
        txt.addView(s);
        row.addView(txt);

        ImageView arrow = new ImageView(c);
        arrow.setImageResource(R.drawable.ic_chevron_right);
        arrow.setImageTintList(ColorStateList.valueOf(Ui.color(c, R.attr.arrowDim)));
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(Ui.dp(c, 20), Ui.dp(c, 20));
        alp.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
        arrow.setLayoutParams(alp);
        row.addView(arrow);

        row.setOnClickListener(v -> r.run());
        return row;
    }

    /** 无浏览器时兜底：复制链接 */
    private static void copyLink(Activity act, String url) {
        ClipboardManager cm =
                (ClipboardManager) act.getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText(
                act.getString(R.string.clip_update_link), url));
        Ui.toast(act, act.getString(R.string.toast_no_browser_link_copied));
    }

    // ---------------- 快捷设置 ----------------

    /** 语言菜单项副标题：当前生效的选项（中文永远显示「中文」，English 永远显示「English」） */
    private static String langLabel(Context c) {
        switch (Ui.langPref(c)) {
            case Ui.LANG_ZH: return c.getString(R.string.lang_zh);
            case Ui.LANG_EN: return c.getString(R.string.lang_en);
            default: return c.getString(R.string.follow_system);
        }
    }

    /** 语言选择弹窗：底部操作面板三选一，当前项副标题标「当前」 */
    private static void showLangPicker(final Activity act) {
        Context c = act;
        final int cur = Ui.langPref(c);
        Ui.sheet(act, c.getString(R.string.menu_language),
                c.getString(R.string.menu_language_en))
                .item(c.getString(R.string.follow_system),
                        cur == Ui.LANG_SYSTEM ? c.getString(R.string.current_option) : null,
                        R.drawable.ic_auto, R.color.iconAppearance,
                        langAction(act, Ui.LANG_SYSTEM, cur))
                .item(c.getString(R.string.lang_zh),
                        cur == Ui.LANG_ZH ? c.getString(R.string.current_option) : null,
                        R.drawable.ic_lang, R.color.iconLanguage,
                        langAction(act, Ui.LANG_ZH, cur))
                .item(c.getString(R.string.lang_en),
                        cur == Ui.LANG_EN ? c.getString(R.string.current_option) : null,
                        R.drawable.ic_lang, R.color.iconLanguage,
                        langAction(act, Ui.LANG_EN, cur))
                .show();
    }

    /** 点当前语言仅关闭面板；选择其他语言立即切换 */
    private static Runnable langAction(final Activity act, final int lang, final int cur) {
        return lang == cur ? null : () -> {
            Ui.setLangPref(act, lang);
            act.recreate();
        };
    }

    // ---------------- 配色方案 ----------------

    /** 主题菜单项副标题：当前配色名称 */
    private static String paletteLabel(Context c) {
        return c.getString(Ui.PALETTE_NAMES[Ui.palettePref(c)]);
    }

    /** 主题选择弹窗：每个配色一行色板（主色块 + 深主色圆点），当前项标「当前」 */
    private static void showThemePicker(final Activity act) {
        Ui.Sheet sheet = Ui.sheet(act, act.getString(R.string.menu_theme),
                act.getString(R.string.menu_theme_sub));
        for (int i = 0; i < Ui.PALETTE_STYLES.length; i++) sheet.custom(themeRow(act, i));
        sheet.show();
    }

    private static View themeRow(final Activity act, final int index) {
        Context c = act;
        boolean on = index == Ui.palettePref(c);
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(c, 6), Ui.dp(c, 10), Ui.dp(c, 6), Ui.dp(c, 10));
        row.setBackgroundResource(R.drawable.bg_row);

        // 色板：主色圆角块 + 居中的深主色圆点
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(Ui.dp(c, 13));
        bg.setColor(Ui.PALETTE_SWATCH[index][0]);
        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        dot.setColor(Ui.PALETTE_SWATCH[index][1]);
        LayerDrawable swatch = new LayerDrawable(new Drawable[]{bg, dot});
        int in = Ui.dp(c, 13);
        swatch.setLayerInset(1, in, in, in, in);
        ImageView swView = new ImageView(c);
        swView.setImageDrawable(swatch);
        LinearLayout.LayoutParams swlp = new LinearLayout.LayoutParams(Ui.dp(c, 40), Ui.dp(c, 40));
        swlp.rightMargin = Ui.dp(c, 14);
        swView.setLayoutParams(swlp);
        row.addView(swView);

        LinearLayout txt = new LinearLayout(c);
        txt.setOrientation(LinearLayout.VERTICAL);
        TextView t = new TextView(c);
        t.setText(c.getString(Ui.PALETTE_NAMES[index]));
        t.setTextSize(15.5f);
        t.setTextColor(Ui.color(c, R.attr.onSurface));
        if (on) t.setTypeface(Typeface.DEFAULT_BOLD);
        txt.addView(t);
        if (on) {
            TextView s = new TextView(c);
            s.setText(c.getString(R.string.current_option));
            s.setTextSize(12);
            s.setTextColor(Ui.color(c, R.attr.onSurfaceVariant));
            s.setPadding(0, Ui.dp(c, 2), 0, 0);
            txt.addView(s);
        }
        row.addView(txt, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        row.setOnClickListener(v -> {
            if (index == Ui.palettePref(act)) return;
            Ui.setPalettePref(act, index);
            act.recreate();
        });
        return row;
    }

    private static View divider(Context c) {
        View dv = new View(c);
        dv.setBackgroundColor(Ui.color(c, R.attr.divider));
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(c, 1));
        dlp.leftMargin = Ui.dp(c, 18);
        dlp.rightMargin = Ui.dp(c, 18);
        dv.setLayoutParams(dlp);
        return dv;
    }

    // ---------------- 外观 ----------------

    private static final int[] APPEARANCE_ICONS =
            {R.drawable.ic_auto, R.drawable.ic_sun, R.drawable.ic_moon};

    /** 侧边栏外观分段切换：跟随系统 / 浅色 / 深色 */
    private static View appearanceSwitch(final Activity act) {
        Context c = act;
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        int hpad = Ui.dp(c, 18);
        box.setPadding(hpad, Ui.dp(c, 10), hpad, Ui.dp(c, 14));

        TextView label = new TextView(c);
        label.setText(c.getString(R.string.label_appearance));
        label.setTextColor(Ui.color(c, R.attr.onSurfaceVariant));
        label.setTextSize(12);
        label.setPadding(Ui.dp(c, 2), 0, 0, Ui.dp(c, 8));
        box.addView(label);

        LinearLayout seg = new LinearLayout(c);
        seg.setOrientation(LinearLayout.HORIZONTAL);
        GradientDrawable track = new GradientDrawable();
        track.setColor(Ui.color(c, R.attr.surface));
        track.setCornerRadius(Ui.dp(c, 14));
        track.setStroke(Ui.dp(c, 1), Ui.color(c, R.attr.outlineVariant));
        seg.setBackground(track);
        seg.setPadding(Ui.dp(c, 3), Ui.dp(c, 3), Ui.dp(c, 3), Ui.dp(c, 3));

        String[] names = {
                c.getString(R.string.follow_system),
                c.getString(R.string.appearance_light),
                c.getString(R.string.appearance_dark)};
        final int cur = Ui.themePref(c);
        for (int i = 0; i < names.length; i++) {
            final int which = i;
            boolean on = i == cur;

            LinearLayout s = new LinearLayout(c);
            s.setOrientation(LinearLayout.VERTICAL);
            s.setGravity(Gravity.CENTER);
            s.setPadding(0, Ui.dp(c, 7), 0, Ui.dp(c, 7));
            GradientDrawable pill = new GradientDrawable();
            pill.setCornerRadius(Ui.dp(c, 11));
            pill.setColor(on ? Ui.color(c, R.attr.primary) : Color.TRANSPARENT);
            s.setBackground(pill);

            ImageView ic = new ImageView(c);
            ic.setImageResource(APPEARANCE_ICONS[i]);
            ic.setImageTintList(ColorStateList.valueOf(on
                    ? Ui.color(c, R.attr.onPrimary) : Ui.color(c, R.attr.onSurfaceVariant)));
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(Ui.dp(c, 15), Ui.dp(c, 15));
            ilp.gravity = Gravity.CENTER_HORIZONTAL;
            ilp.bottomMargin = Ui.dp(c, 4);
            s.addView(ic, ilp);

            TextView t = new TextView(c);
            t.setText(names[i]);
            t.setTextSize(11.5f);
            t.setTypeface(on ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            t.setTextColor(on
                    ? Ui.color(c, R.attr.onPrimary) : Ui.color(c, R.attr.onSurfaceVariant));
            t.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            // 显式声明水平居中：部分机型垂直 LinearLayout 子项不继承容器的 setGravity，会退化为左对齐
            tlp.gravity = Gravity.CENTER_HORIZONTAL;
            s.addView(t, tlp);

            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (i > 0) slp.leftMargin = Ui.dp(c, 3);
            seg.addView(s, slp);

            s.setOnClickListener(v -> {
                if (which == Ui.themePref(act)) return;
                Ui.setThemePref(act, which);
                act.recreate();
            });
        }
        box.addView(seg);
        return box;
    }
}
