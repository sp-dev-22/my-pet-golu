package com.spdev.mypetgolu;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * The app database (golu_app.db): chats, messages, skills, memory facts,
 * key-value settings and asking-mode sessions.
 * NOTE: deleting a chat deletes its messages only - library items live in
 * AiDb and are never touched here.
 */
public class AppDb extends SQLiteOpenHelper {

    private static final String DB = "golu_app.db";
    private static final int VERSION = 1;

    public static final String CHAT_AGENT = "agent";
    public static final String CHAT_CONVERSATION = "conversation";
    public static final String CHAT_ASKING = "asking";

    public static final String ROLE_USER = "user";
    public static final String ROLE_AI = "ai";

    public static class Chat {
        public long id;
        public String title;
        public String kind;
        public String model;
        public long skillId;
        public long created;
        public long updated;
    }

    public static class Msg {
        public long id;
        public long chatId;
        public String role;
        public String text;
        public String kind;
        public long created;
    }

    public static class Skill {
        public long id;
        public String name;
        public String prompt;
        public boolean builtin;
    }

    public static class Fact {
        public long id;
        public String fact;
        public long created;
    }

    public static class AskingSession {
        public long id;
        public String quizTitle;
        public String quizJson;
        public int current;
        public int score;
        public long created;
        public long updated;
    }

    public AppDb(Context c) {
        super(c, DB, null, VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE chats ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "title TEXT NOT NULL,"
                + "kind TEXT NOT NULL,"
                + "model TEXT,"
                + "skill_id INTEGER NOT NULL DEFAULT 0,"
                + "created INTEGER NOT NULL,"
                + "updated INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE messages ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "chat_id INTEGER NOT NULL,"
                + "role TEXT NOT NULL,"
                + "text TEXT NOT NULL,"
                + "kind TEXT NOT NULL DEFAULT 'text',"
                + "created INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE skills ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "name TEXT NOT NULL,"
                + "prompt TEXT NOT NULL,"
                + "builtin INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE TABLE memory ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "fact TEXT NOT NULL,"
                + "created INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE settings ("
                + "key TEXT PRIMARY KEY,"
                + "value TEXT)");
        db.execSQL("CREATE TABLE asking_sessions ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "quiz_title TEXT,"
                + "quiz_json TEXT NOT NULL,"
                + "current INTEGER NOT NULL DEFAULT 0,"
                + "score INTEGER NOT NULL DEFAULT 0,"
                + "created INTEGER NOT NULL,"
                + "updated INTEGER NOT NULL)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // version 1: nothing to migrate yet
    }

    // ----------------------------------------------------------------- chats

    public long createChat(String title, String kind, String model, long skillId) {
        long now = System.currentTimeMillis();
        ContentValues v = new ContentValues();
        v.put("title", title);
        v.put("kind", kind);
        v.put("model", model);
        v.put("skill_id", skillId);
        v.put("created", now);
        v.put("updated", now);
        return getWritableDatabase().insert("chats", null, v);
    }

    /** The single persistent chat of a kind (conversation / asking). */
    public Chat getOrCreateSingleChat(String kind, String title, String model) {
        Chat c = getChatByKind(kind);
        if (c != null) return c;
        long id = createChat(title, kind, model, 0);
        return getChat(id);
    }

    public Chat getChatByKind(String kind) {
        Cursor c = getReadableDatabase().query("chats", null, "kind=?",
                new String[]{kind}, null, null, "id ASC");
        try {
            if (!c.moveToFirst()) return null;
            return chatFromCursor(c);
        } finally {
            c.close();
        }
    }

    public Chat getChat(long id) {
        Cursor c = getReadableDatabase().query("chats", null, "id=?",
                new String[]{String.valueOf(id)}, null, null, null);
        try {
            if (!c.moveToFirst()) return null;
            return chatFromCursor(c);
        } finally {
            c.close();
        }
    }

    public List<Chat> listChats(String kind) {
        List<Chat> out = new ArrayList<>();
        Cursor c = getReadableDatabase().query("chats", null, "kind=?",
                new String[]{kind}, null, null, "updated DESC");
        try {
            while (c.moveToNext()) out.add(chatFromCursor(c));
        } finally {
            c.close();
        }
        return out;
    }

    public void updateChatTitle(long id, String title) {
        ContentValues v = new ContentValues();
        v.put("title", title);
        v.put("updated", System.currentTimeMillis());
        getWritableDatabase().update("chats", v, "id=?", new String[]{String.valueOf(id)});
    }

    public void updateChatModel(long id, String model) {
        ContentValues v = new ContentValues();
        v.put("model", model);
        getWritableDatabase().update("chats", v, "id=?", new String[]{String.valueOf(id)});
    }

    public void updateChatSkill(long id, long skillId) {
        ContentValues v = new ContentValues();
        v.put("skill_id", skillId);
        getWritableDatabase().update("chats", v, "id=?", new String[]{String.valueOf(id)});
    }

    public void touchChat(long id) {
        ContentValues v = new ContentValues();
        v.put("updated", System.currentTimeMillis());
        getWritableDatabase().update("chats", v, "id=?", new String[]{String.valueOf(id)});
    }

    /** Deletes the chat and its messages. Library items are NOT touched. */
    public void deleteChat(long id) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete("messages", "chat_id=?", new String[]{String.valueOf(id)});
        db.delete("chats", "id=?", new String[]{String.valueOf(id)});
    }

