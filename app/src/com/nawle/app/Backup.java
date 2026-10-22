package com.nawle.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * 备份与恢复：把全部数据（数据库 + 照片 + 缩略图）打包成一个 zip。
 * 通过系统的文件选择器（SAF）读写，不需要任何存储权限。
 *
 * zip 结构：
 *   info.txt            格式版本与统计信息
 *   nawle.db            SQLite 数据库
 *   images/xxx.jpg      原图
 *   thumbs/xxx.jpg      缩略图
 */
public final class Backup {
    private Backup() {}

    private static final int REQ_EXPORT = 901;
    private static final int REQ_IMPORT = 902;
    private static final String DB_NAME = "nawle.db";
    private static final String MIME_ZIP = "application/zip";

    /** 侧边栏入口：选择导出或导入（自绘视图，避免部分 ROM 上 message+items 不兼容） */
    public static void showMenu(final Activity act) {
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(act, 20);
        box.setPadding(pad, Ui.dp(act, 8), pad, Ui.dp(act, 6));

        TextView msg = new TextView(act);
        msg.setText("备份是包含全部空间、层级、照片和交互点的一个 zip 文件。\n\n" +
                "· 换手机：旧手机导出 → 微信/网盘/数据线传到新手机 → 新手机导入\n" +
                "· 防丢失：建议每隔一段时间导出一份留存");
        msg.setTextSize(14);
        msg.setTextColor(act.getResources().getColor(R.color.onSurfaceVariant));
        box.addView(msg);

        Button btnExport = new Button(act);
        btnExport.setText("导出备份（生成 zip 文件）");
        btnExport.setAllCaps(false);
        btnExport.setTextColor(act.getResources().getColor(R.color.onPrimary));
        btnExport.setTextSize(15);
        btnExport.setBackgroundResource(R.drawable.bg_btn_primary);
        btnExport.setCompoundDrawablesWithIntrinsicBounds(
                act.getResources().getDrawable(R.drawable.ic_upload_white), null, null, null);
        btnExport.setCompoundDrawablePadding(Ui.dp(act, 8));
        LinearLayout.LayoutParams lp1 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(act, 48));
        lp1.topMargin = Ui.dp(act, 18);
        btnExport.setLayoutParams(lp1);
        box.addView(btnExport);

