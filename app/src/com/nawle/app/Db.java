package com.nawle.app;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class Db extends SQLiteOpenHelper {

    public Db(Context ctx) {
        super(ctx, "nawle.db", null, 2);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE nodes(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "parent_id INTEGER NOT NULL DEFAULT 0," +
                "name TEXT NOT NULL," +
                "note TEXT DEFAULT ''," +
                "kind INTEGER NOT NULL DEFAULT 0," +
                "created_at INTEGER)");
        db.execSQL("CREATE TABLE images(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "node_id INTEGER NOT NULL," +
                "file TEXT NOT NULL," +
                "thumb TEXT NOT NULL," +
                "label TEXT NOT NULL DEFAULT ''," +
                "created_at INTEGER)");
        db.execSQL("CREATE TABLE hotspots(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "image_id INTEGER NOT NULL," +
                "child_id INTEGER NOT NULL," +
                "x REAL NOT NULL," +
                "y REAL NOT NULL)");
        db.execSQL("CREATE INDEX idx_nodes_parent ON nodes(parent_id)");
        db.execSQL("CREATE INDEX idx_images_node ON images(node_id)");
        db.execSQL("CREATE INDEX idx_hotspots_image ON hotspots(image_id)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // v2：nodes.kind 标记展开（相册）节点；images.label 记录相册内照片命名
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE nodes ADD COLUMN kind INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE images ADD COLUMN label TEXT NOT NULL DEFAULT ''");
        }
    }
}
