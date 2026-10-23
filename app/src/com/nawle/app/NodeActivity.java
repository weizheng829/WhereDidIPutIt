package com.nawle.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.LruCache;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 层级页：所有层级（空间/柜子/抽屉/物品…）复用本页。
 * 内部维护导航栈实现无限层级钻取，面包屑可点击跳回任意层级。
 * 「展开」节点（kind=相册）也复用本页：进入后切换为四列相册网格，
 * 只能添加照片，支持命名、删除与长按拖拽排序。
 */
public class NodeActivity extends Activity implements InteractiveImageView.Host {

    public static final String EXTRA_NODE_ID = "node_id";
    /** 携带完整层级链（顶层→目标），用于搜索等直达深层节点的入口 */
    public static final String EXTRA_PATH_IDS = "path_ids";
    /** 搜索照片结果直达：进入后定位到这张照片的大图（配合 EXTRA_PATH_IDS 使用） */
    public static final String EXTRA_PIC_ID = "pic_id";
    private static final int REQ_CAMERA = 1;
    private static final int REQ_GALLERY = 2;

    private Store store;
    private InteractiveImageView iv;
    private LinearLayout crumbWrap;
    private LinearLayout strip;
    private HorizontalScrollView stripScroll;
    private LinearLayout emptyView;
    private TextView hintBar;
    private LinearLayout bottomBar;
    private Button btnHotspot;
    private Button btnExpand;
    private Button btnRenamePic;
    private Button btnDeletePic;
    private FrameLayout framePhoto;
    private TextView tvEmptyTitle;
    private TextView tvEmptyBody;
    private GridView gridAlbum;
    private AlbumAdapter albumAdapter;

