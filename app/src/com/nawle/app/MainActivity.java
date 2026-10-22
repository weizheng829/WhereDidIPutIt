package com.nawle.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends Activity {

    private GridView grid;
    private SpaceAdapter adapter;
    private final List<Store.Node> spaces = new ArrayList<>();
    private final Map<Long, Bitmap> thumbCache = new HashMap<>();
    private Store store;
    private int themeSeqApplied;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(Ui.wrapTheme(base));
        themeSeqApplied = Ui.themeSeq();
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        store = Store.get(this);
        seedIfNeeded();

        grid = findViewById(R.id.grid);
        adapter = new SpaceAdapter();
        grid.setAdapter(adapter);
        grid.setOnItemClickListener((p, v, pos, id) -> {
            Store.Node n = spaces.get(pos);
            startActivity(new Intent(this, NodeActivity.class)
                    .putExtra(NodeActivity.EXTRA_NODE_ID, n.id));
        });
        grid.setOnItemLongClickListener((p, v, pos, id) -> {
            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            showSpaceMenu(spaces.get(pos));
            return true;
        });
        grid.setEmptyView(findViewById(R.id.empty));

        findViewById(R.id.fab).setOnClickListener(v ->
                Ui.promptText(this, "新建收纳空间", "例如：卧室、阳台、储物间", "", false, name -> {
                    store.createNode(0, name);
                    Ui.toast(this, "已创建「" + name + "」，点开它添加照片吧");
                    reload();
                }));

        findViewById(R.id.btnSidebar).setOnClickListener(v -> Sidebar.show(this));
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        Backup.handleResult(this, req, res, data);
    }

    private void seedIfNeeded() {
        SharedPreferences sp = getSharedPreferences("app", MODE_PRIVATE);
        if (!sp.getBoolean("seeded", false)) {
            String[] names = {"卧室", "客厅", "阳台", "办公室"};
            for (String n : names) store.createNode(0, n);
            sp.edit().putBoolean("seeded", true).apply();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (themeSeqApplied != Ui.themeSeq()) recreate();
        reload();
    }

    private void reload() {
        spaces.clear();
        spaces.addAll(store.children(0));
        adapter.notifyDataSetChanged();
    }

    private void showSpaceMenu(Store.Node n) {
        new AlertDialog.Builder(this)
                .setTitle(n.name)
                .setItems(new String[]{"重命名", "删除（含全部子层级和照片）"}, (d, w) -> {
                    if (w == 0) {
                        Ui.promptText(this, "重命名空间", "", n.name, false, s -> {
                            store.renameNode(n.id, s);
                            reload();
                        });
                    } else {
                        Ui.confirm(this, "删除空间",
                                "将删除「" + n.name + "」及其所有子层级、照片，不可恢复。",
                                "删除", () -> {
                                    store.deleteNodeDeep(n.id);
                                    reload();
                                });
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    class SpaceAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return spaces.size();
        }

        @Override
        public Object getItem(int i) {
            return spaces.get(i);
        }

        @Override
        public long getItemId(int i) {
            return spaces.get(i).id;
        }

        @Override
        public View getView(int i, View cv, ViewGroup parent) {
            View v = cv;
            if (v == null) {
                v = LayoutInflater.from(MainActivity.this).inflate(R.layout.item_space, parent, false);
                View card = v.findViewById(R.id.cardRoot);
                card.setClipToOutline(true);
                card.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
            }
            Store.Node n = spaces.get(i);
            TextView name = v.findViewById(R.id.name);
            TextView sub = v.findViewById(R.id.sub);
            ImageView cover = v.findViewById(R.id.cover);
            TextView letter = v.findViewById(R.id.letter);

            name.setText(n.name);
            List<Store.Pic> pics = store.images(n.id);
            int kids = store.countChildren(n.id);
            StringBuilder sb = new StringBuilder();
            if (kids > 0) sb.append(kids).append(" 个收纳位 · ");
            sb.append(pics.isEmpty() ? "还没照片" : pics.size() + " 张照片");
            if (n.note != null && !n.note.trim().isEmpty()) sb.append(" · 备");
            sub.setText(sb.toString());

            Bitmap bmp = null;
            if (!pics.isEmpty()) {
                File tf = store.thumbFile(pics.get(0).thumb);
                if (tf.exists()) {
                    bmp = thumbCache.get(n.id);
                    if (bmp == null) {
                        bmp = Store.decodeScaled(tf, 260);
                        if (bmp != null) thumbCache.put(n.id, bmp);
                    }
                }
            }
            if (bmp != null) {
                cover.setImageBitmap(bmp);
                cover.setVisibility(View.VISIBLE);
                letter.setVisibility(View.GONE);
            } else {
                cover.setImageDrawable(null);
                cover.setVisibility(View.GONE);
                letter.setVisibility(View.VISIBLE);
                letter.setText(n.name.substring(0, 1));
                letter.setBackgroundColor(Ui.tileColor(n.id));
            }
            return v;
        }
    }
}
