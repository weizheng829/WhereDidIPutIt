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
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
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
    /** 按缩略图文件名缓存：层级页换封面后回首页能立即刷新 */
    private final Map<String, Bitmap> thumbCache = new HashMap<>();
    private Store store;
    private int uiSeqApplied;

    /** 批量选择删除：右上角「选择」进入选择态，选择态内再「全选/取消全选」 */
    private final Set<Long> selected = new HashSet<>();
    private boolean selectMode;
    private TextView tvTitle, btnSelDelete, btnSelect, btnSelectAll;
    private View fab, selBar;

    /** 名称搜索：输入即时出结果，点结果直达该层级；也搜相册里命名的照片 */
    private EditText etSearch;
    private View searchPanel;
    private ListView searchList;
    private Drawable searchIcon, clearIcon;
    private final List<Store.Node> searchResults = new ArrayList<>();
    private final List<String> searchLocs = new ArrayList<>();
    /** 照片搜索结果（按名称命中的相册照片），与节点结果合并展示 */
    private final List<Store.Pic> picResults = new ArrayList<>();
    private final List<String> picLocs = new ArrayList<>();

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(Ui.wrapLocale(Ui.wrapTheme(base)));
        uiSeqApplied = Ui.uiSeq();
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setTheme(Ui.themeStyle(this));
        setContentView(R.layout.activity_main);
        store = Store.get(this);
        seedIfNeeded();

        tvTitle = findViewById(R.id.tvTitle);
        btnSelect = findViewById(R.id.btnSelect);
        btnSelectAll = findViewById(R.id.btnSelectAll);
        selBar = findViewById(R.id.selBar);
        btnSelDelete = findViewById(R.id.btnSelDelete);
        fab = findViewById(R.id.fab);
        // 底部悬浮件避让手势条：在基础下边距上叠加导航栏 insets
        // （系统已让出导航栏时 insets 为 0，仅取布局基础值；edge-to-edge 布局时取实际高度）
        ViewGroup.MarginLayoutParams selLp = (ViewGroup.MarginLayoutParams) selBar.getLayoutParams();
        selBar.setOnApplyWindowInsetsListener((v, ins) -> {
            selLp.bottomMargin = Ui.dp(this, 28) + ins.getSystemWindowInsetBottom();
            v.requestLayout();
            return ins;
        });
        ViewGroup.MarginLayoutParams fabLp = (ViewGroup.MarginLayoutParams) fab.getLayoutParams();
        fab.setOnApplyWindowInsetsListener((v, ins) -> {
            fabLp.bottomMargin = Ui.dp(this, 24) + ins.getSystemWindowInsetBottom();
            v.requestLayout();
            return ins;
        });

        grid = findViewById(R.id.grid);
        adapter = new SpaceAdapter();
        grid.setAdapter(adapter);
        grid.setOnItemClickListener((p, v, pos, id) -> {
            Store.Node n = spaces.get(pos);
            if (selectMode) {
                toggleSelected(n.id);
                return;
            }
            startActivity(new Intent(this, NodeActivity.class)
                    .putExtra(NodeActivity.EXTRA_NODE_ID, n.id));
        });
        grid.setOnItemLongClickListener((p, v, pos, id) -> {
            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            // 选择态下长按与点击一致：切换勾选，不弹菜单
            if (selectMode) toggleSelected(spaces.get(pos).id);
            else showSpaceMenu(spaces.get(pos));
            return true;
        });
        grid.setEmptyView(findViewById(R.id.empty));

        fab.setOnClickListener(v ->
                Ui.promptText(this, getString(R.string.dlg_new_space),
                        getString(R.string.hint_new_space), "", false, name -> {
                    store.createNode(0, name);
                    Ui.toast(this, getString(R.string.toast_space_created_fmt, name));
                    reload();
                }));

        findViewById(R.id.btnSidebar).setOnClickListener(v -> Sidebar.show(this));

        // 右上角「选择」进入选择态；选择态内点卡片逐个勾选，「全选/取消全选」一键切换
        btnSelect.setOnClickListener(v -> {
            if (spaces.isEmpty()) return;
            selectMode = true;
            selected.clear();
            updateSelUi();
            adapter.notifyDataSetChanged();
        });
        btnSelectAll.setOnClickListener(v -> {
            if (spaces.isEmpty()) return;
            boolean all = selected.size() == spaces.size();
            selected.clear();
            if (!all) for (Store.Node n : spaces) selected.add(n.id);
            updateSelUi();
            adapter.notifyDataSetChanged();
        });
        findViewById(R.id.btnSelCancel).setOnClickListener(v -> exitSelectMode());
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
        if (selectMode) {
            exitSelectMode();
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
            String[] names = getResources().getStringArray(R.array.seed_spaces);
            for (String n : names) store.createNode(0, n);
            sp.edit().putBoolean("seeded", true).apply();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (uiSeqApplied != Ui.uiSeq()) recreate();
        reload();
    }

    private void reload() {
        spaces.clear();
        spaces.addAll(store.children(0));
        if (!selected.isEmpty()) {
            Set<Long> ids = new HashSet<>();
            for (Store.Node n : spaces) ids.add(n.id);
            selected.retainAll(ids);
        }
        // 选择态下内容被清空（如全部删完）则退出
        if (selectMode && spaces.isEmpty()) selectMode = false;
        adapter.notifyDataSetChanged();
        updateSelUi();
        refreshSearchIfVisible();
        maybeTipLongPress();
    }

    /** 数据变化后刷新搜索结果：面板可能仍显示已删除/过期的条目 */
    private void refreshSearchIfVisible() {
        if (searchPanel != null && searchPanel.getVisibility() == View.VISIBLE) {
            runSearch(etSearch.getText().toString().trim());
        }
    }

    /** 首页长按卡片（重命名/删除）无可见入口，首次有内容时提示一次 */
    private void maybeTipLongPress() {
        if (spaces.isEmpty()) return;
        SharedPreferences sp = getSharedPreferences("app", MODE_PRIVATE);
        if (sp.getBoolean("tip_longpress_main", false)) return;
        sp.edit().putBoolean("tip_longpress_main", true).apply();
        Ui.toast(this, getString(R.string.tip_longpress_main));
    }

    // ---------------- 搜索 ----------------

    private void setupSearch() {
        etSearch = findViewById(R.id.etSearch);
        searchPanel = findViewById(R.id.searchPanel);
        searchList = findViewById(R.id.searchList);
        searchList.setEmptyView(findViewById(R.id.searchEmpty));
        searchList.setAdapter(new SearchAdapter());

        // 搜索图标作复合Drawable：代码里缩放并着色，兼容低版本
        searchIcon = getResources().getDrawable(R.drawable.ic_search);
        searchIcon.setBounds(0, 0, Ui.dp(this, 18), Ui.dp(this, 18));
        searchIcon.setTint(Ui.color(this, R.attr.primary));
        clearIcon = getResources().getDrawable(R.drawable.ic_clear);
        clearIcon.setBounds(0, 0, Ui.dp(this, 16), Ui.dp(this, 16));
        clearIcon.setTint(Ui.color(this, R.attr.onSurfaceVariant));
        etSearch.setCompoundDrawables(searchIcon, null, null, null);
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
                boolean has = s.length() > 0;
                // 有内容时右侧显示 ×，一键清空
                etSearch.setCompoundDrawables(searchIcon, null, has ? clearIcon : null, null);
                runSearch(s.toString().trim());
            }
        });
        etSearch.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_UP) {
                Drawable clear = etSearch.getCompoundDrawables()[2];
                if (clear != null && e.getX() >= etSearch.getWidth()
                        - etSearch.getPaddingRight() - clear.getBounds().width()) {
                    etSearch.setText("");
                    return true;
                }
            }
            return false;
        });
        searchList.setOnItemClickListener((p, v, pos, id) -> {
            if (pos < searchResults.size()) {
                openNode(searchResults.get(pos).id);
            } else {
                openPic(picResults.get(pos - searchResults.size()));
            }
        });
    }

    private void runSearch(String q) {
        searchResults.clear();
        searchLocs.clear();
        picResults.clear();
        picLocs.clear();
        if (!q.isEmpty()) {
            for (Store.Node n : store.searchByName(q)) {
                searchResults.add(n);
                searchLocs.add(locationOf(n));
            }
            for (Store.Pic pic : store.searchImagesByLabel(q)) {
                picResults.add(pic);
                picLocs.add(locationOfPic(pic));
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
        return sb.length() == 0 ? getString(R.string.search_loc_top) : sb.toString();
    }

    /** 照片结果行的位置文本：完整路径含所在相册名 */
    private String locationOfPic(Store.Pic pic) {
        StringBuilder sb = new StringBuilder();
        for (Store.Node n : store.path(pic.nodeId)) {
            if (sb.length() > 0) sb.append(" › ");
            sb.append(n.name);
        }
        return sb.toString();
    }

    /** 携带完整层级链跳转：返回键可逐级回退，面包屑也正确 */
    private void openNode(long id) {
        List<Store.Node> chain = store.path(id);
        if (chain.isEmpty()) {
            // 节点已被删除（结果过期）：刷新结果列表
            runSearch(etSearch.getText().toString().trim());
            return;
        }
        long[] ids = new long[chain.size()];
        for (int i = 0; i < ids.length; i++) ids[i] = chain.get(i).id;
        hideKeyboard();
        startActivity(new Intent(this, NodeActivity.class)
                .putExtra(NodeActivity.EXTRA_PATH_IDS, ids));
    }

    /** 照片结果直达：进入所在相册并定位到这张照片的大图 */
    private void openPic(Store.Pic pic) {
        List<Store.Node> chain = store.path(pic.nodeId);
        if (chain.isEmpty()) {
            // 相册已被删除（结果过期）：刷新结果列表
            runSearch(etSearch.getText().toString().trim());
            return;
        }
        long[] ids = new long[chain.size()];
        for (int i = 0; i < ids.length; i++) ids[i] = chain.get(i).id;
        hideKeyboard();
        startActivity(new Intent(this, NodeActivity.class)
                .putExtra(NodeActivity.EXTRA_PATH_IDS, ids)
                .putExtra(NodeActivity.EXTRA_PIC_ID, pic.id));
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

        private static final int TYPE_NODE = 0;
        private static final int TYPE_PIC = 1;

        @Override
        public int getCount() {
            return searchResults.size() + picResults.size();
        }

        @Override
        public Object getItem(int i) {
            return i < searchResults.size() ? searchResults.get(i) : picResults.get(i - searchResults.size());
        }

        @Override
        public long getItemId(int i) {
            return i < searchResults.size() ? searchResults.get(i).id
                    : picResults.get(i - searchResults.size()).id;
        }

        @Override
        public int getViewTypeCount() {
            return 2;
        }

        @Override
        public int getItemViewType(int pos) {
            return pos < searchResults.size() ? TYPE_NODE : TYPE_PIC;
        }

        @Override
        public View getView(int pos, View cv, ViewGroup parent) {
            View v = cv;
            if (v == null) {
                v = LayoutInflater.from(MainActivity.this)
                        .inflate(R.layout.item_search_result, parent, false);
                // 圆角缩略图：按背景 outline 裁剪
                ImageView pt = v.findViewById(R.id.photoTile);
                pt.setClipToOutline(true);
                pt.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
            }
            TextView tile = v.findViewById(R.id.tile);
            ImageView photoTile = v.findViewById(R.id.photoTile);
            TextView name = v.findViewById(R.id.name);
            TextView loc = v.findViewById(R.id.loc);
            if (getItemViewType(pos) == TYPE_NODE) {
                Store.Node n = searchResults.get(pos);
                GradientDrawable bg = new GradientDrawable();
                bg.setShape(GradientDrawable.RECTANGLE);
                bg.setCornerRadius(Ui.dp(MainActivity.this, 13));
                bg.setColor(Ui.tileColor(n.id));
                tile.setText(Ui.firstChar(n.name));
                tile.setBackground(bg);
                tile.setVisibility(View.VISIBLE);
                photoTile.setVisibility(View.GONE);
                name.setText(n.name);
                loc.setText(searchLocs.get(pos));
            } else {
                int pi = pos - searchResults.size();
                Store.Pic pic = picResults.get(pi);
                Bitmap bmp = thumbCache.get(pic.thumb);
                if (bmp == null) {
                    File tf = store.thumbFile(pic.thumb);
                    if (tf.exists()) {
                        bmp = Store.decodeScaled(tf, 200);
                        if (bmp != null) thumbCache.put(pic.thumb, bmp);
                    }
                }
                if (bmp != null) photoTile.setImageBitmap(bmp);
                else photoTile.setImageDrawable(null);
                tile.setVisibility(View.GONE);
                photoTile.setVisibility(View.VISIBLE);
                name.setText(pic.label);
                loc.setText(picLocs.get(pi));
            }
            return v;
        }
    }

    // ---------------- 批量选择删除 ----------------

    private void toggleSelected(long id) {
        if (!selected.remove(id)) selected.add(id);
        updateSelUi();
        adapter.notifyDataSetChanged();
    }

    private void exitSelectMode() {
        selectMode = false;
        selected.clear();
        updateSelUi();
        adapter.notifyDataSetChanged();
    }

    private void updateSelUi() {
        boolean any = !selected.isEmpty();
        boolean all = any && selected.size() == spaces.size();
        btnSelect.setVisibility(!selectMode && !spaces.isEmpty() ? View.VISIBLE : View.GONE);
        btnSelectAll.setVisibility(selectMode ? View.VISIBLE : View.GONE);
        btnSelectAll.setText(all ? R.string.deselect_all : R.string.select_all);
        if (selectMode) tvTitle.setText(any ? getString(R.string.sel_count_fmt, selected.size())
                : getString(R.string.sel_pick_hint));
        else tvTitle.setText(R.string.app_name);
        fab.setVisibility(selectMode ? View.GONE : View.VISIBLE);
        selBar.setVisibility(selectMode ? View.VISIBLE : View.GONE);
        btnSelDelete.setEnabled(any);
        btnSelDelete.setAlpha(any ? 1f : 0.5f);
    }

    private void confirmDeleteSelected() {
        if (selected.isEmpty()) return;
        final List<Long> ids = new ArrayList<>(selected);
        Ui.confirm(this, getString(R.string.dlg_delete_selected),
                getString(R.string.dlg_delete_selected_msg_fmt, ids.size()),
                getString(R.string.btn_delete), () -> {
                    for (long id : ids) store.deleteNodeDeep(id);
                    thumbCache.clear();
                    Ui.toast(this, getString(R.string.toast_deleted_fmt, ids.size()));
                    exitSelectMode();
                    reload();
                });
    }

    private void showSpaceMenu(Store.Node n) {
        int kids = store.countChildren(n.id);
        int pics = store.images(n.id).size();
        String sub = Ui.subCounts(this, kids, pics);
        Ui.sheet(this, n.name, sub)
                .item(getString(R.string.act_rename), R.drawable.ic_edit, R.color.primary, () ->
                        Ui.promptText(this, getString(R.string.dlg_rename_space), "",
                                n.name, false, s -> {
                            store.renameNode(n.id, s);
                            reload();
                        }))
                .danger(getString(R.string.dlg_delete_space),
                        getString(R.string.sub_delete_subtree), () ->
                        Ui.confirm(this, getString(R.string.dlg_delete_space),
                                getString(R.string.dlg_delete_subtree_msg_fmt, n.name),
                                getString(R.string.btn_delete), () -> {
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
            if (kids > 0) sb.append(getString(R.string.fmt_storages, kids)).append(" · ");
            sb.append(pics.isEmpty() ? getString(R.string.no_photos_yet)
                    : getString(R.string.fmt_pics, pics.size()));
            if (n.note != null && !n.note.trim().isEmpty()) sb.append(getString(R.string.note_marker));
            sub.setText(sb.toString());

            Bitmap bmp = null;
            if (!pics.isEmpty()) {
                String thumbName = pics.get(0).thumb;
                File tf = store.thumbFile(thumbName);
                if (tf.exists()) {
                    bmp = thumbCache.get(thumbName);
                    if (bmp == null) {
                        bmp = Store.decodeScaled(tf, 260);
                        if (bmp != null) thumbCache.put(thumbName, bmp);
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
                letter.setText(Ui.firstChar(n.name));
                letter.setBackgroundColor(Ui.tileColor(n.id));
            }

            boolean on = selected.contains(n.id);
            v.findViewById(R.id.selDim).setVisibility(on ? View.VISIBLE : View.GONE);
            v.findViewById(R.id.selBadge).setVisibility(on ? View.VISIBLE : View.GONE);
            return v;
        }
    }
}
