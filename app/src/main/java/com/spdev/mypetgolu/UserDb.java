package com.spdev.mypetgolu;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * The USER's own database (golu_user.db): folders and uploaded files.
 * Kept in a separate file from the AI library on purpose.
 * File bytes live in internal storage; this DB only stores metadata.
 */
public class UserDb extends SQLiteOpenHelper {

    private static final String DB = "golu_user.db";
    private static final int VERSION = 1;

    public static class Folder {
        public long id;
        public String name;
        public long created;
    }

    public static class UserFile {
        public long id;
        public long folderId;
        public String name;
        public String mime;
        public long size;
        public String path;
        public long created;
    }

    public UserDb(Context c) {
        super(c, DB, null, VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE folders ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "name TEXT NOT NULL,"
                + "created INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE files ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "folder_id INTEGER NOT NULL,"
                + "name TEXT NOT NULL,"
                + "mime TEXT,"
                + "size INTEGER NOT NULL DEFAULT 0,"
                + "path TEXT NOT NULL,"
                + "created INTEGER NOT NULL)");
        // a default folder so uploads always have a home
        ContentValues v = new ContentValues();
        v.put("name", "My PDFs");
        v.put("created", System.currentTimeMillis());
        db.insert("folders", null, v);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // version 1: nothing to migrate yet
    }

    // -------------------------------------------------------------- folders

    public long createFolder(String name) {
        ContentValues v = new ContentValues();
        v.put("name", name);
        v.put("created", System.currentTimeMillis());
        return getWritableDatabase().insert("folders", null, v);
    }

    public void renameFolder(long id, String name) {
        ContentValues v = new ContentValues();
        v.put("name", name);
        getWritableDatabase().update("folders", v, "id=?", new String[]{String.valueOf(id)});
    }

    /** Deletes the folder row and its file rows. Callers delete the bytes. */
    public void deleteFolder(long id) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete("files", "folder_id=?", new String[]{String.valueOf(id)});
        db.delete("folders", "id=?", new String[]{String.valueOf(id)});
    }

    public List<Folder> listFolders() {
        List<Folder> out = new ArrayList<>();
        Cursor c = getReadableDatabase().query("folders", null, null, null, null, null, "created ASC");
        try {
            while (c.moveToNext()) {
                Folder f = new Folder();
                f.id = c.getLong(c.getColumnIndexOrThrow("id"));
                f.name = c.getString(c.getColumnIndexOrThrow("name"));
                f.created = c.getLong(c.getColumnIndexOrThrow("created"));
                out.add(f);
            }
        } finally {
            c.close();
        }
        return out;
    }

    public Folder getFolder(long id) {
        Cursor c = getReadableDatabase().query("folders", null, "id=?",
                new String[]{String.valueOf(id)}, null, null, null);
        try {
            if (!c.moveToFirst()) return null;
            Folder f = new Folder();
            f.id = c.getLong(c.getColumnIndexOrThrow("id"));
            f.name = c.getString(c.getColumnIndexOrThrow("name"));
            f.created = c.getLong(c.getColumnIndexOrThrow("created"));
            return f;
        } finally {
            c.close();
        }
    }

    // ---------------------------------------------------------------- files

    public long addFile(long folderId, String name, String mime, long size, String path) {
        ContentValues v = new ContentValues();
        v.put("folder_id", folderId);
        v.put("name", name);
        v.put("mime", mime);
        v.put("size", size);
        v.put("path", path);
        v.put("created", System.currentTimeMillis());
        return getWritableDatabase().insert("files", null, v);
    }

    public void deleteFile(long id) {
        getWritableDatabase().delete("files", "id=?", new String[]{String.valueOf(id)});
    }

    public UserFile getFile(long id) {
        Cursor c = getReadableDatabase().query("files", null, "id=?",
                new String[]{String.valueOf(id)}, null, null, null);
        try {
            if (!c.moveToFirst()) return null;
            return fromCursor(c);
        } finally {
            c.close();
        }
    }

    public List<UserFile> listFiles(long folderId) {
        List<UserFile> out = new ArrayList<>();
        Cursor c = getReadableDatabase().query("files", null, "folder_id=?",
                new String[]{String.valueOf(folderId)}, null, null, "created DESC");
        try {
            while (c.moveToNext()) out.add(fromCursor(c));
        } finally {
            c.close();
        }
        return out;
    }

    public List<UserFile> listAllFiles() {
        List<UserFile> out = new ArrayList<>();
        Cursor c = getReadableDatabase().query("files", null, null, null, null, null, "created DESC");
        try {
            while (c.moveToNext()) out.add(fromCursor(c));
        } finally {
            c.close();
        }
        return out;
    }

    public int countFiles(long folderId) {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM files WHERE folder_id=?",
                new String[]{String.valueOf(folderId)});
        try {
            return c.moveToFirst() ? c.getInt(0) : 0;
        } finally {
            c.close();
        }
    }

    private static UserFile fromCursor(Cursor c) {
        UserFile f = new UserFile();
        f.id = c.getLong(c.getColumnIndexOrThrow("id"));
        f.folderId = c.getLong(c.getColumnIndexOrThrow("folder_id"));
        f.name = c.getString(c.getColumnIndexOrThrow("name"));
        f.mime = c.getString(c.getColumnIndexOrThrow("mime"));
        f.size = c.getLong(c.getColumnIndexOrThrow("size"));
        f.path = c.getString(c.getColumnIndexOrThrow("path"));
        f.created = c.getLong(c.getColumnIndexOrThrow("created"));
        return f;
    }
}