    /** 导航路径（节点 id），最后一个为当前层级；由进入子项/返回维护 */
    private final List<Long> path = new ArrayList<>();
    /** 记住每个节点上次查看的图片，返回时恢复 */
    private final Map<Long, Long> lastPicByNode = new HashMap<>();
    /** 相册网格与缩略图条共用的缩略图缓存（按文件名），上限约 6MB */
    private final LruCache<String, Bitmap> thumbCache = new LruCache<String, Bitmap>(6 * 1024 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return value.getByteCount();
        }
    };
    /** 未命名照片的占位标题：显示到分钟（如「未命名 14:23」） */
    private final SimpleDateFormat hmFmt = new SimpleDateFormat("HH:mm", Locale.getDefault());

    private Store.Node node;
    private List<Store.Pic> pics = new ArrayList<>();
    private int currentPic = -1;
    /** 搜索照片直达：首次 loadCurrent 时定位到这张照片；-1=无 */
    private long locatePicId = -1;
    private boolean placing = false;
    /** 放置模式下正在放置的是「展开（相册）」而非普通交互点 */
    private boolean placingGallery = false;
    /** 相册节点内：true=大图查看（复用照片视图+缩略图条），false=四列网格 */
    private boolean galleryViewing = false;
    /** 拖拽排序：被拖动照片的当前位置；-1=未在拖拽 */
    private int dragFrom = -1;
    /** 拖拽时最新触点 Y（网格本地坐标），驱动边缘自动滚动 */
    private float lastDragY;
    private Runnable dragScroll;
    private File pendingCapture;
    private int loadToken = 0;
    private int pickSeq = 0;
    private int uiSeqApplied;
    /** 长按操作的一次性提示（hintBar 复用）：hideTip 用于取消自动收起 */
    private Runnable hideTip;
    private boolean tipShowing;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(Ui.wrapLocale(Ui.wrapTheme(base)));
        uiSeqApplied = Ui.uiSeq();
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setTheme(Ui.themeStyle(this));
        setContentView(R.layout.activity_node);
        store = Store.get(this);
        iv = findViewById(R.id.ivImage);
        iv.setHost(this);
        crumbWrap = findViewById(R.id.crumbs);
        strip = findViewById(R.id.strip);
        stripScroll = findViewById(R.id.stripScroll);
        emptyView = findViewById(R.id.emptyView);
        hintBar = findViewById(R.id.hintBar);
        bottomBar = findViewById(R.id.bottomBar);
        // 底栏避让手势条：基础内边距叠加导航栏避让高度（见 Ui.navBottomInset）
        bottomBar.setOnApplyWindowInsetsListener((v, ins) -> {
            bottomBar.setPadding(bottomBar.getPaddingLeft(), bottomBar.getPaddingTop(),
                    bottomBar.getPaddingRight(), Ui.dp(this, 20) + Ui.navBottomInset(this, ins));
            return ins;
        });
        btnHotspot = findViewById(R.id.btnHotspot);
        btnExpand = findViewById(R.id.btnExpand);
        btnRenamePic = findViewById(R.id.btnRenamePic);
        btnDeletePic = findViewById(R.id.btnDeletePic);
        framePhoto = findViewById(R.id.framePhoto);
        tvEmptyTitle = findViewById(R.id.tvEmptyTitle);
        tvEmptyBody = findViewById(R.id.tvEmptyBody);
        gridAlbum = findViewById(R.id.gridAlbum);

        btnRenamePic.setOnClickListener(v -> {
            if (currentPic >= 0 && currentPic < pics.size()) renamePhoto(pics.get(currentPic));
        });
        btnDeletePic.setOnClickListener(v -> {
            if (currentPic >= 0 && currentPic < pics.size()) deletePhoto(pics.get(currentPic));
        });

        albumAdapter = new AlbumAdapter();
        gridAlbum.setAdapter(albumAdapter);
        gridAlbum.setOnItemClickListener((p, v, pos, id) -> {
            if (pos >= pics.size()) {
                chooseAddPhoto();
                return;
            }
            galleryViewing = true;
            applyModeUi();
            showPic(pos);
        });
        gridAlbum.setOnItemLongClickListener((p, v, pos, id) -> {
            if (pos >= pics.size()) return false;
            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            startAlbumDrag(pos, v);
            return true;
        });
        gridAlbum.setOnDragListener((v, ev) -> onAlbumDrag(ev));

        // 搜索等入口带完整层级链直达深层节点；链上的失效节点（如已删除）自动跳过
        long[] chain = getIntent().getLongArrayExtra(EXTRA_PATH_IDS);
        if (chain != null) {
            for (long cid : chain) {
                if (cid > 0 && store.node(cid) != null) path.add(cid);
            }
        }
        if (path.isEmpty()) {
            long id = getIntent().getLongExtra(EXTRA_NODE_ID, -1);
            if (id <= 0 || store.node(id) == null) {
                finish();
                return;
            }
            path.add(id);
        }
        locatePicId = getIntent().getLongExtra(EXTRA_PIC_ID, -1);

        findViewById(R.id.btnBack).setOnClickListener(v -> goBack());
        findViewById(R.id.btnMenu).setOnClickListener(v -> showMenu());
        findViewById(R.id.btnFirstPhoto).setOnClickListener(v -> chooseAddPhoto());
        btnHotspot.setOnClickListener(v -> togglePlacing(false));
        btnExpand.setOnClickListener(v -> togglePlacing(true));

        loadCurrent();
    }

    private boolean isGallery() {
        return node != null && node.kind == Store.KIND_GALLERY;
    }

    // ---------------- 导航 ----------------

    private void goBack() {
        if (placing) {
            togglePlacing(placingGallery);
            return;
        }
        if (isGallery() && galleryViewing) {
            // 相册内看大图：返回先回网格，再返回才离开相册
            galleryViewing = false;
            showPic(-1);
            applyModeUi();
            return;
        }
        if (path.size() > 1) {
            path.remove(path.size() - 1);
            loadCurrent();
        } else {
            finish();
        }
    }

    @Override
    public void onBackPressed() {
        goBack();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (uiSeqApplied != Ui.uiSeq()) recreate();
    }

    private void enterChild(long id) {
        Store.Node n = store.node(id);
        if (n == null) {
            refreshHotspots();
            return;
        }
        if (placing) togglePlacing(placingGallery);
        galleryViewing = false;
        path.add(id);
        loadCurrent();
    }

    private void loadCurrent() {
        node = store.node(path.get(path.size() - 1));
        if (node == null) {
            if (path.size() > 1) {
                path.remove(path.size() - 1);
                loadCurrent();
            } else {
                finish();
            }
            return;
        }
        pics = store.images(node.id);
        int idx = -1;
        if (locatePicId > 0) {
            // 搜索照片直达：定位到这张照片（相册内直接进大图）
            for (int i = 0; i < pics.size(); i++) {
                if (pics.get(i).id == locatePicId) {
                    idx = i;
                    break;
                }
            }
            if (idx >= 0 && isGallery()) galleryViewing = true;
            locatePicId = -1;
        }
        if (idx < 0 && isGallery() && (pics.isEmpty() || !galleryViewing)) {
            // 以网格进入相册（或照片已清空）：不预选大图
            galleryViewing = false;
            showPic(-1);
        } else {
            if (idx < 0) {
                Long remembered = lastPicByNode.get(node.id);
                if (remembered != null) {
                    for (int i = 0; i < pics.size(); i++) {
                        if (pics.get(i).id == remembered) {
                            idx = i;
                            break;
                        }
                    }
                }
                if (idx < 0) idx = pics.isEmpty() ? -1 : 0;
            }
            showPic(idx);
        }
        rebuildCrumb();
        rebuildStrip();
        albumAdapter.notifyDataSetChanged();
        applyModeUi();
        maybeShowLongPressTip();
    }

    private void showPic(int idx) {
        currentPic = idx;
        if (idx < 0 || idx >= pics.size()) {
            iv.clearImage();
            iv.setHotspots(new ArrayList<>());
            rebuildStripSel();
            return;
        }
        Store.Pic p = pics.get(idx);
        lastPicByNode.put(node.id, p.id);
        iv.setHotspots(store.hotspots(p.id));
        final int token = ++loadToken;
        final File f = store.imageFile(p.file);
        new Thread(() -> {
            final Bitmap bmp = Store.decodeScaled(f, 2048);
            runOnUiThread(() -> {
                if (token == loadToken) iv.setImage(bmp);
            });
        }).start();
        rebuildStripSel();
        scrollStripTo(idx);
    }

    private void refreshHotspots() {
        if (currentPic >= 0 && currentPic < pics.size()) {
            iv.setHotspots(store.hotspots(pics.get(currentPic).id));
        }
    }

    // ---------------- 顶部面包屑 ----------------

    private void rebuildCrumb() {
        crumbWrap.removeAllViews();
        crumbWrap.addView(crumbSeg(getString(R.string.crumb_home), () -> finish(), false));
        List<Store.Node> chain = store.path(node.id);
        for (int i = 0; i < chain.size(); i++) {
            final int idx = i;
            crumbWrap.addView(crumbSep());
            crumbWrap.addView(crumbSeg(chain.get(i).name, () -> popToChainIndex(idx),
                    idx == chain.size() - 1));
        }
        HorizontalScrollView hsv = (HorizontalScrollView) crumbWrap.getParent();
        hsv.post(() -> hsv.fullScroll(HorizontalScrollView.FOCUS_RIGHT));
    }

    private void popToChainIndex(int idx) {
        // 面包屑索引与导航路径一一对应（导航只经父→子）
        while (path.size() - 1 > idx) path.remove(path.size() - 1);
        galleryViewing = false;
        loadCurrent();
    }

    /** 面包屑段：当前层级为胶囊高亮，历史层级降为次级色；触控高度统一 40dp */
    private TextView crumbSeg(String text, Runnable r, boolean current) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(14);
        tv.setMaxLines(1);
        tv.setEllipsize(TextUtils.TruncateAt.END);
        tv.setSingleLine(true);
        tv.setMaxWidth(Ui.dp(this, 160));
        tv.setMinHeight(Ui.dp(this, 40));
        tv.setGravity(Gravity.CENTER_VERTICAL);
        if (current) {
            tv.setTextColor(Ui.color(this, R.attr.onPrimaryContainer));
            tv.setTypeface(Typeface.DEFAULT_BOLD);
            tv.setBackgroundResource(R.drawable.bg_crumb_pill);
            tv.setPadding(Ui.dp(this, 12), Ui.dp(this, 5), Ui.dp(this, 12), Ui.dp(this, 5));
        } else {
            tv.setTextColor(Ui.color(this, R.attr.onHeaderDim));
            tv.setPadding(Ui.dp(this, 6), 0, Ui.dp(this, 6), 0);
        }
        tv.setOnClickListener(v -> r.run());
        return tv;
    }

    private TextView crumbSep() {
        TextView tv = new TextView(this);
        tv.setText("›");
        tv.setTextColor(Ui.color(this, R.attr.onHeaderFaint));
        tv.setTextSize(14);
        tv.setPadding(0, 0, Ui.dp(this, 2), 0);
        tv.setGravity(Gravity.CENTER_VERTICAL);
        return tv;
    }

    // ---------------- 主区域模式切换 ----------------

    /** 按层级类型（普通/相册）与当前状态切换主区域：大图查看 / 相册网格 / 空态提示 */
    private void applyModeUi() {
        boolean gallery = isGallery();
        boolean grid = gallery && !galleryViewing;
        boolean gridHasPics = grid && !pics.isEmpty();
        gridAlbum.setVisibility(gridHasPics ? View.VISIBLE : View.GONE);
        framePhoto.setVisibility(gridHasPics ? View.GONE : View.VISIBLE);
        stripScroll.setVisibility(grid ? View.GONE : View.VISIBLE);
        if (gridHasPics) {
            // 网格从隐藏转可见时 getWidth 还是 0，格子尺寸按屏宽估算；
            // 布局完成后重绑一次，用真实列宽摆正格子
            gridAlbum.post(() -> albumAdapter.notifyDataSetChanged());
        }
        // 相册是叶子节点：不放交互点，也不再有下一层展开
        btnHotspot.setVisibility(gallery ? View.GONE : View.VISIBLE);
        btnExpand.setVisibility(gallery ? View.GONE : View.VISIBLE);
        // 相册查看大图时：给出当前照片的可见操作入口（重命名 / 删除）
        boolean viewer = gallery && galleryViewing && !pics.isEmpty();
        btnRenamePic.setVisibility(viewer ? View.VISIBLE : View.GONE);
        btnDeletePic.setVisibility(viewer ? View.VISIBLE : View.GONE);
        // 相册网格模式没有底栏操作（添加走网格末尾的 ＋，管理走格子上的 ⋮）
        bottomBar.setVisibility(grid ? View.GONE : View.VISIBLE);
        if (pics.isEmpty()) {
            emptyView.setVisibility(View.VISIBLE);
            iv.setVisibility(View.INVISIBLE);
            if (gallery) {
                tvEmptyTitle.setText(R.string.album_empty_title);
                tvEmptyBody.setText(R.string.album_empty_body);
            } else {
                tvEmptyTitle.setText(R.string.node_empty_title);
                tvEmptyBody.setText(R.string.node_empty_body);
            }
        } else {
            emptyView.setVisibility(View.GONE);
            iv.setVisibility(View.VISIBLE);
        }
    }

    // ---------------- 缩略图条 ----------------

    private void rebuildStrip() {
        strip.removeAllViews();
        for (int i = 0; i < pics.size(); i++) {
            final int idx = i;
            View v = LayoutInflater.from(this).inflate(R.layout.item_thumb, strip, false);
            ImageView im = v.findViewById(R.id.img);
            im.setContentDescription(getString(R.string.photo_desc, idx + 1));
            Bitmap b = decodeThumb(pics.get(i).thumb);
            if (b != null) im.setImageBitmap(b);
            v.setOnClickListener(vv -> showPic(idx));
            v.setOnLongClickListener(vv -> {
                vv.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                showPicMenu(idx);
                return true;
            });
            strip.addView(v);
        }
        // 末尾的“添加”块
        FrameLayout add = new FrameLayout(this);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                Ui.dp(this, 78), Ui.dp(this, 78));
        alp.setMargins(Ui.dp(this, 5), Ui.dp(this, 5), Ui.dp(this, 5), Ui.dp(this, 5));
        add.setLayoutParams(alp);
        add.setBackgroundResource(R.drawable.bg_add_tile);
        add.setForeground(getResources().getDrawable(R.drawable.bg_quiet_ripple));
        ImageView plus = new ImageView(this);
        plus.setImageResource(R.drawable.ic_add_teal);
        plus.setContentDescription(getString(R.string.cd_add_photo));
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(
                Ui.dp(this, 28), Ui.dp(this, 28), Gravity.CENTER);
        plus.setLayoutParams(plp);
        add.addView(plus);
        add.setOnClickListener(v -> chooseAddPhoto());
        strip.addView(add);
        rebuildStripSel();
    }

    private void rebuildStripSel() {
        for (int i = 0; i < pics.size(); i++) {
            View v = strip.getChildAt(i);
            if (v == null) continue;
            View sel = v.findViewById(R.id.sel);
            if (sel != null) sel.setVisibility(i == currentPic ? View.VISIBLE : View.GONE);
        }
    }

    private void scrollStripTo(int idx) {
        stripScroll.post(() -> {
            View c = strip.getChildAt(idx);
            if (c != null) {
                int x = Math.max(0, c.getLeft() - stripScroll.getWidth() / 2 + c.getWidth() / 2);
                stripScroll.smoothScrollTo(x, 0);
            }
        });
    }

    private void showPicMenu(int idx) {
        Store.Pic p = pics.get(idx);
        Ui.Sheet sheet = Ui.sheet(this, getString(R.string.sheet_photo_ops),
                getString(R.string.fmt_photo_n_of_m, idx + 1, pics.size()));
        // 封面只在首页空间卡片展示：仅首页那一层提供「设为封面」；相册（展开）额外提供重命名
        if (isGallery()) {
            sheet.item(getString(R.string.act_rename_photo), getString(R.string.hint_rename_photo),
                    R.drawable.ic_edit, R.color.primary, () -> renamePhoto(p));
        } else if (node.parentId <= 0) {
            sheet.item(getString(R.string.act_set_cover), getString(R.string.act_set_cover_sub),
                            R.drawable.ic_star, R.color.iconSettings, () -> {
                                store.moveImageFirst(p.id);
                                pics = store.images(node.id);
                                rebuildStrip();
                                albumAdapter.notifyDataSetChanged();
                                showPic(0);
                                Ui.toast(this, getString(R.string.toast_cover_set));
                            });
        }
        sheet.danger(getString(R.string.act_delete_photo),
                getString(R.string.sub_delete_photo), () -> deletePhoto(p))
                .show();
    }

    /** 相册内照片命名：名称显示在网格缩略图下方 */
    private void renamePhoto(Store.Pic p) {
        Ui.promptText(this, getString(R.string.dlg_rename_photo),
                getString(R.string.hint_rename_photo),
                p.label == null ? "" : p.label, false, s -> {
                    store.renameImage(p.id, s);
                    pics = store.images(node.id);
                    albumAdapter.notifyDataSetChanged();
                    Ui.toast(this, getString(R.string.toast_photo_renamed_fmt, s));
                });
    }

    private void deletePhoto(Store.Pic p) {
        Ui.confirm(this, getString(R.string.dlg_delete_photo),
                getString(R.string.dlg_delete_photo_msg),
                getString(R.string.btn_delete), () -> {
                    store.deleteImage(p.id);
                    lastPicByNode.remove(node.id);
                    loadCurrent();
                });
    }

    /** 首次见到照片时提示长按操作（每台设备仅一次），点击或超时后收起；相册与普通层级各提示一次 */
    private void maybeShowLongPressTip() {
        if (pics.isEmpty() || tipShowing) return;
        boolean album = isGallery();
        String key = album ? "tip_longpress_album" : "tip_longpress_node";
        SharedPreferences sp = getSharedPreferences("app", MODE_PRIVATE);
        if (sp.getBoolean(key, false)) return;
        sp.edit().putBoolean(key, true).apply();
        tipShowing = true;
        hideTip = this::hideLongPressTip;
        hintBar.setText(album ? R.string.tip_album_longpress
                : node.parentId <= 0 ? R.string.hint_longpress : R.string.hint_longpress_sub);
        hintBar.setVisibility(View.VISIBLE);
        hintBar.setOnClickListener(v -> hideLongPressTip());
        hintBar.postDelayed(hideTip, 6000);
    }

    private void hideLongPressTip() {
        if (hideTip != null) {
            hintBar.removeCallbacks(hideTip);
            hideTip = null;
        }
        if (!tipShowing) return;
        tipShowing = false;
        // 放置模式的提示条不受影响
        if (!placing) hintBar.setVisibility(View.GONE);
    }

    // ---------------- 相册网格 ----------------

    /** 网格缩略图：与缩略图条共用同一份缓存，长相册滚动不重复解码 */
    private Bitmap decodeThumb(String name) {
        Bitmap b = thumbCache.get(name);
        if (b != null) return b;
        b = Store.decodeScaled(store.thumbFile(name), 260);
        if (b != null) thumbCache.put(name, b);
        return b;
    }

    /** 单列宽度（不含单元格外边距），网格单元按此裁成正方形 */
    private int cellSide() {
        int w = gridAlbum.getWidth();
        if (w <= 0) w = getResources().getDisplayMetrics().widthPixels;
        int spacing = Ui.dp(this, 4);
        int used = gridAlbum.getPaddingLeft() + gridAlbum.getPaddingRight() + spacing * 3;
        return Math.max(1, (w - used) / 4 - Ui.dp(this, 4));
    }

    private void startAlbumDrag(int pos, View cell) {
        dragFrom = pos;
        ClipData cd = ClipData.newPlainText("pic", String.valueOf(pics.get(pos).id));
        if (Build.VERSION.SDK_INT >= 24) cell.startDragAndDrop(cd, new View.DragShadowBuilder(cell), null, 0);
        else cell.startDrag(cd, new View.DragShadowBuilder(cell), null, 0);
    }

    private boolean onAlbumDrag(DragEvent ev) {
        switch (ev.getAction()) {
            case DragEvent.ACTION_DRAG_LOCATION:
                lastDragY = ev.getY();
                if (dragScroll == null) {
                    dragScroll = this::autoScrollAlbumDrag;
                    gridAlbum.postOnAnimation(dragScroll);
                }
                int to = gridAlbum.pointToPosition((int) ev.getX(), (int) ev.getY());
                if (to >= 0 && to < pics.size() && to != dragFrom) {
                    Collections.swap(pics, dragFrom, to);
                    dragFrom = to;
                    albumAdapter.notifyDataSetChanged();
                    gridAlbum.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                }
                return true;
            case DragEvent.ACTION_DRAG_ENDED:
                if (dragFrom >= 0) {
                    dragFrom = -1;
                    store.reorderImages(pics);
                    albumAdapter.notifyDataSetChanged();
                }
                if (dragScroll != null) {
                    gridAlbum.removeCallbacks(dragScroll);
                    dragScroll = null;
                }
                return true;
            default:
                return true;
        }
    }

    /** 拖拽悬停在上/下边缘时自动滚动网格 */
    private void autoScrollAlbumDrag() {
        if (dragFrom < 0 || dragScroll == null) return;
        int edge = Ui.dp(this, 72);
        int step = Ui.dp(this, 16);
        int dy = 0;
        if (lastDragY < edge) dy = -step;
        else if (lastDragY > gridAlbum.getHeight() - edge) dy = step;
        if (dy != 0) gridAlbum.smoothScrollBy(dy, 100);
        gridAlbum.postOnAnimation(dragScroll);
    }

    /** 相册网格适配器：照片格 + 末尾「添加」格 */
    private class AlbumAdapter extends BaseAdapter {
        private static final int TYPE_PHOTO = 0;
        private static final int TYPE_ADD = 1;

        @Override
        public int getCount() {
            return pics.size() + 1;
        }

        @Override
        public Object getItem(int pos) {
            return pos < pics.size() ? pics.get(pos) : null;
        }

        @Override
        public long getItemId(int pos) {
            return pos < pics.size() ? pics.get(pos).id : -1;
        }

        @Override
        public int getViewTypeCount() {
            return 2;
        }

        @Override
        public int getItemViewType(int pos) {
            return pos == pics.size() ? TYPE_ADD : TYPE_PHOTO;
        }

        @Override
        public View getView(int pos, View cv, ViewGroup parent) {
            if (getItemViewType(pos) == TYPE_ADD) {
                View tile = cv instanceof FrameLayout ? cv : buildAddTile();
                int side = cellSide();
                if (tile.getLayoutParams() == null || tile.getLayoutParams().height != side) {
                    tile.setLayoutParams(new GridView.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, side));
                }
                return tile;
            }
            View v = cv;
            if (v == null || v.findViewById(R.id.imgWrap) == null) {
                v = LayoutInflater.from(NodeActivity.this)
                        .inflate(R.layout.item_album_cell, parent, false);
            }
            Store.Pic p = pics.get(pos);
            View wrap = v.findViewById(R.id.imgWrap);
            int side = cellSide();
            if (wrap.getLayoutParams().height != side) wrap.getLayoutParams().height = side;
            ImageView im = v.findViewById(R.id.img);
            Bitmap b = decodeThumb(p.thumb);
            if (b != null) im.setImageBitmap(b);
            // 每格右上「⋮」：直接呼出这张照片的操作菜单（命名/删除）
            v.findViewById(R.id.btnMore).setOnClickListener(more -> showPicMenu(pos));
            // 每格下方始终有标题：已命名显示名字，未命名显示「未命名 + 时间」占位
            String label = p.label == null ? "" : p.label.trim();
            boolean named = !label.isEmpty();
            String caption = named ? label
                    : getString(R.string.unnamed_photo_time_fmt, hmFmt.format(new Date(p.createdAt)));
            TextView tv = v.findViewById(R.id.label);
            tv.setVisibility(View.VISIBLE);
            tv.setText(caption);
            tv.setAlpha(named ? 1f : 0.65f);
            im.setContentDescription(caption);
            v.setAlpha(pos == dragFrom ? 0.35f : 1f);
            return v;
        }

        /** 末尾「添加」格：与缩略图条上的添加块同款虚线框 */
        private View buildAddTile() {
            FrameLayout add = new FrameLayout(NodeActivity.this);
            add.setBackgroundResource(R.drawable.bg_add_tile);
            add.setForeground(getResources().getDrawable(R.drawable.bg_quiet_ripple));
            ImageView plus = new ImageView(NodeActivity.this);
            plus.setImageResource(R.drawable.ic_add_teal);
            plus.setContentDescription(getString(R.string.cd_add_photo));
            plus.setLayoutParams(new FrameLayout.LayoutParams(
                    Ui.dp(NodeActivity.this, 28), Ui.dp(NodeActivity.this, 28), Gravity.CENTER));
            add.addView(plus);
            return add;
        }
    }

    // ---------------- 添加照片 ----------------

    private void chooseAddPhoto() {
        Ui.sheet(this, getString(R.string.add_photo), null)
                .item(getString(R.string.act_camera), getString(R.string.act_camera_sub),
                        R.drawable.ic_camera_white, R.color.primary, () -> launchCamera())
                .item(getString(R.string.act_gallery), getString(R.string.act_gallery_sub),
                        R.drawable.ic_gallery, R.color.iconBackup, () -> launchGallery())
                .show();
    }

    private void launchCamera() {
        pendingCapture = new File(store.pendingDir, "cap_" + System.currentTimeMillis() + ".jpg");
        Uri out = FilesProvider.forFile(pendingCapture);
        Intent it = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        it.putExtra(MediaStore.EXTRA_OUTPUT, out);
        it.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        try {
            startActivityForResult(it, REQ_CAMERA);
        } catch (Exception e) {
            Ui.toast(this, getString(R.string.toast_no_camera));
            pendingCapture = null;
        }
    }

    private void launchGallery() {
        Intent it = new Intent(Intent.ACTION_GET_CONTENT);
        it.setType("image/*");
        it.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        it.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(it, REQ_GALLERY);
        } catch (Exception e) {
            Ui.toast(this, getString(R.string.toast_no_gallery));
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (Backup.handleResult(this, req, res, data)) return;
        if (req == REQ_CAMERA) {
            File f = pendingCapture;
            pendingCapture = null;
            if (res == RESULT_OK && f != null && f.exists() && f.length() > 0) {
                processPending(new File[]{f});
            } else {
                if (f != null) f.delete();
                if (res == RESULT_OK) Ui.toast(this, getString(R.string.toast_capture_failed));
            }
            return;
        }
        if (req == REQ_GALLERY && res == RESULT_OK && data != null) {
            List<Uri> uris = new ArrayList<>();
            ClipData cd = data.getClipData();
            if (cd != null) {
                for (int i = 0; i < cd.getItemCount(); i++) uris.add(cd.getItemAt(i).getUri());
            } else if (data.getData() != null) {
                uris.add(data.getData());
            }
            if (uris.isEmpty()) return;
            List<File> tmps = new ArrayList<>();
            for (Uri u : uris) {
                File t = copyToPending(u);
                if (t != null) tmps.add(t);
            }
            if (tmps.isEmpty()) {
                Ui.toast(this, getString(R.string.toast_read_failed));
                return;
            }
            processPending(tmps.toArray(new File[0]));
        }
    }

    private File copyToPending(Uri u) {
        File t = new File(store.pendingDir, "pick_" + (pickSeq++) + "_" + System.currentTimeMillis() + ".jpg");
        try (InputStream in = getContentResolver().openInputStream(u);
             OutputStream os = new FileOutputStream(t)) {
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
            return t;
        } catch (Exception e) {
            t.delete();
            return null;
        }
    }

    private void processPending(final File[] files) {
        final long nodeId = node.id;
        final AlertDialog pd = Ui.progress(this, getString(R.string.progress_saving_photos));
        pd.show();
        new Thread(() -> {
            int ok = 0;
            for (File f : files) {
                if (store.addImageFromPending(nodeId, f) != null) ok++;
            }
            final int okCount = ok;
            runOnUiThread(() -> {
                try {
                    pd.dismiss();
                } catch (Throwable ignored) {
                }
                if (node == null || node.id != nodeId) return; // 已导航离开
                pics = store.images(nodeId);
                if (okCount > 0) {
                    rebuildStrip();
                    albumAdapter.notifyDataSetChanged();
                    if (isGallery() && !galleryViewing) {
                        // 相册网格模式：留在网格，新照片追加在末尾
                        applyModeUi();
                    } else {
                        int idx = pics.isEmpty() ? 0 : Math.max(0, pics.size() - okCount);
                        applyModeUi();
                        showPic(idx);
                        maybeShowLongPressTip();
                    }
                    Ui.toast(this, getString(R.string.toast_saved_fmt, okCount));
                } else {
                    Ui.toast(this, getString(R.string.toast_save_failed));
                }
            });
        }).start();
    }

    // ---------------- 交互点 ----------------

    /** 进入/退出放置模式；gallery=true 时放置的是「展开（相册）」标记 */
    private void togglePlacing(boolean gallery) {
        if (isGallery()) return; // 相册内不放交互点
        placing = !placing;
        placingGallery = placing && gallery;
        if (placing) hideLongPressTip();
        iv.setPlacementMode(placing);
        hintBar.setText(placingGallery ? R.string.hint_placing_album : R.string.hint_placing);
        hintBar.setVisibility(placing ? View.VISIBLE : View.GONE);
        stylePlaceButton(btnHotspot, placing && !placingGallery,
                R.drawable.ic_adjust_teal, R.string.add_hotspot);
        stylePlaceButton(btnExpand, placing && placingGallery,
                R.drawable.ic_grid_teal, R.string.add_expand);
        if (placing && (currentPic < 0 || currentPic >= pics.size())) {
            Ui.toast(this, getString(placingGallery
                    ? R.string.toast_need_photo_first_album : R.string.toast_need_photo_first));
        }
    }

    private void stylePlaceButton(Button b, boolean on, int iconRes, int textRes) {
        b.setText(on ? R.string.cancel_placing : textRes);
        b.setBackgroundResource(on ? R.drawable.bg_btn_accent_on : R.drawable.bg_tonal);
        b.setTextColor(on
                ? Ui.color(this, R.attr.onAccent) : Ui.color(this, R.attr.onPrimaryContainer));
        Drawable icon = getResources().getDrawable(
                on ? R.drawable.ic_close_white : iconRes);
        b.setCompoundDrawablesWithIntrinsicBounds(icon, null, null, null);
    }

    @Override
    public void onImageTap(float x, float y) {
        if (isGallery()) return; // 相册内照片不放交互点
        if (currentPic < 0 || currentPic >= pics.size()) return;
        final long imageId = pics.get(currentPic).id;
        if (placingGallery) {
            String suggest = getString(R.string.suggest_album_name_fmt,
                    store.countChildren(node.id) + 1);
            Ui.promptText(this, getString(R.string.dlg_name_album),
                    getString(R.string.hint_name_album), suggest, false, name -> {
                        long childId = store.createNode(node.id, name, Store.KIND_GALLERY);
                        store.addHotspot(imageId, x, y, childId);
                        refreshHotspots();
                        Ui.toast(this, getString(R.string.toast_album_created_fmt, name));
                    });
            return;
        }
        String suggest = getString(R.string.suggest_name_fmt, store.countChildren(node.id) + 1);
        Ui.promptText(this, getString(R.string.dlg_name_spot),
                getString(R.string.hint_name_spot), suggest, false, name -> {
            long childId = store.createNode(node.id, name);
            store.addHotspot(imageId, x, y, childId);
            refreshHotspots();
            Ui.toast(this, getString(R.string.toast_spot_created_fmt, name));
        });
    }

    @Override
    public void onHotspotTap(Store.Hotspot h) {
        enterChild(h.childId);
    }

    @Override
    public void onImageLongPress() {
        // 相册查看大图时，长按照片空白处唤出这张照片的操作菜单（命名/删除）
        if (isGallery() && currentPic >= 0 && currentPic < pics.size()) {
            iv.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            showPicMenu(currentPic);
        }
    }

    @Override
    public void onPhotoSwipe(int dir) {
        // 大图左右快滑切换上一张/下一张：放置模式不切，只有一张也没有切换意义
        if (placing || pics.size() < 2) return;
        if (isGallery() && !galleryViewing) return;
        int cur = currentPic >= 0 ? currentPic : (dir > 0 ? -1 : pics.size());
        int next = cur + dir;
        if (next < 0 || next >= pics.size()) return;
        iv.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        showPic(next);
    }

    @Override
    public void onHotspotLongPress(Store.Hotspot h) {
        iv.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        Store.Node child = store.node(h.childId);
        final String nm = child == null ? getString(R.string.default_child_name) : child.name;
        String sub = getString(child != null && child.kind == Store.KIND_GALLERY
                ? R.string.sheet_spot_sub_album : R.string.sheet_spot_sub);
        Ui.sheet(this, nm, sub)
                .item(getString(R.string.act_open), R.drawable.ic_open, R.color.primary,
                        () -> enterChild(h.childId))
                .item(getString(R.string.act_rename), R.drawable.ic_edit, R.color.primary, () ->
                        Ui.promptText(this, getString(R.string.act_rename), "", nm, false, s -> {
                            store.renameNode(h.childId, s);
                            refreshHotspots();
                        }))
                .item(getString(R.string.act_remove_pin), getString(R.string.act_remove_pin_sub),
                        R.drawable.ic_close_white, R.color.iconNeutral, () -> {
                            store.deleteHotspot(h.id);
                            refreshHotspots();
                        })
                .danger(getString(R.string.act_delete_child),
                        getString(R.string.sub_delete_child_fmt, nm), () ->
                        Ui.confirm(this, getString(R.string.dlg_delete_child),
                                getString(R.string.dlg_delete_subtree_msg_fmt, nm),
                                getString(R.string.btn_delete), () -> {
                                    store.deleteNodeDeep(h.childId);
                                    refreshHotspots();
                                }))
                .show();
    }

    // ---------------- 右上菜单 ----------------

    private void showMenu() {
        boolean gallery = isGallery();
        Ui.Sheet sheet = Ui.sheet(this, node.name, gallery
                ? getString(R.string.album_menu_sub_fmt, pics.size())
                : getString(R.string.sheet_menu_sub))
                .item(getString(R.string.act_rename), R.drawable.ic_edit, R.color.primary, () ->
                        Ui.promptText(this, getString(R.string.act_rename), "", node.name, false, s -> {
                            store.renameNode(node.id, s);
                            loadCurrent();
                        }))
                .item(getString(R.string.act_edit_note), getString(R.string.act_edit_note_sub),
                        R.drawable.ic_note, R.color.iconAppearance, () ->
                        Ui.promptText(this, getString(R.string.dlg_note),
                                getString(R.string.hint_note),
                                node.note == null ? "" : node.note, true, s -> {
                                    store.setNote(node.id, s);
                                    node = store.node(node.id);
                                    Ui.toast(this, getString(R.string.toast_note_saved));
                                }));
        if (!gallery) {
            sheet.item(getString(R.string.act_child_list), R.drawable.ic_list, R.color.iconBackup,
                    () -> showChildList());
        }
        sheet.item(getString(R.string.act_sidebar), R.drawable.ic_menu,
                        R.color.iconAbout, () -> Sidebar.show(this))
                .danger(getString(R.string.act_delete_level),
                        getString(gallery ? R.string.sub_delete_album_level
                                : R.string.sub_delete_subtree), () ->
                        Ui.confirm(this, getString(R.string.act_delete_level),
                                getString(R.string.dlg_delete_subtree_msg_fmt, node.name),
                                getString(R.string.btn_delete), () -> {
                                    store.deleteNodeDeep(node.id);
                                    if (path.size() > 1) {
                                        path.remove(path.size() - 1);
                                        loadCurrent();
                                    } else {
                                        finish();
                                    }
                                }))
                .show();
    }

    private void showChildList() {
        List<Store.Node> kids = store.children(node.id);
        if (kids.isEmpty()) {
            Ui.toast(this, getString(R.string.toast_no_children));
            return;
        }
        Ui.Sheet sheet = Ui.sheet(this, getString(R.string.sheet_child_list),
                getString(R.string.fmt_total_spots, kids.size()));
        for (Store.Node k : kids) {
            String sub;
            if (k.kind == Store.KIND_GALLERY) {
                int n = store.images(k.id).size();
                sub = n > 0 ? getString(R.string.album_item_sub_fmt, n)
                        : getString(R.string.album_item_empty);
            } else {
                sub = Ui.subCounts(this, store.countChildren(k.id), store.images(k.id).size());
            }
            sheet.child(k.name, sub, Ui.tileColor(k.id), () -> enterChild(k.id));
        }
        sheet.show();
    }
}