        Button btnImport = new Button(act);
        btnImport.setText("导入备份（覆盖本机现有数据）");
        btnImport.setAllCaps(false);
        btnImport.setTextColor(0xFFFFFFFF);
        btnImport.setTextSize(15);
        btnImport.setBackgroundResource(R.drawable.bg_btn_accent);
        btnImport.setCompoundDrawablesWithIntrinsicBounds(
                act.getResources().getDrawable(R.drawable.ic_download_white), null, null, null);
        btnImport.setCompoundDrawablePadding(Ui.dp(act, 8));
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(act, 48));
        lp2.topMargin = Ui.dp(act, 12);
        btnImport.setLayoutParams(lp2);
        box.addView(btnImport);

        final AlertDialog[] holder = new AlertDialog[1];
        btnExport.setOnClickListener(v -> {
            if (holder[0] != null) holder[0].dismiss();
            startExport(act);
        });
        btnImport.setOnClickListener(v -> {
            if (holder[0] != null) holder[0].dismiss();
            startImport(act);
        });

        holder[0] = new AlertDialog.Builder(act)
                .setTitle("备份与恢复")
                .setView(box)
                .setNegativeButton("取消", null)
                .show();
    }

    // ---------------- 导出 ----------------

    public static void startExport(Activity act) {
        Store.get(act).checkpoint();
        String name = "收哪了-备份-" +
                new SimpleDateFormat("yyyyMMdd-HHmm", Locale.CHINA).format(new Date()) + ".zip";
        Intent it = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        it.addCategory(Intent.CATEGORY_OPENABLE);
        it.setType(MIME_ZIP);
        it.putExtra(Intent.EXTRA_TITLE, name);
        try {
            act.startActivityForResult(it, REQ_EXPORT);
        } catch (Exception e) {
            Ui.toast(act, "没有可用的文件选择器");
        }
    }

    private static void runExport(final Activity act, final Uri target) {
        final AlertDialog pd = Ui.progress(act, "正在导出备份…");
        pd.show();
        new Thread(() -> {
            String err = null;
            try {
                Store st = Store.get(act);
                st.checkpoint();
                try (ZipOutputStream zos = new ZipOutputStream(
                        act.getContentResolver().openOutputStream(target))) {
                    StringBuilder info = new StringBuilder();
                    info.append("format=1\n")
                        .append("app=").append(About.versionName(act)).append('\n')
                        .append("time=").append(new SimpleDateFormat("yyyy-MM-dd HH:mm",
                                Locale.CHINA).format(new Date())).append('\n')
                        .append("spaces=").append(st.children(0).size()).append('\n');
                    zos.putNextEntry(new ZipEntry("info.txt"));
                    zos.write(info.toString().getBytes("UTF-8"));
                    zos.closeEntry();

                    File db = act.getDatabasePath(DB_NAME);
                    if (db != null && db.exists()) addFile(zos, db, DB_NAME);
                    addDir(zos, st.imgDir, "images");
                    addDir(zos, st.thumbDir, "thumbs");
                }
            } catch (Exception e) {
                err = "导出失败：" + e.getMessage();
            }
            final String e2 = err;
            act.runOnUiThread(() -> {
                try {
                    pd.dismiss();
                } catch (Throwable ignored) {
                }
                Ui.toast(act, e2 == null ? "导出成功，请妥善保存该 zip 文件" : e2);
            });
        }).start();
    }

    private static void addFile(ZipOutputStream zos, File f, String entryName) throws Exception {
        zos.putNextEntry(new ZipEntry(entryName));
        try (InputStream in = new FileInputStream(f)) {
            copy(in, zos);
        }
        zos.closeEntry();
    }

    private static void addDir(ZipOutputStream zos, File dir, String prefix) throws Exception {
        File[] fs = dir.listFiles();
        if (fs == null) return;
        for (File f : fs) {
            if (f.isFile()) addFile(zos, f, prefix + "/" + f.getName());
        }
    }

    // ---------------- 导入 ----------------

    public static void startImport(final Activity act) {
        Ui.confirm(act, "导入备份",
                "将用备份文件覆盖本机现有全部数据（空间、层级、照片），覆盖后无法恢复。\n\n确定继续吗？",
                "选择文件并导入", () -> {
                    Intent it = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    it.addCategory(Intent.CATEGORY_OPENABLE);
                    it.setType(MIME_ZIP);
                    it.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{MIME_ZIP, "application/octet-stream"});
                    try {
                        act.startActivityForResult(it, REQ_IMPORT);
                    } catch (Exception e) {
                        Ui.toast(act, "没有可用的文件选择器");
                    }
                });
    }

    private static void runImport(final Activity act, final Uri src) {
        final AlertDialog pd = Ui.progress(act, "正在导入备份…");
        pd.show();
        new Thread(() -> {
            String err = importSync(act, src);
            act.runOnUiThread(() -> {
                try {
                    pd.dismiss();
                } catch (Throwable ignored) {
                }
                if (err == null) {
                    Ui.toast(act, "导入成功");
                    Intent it = new Intent(act, MainActivity.class);
                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    act.startActivity(it);
                } else {
                    Ui.toast(act, err);
                }
            });
        }).start();
    }

    private static String importSync(Activity act, Uri src) {
        File staging = new File(act.getCacheDir(), "import_staging");
        deleteRecursive(staging);
        File dbTarget = act.getDatabasePath(DB_NAME).getParentFile();

        // 第一步：全部解压到暂存目录（此阶段不动现有数据）
        try (InputStream in = act.getContentResolver().openInputStream(src);
             ZipInputStream zis = new ZipInputStream(in)) {
            byte[] buf = new byte[16384];
            ZipEntry e;
            int files = 0;
            while ((e = zis.getNextEntry()) != null) {
                String name = e.getName();
                if (name.contains("..") || name.startsWith("/")) return "备份文件格式不合法";
                File out;
                if (name.equals(DB_NAME) || name.startsWith("databases/")) {
                    out = new File(staging, DB_NAME);
                } else if (name.startsWith("images/")) {
                    out = new File(new File(staging, "images"), name.substring("images/".length()));
                } else if (name.startsWith("thumbs/")) {
                    out = new File(new File(staging, "thumbs"), name.substring("thumbs/".length()));
                } else {
                    continue; // info.txt 等忽略
                }
                if (e.isDirectory()) {
                    out.mkdirs();
                    continue;
                }
                out.getParentFile().mkdirs();
                try (OutputStream os = new FileOutputStream(out)) {
                    int n;
                    while ((n = zis.read(buf)) > 0) os.write(buf, 0, n);
                }
                files++;
            }
            if (!new File(staging, DB_NAME).exists() || files == 0) {
                deleteRecursive(staging);
                return "这不是有效的「收哪了」备份文件";
            }
        } catch (Exception ex) {
            deleteRecursive(staging);
            return "读取备份失败：" + ex.getMessage();
        }

        // 第二步：校验通过，替换本机数据
        Store.reset(); // 关闭数据库连接，后续 MainActivity 会用新文件重新打开
        File[] oldDbs = dbTarget.listFiles();
        if (oldDbs != null) {
            for (File f : oldDbs) {
                if (f.getName().startsWith(DB_NAME)) f.delete();
            }
        }
        File files = act.getFilesDir();
        File imgDir = new File(files, "images");
        File thumbDir = new File(files, "thumbs");
        imgDir.mkdirs();
        thumbDir.mkdirs();
        try {
            move(new File(staging, DB_NAME), new File(dbTarget, DB_NAME));
            File si = new File(staging, "images");
            File sth = new File(staging, "thumbs");
            for (File dir : new File[]{imgDir, thumbDir}) {
                File[] fs = dir.listFiles();
                if (fs != null) for (File f : fs) f.delete();
            }
            moveDirChildren(si, imgDir);
            moveDirChildren(sth, thumbDir);
            deleteRecursive(staging);
            return null;
        } catch (Exception ex) {
            return "写入数据失败：" + ex.getMessage();
        }
    }

    private static void moveDirChildren(File srcDir, File dstDir) throws Exception {
        File[] fs = srcDir.listFiles();
        if (fs == null) return;
        for (File f : fs) move(f, new File(dstDir, f.getName()));
    }

    private static void move(File src, File dst) throws Exception {
        if (!src.renameTo(dst) && !dst.isDirectory()) {
            try (InputStream in = new FileInputStream(src);
                 OutputStream os = new FileOutputStream(dst)) {
                copy(in, os);
            }
            src.delete();
        }
    }

    private static void copy(InputStream in, OutputStream os) throws Exception {
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
    }

    private static void deleteRecursive(File f) {
        File[] fs = f.listFiles();
        if (fs != null) for (File c : fs) deleteRecursive(c);
        f.delete();
    }

    // ---------------- 结果分发 ----------------

    /** 两个 Activity 的 onActivityResult 都调用；返回 true 表示该结果已被备份模块消费 */
    public static boolean handleResult(Activity act, int req, int res, Intent data) {
        if (req == REQ_EXPORT) {
            if (res == Activity.RESULT_OK && data != null && data.getData() != null) {
                runExport(act, data.getData());
            }
            return true;
        }
        if (req == REQ_IMPORT) {
            if (res == Activity.RESULT_OK && data != null && data.getData() != null) {
                runImport(act, data.getData());
            }
            return true;
        }
        return false;
    }
}