    private static Chat chatFromCursor(Cursor c) {
        Chat ch = new Chat();
        ch.id = c.getLong(c.getColumnIndexOrThrow("id"));
        ch.title = c.getString(c.getColumnIndexOrThrow("title"));
        ch.kind = c.getString(c.getColumnIndexOrThrow("kind"));
        ch.model = c.getString(c.getColumnIndexOrThrow("model"));
        ch.skillId = c.getLong(c.getColumnIndexOrThrow("skill_id"));
        ch.created = c.getLong(c.getColumnIndexOrThrow("created"));
        ch.updated = c.getLong(c.getColumnIndexOrThrow("updated"));
        return ch;
    }

    // -------------------------------------------------------------- messages

    public long addMessage(long chatId, String role, String text, String kind) {
        ContentValues v = new ContentValues();
        v.put("chat_id", chatId);
        v.put("role", role);
        v.put("text", text);
        v.put("kind", kind == null ? "text" : kind);
        v.put("created", System.currentTimeMillis());
        long id = getWritableDatabase().insert("messages", null, v);
        touchChat(chatId);
        return id;
    }

    public List<Msg> listMessages(long chatId) {
        List<Msg> out = new ArrayList<>();
        Cursor c = getReadableDatabase().query("messages", null, "chat_id=?",
                new String[]{String.valueOf(chatId)}, null, null, "id ASC");
        try {
            while (c.moveToNext()) out.add(msgFromCursor(c));
        } finally {
            c.close();
        }
        return out;
    }

    /** Last N messages, oldest-first (for prompt history). */
    public List<Msg> lastMessages(long chatId, int n) {
        List<Msg> all = listMessages(chatId);
        if (all.size() <= n) return all;
        return new ArrayList<>(all.subList(all.size() - n, all.size()));
    }

    public Msg getMessage(long id) {
        Cursor c = getReadableDatabase().query("messages", null, "id=?",
                new String[]{String.valueOf(id)}, null, null, null);
        try {
            if (!c.moveToFirst()) return null;
            return msgFromCursor(c);
        } finally {
            c.close();
        }
    }

    public void deleteMessage(long id) {
        getWritableDatabase().delete("messages", "id=?", new String[]{String.valueOf(id)});
    }

