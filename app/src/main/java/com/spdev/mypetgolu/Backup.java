package com.spdev.mypetgolu;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Full app backup & restore (Settings > Backup & Restore).
 *
 * A backup is ONE zip file:
 * - manifest.json: {"format":"golu-backup","formatVersion":1,
 *   "appVersion":"0.5.0","exportedAt":...,"counts":{...}}
 * - data.json: all three databases' data (user folders + files metadata,
 *   library items, chats + messages, skills, memories, app settings,
 *   practice sessions) plus the app preferences.
 * - files/N_name: the actual user file bytes, referenced from
 *   data.json by each file's "zipEntry".
 *
 * The Gemini API key is NEVER in a backup: Prefs.exportSettings() leaves
 * it out, and Prefs.restoreSettings() ignores it even if a forged backup
 * contained one. After a restore the user re-enters the key in Settings.
 *
 * Restore is validated BEFORE anything is touched: the manifest, the
 * format version, the data structure and every referenced file entry are
 * checked first, so a bad backup leaves the current data untouched.
 * Individual damaged rows are skipped and counted, never fatal.
 *
 * This class deliberately does NOT use DataPort's import/export paths -
 * the backup format is versioned on its own and must keep working even
 * if the single-screen exports change.
 */
public final class Backup {

    /** Backup format id, stored in manifest.json. */
    public static final String FORMAT = "golu-backup";
    /** Format version this build reads and writes. */
    public static final int FORMAT_VERSION = 1;
    /** App version that wrote the backup (informational). */
    public static final String APP_VERSION = "0.5.0";

    /** Largest data.json accepted on restore (50 MB of text is plenty). */
    private static final long MAX_DATA_JSON_BYTES = 50L * 1024L * 1024L;

    /** Summary of a backup write, a validation or a restore. */
    public static class Summary {
        public boolean ok = false;
        /** Human summary, e.g. "12 chats, 340 messages, 8 library items...". */
        public String message = "";
        /** Plain-words error when ok is false. */
        public String error = "";
        public int folders, files, library, chats, messages, skills, memories, sessions;
        /** Rows skipped during a restore (damaged rows). */
        public int skipped = 0;
        /** When the backup was written (0 = unknown). */
        public long exportedAt = 0;
        /** App version that wrote the backup ("" = unknown). */
        public String appVersion = "";
    }

    public static String backupFileName() {
        return "golu-backup-"
                + new SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(new Date())
                + ".zip";
    }

    /** "12 chats, 340 messages, 8 library items, 5 files, 4 skills, 10 memories" */
    public static String describe(Summary s) {
        StringBuilder b = new StringBuilder();
        b.append(s.chats).append(s.chats == 1 ? " chat" : " chats");
        b.append(", ").append(s.messages).append(s.messages == 1 ? " message" : " messages");
        b.append(", ").append(s.library)
                .append(s.library == 1 ? " library item" : " library items");
        b.append(", ").append(s.files).append(s.files == 1 ? " file" : " files");
        b.append(", ").append(s.skills).append(s.skills == 1 ? " skill" : " skills");
        b.append(", ").append(s.memories).append(s.memories == 1 ? " memory" : " memories");
        return b.toString();
    }

    // ------------------------------------------------------------- write backup

