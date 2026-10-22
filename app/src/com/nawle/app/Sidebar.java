package com.nawle.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
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

    private static final String[] SEED_SPACES = {"卧室", "客厅", "阳台", "办公室"};

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
        ver.setText("v" + About.versionName(c) + " · 完全离线 · 无需权限");
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
        p.addView(item(c, "设置", "外观 / 恢复预置空间 / 清空数据",
                R.drawable.ic_settings, R.color.iconSettings, () -> {
            dismissPanel(p);
            showSettings(act);
        }));
        p.addView(item(c, "关于与联系", "作者 · QQ群 · 赞助 · 开源协议",
                R.drawable.ic_info, R.color.iconAbout, () -> {
            dismissPanel(p);
            act.startActivity(new Intent(act, AboutActivity.class));
        }));

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
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(0, Ui.dp(c, 20), 1f);
        alp.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
        arrow.setLayoutParams(alp);
        row.addView(arrow);

        row.setOnClickListener(v -> r.run());
        return row;
    }

    // ---------------- 设置 ----------------

    private static final String[] APPEARANCE = {"跟随系统", "浅色", "深色"};

    private static void showSettings(final Activity act) {
        new AlertDialog.Builder(act)
                .setTitle("设置")
                .setItems(new String[]{
                        "外观（当前：" + APPEARANCE[Ui.themePref(act)] + "）",
                        "恢复预置空间（补回 卧室/客厅/阳台/办公室）",
                        "清空全部数据并恢复初始状态"}, (d, w) -> {
                    if (w == 0) showAppearance(act);
                    else if (w == 1) restoreSeeds(act);
                    else wipeAll(act);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private static void showAppearance(final Activity act) {
        final int cur = Ui.themePref(act);
        new AlertDialog.Builder(act)
                .setTitle("外观")
                .setSingleChoiceItems(APPEARANCE, cur, (d, w) -> {
                    d.dismiss();
                    if (w != cur) {
                        Ui.setThemePref(act, w);
                        act.recreate();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private static void restoreSeeds(Activity act) {
        Store st = Store.get(act);
        int added = 0;
        for (String n : SEED_SPACES) {
            boolean exists = false;
            for (Store.Node top : st.children(0)) {
                if (top.name.equals(n)) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                st.createNode(0, n);
                added++;
            }
        }
        Ui.toast(act, added == 0 ? "预置空间都在，无需恢复" : "已补回 " + added + " 个预置空间");
    }

    private static void wipeAll(final Activity act) {
        Ui.confirm(act, "清空全部数据",
                "将删除所有空间、层级、照片和交互点，并恢复到初始状态。此操作不可撤销！",
                "继续", () -> Ui.confirm(act, "最后确认",
                        "真的要清空全部数据吗？建议先「导出备份」留存一份。",
                        "确认清空", () -> {
                            Store st = Store.get(act);
                            st.wipeAll();
                            for (String n : SEED_SPACES) st.createNode(0, n);
                            Ui.toast(act, "已恢复初始状态");
                            Intent it = new Intent(act, MainActivity.class);
                            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            act.startActivity(it);
                        }));
    }
}
