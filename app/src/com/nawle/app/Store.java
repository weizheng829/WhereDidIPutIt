package com.nawle.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 数据层：节点树 + 图片 + 交互点，全部存于应用私有目录，无需任何权限。
 */
public class Store {

    /** 普通层级节点：可继续放照片和交互点，无限嵌套 */
    public static final int KIND_NODE = 0;
    /** 展开（相册）节点：只能放照片，进入后是网格相册 */
    public static final int KIND_GALLERY = 1;

    public static class Node {
        public long id;
        public long parentId;
        public String name;
        public String note;
        public int kind;
    }

    public static class Pic {
        public long id;
        public long nodeId;
        public String file;
        public String thumb;
        public String label;
        public long createdAt;
    }

    public static class Hotspot {
        public long id;
        public long childId;
        public float x;
        public float y;
        public String childName;
        /** 子节点类型：KIND_NODE / KIND_GALLERY，用于交互点绘制区分 */
        public int childKind;
    }

    private static Store sInstance;

    public static synchronized Store get(Context ctx) {
        if (sInstance == null) sInstance = new Store(ctx.getApplicationContext());
        return sInstance;
    }

    /** 导出备份前调用：把 WAL 日志写回主 db 文件，保证复制到的主文件完整 */
    public void checkpoint() {
        try {
            w().execSQL("PRAGMA wal_checkpoint(TRUNCATE)");
        } catch (Throwable ignored) {
        }
    }

    /** 导入备份后调用：关闭并丢弃单例，下次 get() 重新打开 */
    public static synchronized void reset() {
        if (sInstance != null) {
            try {
                sInstance.db.close();
            } catch (Throwable ignored) {
            }
            sInstance = null;
        }
    }

    private final Db db;
    public final File imgDir;
    public final File thumbDir;
    public final File pendingDir;

    private Store(Context ctx) {
        db = new Db(ctx);
        File files = ctx.getFilesDir();
        imgDir = new File(files, "images");
        thumbDir = new File(files, "thumbs");
        pendingDir = new File(files, "pending");
        imgDir.mkdirs();
        thumbDir.mkdirs();
        pendingDir.mkdirs();
        // 清掉上次进程异常中断遗留的拍照/选图临时文件
        File[] leftovers = pendingDir.listFiles();
        if (leftovers != null) for (File f : leftovers) f.delete();
    }

    private SQLiteDatabase r() {
        return db.getReadableDatabase();
    }

    private SQLiteDatabase w() {
        return db.getWritableDatabase();
    }

    // ---------------- 节点 ----------------

    public long createNode(long parentId, String name) {
        return createNode(parentId, name, KIND_NODE);
    }

    public long createNode(long parentId, String name, int kind) {
        ContentValues v = new ContentValues();
        v.put("parent_id", parentId);
        v.put("name", name);
        v.put("note", "");
        v.put("kind", kind);
        v.put("created_at", System.currentTimeMillis());
        return w().insert("nodes", null, v);
    }

    public void renameNode(long id, String name) {
        ContentValues v = new ContentValues();
        v.put("name", name);
        w().update("nodes", v, "id=?", new String[]{String.valueOf(id)});
    }

    public void setNote(long id, String note) {
        ContentValues v = new ContentValues();
        v.put("note", note);
        w().update("nodes", v, "id=?", new String[]{String.valueOf(id)});
    }

    public Node node(long id) {
        try (Cursor c = r().query("nodes", new String[]{"id", "parent_id", "name", "note", "kind"},
                "id=?", new String[]{String.valueOf(id)}, null, null, null)) {
            if (c.moveToFirst()) return bindNode(c);
        }
        return null;
    }

    private static Node bindNode(Cursor c) {
        Node n = new Node();
        n.id = c.getLong(0);
        n.parentId = c.getLong(1);
        n.name = c.getString(2);
        n.note = c.getString(3);
        n.kind = c.getInt(4);
        return n;
    }

