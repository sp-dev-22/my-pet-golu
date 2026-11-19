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
    private static final int VERSION = 2;

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
        /** comma-separated skill ids (v3.1: a chat can have several skills) */
        public String skillIds;
        public long created;
        public long updated;

        /** The chat's skill ids as a list (empty = default skill). */
        public List<Long> skillIdList() {
            List<Long> out = new ArrayList<>();
            if (skillIds != null && !skillIds.trim().isEmpty()) {
                for (String s : skillIds.split(",")) {
                    try {
                        long v = Long.parseLong(s.trim());
                        if (v > 0) out.add(v);
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            if (out.isEmpty() && skillId > 0) out.add(skillId);
            return out;
        }
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

    public static class Setting {
        public String key;
        public String value;
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
                + "skill_ids TEXT,"
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
        if (oldVersion < 2) {
            // v3.1: chats can hold several skills (comma-separated ids)
            db.execSQL("ALTER TABLE chats ADD COLUMN skill_ids TEXT");
            db.execSQL("UPDATE chats SET skill_ids = CAST(skill_id AS TEXT) "
                    + "WHERE skill_id > 0");
        }
    }

    // ----------------------------------------------------------------- chats

    /** Joins skill ids into the comma-separated form stored in the chats table. */
    public static String joinSkillIds(List<Long> ids) {
        StringBuilder b = new StringBuilder();
        if (ids != null) {
            for (Long id : ids) {
                if (id == null || id <= 0) continue;
                if (b.length() > 0) b.append(',');
                b.append(id.longValue());
            }
        }
        return b.toString();
    }

    public long createChat(String title, String kind, String model, long skillId) {
        List<Long> ids = new ArrayList<>();
        if (skillId > 0) ids.add(skillId);
        return createChat(title, kind, model, ids);
    }

    public long createChat(String title, String kind, String model, List<Long> skillIds) {
        long now = System.currentTimeMillis();
        ContentValues v = new ContentValues();
        v.put("title", title);
        v.put("kind", kind);
        v.put("model", model);
        long first = (skillIds != null && !skillIds.isEmpty()) ? skillIds.get(0) : 0;
        v.put("skill_id", first);
        v.put("skill_ids", joinSkillIds(skillIds));
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

    public void updateChatSkills(long id, List<Long> skillIds) {
        ContentValues v = new ContentValues();
        long first = (skillIds != null && !skillIds.isEmpty()) ? skillIds.get(0) : 0;
        v.put("skill_id", first);
        v.put("skill_ids", joinSkillIds(skillIds));
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
        int si = c.getColumnIndex("skill_ids");
        ch.skillIds = si >= 0 ? c.getString(si) : null;
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

    /** Last N messages, oldest-first (for prompt history). Uses LIMIT - it
     *  no longer loads the whole chat into memory first. */
    public List<Msg> lastMessages(long chatId, int n) {
        List<Msg> out = new ArrayList<>();
        Cursor c = getReadableDatabase().query("messages", null, "chat_id=?",
                new String[]{String.valueOf(chatId)}, null, null, "id DESC",
                String.valueOf(Math.max(1, n)));
        try {
            while (c.moveToNext()) out.add(msgFromCursor(c));
        } finally {
            c.close();
        }
        // query returned newest-first; flip back to oldest-first
        List<Msg> rev = new ArrayList<>(out.size());
        for (int i = out.size() - 1; i >= 0; i--) rev.add(out.get(i));
        return rev;
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

    /**
     * The pair a message belongs to, for multi-select delete: a user
     * message is its own pair key; an AI message belongs to the user
     * message right before it (or to itself when it has no user message
     * before it, e.g. a Practice question). Two messages share a pair key
     * exactly when deleteMessagePair would delete them together.
     */
    public static long pairKey(List<Msg> msgs, int position) {
        if (msgs == null || position < 0 || position >= msgs.size()) return -1;
        Msg m = msgs.get(position);
        if (ROLE_USER.equals(m.role)) return m.id;
        if (position - 1 >= 0 && ROLE_USER.equals(msgs.get(position - 1).role)) {
            return msgs.get(position - 1).id;
        }
        return m.id;
    }

    /** Deletes every message belonging to any of the given pair keys. */
    public void deletePairs(long chatId, java.util.Set<Long> pairKeys) {
        if (pairKeys == null || pairKeys.isEmpty()) return;
        List<Msg> all = listMessages(chatId);
        SQLiteDatabase db = getWritableDatabase();
        for (int i = 0; i < all.size(); i++) {
            if (pairKeys.contains(pairKey(all, i))) {
                db.delete("messages", "id=?", new String[]{String.valueOf(all.get(i).id)});
            }
        }
    }

    /** Deletes all messages in a chat (the chat itself stays). */
    public void deleteAllMessages(long chatId) {
        getWritableDatabase().delete("messages", "chat_id=?",
                new String[]{String.valueOf(chatId)});
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

    /** Skills for the given ids, in the order the ids were passed. */
    public List<Skill> getSkillsByIds(List<Long> ids) {
        List<Skill> out = new ArrayList<>();
        if (ids == null) return out;
        for (Long id : ids) {
            if (id == null) continue;
            Skill s = getSkill(id);
            if (s != null) out.add(s);
        }
        return out;
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

    /** All key/value rows of the settings table, for backup. */
    public List<Setting> listSettings() {
        List<Setting> out = new ArrayList<>();
        Cursor c = getReadableDatabase().query("settings", null, null, null, null, null, "key ASC");
        try {
            while (c.moveToNext()) {
                Setting s = new Setting();
                s.key = c.getString(c.getColumnIndexOrThrow("key"));
                s.value = c.getString(c.getColumnIndexOrThrow("value"));
                out.add(s);
            }
        } finally {
            c.close();
        }
        return out;
    }

    // ------------------------------------------------------- restore helpers
    // (used by Backup only: re-insert rows with their original timestamps)

    /** Re-inserts a chat with its original timestamps (backup restore). */
    public long restoreChat(String title, String kind, String model, String skillIds,
                            long created, long updated) {
        ContentValues v = new ContentValues();
        v.put("title", title);
        v.put("kind", kind);
        v.put("model", model);
        long first = 0;
        if (skillIds != null && !skillIds.trim().isEmpty()) {
            try {
                first = Long.parseLong(skillIds.split(",")[0].trim());
            } catch (NumberFormatException ignored) {
            }
        }
        v.put("skill_id", first);
        v.put("skill_ids", skillIds == null ? "" : skillIds);
        v.put("created", created);
        v.put("updated", updated);
        return getWritableDatabase().insert("chats", null, v);
    }

    /** Re-inserts a message with its original timestamp (backup restore). */
    public long restoreMessage(long chatId, String role, String text, String kind, long created) {
        ContentValues v = new ContentValues();
        v.put("chat_id", chatId);
        v.put("role", role);
        v.put("text", text);
        v.put("kind", kind == null ? "text" : kind);
        v.put("created", created);
        return getWritableDatabase().insert("messages", null, v);
    }

    /** Re-inserts a memory fact with its original timestamp (backup restore). */
    public long restoreMemory(String fact, long created) {
        ContentValues v = new ContentValues();
        v.put("fact", fact);
        v.put("created", created);
        return getWritableDatabase().insert("memory", null, v);
    }

    /** Re-inserts a practice session with its original timestamps (backup restore). */
    public long restoreSession(String quizTitle, String quizJson, int current, int score,
                               long created, long updated) {
        ContentValues v = new ContentValues();
        v.put("quiz_title", quizTitle);
        v.put("quiz_json", quizJson);
        v.put("current", current);
        v.put("score", score);
        v.put("created", created);
        v.put("updated", updated);
        return getWritableDatabase().insert("asking_sessions", null, v);
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