    /**
     * Writes the full backup zip to the stream. The stream is NOT closed
     * here - the caller owns and closes it. Returns the counts.
     */
    public static Summary writeBackup(Context c, OutputStream out) throws Exception {
        Summary s = new Summary();
        UserDb userDb = new UserDb(c);
        AiDb aiDb = new AiDb(c);
        AppDb appDb = new AppDb(c);

        List<UserDb.Folder> folders = userDb.listFolders();
        List<UserDb.UserFile> files = userDb.listAllFiles();
        List<AiDb.Item> library = aiDb.listItems();
        List<AppDb.Skill> skills = appDb.listSkills();
        List<AppDb.Fact> memories = appDb.listMemory();
        List<AppDb.Chat> chats = new ArrayList<>();
        chats.addAll(appDb.listChats(AppDb.CHAT_AGENT));
        AppDb.Chat convo = appDb.getChatByKind(AppDb.CHAT_CONVERSATION);
        if (convo != null) chats.add(convo);
        AppDb.Chat asking = appDb.getChatByKind(AppDb.CHAT_ASKING);
        if (asking != null) chats.add(asking);
        List<AppDb.AskingSession> sessions = appDb.listAskingSessions();
        List<AppDb.Setting> settings = appDb.listSettings();

        JSONObject data = new JSONObject();

        // --- user database: folders + files metadata
        JSONObject user = new JSONObject();
        JSONArray foldersArr = new JSONArray();
        for (UserDb.Folder f : folders) {
            JSONObject o = new JSONObject();
            o.put("id", f.id);
            o.put("name", f.name);
            o.put("parentId", f.parentId);
            o.put("created", f.created);
            foldersArr.put(o);
        }
        user.put("folders", foldersArr);
        JSONArray filesArr = new JSONArray();
        Map<Long, String> entries = new HashMap<>();
        int idx = 0;
        for (UserDb.UserFile f : files) {
            JSONObject o = new JSONObject();
            o.put("id", f.id);
            o.put("folderId", f.folderId);
            o.put("name", f.name);
            o.put("mime", f.mime == null ? "" : f.mime);
            o.put("size", f.size);
            o.put("created", f.created);
            File file = new File(f.path);
            if (file.exists() && file.isFile()) {
                String e = "files/" + idx + "_" + sanitize(f.name);
                entries.put(f.id, e);
                o.put("zipEntry", e);
                idx++;
            }
            filesArr.put(o);
        }
        user.put("files", filesArr);
        data.put("userDb", user);

        // --- AI database: library
        JSONObject ai = new JSONObject();
        JSONArray libArr = new JSONArray();
        for (AiDb.Item it : library) {
            JSONObject o = new JSONObject();
            o.put("title", it.title);
            o.put("kind", it.kind);
            o.put("content", it.content);
            o.put("source", it.source == null ? "" : it.source);
            o.put("folder", it.folder == null ? "" : it.folder);
            o.put("created", it.created);
            libArr.put(o);
        }
        ai.put("library", libArr);
        data.put("aiDb", ai);

        // --- app database: skills, chats, messages, memories, settings, sessions
        JSONObject app = new JSONObject();
        JSONArray skillsArr = new JSONArray();
        for (AppDb.Skill sk : skills) {
            JSONObject o = new JSONObject();
            o.put("id", sk.id);
            o.put("name", sk.name);
            o.put("prompt", sk.prompt);
            o.put("builtin", sk.builtin);
            skillsArr.put(o);
        }
        app.put("skills", skillsArr);
        JSONArray chatsArr = new JSONArray();
        JSONArray msgsArr = new JSONArray();
        int msgCount = 0;
        for (AppDb.Chat ch : chats) {
            JSONObject o = new JSONObject();
            o.put("id", ch.id);
            o.put("title", ch.title);
            o.put("kind", ch.kind);
            o.put("model", ch.model == null ? "" : ch.model);
            o.put("skillIds", ch.skillIds == null ? "" : ch.skillIds);
            o.put("created", ch.created);
            o.put("updated", ch.updated);
            chatsArr.put(o);
            for (AppDb.Msg m : appDb.listMessages(ch.id)) {
                JSONObject mo = new JSONObject();
                mo.put("chatId", ch.id);
                mo.put("role", m.role);
                mo.put("text", m.text);
                mo.put("kind", m.kind == null ? "text" : m.kind);
                mo.put("created", m.created);
                msgsArr.put(mo);
                msgCount++;
            }
        }
        app.put("chats", chatsArr);
        app.put("messages", msgsArr);
        JSONArray memArr = new JSONArray();
        for (AppDb.Fact f : memories) {
            JSONObject o = new JSONObject();
            o.put("fact", f.fact);
            o.put("created", f.created);
            memArr.put(o);
        }
        app.put("memories", memArr);
        JSONArray setArr = new JSONArray();
        for (AppDb.Setting st : settings) {
            JSONObject o = new JSONObject();
            o.put("key", st.key);
            o.put("value", st.value == null ? "" : st.value);
            setArr.put(o);
        }
        app.put("settings", setArr);
        JSONArray sessArr = new JSONArray();
        for (AppDb.AskingSession ss : sessions) {
            JSONObject o = new JSONObject();
            o.put("quizTitle", ss.quizTitle == null ? "" : ss.quizTitle);
            o.put("quizJson", ss.quizJson);
            o.put("current", ss.current);
            o.put("score", ss.score);
            o.put("created", ss.created);
            o.put("updated", ss.updated);
            sessArr.put(o);
        }
        app.put("sessions", sessArr);
        data.put("appDb", app);

        // --- preferences (the API key is never included - see Prefs)
        data.put("prefs", Prefs.exportSettings(c));

        JSONObject manifest = new JSONObject();
        manifest.put("format", FORMAT);
        manifest.put("formatVersion", FORMAT_VERSION);
        manifest.put("appVersion", APP_VERSION);
        manifest.put("exportedAt", System.currentTimeMillis());
        JSONObject counts = new JSONObject();
        counts.put("folders", folders.size());
        counts.put("files", files.size());
        counts.put("library", library.size());
        counts.put("chats", chats.size());
        counts.put("messages", msgCount);
        counts.put("skills", skills.size());
        counts.put("memories", memories.size());
        counts.put("sessions", sessions.size());
        manifest.put("counts", counts);

        ZipOutputStream zip = new ZipOutputStream(out);
        putEntry(zip, "manifest.json", manifest.toString(2).getBytes("UTF-8"));
        putEntry(zip, "data.json", data.toString(2).getBytes("UTF-8"));
        for (UserDb.UserFile f : files) {
            String e = entries.get(f.id);
            if (e == null) continue; // bytes missing: metadata only
            zip.putNextEntry(new ZipEntry(e));
            FileInputStream fin = new FileInputStream(f.path);
            try {
                copyStream(fin, zip);
            } finally {
                try {
                    fin.close();
                } catch (Exception ignored) {
                }
            }
            zip.closeEntry();
        }
        zip.finish();
        zip.flush();

        s.ok = true;
        s.folders = folders.size();
        s.files = files.size();
        s.library = library.size();
        s.chats = chats.size();
        s.messages = msgCount;
        s.skills = skills.size();
        s.memories = memories.size();
        s.sessions = sessions.size();
        s.exportedAt = System.currentTimeMillis();
        s.appVersion = APP_VERSION;
        s.message = describe(s);
        return s;
    }

