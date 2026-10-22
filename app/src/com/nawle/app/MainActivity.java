package com.nawle.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MainActivity extends Activity {

    private GridView grid;
    private SpaceAdapter adapter;
    private final List<Store.Node> spaces = new ArrayList<>();
    private final Map<Long, Bitmap> thumbCache = new HashMap<>();
    private Store store;
    private int themeSeqApplied;

    /** 批量选择删除：右上角「全选」勾选后进入选择态 */
    private final Set<Long> selected = new HashSet<>();
    private TextView tvTitle, btnSelDelete;
    private CheckBox cbSelectAll;
    private View fab, selBar;

    /** 名称搜索：输入即时出结果，点结果直达该层级 */
    private EditText etSearch;
    private View searchPanel;
    private ListView searchList;
    private final List<Store.Node> searchResults = new ArrayList<>();
    private final List<String> searchLocs = new ArrayList<>();

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

        tvTitle = findViewById(R.id.tvTitle);
        cbSelectAll = findViewById(R.id.cbSelectAll);
        selBar = findViewById(R.id.selBar);
        btnSelDelete = findViewById(R.id.btnSelDelete);
        fab = findViewById(R.id.fab);

        grid = findViewById(R.id.grid);
        adapter = new SpaceAdapter();
        grid.setAdapter(adapter);
        grid.setOnItemClickListener((p, v, pos, id) -> {
            Store.Node n = spaces.get(pos);
            if (!selected.isEmpty()) {
                toggleSelected(n.id);
                return;
            }
            startActivity(new Intent(this, NodeActivity.class)
                    .putExtra(NodeActivity.EXTRA_NODE_ID, n.id));
        });
        grid.setOnItemLongClickListener((p, v, pos, id) -> {
            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            showSpaceMenu(spaces.get(pos));
            return true;
        });
        grid.setEmptyView(findViewById(R.id.empty));

        fab.setOnClickListener(v ->
                Ui.promptText(this, "新建收纳空间", "例如：卧室、阳台、储物间", "", false, name -> {
                    store.createNode(0, name);
                    Ui.toast(this, "已创建「" + name + "」，点开它添加照片吧");
                    reload();
                }));

        findViewById(R.id.btnSidebar).setOnClickListener(v -> Sidebar.show(this));

        // 右上角全选框：勾选进入选择态，可再点单个卡片调整；取消勾选整体退出
        cbSelectAll.setOnClickListener(v -> {
            if (spaces.isEmpty()) {
                cbSelectAll.setChecked(false);
                Ui.toast(this, "还没有收纳空间");
                return;
            }
            if (cbSelectAll.isChecked()) {
                for (Store.Node n : spaces) selected.add(n.id);
            } else {
                selected.clear();
            }
            updateSelUi();
            adapter.notifyDataSetChanged();
        });
        findViewById(R.id.btnSelCancel).setOnClickListener(v -> clearSelection());
        btnSelDelete.setOnClickListener(v -> confirmDeleteSelected());

        setupSearch();
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        Backup.handleResult(this, req, res, data);
    }

    @Override
    public void onBackPressed() {
        if (!selected.isEmpty()) {
            clearSelection();
            return;
        }
        if (searchPanel != null && searchPanel.getVisibility() == View.VISIBLE) {
            clearSearch();
            return;
        }
        super.onBackPressed();
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
        // 层级页里可能改名/删除，回首页时刷新搜索结果
        if (searchPanel != null && searchPanel.getVisibility() == View.VISIBLE) {
            runSearch(etSearch.getText().toString().trim());
        }
    }

    private void reload() {
        spaces.clear();
        spaces.addAll(store.children(0));
        if (!selected.isEmpty()) {
            Set<Long> ids = new HashSet<>();
            for (Store.Node n : spaces) ids.add(n.id);
            selected.retainAll(ids);
        }
        adapter.notifyDataSetChanged();
        updateSelUi();
        maybeTipLongPress();
    }

    /** 首页长按卡片（重命名/删除）无可见入口，首次有内容时提示一次 */
    private void maybeTipLongPress() {
        if (spaces.isEmpty()) return;
        SharedPreferences sp = getSharedPreferences("app", MODE_PRIVATE);
        if (sp.getBoolean("tip_longpress_main", false)) return;
        sp.edit().putBoolean("tip_longpress_main", true).apply();
        Ui.toast(this, "小提示：长按卡片可重命名或删除空间");
    }

    // ---------------- 搜索 ----------------

    private void setupSearch() {
        etSearch = findViewById(R.id.etSearch);
        searchPanel = findViewById(R.id.searchPanel);
        searchList = findViewById(R.id.searchList);
        searchList.setEmptyView(findViewById(R.id.searchEmpty));
        searchList.setAdapter(new SearchAdapter());

        // 搜索图标作复合Drawable：代码里设置并缩放，兼容低版本且保证白色
        Drawable icon = getResources().getDrawable(R.drawable.ic_search);
        icon.setBounds(0, 0, Ui.dp(this, 18), Ui.dp(this, 18));
        etSearch.setCompoundDrawables(icon, null, null, null);
        etSearch.setCompoundDrawablePadding(Ui.dp(this, 8));

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                runSearch(s.toString().trim());
            }
        });
        searchList.setOnItemClickListener((p, v, pos, id) -> openNode(searchResults.get(pos).id));
    }

    private void runSearch(String q) {
        searchResults.clear();
        searchLocs.clear();
        if (!q.isEmpty()) {
            for (Store.Node n : store.searchByName(q)) {
                searchResults.add(n);
                searchLocs.add(locationOf(n));
            }
        }
        ((BaseAdapter) searchList.getAdapter()).notifyDataSetChanged();
        searchPanel.setVisibility(q.isEmpty() ? View.GONE : View.VISIBLE);
    }

    /** 结果行的位置文本：顶层空间无上级时给个说明 */
    private String locationOf(Store.Node n) {
        StringBuilder sb = new StringBuilder();
        List<Store.Node> chain = store.path(n.id);
        for (int i = 0; i < chain.size() - 1; i++) {
            if (sb.length() > 0) sb.append(" › ");
            sb.append(chain.get(i).name);
        }
        return sb.length() == 0 ? "位于首页的收纳空间" : sb.toString();
    }

    /** 携带完整层级链跳转：返回键可逐级回退，面包屑也正确 */
    private void openNode(long id) {
        List<Store.Node> chain = store.path(id);
        if (chain.isEmpty()) return;
        long[] ids = new long[chain.size()];
        for (int i = 0; i < ids.length; i++) ids[i] = chain.get(i).id;
        hideKeyboard();
        startActivity(new Intent(this, NodeActivity.class)
                .putExtra(NodeActivity.EXTRA_PATH_IDS, ids));
    }

    private void clearSearch() {
        hideKeyboard();
        etSearch.setText(""); // 触发 TextWatcher，面板随之隐藏
        etSearch.clearFocus();
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(etSearch.getWindowToken(), 0);
    }

    class SearchAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return searchResults.size();
        }

        @Override
        public Object getItem(int i) {
            return searchResults.get(i);
        }

        @Override
        public long getItemId(int i) {
            return searchResults.get(i).id;
        }

        @Override
        public View getView(int i, View cv, ViewGroup parent) {
            View v = cv;
            if (v == null) {
                v = LayoutInflater.from(MainActivity.this)
                        .inflate(R.layout.item_search_result, parent, false);
            }
            Store.Node n = searchResults.get(i);
            TextView tile = v.findViewById(R.id.tile);
            tile.setText(n.name.substring(0, 1));
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.RECTANGLE);
            bg.setCornerRadius(Ui.dp(MainActivity.this, 13));
            bg.setColor(Ui.tileColor(n.id));
            tile.setBackground(bg);
            ((TextView) v.findViewById(R.id.name)).setText(n.name);
            ((TextView) v.findViewById(R.id.loc)).setText(searchLocs.get(i));
            return v;
        }
    }

    // ---------------- 批量选择删除 ----------------

    private void toggleSelected(long id) {
        if (!selected.remove(id)) selected.add(id);
        updateSelUi();
        adapter.notifyDataSetChanged();
    }

    private void clearSelection() {
        selected.clear();
        updateSelUi();
        adapter.notifyDataSetChanged();
    }

    private void updateSelUi() {
        boolean any = !selected.isEmpty();
        cbSelectAll.setChecked(any && selected.size() == spaces.size());
        if (any) tvTitle.setText("已选 " + selected.size() + " 项");
        else tvTitle.setText(R.string.app_name);
        fab.setVisibility(any ? View.GONE : View.VISIBLE);
        selBar.setVisibility(any ? View.VISIBLE : View.GONE);
        btnSelDelete.setEnabled(any);
        btnSelDelete.setAlpha(any ? 1f : 0.5f);
    }

    private void confirmDeleteSelected() {
        if (selected.isEmpty()) return;
        final List<Long> ids = new ArrayList<>(selected);
        Ui.confirm(this, "删除所选空间",
                "将删除所选 " + ids.size() + " 个空间及其全部子层级、照片和交互点，不可恢复。",
                "删除", () -> {
                    for (long id : ids) store.deleteNodeDeep(id);
                    thumbCache.clear();
                    Ui.toast(this, "已删除 " + ids.size() + " 个空间");
                    reload();
                });
    }

    private void showSpaceMenu(Store.Node n) {
        int kids = store.countChildren(n.id);
        int pics = store.images(n.id).size();
        String sub;
        if (kids == 0 && pics == 0) sub = "还没有内容";
        else sub = (kids > 0 ? kids + " 个子层级" : "")
                + (kids > 0 && pics > 0 ? " · " : "")
                + (pics > 0 ? pics + " 张照片" : "");
        Ui.sheet(this, n.name, sub)
                .item("重命名", R.drawable.ic_edit, R.color.primary, () ->
                        Ui.promptText(this, "重命名空间", "", n.name, false, s -> {
                            store.renameNode(n.id, s);
                            reload();
                        }))
                .danger("删除空间", "将删除全部子层级和照片，不可恢复", () ->
                        Ui.confirm(this, "删除空间",
                                "将删除「" + n.name + "」及其所有子层级、照片，不可恢复。",
                                "删除", () -> {
                                    store.deleteNodeDeep(n.id);
                                    reload();
                                }))
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

            boolean on = selected.contains(n.id);
            v.findViewById(R.id.selDim).setVisibility(on ? View.VISIBLE : View.GONE);
            v.findViewById(R.id.selBadge).setVisibility(on ? View.VISIBLE : View.GONE);
            return v;
        }
    }
}
