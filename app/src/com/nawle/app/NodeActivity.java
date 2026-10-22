package com.nawle.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 层级页：所有层级（空间/柜子/抽屉/物品…）复用本页。
 * 内部维护导航栈实现无限层级钻取，面包屑可点击跳回任意层级。
 */
public class NodeActivity extends Activity implements InteractiveImageView.Host {

    public static final String EXTRA_NODE_ID = "node_id";
    private static final int REQ_CAMERA = 1;
    private static final int REQ_GALLERY = 2;

    private Store store;
    private InteractiveImageView iv;
    private LinearLayout crumbWrap;
    private LinearLayout strip;
    private HorizontalScrollView stripScroll;
    private LinearLayout emptyView;
    private TextView hintBar;
    private Button btnHotspot;

    /** 导航路径（节点 id），最后一个为当前层级；由进入子项/返回维护 */
    private final List<Long> path = new ArrayList<>();
    /** 记住每个节点上次查看的图片，返回时恢复 */
    private final Map<Long, Long> lastPicByNode = new HashMap<>();

    private Store.Node node;
    private List<Store.Pic> pics = new ArrayList<>();
    private int currentPic = -1;
    private boolean placing = false;
    private File pendingCapture;
    private int loadToken = 0;
    private int pickSeq = 0;
    private int themeSeqApplied;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(Ui.wrapTheme(base));
        themeSeqApplied = Ui.themeSeq();
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_node);
        store = Store.get(this);
        iv = findViewById(R.id.ivImage);
        iv.setHost(this);
        crumbWrap = findViewById(R.id.crumbs);
        strip = findViewById(R.id.strip);
        stripScroll = findViewById(R.id.stripScroll);
        emptyView = findViewById(R.id.emptyView);
        hintBar = findViewById(R.id.hintBar);
        btnHotspot = findViewById(R.id.btnHotspot);

        long id = getIntent().getLongExtra(EXTRA_NODE_ID, -1);
        if (id <= 0 || store.node(id) == null) {
            finish();
            return;
        }
        path.add(id);

        findViewById(R.id.btnBack).setOnClickListener(v -> goBack());
        findViewById(R.id.btnMenu).setOnClickListener(v -> showMenu());
        findViewById(R.id.btnAddPhoto).setOnClickListener(v -> chooseAddPhoto());
        findViewById(R.id.btnFirstPhoto).setOnClickListener(v -> chooseAddPhoto());
        btnHotspot.setOnClickListener(v -> togglePlacing());

        loadCurrent();
    }

    // ---------------- 导航 ----------------

    private void goBack() {
        if (placing) {
            togglePlacing();
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
        if (themeSeqApplied != Ui.themeSeq()) recreate();
    }

    private void enterChild(long id) {
        Store.Node n = store.node(id);
        if (n == null) {
            refreshHotspots();
            return;
        }
        if (placing) togglePlacing();
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
        showPic(idx);
        rebuildCrumb();
        rebuildStrip();
        updateEmptyState();
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
        crumbWrap.addView(crumbSeg("首页", () -> finish(), false));
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
            tv.setTextColor(getResources().getColor(R.color.onPrimaryContainer));
            tv.setTypeface(Typeface.DEFAULT_BOLD);
            tv.setBackgroundResource(R.drawable.bg_crumb_pill);
            tv.setPadding(Ui.dp(this, 12), Ui.dp(this, 5), Ui.dp(this, 12), Ui.dp(this, 5));
        } else {
            tv.setTextColor(getResources().getColor(R.color.onHeaderDim));
            tv.setPadding(Ui.dp(this, 6), 0, Ui.dp(this, 6), 0);
        }
        tv.setOnClickListener(v -> r.run());
        return tv;
    }

    private TextView crumbSep() {
        TextView tv = new TextView(this);
        tv.setText("›");
        tv.setTextColor(getResources().getColor(R.color.onHeaderFaint));
        tv.setTextSize(14);
        tv.setPadding(0, 0, Ui.dp(this, 2), 0);
        tv.setGravity(Gravity.CENTER_VERTICAL);
        return tv;
    }

    // ---------------- 缩略图条 ----------------

    private void rebuildStrip() {
        strip.removeAllViews();
        for (int i = 0; i < pics.size(); i++) {
            final int idx = i;
            View v = LayoutInflater.from(this).inflate(R.layout.item_thumb, strip, false);
            ImageView im = v.findViewById(R.id.img);
            im.setContentDescription(getString(R.string.photo_desc, idx + 1));
            File tf = store.thumbFile(pics.get(i).thumb);
            Bitmap b = Store.decodeScaled(tf, 200);
            if (b != null) im.setImageBitmap(b);
            v.setOnClickListener(vv -> showPic(idx));
            v.setOnLongClickListener(vv -> {
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
        new AlertDialog.Builder(this)
                .setTitle("照片操作")
                .setItems(new String[]{"设为封面（移到最前）", "删除这张照片"}, (d, w) -> {
                    if (w == 0) {
                        store.moveImageFirst(p.id);
                        pics = store.images(node.id);
                        rebuildStrip();
                        showPic(0);
                        Ui.toast(this, "已设为封面");
                    } else {
                        Ui.confirm(this, "删除照片", "删除后不可恢复，照片上的交互点也会一并删除。",
                                "删除", () -> {
                                    store.deleteImage(p.id);
                                    lastPicByNode.remove(node.id);
                                    loadCurrent();
                                });
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void updateEmptyState() {
        emptyView.setVisibility(pics.isEmpty() ? View.VISIBLE : View.GONE);
        iv.setVisibility(pics.isEmpty() ? View.INVISIBLE : View.VISIBLE);
    }

    // ---------------- 添加照片 ----------------

    private void chooseAddPhoto() {
        new AlertDialog.Builder(this)
                .setTitle("添加照片")
                .setItems(new String[]{"拍照", "从相册选择（可多选）"}, (d, w) -> {
                    if (w == 0) launchCamera();
                    else launchGallery();
                })
                .setNegativeButton("取消", null)
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
            Ui.toast(this, "没有可用的相机应用");
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
            Ui.toast(this, "没有可用的相册应用");
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
                if (res == RESULT_OK) Ui.toast(this, "拍照失败，请重试");
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
                Ui.toast(this, "无法读取所选图片");
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
        final AlertDialog pd = Ui.progress(this, "正在保存照片…");
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
                    int idx = pics.isEmpty() ? 0 : Math.max(0, pics.size() - okCount);
                    rebuildStrip();
                    updateEmptyState();
                    showPic(idx);
                    Ui.toast(this, "已保存 " + okCount + " 张照片");
                } else {
                    Ui.toast(this, "保存失败，请重试");
                }
            });
        }).start();
    }

    // ---------------- 交互点 ----------------

    private void togglePlacing() {
        placing = !placing;
        iv.setPlacementMode(placing);
        hintBar.setVisibility(placing ? View.VISIBLE : View.GONE);
        btnHotspot.setText(placing ? R.string.cancel_placing : R.string.add_hotspot);
        btnHotspot.setBackgroundResource(placing ? R.drawable.bg_btn_accent_on : R.drawable.bg_tonal);
        btnHotspot.setTextColor(getResources().getColor(
                placing ? R.color.onPrimary : R.color.onPrimaryContainer));
        Drawable icon = getResources().getDrawable(
                placing ? R.drawable.ic_close_white : R.drawable.ic_adjust_teal);
        btnHotspot.setCompoundDrawablesWithIntrinsicBounds(icon, null, null, null);
        if (placing && (currentPic < 0 || currentPic >= pics.size())) {
            Ui.toast(this, "请先添加一张照片，再放置交互点");
        }
    }

    @Override
    public void onImageTap(float x, float y) {
        if (currentPic < 0 || currentPic >= pics.size()) return;
        final long imageId = pics.get(currentPic).id;
        String suggest = "收纳位" + (store.countChildren(node.id) + 1);
        Ui.promptText(this, "命名这个收纳位", "例如：抽屉1、上层、工具箱", suggest, false, name -> {
            long childId = store.createNode(node.id, name);
            store.addHotspot(imageId, x, y, childId);
            refreshHotspots();
            Ui.toast(this, "已创建「" + name + "」，点击圆点即可进入");
        });
    }

    @Override
    public void onHotspotTap(Store.Hotspot h) {
        enterChild(h.childId);
    }

    @Override
    public void onHotspotLongPress(Store.Hotspot h) {
        iv.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        Store.Node child = store.node(h.childId);
        final String nm = child == null ? "子项" : child.name;
        new AlertDialog.Builder(this)
                .setTitle(nm)
                .setItems(new String[]{"打开", "重命名", "移除标记（保留子项）", "删除子项及全部内容"}, (d, w) -> {
                    if (w == 0) {
                        enterChild(h.childId);
                    } else if (w == 1) {
                        Ui.promptText(this, "重命名", "", nm, false, s -> {
                            store.renameNode(h.childId, s);
                            refreshHotspots();
                        });
                    } else if (w == 2) {
                        store.deleteHotspot(h.id);
                        refreshHotspots();
                    } else {
                        Ui.confirm(this, "删除子项",
                                "将删除「" + nm + "」及其全部子层级和照片，不可恢复。",
                                "删除", () -> {
                                    store.deleteNodeDeep(h.childId);
                                    refreshHotspots();
                                });
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // ---------------- 右上菜单 ----------------

    private void showMenu() {
        new AlertDialog.Builder(this)
                .setTitle(node.name)
                .setItems(new String[]{"重命名", "编辑备注", "查看子项列表", "删除本层级",
                        "备份与恢复…", "关于与联系…"}, (d, w) -> {
                    if (w == 0) {
                        Ui.promptText(this, "重命名", "", node.name, false, s -> {
                            store.renameNode(node.id, s);
                            loadCurrent();
                        });
                    } else if (w == 1) {
                        Ui.promptText(this, "备注", "记录这里的存放说明", node.note == null ? "" : node.note,
                                true, s -> {
                                    store.setNote(node.id, s);
                                    node = store.node(node.id);
                                    Ui.toast(this, "备注已保存");
                                });
                    } else if (w == 2) {
                        showChildList();
                    } else if (w == 4) {
                        Backup.showMenu(this);
                    } else if (w == 5) {
                        startActivity(new Intent(this, AboutActivity.class));
                    } else {
                        Ui.confirm(this, "删除本层级",
                                "将删除「" + node.name + "」及其全部子层级和照片，不可恢复。",
                                "删除", () -> {
                                    store.deleteNodeDeep(node.id);
                                    if (path.size() > 1) {
                                        path.remove(path.size() - 1);
                                        loadCurrent();
                                    } else {
                                        finish();
                                    }
                                });
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showChildList() {
        List<Store.Node> kids = store.children(node.id);
        if (kids.isEmpty()) {
            Ui.toast(this, "还没有子项：先添加照片，再放置交互点");
            return;
        }
        String[] items = new String[kids.size()];
        for (int i = 0; i < kids.size(); i++) {
            Store.Node k = kids.get(i);
            items[i] = k.name + "（" + store.countChildren(k.id) + " 子项 · "
                    + store.images(k.id).size() + " 图）";
        }
        new AlertDialog.Builder(this)
                .setTitle("子项列表")
                .setItems(items, (d, i) -> enterChild(kids.get(i).id))
                .setNegativeButton("关闭", null)
                .show();
    }
}