    // ---------------------------------------------------------- validate backup

    /**
     * Checks a backup zip WITHOUT changing anything: manifest, format
     * version, data structure and every referenced file entry. Returns
     * ok=false with a plain-words error when the backup cannot be used.
     */
    public static Summary validateBackup(Context c, Uri uri) {
        Summary s = new Summary();
        InputStream raw = null;
        try {
            raw = c.getContentResolver().openInputStream(uri);
            if (raw == null) {
                s.error = "Could not open that file.";
                return s;
            }
            ZipInputStream zip = new ZipInputStream(raw);
            ZipEntry e;
            byte[] manifestBytes = null;
            byte[] dataBytes = null;
            Set<String> entryNames = new HashSet<>();
            while ((e = zip.getNextEntry()) != null) {
                entryNames.add(e.getName());
                if ("manifest.json".equals(e.getName())) {
                    manifestBytes = readAllCapped(zip, 1024 * 1024);
                } else if ("data.json".equals(e.getName())) {
                    dataBytes = readAllCapped(zip, MAX_DATA_JSON_BYTES);
                }
            }
            if (manifestBytes == null || dataBytes == null) {
                s.error = "This is not a Golu backup (missing manifest or data).";
                return s;
            }
            JSONObject manifest;
            JSONObject data;
            try {
                manifest = new JSONObject(new String(manifestBytes, "UTF-8"));
                data = new JSONObject(new String(dataBytes, "UTF-8"));
            } catch (Exception ex) {
                s.error = "This backup file is damaged (bad JSON).";
                return s;
            }
            if (!FORMAT.equals(manifest.optString("format", ""))) {
                s.error = "This is not a Golu backup.";
                return s;
            }
            int fv = manifest.optInt("formatVersion", -1);
            if (fv > FORMAT_VERSION) {
                s.error = "This backup was made by a newer version of Golu. "
                        + "Update the app first, then restore.";
                return s;
            }
            if (fv < 1) {
                s.error = "This backup is too old to read.";
                return s;
            }
            if (!data.has("userDb") || !data.has("aiDb")
                    || !data.has("appDb") || !data.has("prefs")) {
                s.error = "This backup is incomplete (missing sections).";
                return s;
            }
            JSONArray filesArr = optArr(data, "userDb", "files");
            if (filesArr != null) {
                for (int i = 0; i < filesArr.length(); i++) {
                    JSONObject o = filesArr.optJSONObject(i);
                    if (o == null) continue;
                    String entry = o.optString("zipEntry", "");
                    if (!entry.isEmpty() && !entryNames.contains(entry)) {
                        s.error = "This backup is incomplete (a file is missing from "
                                + "the ZIP: " + o.optString("name", "?") + ").";
                        return s;
                    }
                }
            }
            s.exportedAt = manifest.optLong("exportedAt", 0);
            s.appVersion = manifest.optString("appVersion", "");
            JSONObject counts = manifest.optJSONObject("counts");
            if (counts != null) {
                s.folders = counts.optInt("folders", 0);
                s.files = counts.optInt("files", 0);
                s.library = counts.optInt("library", 0);
                s.chats = counts.optInt("chats", 0);
                s.messages = counts.optInt("messages", 0);
                s.skills = counts.optInt("skills", 0);
                s.memories = counts.optInt("memories", 0);
                s.sessions = counts.optInt("sessions", 0);
            } else {
                // no counts in the manifest: count from the data itself
                s.folders = len(optArr(data, "userDb", "folders"));
                s.files = len(optArr(data, "userDb", "files"));
                s.library = len(optArr(data, "aiDb", "library"));
                s.chats = len(optArr(data, "appDb", "chats"));
                s.messages = len(optArr(data, "appDb", "messages"));
                s.skills = len(optArr(data, "appDb", "skills"));
                s.memories = len(optArr(data, "appDb", "memories"));
                s.sessions = len(optArr(data, "appDb", "sessions"));
            }
            s.ok = true;
            s.message = describe(s);
            return s;
        } catch (Exception ex) {
            s.error = plainError(ex);
            return s;
        } finally {
            if (raw != null) {
                try {
                    raw.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    // ----------------------------------------------------------- restore backup

    /**
     * Restores a backup: validates first (nothing is touched when the
     * backup is bad), then wipes all local data and rebuilds it from
     * the backup. The caller must restart the app afterwards so every
     * screen reloads fresh data (see restartApp).
     */
    public static Summary restoreBackup(Context c, Uri uri) {
        Summary v = validateBackup(c, uri);
        if (!v.ok) return v; // nothing touched

        Summary s = new Summary();
        JSONObject data;
        InputStream raw = null;
        try {
            raw = c.getContentResolver().openInputStream(uri);
            if (raw == null) {
                s.error = "Could not open that file.";
                return s;
            }
            ZipInputStream zip = new ZipInputStream(raw);
            ZipEntry e;
            byte[] dataBytes = null;
            while ((e = zip.getNextEntry()) != null) {
                if ("data.json".equals(e.getName())) {
                    dataBytes = readAllCapped(zip, MAX_DATA_JSON_BYTES);
                    break;
                }
            }
            if (dataBytes == null) {
                s.error = "This backup is missing its data.";
                return s;
            }
            data = new JSONObject(new String(dataBytes, "UTF-8"));
        } catch (Exception ex) {
            s.error = plainError(ex);
            return s;
        } finally {
            if (raw != null) {
                try {
                    raw.close();
                } catch (Exception ignored) {
                }
            }
        }

        // ---- the point of no return: wipe everything ----
        ReminderReceiver.cancel(c);
        c.deleteDatabase("golu_user.db");
        c.deleteDatabase("golu_ai.db");
        c.deleteDatabase("golu_app.db");
        deleteRecursive(new File(c.getFilesDir(), "user_files"));
        Prefs.clearAll(c);

        int skipped = 0;
        UserDb userDb = new UserDb(c);
        AiDb aiDb = new AiDb(c);
        AppDb appDb = new AppDb(c);
        try {
            // skills first - chats reference them by id
            Map<Long, Long> skillMap = new HashMap<>();
            JSONArray skillsArr = optArr(data, "appDb", "skills");
            if (skillsArr != null) {
                for (int i = 0; i < skillsArr.length(); i++) {
                    try {
                        JSONObject o = skillsArr.getJSONObject(i);
                        String name = o.optString("name", "").trim();
                        if (name.isEmpty()) {
                            skipped++;
                            continue;
                        }
                        long oldId = o.optLong("id", -1);
                        long newId = appDb.addSkill(name, o.optString("prompt", ""),
                                o.optBoolean("builtin", false));
                        if (oldId > 0) skillMap.put(oldId, newId);
                        s.skills++;
                    } catch (Exception ex) {
                        skipped++;
                    }
                }
            }
            // folders (parents before children: exported oldest-first, and
            // a parent folder is always older than its children)
            // NOTE: creating the fresh user DB auto-creates a default
            // "My PDFs" folder - remove it first so the restore doesn't
            // leave a duplicate behind.
            for (UserDb.Folder f : userDb.listFolders()) userDb.deleteFolder(f.id);
            Map<Long, Long> folderMap = new HashMap<>();
            JSONArray foldersArr = optArr(data, "userDb", "folders");
            if (foldersArr != null) {
                for (int i = 0; i < foldersArr.length(); i++) {
                    try {
                        JSONObject o = foldersArr.getJSONObject(i);
                        String name = o.optString("name", "").trim();
                        if (name.isEmpty()) {
                            skipped++;
                            continue;
                        }
                        long oldId = o.optLong("id", -1);
                        long oldParent = o.optLong("parentId", 0);
                        Long newParent = oldParent == 0 ? 0L : folderMap.get(oldParent);
                        if (newParent == null) newParent = 0L; // parent missing: top level
                        long newId = userDb.restoreFolder(name, newParent,
                                o.optLong("created", System.currentTimeMillis()));
                        if (oldId > 0) folderMap.put(oldId, newId);
                        s.folders++;
                    } catch (Exception ex) {
                        skipped++;
                    }
                }
            }
            // chats (skill ids remapped to the new skill ids)
            Map<Long, Long> chatMap = new HashMap<>();
            JSONArray chatsArr = optArr(data, "appDb", "chats");
            if (chatsArr != null) {
                for (int i = 0; i < chatsArr.length(); i++) {
                    try {
                        JSONObject o = chatsArr.getJSONObject(i);
                        long oldId = o.optLong("id", -1);
                        String skillIds = remapSkillIds(
                                o.optString("skillIds", ""), skillMap);
                        long newId = appDb.restoreChat(
                                o.optString("title", "Chat"),
                                o.optString("kind", AppDb.CHAT_AGENT),
                                o.optString("model", ""),
                                skillIds,
                                o.optLong("created", System.currentTimeMillis()),
                                o.optLong("updated", System.currentTimeMillis()));
                        if (oldId > 0) chatMap.put(oldId, newId);
                        s.chats++;
                    } catch (Exception ex) {
                        skipped++;
                    }
                }
            }
            // messages
            JSONArray msgsArr = optArr(data, "appDb", "messages");
            if (msgsArr != null) {
                for (int i = 0; i < msgsArr.length(); i++) {
                    try {
                        JSONObject o = msgsArr.getJSONObject(i);
                        Long chatId = chatMap.get(o.optLong("chatId", -1));
                        if (chatId == null) {
                            skipped++;
                            continue;
                        }
                        appDb.restoreMessage(chatId, o.optString("role", AppDb.ROLE_USER),
                                o.optString("text", ""), o.optString("kind", "text"),
                                o.optLong("created", System.currentTimeMillis()));
                        s.messages++;
                    } catch (Exception ex) {
                        skipped++;
                    }
                }
            }
            // library
            JSONArray libArr = optArr(data, "aiDb", "library");
            if (libArr != null) {
                for (int i = 0; i < libArr.length(); i++) {
                    try {
                        JSONObject o = libArr.getJSONObject(i);
                        aiDb.restoreItem(o.optString("title", "Untitled"),
                                o.optString("kind", AiDb.KIND_OTHER),
                                o.optString("content", ""), o.optString("source", ""),
                                o.optString("folder", ""),
                                o.optLong("created", System.currentTimeMillis()));
                        s.library++;
                    } catch (Exception ex) {
                        skipped++;
                    }
                }
            }
            // memories
            JSONArray memArr = optArr(data, "appDb", "memories");
            if (memArr != null) {
                for (int i = 0; i < memArr.length(); i++) {
                    try {
                        JSONObject o = memArr.getJSONObject(i);
                        String fact = o.optString("fact", "").trim();
                        if (fact.isEmpty()) {
                            skipped++;
                            continue;
                        }
                        appDb.restoreMemory(fact,
                                o.optLong("created", System.currentTimeMillis()));
                        s.memories++;
                    } catch (Exception ex) {
                        skipped++;
                    }
                }
            }
            // app settings table
            JSONArray setArr = optArr(data, "appDb", "settings");
            if (setArr != null) {
                for (int i = 0; i < setArr.length(); i++) {
                    try {
                        JSONObject o = setArr.getJSONObject(i);
                        String key = o.optString("key", "");
                        if (key.isEmpty()) continue;
                        appDb.setSetting(key, o.optString("value", ""));
                    } catch (Exception ex) {
                        skipped++;
                    }
                }
            }
            // practice sessions
            JSONArray sessArr = optArr(data, "appDb", "sessions");
            if (sessArr != null) {
                for (int i = 0; i < sessArr.length(); i++) {
                    try {
                        JSONObject o = sessArr.getJSONObject(i);
                        appDb.restoreSession(o.optString("quizTitle", ""),
                                o.optString("quizJson", ""),
                                o.optInt("current", 0), o.optInt("score", 0),
                                o.optLong("created", System.currentTimeMillis()),
                                o.optLong("updated", System.currentTimeMillis()));
                        s.sessions++;
                    } catch (Exception ex) {
                        skipped++;
                    }
                }
            }
            // files: extract bytes from the zip (third pass) + register rows
            JSONArray filesArr = optArr(data, "userDb", "files");
            if (filesArr != null && filesArr.length() > 0) {
                int[] filesResult = restoreFiles(c, uri, userDb, folderMap, filesArr);
                s.files = filesResult[0];
                skipped += filesResult[1];
            }
            // preferences (the backup never contains the API key)
            JSONObject prefs = data.optJSONObject("prefs");
            if (prefs != null) Prefs.restoreSettings(c, prefs);
            // safety net: a backup with no skills at all would leave chats
            // skill-less - re-seed the built-ins in that case
            if (appDb.listSkills().isEmpty()) {
                Skills.seed(appDb);
                Prefs.setSkillsSeeded(c);
            }
        } finally {
            try {
                userDb.close();
            } catch (Exception ignored) {
            }
            try {
                aiDb.close();
            } catch (Exception ignored) {
            }
            try {
                appDb.close();
            } catch (Exception ignored) {
            }
        }
        // re-schedule the study reminder if the restored settings want it
        if (Prefs.isReminderOn(c)) ReminderReceiver.schedule(c);
        s.ok = true;
        s.skipped = skipped;
        s.exportedAt = v.exportedAt;
        s.appVersion = v.appVersion;
        s.message = describe(s)
                + (skipped > 0 ? " (" + skipped + " damaged item(s) skipped)" : "");
        return s;
    }

    /** Third zip pass: extracts each file's bytes and registers it. Returns {imported, skipped}. */
    private static int[] restoreFiles(Context c, Uri uri, UserDb db,
                                      Map<Long, Long> folderMap, JSONArray filesArr) {
        int[] out = new int[]{0, 0};
        Map<String, JSONObject> wanted = new HashMap<>();
        Map<String, Long> folderForEntry = new HashMap<>();
        for (int i = 0; i < filesArr.length(); i++) {
            JSONObject o = filesArr.optJSONObject(i);
            if (o == null) {
                out[1]++;
                continue;
            }
            String entry = o.optString("zipEntry", "");
            String name = o.optString("name", "").trim();
            if (entry.isEmpty() || name.isEmpty()) {
                out[1]++;
                continue;
            }
            Long folderId = folderMap.get(o.optLong("folderId", -1));
            if (folderId == null) folderId = 0L; // folder missing: default folder
            wanted.put(entry, o);
            folderForEntry.put(entry, folderId);
        }
        if (wanted.isEmpty()) return out;
        InputStream raw = null;
        try {
            raw = c.getContentResolver().openInputStream(uri);
            if (raw == null) {
                out[1] += wanted.size();
                return out;
            }
            ZipInputStream zip = new ZipInputStream(raw);
            ZipEntry e;
            Set<String> done = new HashSet<>();
            while ((e = zip.getNextEntry()) != null) {
                JSONObject o = wanted.get(e.getName());
                if (o == null) continue;
                done.add(e.getName());
                try {
                    long folderId = folderForEntry.get(e.getName());
                    if (folderId == 0) folderId = ensureDefaultFolder(db);
                    String name = o.optString("name", "");
                    String mime = o.optString("mime", "");
                    FileStore.Stored stored = FileStore.copyFromStream(
                            c, zip, name, mime, folderId);
                    db.restoreFile(folderId, stored.name, stored.mime, stored.size,
                            stored.path, o.optLong("created", System.currentTimeMillis()));
                    out[0]++;
                } catch (Exception ex) {
                    out[1]++;
                }
            }
            for (String entry : wanted.keySet()) {
                if (!done.contains(entry)) out[1]++;
            }
        } catch (Exception ex) {
            out[1] += wanted.size() - out[0] - out[1];
            if (out[1] < 0) out[1] = 0;
        } finally {
            if (raw != null) {
                try {
                    raw.close();
                } catch (Exception ignored) {
                }
            }
        }
        return out;
    }

    private static long ensureDefaultFolder(UserDb db) {
        List<UserDb.Folder> top = db.listChildFolders(0);
        if (!top.isEmpty()) return top.get(0).id;
        return db.createFolder("My PDFs", 0);
    }

    /** Remaps a comma-separated list of old skill ids to the new ones. */
    private static String remapSkillIds(String csv, Map<Long, Long> skillMap) {
        if (csv == null || csv.trim().isEmpty()) return "";
        StringBuilder b = new StringBuilder();
        for (String part : csv.split(",")) {
            try {
                long oldId = Long.parseLong(part.trim());
                Long newId = skillMap.get(oldId);
                if (newId != null) {
                    if (b.length() > 0) b.append(',');
                    b.append(newId.longValue());
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return b.toString();
    }

    /**
     * Restarts the app so every screen reloads fresh data after a restore.
     * Schedules the relaunch a moment ahead, then kills this process.
     */
    public static void restartApp(Context c) {
        Intent intent = new Intent(c, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        int flags = PendingIntent.FLAG_CANCEL_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pi = PendingIntent.getActivity(c, 9001, intent, flags);
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am != null) {
            am.set(AlarmManager.RTC, System.currentTimeMillis() + 150, pi);
        }
        System.exit(0);
    }

    // ---------------------------------------------------------------- helpers

    private static void copyStream(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
    }

    private static byte[] readAllCapped(InputStream in, long maxBytes) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        long total = 0;
        while ((n = in.read(buf)) > 0) {
            total += n;
            if (total > maxBytes) {
                throw new Exception("File is too large (over "
                        + (maxBytes / (1024 * 1024)) + " MB).");
            }
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    private static void putEntry(ZipOutputStream zip, String name, byte[] data)
            throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(data);
        zip.closeEntry();
    }

    private static String sanitize(String name) {
        String s = name.replaceAll("[^A-Za-z0-9._\\- ]", "_").trim();
        if (s.isEmpty()) s = "file";
        return s;
    }

    private static String plainError(Exception e) {
        String m = e.getMessage();
        if (m == null || m.trim().isEmpty()) {
            return "Something went wrong while reading the file.";
        }
        if (m.length() > 300) m = m.substring(0, 297) + "...";
        return m;
    }

    /** Null-safe: parent[key][subKey] as a JSONArray, or null. */
    private static JSONArray optArr(JSONObject parent, String key, String subKey) {
        if (parent == null) return null;
        JSONObject o = parent.optJSONObject(key);
        if (o == null) return null;
        return o.optJSONArray(subKey);
    }

    private static int len(JSONArray arr) {
        return arr == null ? 0 : arr.length();
    }

    private static void deleteRecursive(File f) {
        if (f == null || !f.exists()) return;
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null) {
                for (File k : kids) deleteRecursive(k);
            }
        }
        f.delete();
    }

    private Backup() {
    }
}
