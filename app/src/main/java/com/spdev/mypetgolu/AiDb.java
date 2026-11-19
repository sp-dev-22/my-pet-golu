package com.spdev.mypetgolu;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * The AI's own database (golu_ai.db): the Library.
 * Everything the AI creates (quizzes, notes, flashcards, one-liners,
 * questions, web apps) is saved here and SURVIVES chat deletion - deleting a chat
 * never touches this file.
 */
public class AiDb extends SQLiteOpenHelper {

    private static final String DB = "golu_ai.db";
    private static final int VERSION = 2;

    public static final String KIND_QUIZ = "quiz";
    public static final String KIND_NOTES = "notes";
    public static final String KIND_FLASHCARDS = "flashcards";
    public static final String KIND_ONELINER = "oneliner";
    public static final String KIND_QUESTIONS = "questions";
    public static final String KIND_WEBAPP = "webapp";
    public static final String KIND_OTHER = "other";

    public static class Item {
        public long id;
        public String title;
        public String kind;
        public String content;
        public String source;
        /** Library folder name ("" = not filed yet) */
        public String folder;
        public long created;
    }

    public AiDb(Context c) {
        super(c, DB, null, VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE library ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "title TEXT NOT NULL,"
                + "kind TEXT NOT NULL,"
                + "content TEXT NOT NULL,"
                + "source TEXT,"
                + "folder TEXT NOT NULL DEFAULT '',"
                + "created INTEGER NOT NULL)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            // v3.1: library items can live in folders ("" = unfiled)
            db.execSQL("ALTER TABLE library ADD COLUMN folder TEXT NOT NULL DEFAULT ''");
        }
    }

    public long addItem(String title, String kind, String content, String source) {
        ContentValues v = new ContentValues();
        v.put("title", title == null || title.isEmpty() ? "Untitled" : title);
        v.put("kind", kind == null ? KIND_OTHER : kind);
        v.put("content", content == null ? "" : content);
        v.put("source", source == null ? "" : source);
        v.put("created", System.currentTimeMillis());
        return getWritableDatabase().insert("library", null, v);
    }

    public void deleteItem(long id) {
        getWritableDatabase().delete("library", "id=?", new String[]{String.valueOf(id)});
    }

    /** Re-inserts a library item with its original folder and timestamp (backup restore). */
    public long restoreItem(String title, String kind, String content, String source,
                            String folder, long created) {
        ContentValues v = new ContentValues();
        v.put("title", title == null || title.isEmpty() ? "Untitled" : title);
        v.put("kind", kind == null ? KIND_OTHER : kind);
        v.put("content", content == null ? "" : content);
        v.put("source", source == null ? "" : source);
        v.put("folder", folder == null ? "" : folder);
        v.put("created", created);
        return getWritableDatabase().insert("library", null, v);
    }

    public Item getItem(long id) {
        Cursor c = getReadableDatabase().query("library", null, "id=?",
                new String[]{String.valueOf(id)}, null, null, null);
        try {
            if (!c.moveToFirst()) return null;
            return fromCursor(c);
        } finally {
            c.close();
        }
    }

    public List<Item> listItems() {
        List<Item> out = new ArrayList<>();
        Cursor c = getReadableDatabase().query("library", null, null, null, null, null, "created DESC");
        try {
            while (c.moveToNext()) out.add(fromCursor(c));
        } finally {
            c.close();
        }
        return out;
    }

    public List<Item> listItemsByKind(String kind) {
        List<Item> out = new ArrayList<>();
        Cursor c = getReadableDatabase().query("library", null, "kind=?",
                new String[]{kind}, null, null, "created DESC");
        try {
            while (c.moveToNext()) out.add(fromCursor(c));
        } finally {
            c.close();
        }
        return out;
    }

