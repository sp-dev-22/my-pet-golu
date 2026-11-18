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
    private static final int VERSION = 2;

    public static class Folder {
        public long id;
        public String name;
        /** parent folder id (0 = top-level folder) */
        public long parentId;
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
                + "parent_id INTEGER NOT NULL DEFAULT 0,"
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
        if (oldVersion < 2) {
            // v3.1: folders can have sub-folders (parent_id, 0 = top-level)
            db.execSQL("ALTER TABLE folders ADD COLUMN parent_id INTEGER NOT NULL DEFAULT 0");
        }
    }

    // -------------------------------------------------------------- folders

    public long createFolder(String name) {
        return createFolder(name, 0);
    }

    /** Creates a folder inside the given parent (0 = top-level). */
    public long createFolder(String name, long parentId) {
        ContentValues v = new ContentValues();
        v.put("name", name);
        v.put("parent_id", parentId);
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

    /** The folder id plus all of its descendant folder ids (for tree delete). */
    public List<Long> collectFolderTreeIds(long id) {
        List<Long> out = new ArrayList<>();
        out.add(id);
        List<Folder> kids = listChildFolders(id);
        for (Folder k : kids) out.addAll(collectFolderTreeIds(k.id));
        return out;
    }

    /** Deletes a folder, all its sub-folders and their file rows. */
    public void deleteFolderTree(long id) {
        List<Long> ids = collectFolderTreeIds(id);
        SQLiteDatabase db = getWritableDatabase();
        for (Long fid : ids) {
            db.delete("files", "folder_id=?", new String[]{String.valueOf(fid)});
            db.delete("folders", "id=?", new String[]{String.valueOf(fid)});
        }
    }

    /** Number of files inside a folder and all its sub-folders. */
    public int countFilesRecursive(long folderId) {
        List<Long> ids = collectFolderTreeIds(folderId);
        int n = 0;
        for (Long fid : ids) n += countFiles(fid);
        return n;
    }

    /** Full path of a folder, e.g. "My PDFs / Science / Chapter 3". */
    public String folderPath(long folderId) {
        List<String> names = new ArrayList<>();
        long cur = folderId;
        int guard = 0;
        while (cur > 0 && guard++ < 20) {
            Folder f = getFolder(cur);
            if (f == null) break;
            names.add(0, f.name);
            cur = f.parentId;
        }
        if (names.isEmpty()) return "Files";
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < names.size(); i++) {
            if (i > 0) b.append(" / ");
            b.append(names.get(i));
        }
        return b.toString();
    }

    /** All folders (any level), oldest first. */
    public List<Folder> listFolders() {
        return listFoldersWhere(null, null);
    }

    /** Direct children of one folder (parentId 0 = top-level folders). */
    public List<Folder> listChildFolders(long parentId) {
        return listFoldersWhere("parent_id=?", new String[]{String.valueOf(parentId)});
    }

    private List<Folder> listFoldersWhere(String where, String[] args) {
        List<Folder> out = new ArrayList<>();
        Cursor c = getReadableDatabase().query("folders", null, where, args, null, null,
                "created ASC");
        try {
            while (c.moveToNext()) out.add(folderFromCursor(c));
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
            return folderFromCursor(c);
        } finally {
            c.close();
        }
    }

    private static Folder folderFromCursor(Cursor c) {
        Folder f = new Folder();
        f.id = c.getLong(c.getColumnIndexOrThrow("id"));
        f.name = c.getString(c.getColumnIndexOrThrow("name"));
        int pi = c.getColumnIndex("parent_id");
        f.parentId = pi >= 0 ? c.getLong(pi) : 0;
        f.created = c.getLong(c.getColumnIndexOrThrow("created"));
        return f;
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

    /** Re-inserts a folder with its original timestamp (backup restore). */
    public long restoreFolder(String name, long parentId, long created) {
        ContentValues v = new ContentValues();
        v.put("name", name);
        v.put("parent_id", parentId);
        v.put("created", created);
        return getWritableDatabase().insert("folders", null, v);
    }

    /**
     * Re-inserts a file row with its original timestamp (backup restore).
     * The bytes must already be stored at {@code path}.
     */
    public long restoreFile(long folderId, String name, String mime, long size, String path,
                            long created) {
        ContentValues v = new ContentValues();
        v.put("folder_id", folderId);
        v.put("name", name);
        v.put("mime", mime);
        v.put("size", size);
        v.put("path", path);
        v.put("created", created);
        return getWritableDatabase().insert("files", null, v);
    }

    /** Total number of files across all folders. */
    public int countAllFiles() {
        Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM files", null);
        try {
            return c.moveToFirst() ? c.getInt(0) : 0;
        } finally {
            c.close();
        }
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