    public List<Node> children(long parentId) {
        List<Node> out = new ArrayList<>();
        try (Cursor c = r().query("nodes", new String[]{"id", "parent_id", "name", "note", "kind"},
                "parent_id=?", new String[]{String.valueOf(parentId)},
                null, null, "created_at ASC, id ASC")) {
            while (c.moveToNext()) out.add(bindNode(c));
        }
        return out;
    }

    public int countChildren(long parentId) {
        try (Cursor c = r().rawQuery("SELECT COUNT(*) FROM nodes WHERE parent_id=?",
                new String[]{String.valueOf(parentId)})) {
            if (c.moveToFirst()) return c.getInt(0);
        }
        return 0;
    }

    /** 按名称模糊搜索所有层级的节点（含空间/柜子/抽屉/物品），名称短的排前，最多 50 条 */
    public List<Node> searchByName(String q) {
        List<Node> out = new ArrayList<>();
        if (q == null || q.trim().isEmpty()) return out;
        try (Cursor c = r().query("nodes", new String[]{"id", "parent_id", "name", "note", "kind"},
                "name LIKE ?", new String[]{"%" + q.trim() + "%"},
                null, null, "LENGTH(name) ASC, created_at ASC, id ASC", "50")) {
            while (c.moveToNext()) out.add(bindNode(c));
        }
        return out;
    }

    /** 从顶层到该节点的链路 */
    public List<Node> path(long id) {
        List<Node> out = new ArrayList<>();
        Node n = node(id);
        int guard = 0;
        while (n != null && guard++ < 100) {
            out.add(0, n);
            if (n.parentId <= 0) break;
            n = node(n.parentId);
        }
        return out;
    }

    /** 级联删除节点子树及其全部图片文件 */
    public void deleteNodeDeep(long rootId) {
        List<Long> ids = new ArrayList<>();
        List<Long> queue = new ArrayList<>();
        queue.add(rootId);
        while (!queue.isEmpty()) {
            long id = queue.remove(queue.size() - 1);
            ids.add(id);
            try (Cursor c = r().query("nodes", new String[]{"id"}, "parent_id=?",
                    new String[]{String.valueOf(id)}, null, null, null)) {
                while (c.moveToNext()) queue.add(c.getLong(0));
            }
        }
        StringBuilder in = new StringBuilder();
        for (Long id : ids) {
            if (in.length() > 0) in.append(',');
            in.append(id);
        }
        String inS = in.toString();

        List<String> files = new ArrayList<>();
        try (Cursor c = r().rawQuery(
                "SELECT file, thumb FROM images WHERE node_id IN (" + inS + ")", null)) {
            while (c.moveToNext()) {
                files.add(c.getString(0));
                files.add(c.getString(1));
            }
        }
        SQLiteDatabase d = w();
        d.execSQL("DELETE FROM hotspots WHERE child_id IN (" + inS + ")");
        d.execSQL("DELETE FROM hotspots WHERE image_id IN (SELECT id FROM images WHERE node_id IN (" + inS + "))");
        d.execSQL("DELETE FROM images WHERE node_id IN (" + inS + ")");
        d.execSQL("DELETE FROM nodes WHERE id IN (" + inS + ")");
        for (String f : files) {
            new File(imgDir, f).delete();
            new File(thumbDir, f).delete();
        }
    }

    // ---------------- 图片 ----------------

    public List<Pic> images(long nodeId) {
        List<Pic> out = new ArrayList<>();
        try (Cursor c = r().query("images",
                new String[]{"id", "node_id", "file", "thumb", "label", "created_at"},
                "node_id=?", new String[]{String.valueOf(nodeId)},
                null, null, "created_at ASC, id ASC")) {
            while (c.moveToNext()) out.add(bindPic(c));
        }
        return out;
    }

    public Pic image(long picId) {
        try (Cursor c = r().query("images",
                new String[]{"id", "node_id", "file", "thumb", "label", "created_at"},
                "id=?", new String[]{String.valueOf(picId)}, null, null, null)) {
            if (c.moveToFirst()) return bindPic(c);
        }
        return null;
    }

