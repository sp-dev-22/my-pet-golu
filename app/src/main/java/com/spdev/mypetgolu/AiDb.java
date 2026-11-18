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
 * questions) is saved here and SURVIVES chat deletion - deleting a chat
 * never touches this file.
 */
public class AiDb extends SQLiteOpenHelper {

    private static final String DB = "golu_ai.db";
    private static final int VERSION = 1;

    public static final String KIND_QUIZ = "quiz";
    public static final String KIND_NOTES = "notes";
    public static final String KIND_FLASHCARDS = "flashcards";
    public static final String KIND_ONELINER = "oneliner";
    public static final String KIND_QUESTIONS = "questions";
    public static final String KIND_OTHER = "other";

    public static class Item {
        public long id;
        public String title;
        public String kind;
        public String content;
        public String source;
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
                + "created INTEGER NOT NULL)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // version 1: nothing to migrate yet
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
        return "Saved";
    }

    /** Short emoji-ish marker per kind (plain text glyphs, no images). */
    public static String kindMark(String kind) {
        if (KIND_QUIZ.equals(kind)) return "Q";
        if (KIND_NOTES.equals(kind)) return "N";
        if (KIND_FLASHCARDS.equals(kind)) return "F";
        if (KIND_ONELINER.equals(kind)) return "1";
        if (KIND_QUESTIONS.equals(kind)) return "?";
        return "*";
    }

    private static Item fromCursor(Cursor c) {
        Item it = new Item();
        it.id = c.getLong(c.getColumnIndexOrThrow("id"));
        it.title = c.getString(c.getColumnIndexOrThrow("title"));
        it.kind = c.getString(c.getColumnIndexOrThrow("kind"));
        it.content = c.getString(c.getColumnIndexOrThrow("content"));
        it.source = c.getString(c.getColumnIndexOrThrow("source"));
        it.created = c.getLong(c.getColumnIndexOrThrow("created"));
        return it;
    }
}
