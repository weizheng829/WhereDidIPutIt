package com.nawle.app;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
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
        dim.setBackgroundColor(act.getResources().getColor(R.color.scrim));
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
        p.setBackgroundColor(c.getResources().getColor(R.color.cardBg));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                Ui.dp(c, 300), ViewGroup.LayoutParams.MATCH_PARENT);
        p.setLayoutParams(lp);

        // 头部
        LinearLayout head = new LinearLayout(c);
        head.setOrientation(LinearLayout.VERTICAL);
        head.setBackgroundColor(c.getResources().getColor(R.color.headerBg));
        head.setPadding(Ui.dp(c, 20), Ui.dp(c, 30), Ui.dp(c, 20), Ui.dp(c, 22));
        TextView app = new TextView(c);
        app.setText(R.string.app_name);
        app.setTextColor(c.getResources().getColor(R.color.onHeader));
        app.setTextSize(22);
        app.setTypeface(Typeface.DEFAULT_BOLD);
        TextView slogan = new TextView(c);
        slogan.setText(R.string.app_slogan);
        slogan.setTextColor(c.getResources().getColor(R.color.onHeaderDim));
        slogan.setTextSize(12);
        slogan.setPadding(0, Ui.dp(c, 4), 0, 0);
        TextView ver = new TextView(c);
        ver.setText("v" + About.versionName(c) + " · 不联网 · 无需权限");
        ver.setTextColor(c.getResources().getColor(R.color.onHeaderFaint));
        ver.setTextSize(11);
        ver.setPadding(0, Ui.dp(c, 6), 0, 0);
        head.addView(app);
        head.addView(slogan);
        head.addView(ver);
        p.addView(head);

        // 菜单项
        p.addView(item(c, "备份与恢复", "导出 zip / 换手机时导入",
                R.drawable.ic_backup, R.color.iconBackup, () -> {
            dismissPanel(p);
            Backup.showMenu(act);
        }));
        p.addView(item(c, "获取新版本", "浏览器打开 GitHub 发布页",
                R.drawable.ic_open, R.color.iconUpdate, () -> {
            dismissPanel(p);
            String url = About.REPO_URL + "/releases";
            try {
                act.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            } catch (Exception e) {
                copyLink(act, url);
            }
        }));
        p.addView(item(c, "关于与联系", "作者 · QQ群 · 赞助",
                R.drawable.ic_info, R.color.iconAbout, () -> {
            dismissPanel(p);
            act.startActivity(new Intent(act, AboutActivity.class));
        }));

        // 外观快捷切换（深浅色直达，无需进设置）
        View dv = new View(c);
        dv.setBackgroundColor(c.getResources().getColor(R.color.divider));
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(c, 1));
        dlp.leftMargin = Ui.dp(c, 18);
        dlp.rightMargin = Ui.dp(c, 18);
        dv.setLayoutParams(dlp);
        p.addView(dv);
        p.addView(appearanceSwitch(act));

        // 底部版权
        TextView foot = new TextView(c);
        foot.setText(About.COPYRIGHT);
        foot.setTextColor(c.getResources().getColor(R.color.onSurfaceVariant));
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
        t.setTextColor(c.getResources().getColor(R.color.onSurface));
        t.setTextSize(16);
        TextView s = new TextView(c);
        s.setText(sub);
        s.setTextColor(c.getResources().getColor(R.color.onSurfaceVariant));
        s.setTextSize(12);
        s.setPadding(0, Ui.dp(c, 2), 0, 0);
        txt.addView(t);
        txt.addView(s);
        row.addView(txt);

        ImageView arrow = new ImageView(c);
        arrow.setImageResource(R.drawable.ic_chevron_right);
        arrow.setImageTintList(ColorStateList.valueOf(c.getResources().getColor(R.color.arrowDim)));
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
        cm.setPrimaryClip(ClipData.newPlainText("发布页链接", url));
        Ui.toast(act, "没有浏览器，链接已复制");
    }

    // ---------------- 外观 ----------------

    private static final String[] APPEARANCE = {"跟随系统", "浅色", "深色"};
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
        label.setText("外观");
        label.setTextColor(c.getResources().getColor(R.color.onSurfaceVariant));
        label.setTextSize(12);
        label.setPadding(Ui.dp(c, 2), 0, 0, Ui.dp(c, 8));
        box.addView(label);

        LinearLayout seg = new LinearLayout(c);
        seg.setOrientation(LinearLayout.HORIZONTAL);
        GradientDrawable track = new GradientDrawable();
        track.setColor(c.getResources().getColor(R.color.surface));
        track.setCornerRadius(Ui.dp(c, 14));
        track.setStroke(Ui.dp(c, 1), c.getResources().getColor(R.color.outlineVariant));
        seg.setBackground(track);
        seg.setPadding(Ui.dp(c, 3), Ui.dp(c, 3), Ui.dp(c, 3), Ui.dp(c, 3));

        final int cur = Ui.themePref(c);
        for (int i = 0; i < APPEARANCE.length; i++) {
            final int which = i;
            boolean on = i == cur;

            LinearLayout s = new LinearLayout(c);
            s.setOrientation(LinearLayout.VERTICAL);
            s.setGravity(Gravity.CENTER);
            s.setPadding(0, Ui.dp(c, 7), 0, Ui.dp(c, 7));
            GradientDrawable pill = new GradientDrawable();
            pill.setCornerRadius(Ui.dp(c, 11));
            pill.setColor(on ? c.getResources().getColor(R.color.primary) : Color.TRANSPARENT);
            s.setBackground(pill);

            ImageView ic = new ImageView(c);
            ic.setImageResource(APPEARANCE_ICONS[i]);
            ic.setImageTintList(ColorStateList.valueOf(c.getResources().getColor(
                    on ? R.color.onPrimary : R.color.onSurfaceVariant)));
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(Ui.dp(c, 15), Ui.dp(c, 15));
            ilp.gravity = Gravity.CENTER_HORIZONTAL;
            ilp.bottomMargin = Ui.dp(c, 4);
            s.addView(ic, ilp);

            TextView t = new TextView(c);
            t.setText(APPEARANCE[i]);
            t.setTextSize(11.5f);
            t.setTypeface(on ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            t.setTextColor(c.getResources().getColor(
                    on ? R.color.onPrimary : R.color.onSurfaceVariant));
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