    /**
     * Light list for the Library screen and pickers: id, title, kind,
     * source, folder and date only - the (often large) content column is
     * NOT loaded, so opening the list stays fast. Load the full item with
     * getItem() only when it is actually viewed or sent to Gemini.
     * kind, folder and query may each be null/empty to mean "any".
     */
    public List<Item> listItemSummaries(String kind, String folder, String query) {
        String[] cols = {"id", "title", "kind", "source", "folder", "created"};
        StringBuilder where = new StringBuilder();
        List<String> args = new ArrayList<>();
        if (kind != null && !kind.isEmpty()) {
            where.append("kind=?");
            args.add(kind);
        }
        if (folder != null && !folder.isEmpty()) {
            if (where.length() > 0) where.append(" AND ");
            where.append("folder=?");
            args.add(folder);
        }
        if (query != null && !query.trim().isEmpty()) {
            if (where.length() > 0) where.append(" AND ");
            where.append("(title LIKE ? OR content LIKE ?)");
            String q = "%" + query.trim() + "%";
            args.add(q);
            args.add(q);
        }
        List<Item> out = new ArrayList<>();
        Cursor c = getReadableDatabase().query("library", cols,
                where.length() == 0 ? null : where.toString(),
                args.isEmpty() ? null : args.toArray(new String[0]),
                null, null, "created DESC");
        try {
            while (c.moveToNext()) {
                Item it = new Item();
                it.id = c.getLong(c.getColumnIndexOrThrow("id"));
                it.title = c.getString(c.getColumnIndexOrThrow("title"));
                it.kind = c.getString(c.getColumnIndexOrThrow("kind"));
                it.source = c.getString(c.getColumnIndexOrThrow("source"));
                it.folder = c.getString(c.getColumnIndexOrThrow("folder"));
                it.created = c.getLong(c.getColumnIndexOrThrow("created"));
                it.content = "";
                out.add(it);
            }
        } finally {
            c.close();
        }
        return out;
    }

    /** Distinct non-empty folder names in use, A-Z. */
    public List<String> listFolders() {
        List<String> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT DISTINCT folder FROM library WHERE folder != '' ORDER BY folder ASC",
                null);
        try {
            while (c.moveToNext()) {
                String f = c.getString(0);
                if (f != null && !f.trim().isEmpty()) out.add(f);
            }
        } finally {
            c.close();
        }
        return out;
    }

    /** Items that are not filed into any folder yet. */
    public List<Item> listUnfiledItems() {
        List<Item> out = new ArrayList<>();
        Cursor c = getReadableDatabase().query("library", null, "folder=''",
                null, null, null, "created DESC");
        try {
            while (c.moveToNext()) out.add(fromCursor(c));
        } finally {
            c.close();
        }
        return out;
    }

    /** Moves an item into a folder ("" = no folder). */
    public void setItemFolder(long id, String folder) {
        ContentValues v = new ContentValues();
        v.put("folder", folder == null ? "" : folder.trim());
        getWritableDatabase().update("library", v, "id=?", new String[]{String.valueOf(id)});
    }

    public int countItems() {
        Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM library", null);
        try {
            return c.moveToFirst() ? c.getInt(0) : 0;
        } finally {
            c.close();
        }
    }

    /** Human label for a kind, used in lists. */
    public static String kindLabel(String kind) {
        if (KIND_QUIZ.equals(kind)) return "Quiz";
        if (KIND_NOTES.equals(kind)) return "Notes";
        if (KIND_FLASHCARDS.equals(kind)) return "Flashcards";
        if (KIND_ONELINER.equals(kind)) return "One-liners";
        if (KIND_QUESTIONS.equals(kind)) return "Questions";
        if (KIND_WEBAPP.equals(kind)) return "Web app";
        return "Saved";
    }

    /** Short emoji-ish marker per kind (plain text glyphs, no images). */
    public static String kindMark(String kind) {
        if (KIND_QUIZ.equals(kind)) return "Q";
        if (KIND_NOTES.equals(kind)) return "N";
        if (KIND_FLASHCARDS.equals(kind)) return "F";
        if (KIND_ONELINER.equals(kind)) return "1";
        if (KIND_QUESTIONS.equals(kind)) return "?";
        if (KIND_WEBAPP.equals(kind)) return "W";
        return "*";
    }

    private static Item fromCursor(Cursor c) {
        Item it = new Item();
        it.id = c.getLong(c.getColumnIndexOrThrow("id"));
        it.title = c.getString(c.getColumnIndexOrThrow("title"));
        it.kind = c.getString(c.getColumnIndexOrThrow("kind"));
        it.content = c.getString(c.getColumnIndexOrThrow("content"));
        it.source = c.getString(c.getColumnIndexOrThrow("source"));
        int fi = c.getColumnIndex("folder");
        it.folder = fi >= 0 ? c.getString(fi) : "";
        if (it.folder == null) it.folder = "";
        it.created = c.getLong(c.getColumnIndexOrThrow("created"));
        return it;
    }
}