    private static Pic bindPic(Cursor c) {
        Pic p = new Pic();
        p.id = c.getLong(0);
        p.nodeId = c.getLong(1);
        p.file = c.getString(2);
        p.thumb = c.getString(3);
        p.label = c.isNull(4) ? "" : c.getString(4);
        p.createdAt = c.getLong(5);
        return p;
    }

    /** 按照片名称模糊搜索（相册里命名的照片），名称短的排前，最多 20 条 */
    public List<Pic> searchImagesByLabel(String q) {
        List<Pic> out = new ArrayList<>();
        if (q == null || q.trim().isEmpty()) return out;
        try (Cursor c = r().query("images",
                new String[]{"id", "node_id", "file", "thumb", "label", "created_at"},
                "label LIKE ?", new String[]{"%" + q.trim() + "%"},
                null, null, "LENGTH(label) ASC, created_at ASC, id ASC", "20")) {
            while (c.moveToNext()) out.add(bindPic(c));
        }
        return out;
    }

    public File imageFile(String name) {
        return new File(imgDir, name);
    }

    public File thumbFile(String name) {
        return new File(thumbDir, name);
    }

    /** 处理 pending 目录中的临时图片文件（压缩、旋转、生成缩略图）并挂到节点上 */
    public Pic addImageFromPending(long nodeId, File pendingFile) {
        try {
            Bitmap bmp = decodeRotatedScaled(pendingFile, 1600);
            if (bmp == null) {
                pendingFile.delete();
                return null;
            }
            String base = "img_" + System.nanoTime() + "_" + nodeId + ".jpg";
            File full = new File(imgDir, base);
            File th = new File(thumbDir, base);
            try (OutputStream os = new FileOutputStream(full)) {
                bmp.compress(Bitmap.CompressFormat.JPEG, 86, os);
            }
            Bitmap tb = scaleBitmap(bmp, 320);
            try (OutputStream os = new FileOutputStream(th)) {
                tb.compress(Bitmap.CompressFormat.JPEG, 80, os);
            }
            if (tb != bmp) tb.recycle();
            bmp.recycle();
            ContentValues v = new ContentValues();
            v.put("node_id", nodeId);
            v.put("file", base);
            v.put("thumb", base);
            v.put("created_at", System.currentTimeMillis());
            long id = w().insert("images", null, v);
            pendingFile.delete();
            Pic p = new Pic();
            p.id = id;
            p.nodeId = nodeId;
            p.file = base;
            p.thumb = base;
            p.createdAt = v.getAsLong("created_at");
            return p;
        } catch (Exception e) {
            pendingFile.delete();
            return null;
        }
    }

    public void moveImageFirst(long picId) {
        try (Cursor c = r().rawQuery(
                "SELECT MIN(created_at) FROM images WHERE node_id=(SELECT node_id FROM images WHERE id=?)",
                new String[]{String.valueOf(picId)})) {
            if (c.moveToFirst() && !c.isNull(0)) {
                ContentValues v = new ContentValues();
                v.put("created_at", c.getLong(0) - 1);
                w().update("images", v, "id=?", new String[]{String.valueOf(picId)});
            }
        }
    }

    /** 相册内照片命名（网格下方的标题） */
    public void renameImage(long picId, String label) {
        ContentValues v = new ContentValues();
        v.put("label", label == null ? "" : label);
        w().update("images", v, "id=?", new String[]{String.valueOf(picId)});
    }

    /** 拖拽排序落定后，按列表顺序重写 created_at（保持 ASC 语义，顺序即持久化） */
    public void reorderImages(List<Pic> order) {
        SQLiteDatabase d = w();
        d.beginTransaction();
        try {
            long base = System.currentTimeMillis();
            for (int i = 0; i < order.size(); i++) {
                ContentValues v = new ContentValues();
                v.put("created_at", base - (order.size() - i));
                d.update("images", v, "id=?", new String[]{String.valueOf(order.get(i).id)});
            }
            d.setTransactionSuccessful();
        } finally {
            d.endTransaction();
        }
    }