    /**
     * Deletes a user+AI message pair: the message plus its partner
     * (the AI reply after a user message, or the user message before
     * an AI message). If no partner exists, deletes just the message.
     */
    public void deleteMessagePair(long chatId, long messageId) {
        List<Msg> all = listMessages(chatId);
        int idx = -1;
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).id == messageId) {
                idx = i;
                break;
            }
        }
        if (idx < 0) return;
        Msg m = all.get(idx);
        SQLiteDatabase db = getWritableDatabase();
        db.delete("messages", "id=?", new String[]{String.valueOf(messageId)});
        if (ROLE_USER.equals(m.role) && idx + 1 < all.size()
                && ROLE_AI.equals(all.get(idx + 1).role)) {
            db.delete("messages", "id=?", new String[]{String.valueOf(all.get(idx + 1).id)});
        } else if (ROLE_AI.equals(m.role) && idx - 1 >= 0
                && ROLE_USER.equals(all.get(idx - 1).role)) {
            db.delete("messages", "id=?", new String[]{String.valueOf(all.get(idx - 1).id)});
        }
    }

    private static Msg msgFromCursor(Cursor c) {
        Msg m = new Msg();
        m.id = c.getLong(c.getColumnIndexOrThrow("id"));
        m.chatId = c.getLong(c.getColumnIndexOrThrow("chat_id"));
        m.role = c.getString(c.getColumnIndexOrThrow("role"));
        m.text = c.getString(c.getColumnIndexOrThrow("text"));
        m.kind = c.getString(c.getColumnIndexOrThrow("kind"));
        m.created = c.getLong(c.getColumnIndexOrThrow("created"));
        return m;
    }

    // ---------------------------------------------------------------- skills

    public long addSkill(String name, String prompt, boolean builtin) {
        ContentValues v = new ContentValues();
        v.put("name", name);
        v.put("prompt", prompt);
        v.put("builtin", builtin ? 1 : 0);
        return getWritableDatabase().insert("skills", null, v);
    }

    public Skill getSkill(long id) {
        Cursor c = getReadableDatabase().query("skills", null, "id=?",
                new String[]{String.valueOf(id)}, null, null, null);
        try {
            if (!c.moveToFirst()) return null;
            return skillFromCursor(c);
        } finally {
            c.close();
        }
    }

    public Skill getSkillByName(String name) {
        Cursor c = getReadableDatabase().query("skills", null, "name=?",
                new String[]{name}, null, null, null);
        try {
            if (!c.moveToFirst()) return null;
            return skillFromCursor(c);
        } finally {
            c.close();
        }
    }

    public List<Skill> listSkills() {
        List<Skill> out = new ArrayList<>();
        Cursor c = getReadableDatabase().query("skills", null, null, null, null, null,
                "builtin DESC, name ASC");
        try {
            while (c.moveToNext()) out.add(skillFromCursor(c));
        } finally {
            c.close();
        }
        return out;
    }

    public void updateSkill(long id, String name, String prompt) {
        ContentValues v = new ContentValues();
        v.put("name", name);
        v.put("prompt", prompt);
        getWritableDatabase().update("skills", v, "id=?", new String[]{String.valueOf(id)});
    }

    /** Only custom skills can be deleted; built-ins are protected. */
    public void deleteSkill(long id) {
        getWritableDatabase().delete("skills", "id=? AND builtin=0",
                new String[]{String.valueOf(id)});
    }

    private static Skill skillFromCursor(Cursor c) {
        Skill s = new Skill();
        s.id = c.getLong(c.getColumnIndexOrThrow("id"));
        s.name = c.getString(c.getColumnIndexOrThrow("name"));
        s.prompt = c.getString(c.getColumnIndexOrThrow("prompt"));
        s.builtin = c.getInt(c.getColumnIndexOrThrow("builtin")) == 1;
        return s;
    }

    // ---------------------------------------------------------------- memory

    public long addMemory(String fact) {
        ContentValues v = new ContentValues();
        v.put("fact", fact);
        v.put("created", System.currentTimeMillis());
        return getWritableDatabase().insert("memory", null, v);
    }

    public void deleteMemory(long id) {
        getWritableDatabase().delete("memory", "id=?", new String[]{String.valueOf(id)});
    }

    public List<Fact> listMemory() {
        List<Fact> out = new ArrayList<>();
        Cursor c = getReadableDatabase().query("memory", null, null, null, null, null, "created DESC");
        try {
            while (c.moveToNext()) {
                Fact f = new Fact();
                f.id = c.getLong(c.getColumnIndexOrThrow("id"));
                f.fact = c.getString(c.getColumnIndexOrThrow("fact"));
                f.created = c.getLong(c.getColumnIndexOrThrow("created"));
                out.add(f);
            }
        } finally {
            c.close();
        }
        return out;
    }

    /** All facts as one block, for prepending to a system prompt. */
    public String memoryBlock() {
        List<Fact> facts = listMemory();
        if (facts.isEmpty()) return "";
        StringBuilder b = new StringBuilder();
        b.append("\n\n[Things to remember about the student]\n");
        for (Fact f : facts) b.append("- ").append(f.fact).append('\n');
        return b.toString();
    }

    // -------------------------------------------------------------- settings

    public String getSetting(String key, String def) {
        Cursor c = getReadableDatabase().query("settings", null, "key=?",
                new String[]{key}, null, null, null);
        try {
            if (!c.moveToFirst()) return def;
            return c.getString(c.getColumnIndexOrThrow("value"));
        } finally {
            c.close();
        }
    }

    public void setSetting(String key, String value) {
        ContentValues v = new ContentValues();
        v.put("key", key);
        v.put("value", value);
        SQLiteDatabase db = getWritableDatabase();
        int n = db.update("settings", v, "key=?", new String[]{key});
        if (n == 0) db.insert("settings", null, v);
    }

    // ------------------------------------------------------- asking sessions

    public long saveAskingSession(String quizTitle, String quizJson, int current, int score) {
        long now = System.currentTimeMillis();
        ContentValues v = new ContentValues();
        v.put("quiz_title", quizTitle);
        v.put("quiz_json", quizJson);
        v.put("current", current);
        v.put("score", score);
        v.put("created", now);
        v.put("updated", now);
        return getWritableDatabase().insert("asking_sessions", null, v);
    }

    public AskingSession getAskingSession(long id) {
        Cursor c = getReadableDatabase().query("asking_sessions", null, "id=?",
                new String[]{String.valueOf(id)}, null, null, null);
        try {
            if (!c.moveToFirst()) return null;
            AskingSession s = new AskingSession();
            s.id = c.getLong(c.getColumnIndexOrThrow("id"));
            s.quizTitle = c.getString(c.getColumnIndexOrThrow("quiz_title"));
            s.quizJson = c.getString(c.getColumnIndexOrThrow("quiz_json"));
            s.current = c.getInt(c.getColumnIndexOrThrow("current"));
            s.score = c.getInt(c.getColumnIndexOrThrow("score"));
            s.created = c.getLong(c.getColumnIndexOrThrow("created"));
            s.updated = c.getLong(c.getColumnIndexOrThrow("updated"));
            return s;
        } finally {
            c.close();
        }
    }

    public List<AskingSession> listAskingSessions() {
        List<AskingSession> out = new ArrayList<>();
        Cursor c = getReadableDatabase().query("asking_sessions", null, null, null,
                null, null, "updated DESC");
        try {
            while (c.moveToNext()) {
                AskingSession s = new AskingSession();
                s.id = c.getLong(c.getColumnIndexOrThrow("id"));
                s.quizTitle = c.getString(c.getColumnIndexOrThrow("quiz_title"));
                s.quizJson = c.getString(c.getColumnIndexOrThrow("quiz_json"));
                s.current = c.getInt(c.getColumnIndexOrThrow("current"));
                s.score = c.getInt(c.getColumnIndexOrThrow("score"));
                s.created = c.getLong(c.getColumnIndexOrThrow("created"));
                s.updated = c.getLong(c.getColumnIndexOrThrow("updated"));
                out.add(s);
            }
        } finally {
            c.close();
        }
        return out;
    }

    public void updateAskingSession(long id, int current, int score) {
        ContentValues v = new ContentValues();
        v.put("current", current);
        v.put("score", score);
        v.put("updated", System.currentTimeMillis());
        getWritableDatabase().update("asking_sessions", v, "id=?",
                new String[]{String.valueOf(id)});
    }

    public void deleteAskingSession(long id) {
        getWritableDatabase().delete("asking_sessions", "id=?",
                new String[]{String.valueOf(id)});
    }
}