    public void deleteImage(long picId) {
        Pic p = image(picId);
        if (p == null) return;
        w().delete("hotspots", "image_id=?", new String[]{String.valueOf(picId)});
        w().delete("images", "id=?", new String[]{String.valueOf(picId)});
        new File(imgDir, p.file).delete();
        new File(thumbDir, p.thumb).delete();
    }

    // ---------------- 交互点 ----------------

    public List<Hotspot> hotspots(long imageId) {
        List<Hotspot> out = new ArrayList<>();
        try (Cursor c = r().rawQuery(
                "SELECT h.id, h.child_id, h.x, h.y, n.name, n.kind FROM hotspots h " +
                        "LEFT JOIN nodes n ON n.id = h.child_id " +
                        "WHERE h.image_id=? ORDER BY h.id ASC",
                new String[]{String.valueOf(imageId)})) {
            while (c.moveToNext()) {
                Hotspot h = new Hotspot();
                h.id = c.getLong(0);
                h.childId = c.getLong(1);
                h.x = (float) c.getDouble(2);
                h.y = (float) c.getDouble(3);
                h.childName = c.isNull(4) ? "" : c.getString(4);
                h.childKind = c.isNull(5) ? KIND_NODE : c.getInt(5);
                out.add(h);
            }
        }
        return out;
    }

    public void addHotspot(long imageId, float x, float y, long childId) {
        ContentValues v = new ContentValues();
        v.put("image_id", imageId);
        v.put("child_id", childId);
        v.put("x", x);
        v.put("y", y);
        w().insert("hotspots", null, v);
    }

    public void deleteHotspot(long hotspotId) {
        w().delete("hotspots", "id=?", new String[]{String.valueOf(hotspotId)});
    }

    // ---------------- 图片解码 ----------------

    public static Bitmap decodeScaled(File f, int maxDim) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getAbsolutePath(), o);
            if (o.outWidth <= 0 || o.outHeight <= 0) return null;
            int s = 1;
            while (Math.max(o.outWidth, o.outHeight) / (s * 2) >= maxDim) s *= 2;
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = s;
            Bitmap b = BitmapFactory.decodeFile(f.getAbsolutePath(), o2);
            if (b == null) return null;
            if (Math.max(b.getWidth(), b.getHeight()) > maxDim) {
                float sc = maxDim / (float) Math.max(b.getWidth(), b.getHeight());
                Bitmap b2 = Bitmap.createScaledBitmap(b,
                        Math.max(1, Math.round(b.getWidth() * sc)),
                        Math.max(1, Math.round(b.getHeight() * sc)), true);
                if (b2 != b) b.recycle();
                b = b2;
            }
            return b;
        } catch (Throwable t) {
            return null;
        }
    }

    public static Bitmap decodeRotatedScaled(File f, int maxDim) {
        Bitmap b = decodeScaled(f, maxDim);
        if (b == null) return null;
        int deg = 0;
        try {
            ExifInterface e = new ExifInterface(f.getAbsolutePath());
            int ori = e.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            if (ori == ExifInterface.ORIENTATION_ROTATE_90) deg = 90;
            else if (ori == ExifInterface.ORIENTATION_ROTATE_180) deg = 180;
            else if (ori == ExifInterface.ORIENTATION_ROTATE_270) deg = 270;
        } catch (Throwable ignored) {
        }
        if (deg != 0) {
            Matrix m = new Matrix();
            m.postRotate(deg);
            Bitmap b2 = Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), m, true);
            if (b2 != b) b.recycle();
            b = b2;
        }
        return b;
    }

    public static Bitmap scaleBitmap(Bitmap src, int maxDim) {
        if (Math.max(src.getWidth(), src.getHeight()) <= maxDim) return src;
        float sc = maxDim / (float) Math.max(src.getWidth(), src.getHeight());
        return Bitmap.createScaledBitmap(src,
                Math.max(1, Math.round(src.getWidth() * sc)),
                Math.max(1, Math.round(src.getHeight() * sc)), true);
    }
}
