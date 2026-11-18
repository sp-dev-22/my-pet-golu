package com.spdev.mypetgolu;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.provider.OpenableColumns;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Shared import / export helper for Skills, Memory, Library and Files.
 * One place for the JSON formats, the PDF builder, the ZIP reader/writer
 * and the Storage Access Framework (SAF) intents, so the four screens
 * behave the same way.
 *
 * Export formats (per screen):
 * - JSON: structured data ({@code type}, {@code version}, {@code items}).
 * - PDF: a human-readable document (export only, never imported).
 * - ZIP: the JSON plus, for Files, the actual file bytes.
 *
 * Import merges into the local database: new items are added, items that
 * already exist are skipped and counted. A malformed file never crashes -
 * the caller gets a plain-words error instead.
 *
 * The full-app backup (Backup.java) deliberately does NOT use these
 * import/export paths; it has its own versioned format.
 */
public final class DataPort {

    /** Version of the single-screen export formats. */
    public static final int EXPORT_VERSION = 1;

    public static final String APP_NAME = "Golu Study Buddy";

    public static final String TYPE_SKILLS = "golu-skills";
    public static final String TYPE_MEMORY = "golu-memory";
    public static final String TYPE_LIBRARY = "golu-library";
    public static final String TYPE_FILES = "golu-files";

    /** Export format picked in the format dialog. */
    public static final int FORMAT_JSON = 0;
    public static final int FORMAT_PDF = 1;
    public static final int FORMAT_ZIP = 2;

    /** Largest JSON file accepted on import (20 MB of text is plenty). */
    private static final long MAX_JSON_BYTES = 20L * 1024L * 1024L;

    /** Result of an import: counts plus a human-readable summary or error. */
    public static class Result {
        public boolean ok = false;
        public int imported = 0;
        public int skipped = 0;
        /** Human summary on success ("Imported 5 skills, skipped 2 (already exist)."). */
        public String message = "";
        /** Plain-words error on failure. */
        public String error = "";
    }

    // ------------------------------------------------------------ file names

    private static String stamp() {
        return new SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(new Date());
    }

    /** e.g. golu-skills-20261118-2130.json */
    public static String exportFileName(String kind, String ext) {
        return "golu-" + kind + "-" + stamp() + "." + ext;
    }

    public static String formatExt(int format) {
        if (format == FORMAT_PDF) return "pdf";
        if (format == FORMAT_ZIP) return "zip";
        return "json";
    }

    public static String formatMime(int format) {
        if (format == FORMAT_PDF) return "application/pdf";
        if (format == FORMAT_ZIP) return "application/zip";
        return "application/json";
    }

    // ------------------------------------------------------------------- SAF

    /** Asks the user where to save a file (Storage Access Framework). */
    public static void launchCreate(Activity a, int requestCode, String fileName, String mime) {
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType(mime);
        i.putExtra(Intent.EXTRA_TITLE, fileName);
        a.startActivityForResult(i, requestCode);
    }

    /** Asks the user to pick a JSON or ZIP file to import. */
    public static void launchOpen(Activity a, int requestCode) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "application/json", "application/zip",
                "application/octet-stream", "text/plain"});
        a.startActivityForResult(i, requestCode);
    }

    public static String displayName(Context c, Uri uri) {
        Cursor cur = null;
        try {
            cur = c.getContentResolver().query(uri,
                    new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (cur != null && cur.moveToFirst()) return cur.getString(0);
        } catch (Exception ignored) {
        } finally {
            if (cur != null) cur.close();
        }
        String last = uri.getLastPathSegment();
        return last == null ? "file" : last;
    }

    // ---------------------------------------------------------- stream helpers

    public static void copyStream(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
    }

    /** Reads a stream fully, refusing files bigger than maxBytes. */
    public static byte[] readAllCapped(InputStream in, long maxBytes) throws Exception {
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

    private static String sanitize(String name) {
        String s = name.replaceAll("[^A-Za-z0-9._\\- ]", "_").trim();
        if (s.isEmpty()) s = "file";
        return s;
    }

    private static String fmtDate(long t) {
        return new SimpleDateFormat("dd MMM yyyy", Locale.US).format(new Date(t));
    }

    private static String fmtDateTime(long t) {
        return new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.US).format(new Date(t));
    }

    private static String plainError(Exception e) {
        String m = e.getMessage();
        if (m == null || m.trim().isEmpty()) {
            return "Something went wrong while reading the file.";
        }
        if (m.length() > 300) m = m.substring(0, 297) + "...";
        return m;
    }

    // ---------------------------------------------------------- JSON builders
    //
    // Formats (all carry type, version, app, exportedAt, count):
    //   skills:  items: [{name, prompt, builtin}]
    //   memory:  items: [{fact, created}]
    //   library: items: [{title, kind, content, source, folder, created}]
    //   files:   folders: [{path, name, parent}],
    //            files: [{folder, name, mime, size, zipEntry?}]
    //            (zipEntry is only present in ZIP exports - a JSON export
    //            lists files without their bytes.)

    public static JSONObject skillsJson(Context c) throws Exception {
        AppDb db = new AppDb(c);
        JSONObject root = new JSONObject();
        root.put("type", TYPE_SKILLS);
        root.put("version", EXPORT_VERSION);
        root.put("app", APP_NAME);
        root.put("exportedAt", System.currentTimeMillis());
        JSONArray arr = new JSONArray();
        List<AppDb.Skill> skills = db.listSkills();
        for (AppDb.Skill s : skills) {
            JSONObject o = new JSONObject();
            o.put("name", s.name);
            o.put("prompt", s.prompt);
            o.put("builtin", s.builtin);
            arr.put(o);
        }
        root.put("count", skills.size());
        root.put("items", arr);
        return root;
    }

    public static JSONObject memoryJson(Context c) throws Exception {
        AppDb db = new AppDb(c);
        JSONObject root = new JSONObject();
        root.put("type", TYPE_MEMORY);
        root.put("version", EXPORT_VERSION);
        root.put("app", APP_NAME);
        root.put("exportedAt", System.currentTimeMillis());
        JSONArray arr = new JSONArray();
        List<AppDb.Fact> facts = db.listMemory();
        for (AppDb.Fact f : facts) {
            JSONObject o = new JSONObject();
            o.put("fact", f.fact);
            o.put("created", f.created);
            arr.put(o);
        }
        root.put("count", facts.size());
        root.put("items", arr);
        return root;
    }

    public static JSONObject libraryJson(Context c) throws Exception {
        AiDb db = new AiDb(c);
        JSONObject root = new JSONObject();
        root.put("type", TYPE_LIBRARY);
        root.put("version", EXPORT_VERSION);
        root.put("app", APP_NAME);
        root.put("exportedAt", System.currentTimeMillis());
        JSONArray arr = new JSONArray();
        List<AiDb.Item> items = db.listItems();
        for (AiDb.Item it : items) {
            JSONObject o = new JSONObject();
            o.put("title", it.title);
            o.put("kind", it.kind);
            o.put("content", it.content);
            o.put("source", it.source == null ? "" : it.source);
            o.put("folder", it.folder == null ? "" : it.folder);
            o.put("created", it.created);
            arr.put(o);
        }
        root.put("count", items.size());
        root.put("items", arr);
        return root;
    }

    /**
     * Files metadata. zipEntryById maps file ids to their entry names
     * inside a ZIP export; pass null for a plain JSON export (no bytes).
     */
    public static JSONObject filesJson(Context c, Map<Long, String> zipEntryById) throws Exception {
        UserDb db = new UserDb(c);
        JSONObject root = new JSONObject();
        root.put("type", TYPE_FILES);
        root.put("version", EXPORT_VERSION);
        root.put("app", APP_NAME);
        root.put("exportedAt", System.currentTimeMillis());
        JSONArray folders = new JSONArray();
        for (UserDb.Folder f : db.listFolders()) {
            JSONObject o = new JSONObject();
            o.put("path", db.folderPath(f.id));
            o.put("name", f.name);
            o.put("parent", f.parentId == 0 ? "" : db.folderPath(f.parentId));
            folders.put(o);
        }
        root.put("folders", folders);
        JSONArray files = new JSONArray();
        List<UserDb.UserFile> all = db.listAllFiles();
        for (UserDb.UserFile f : all) {
            JSONObject o = new JSONObject();
            o.put("folder", db.folderPath(f.folderId));
            o.put("name", f.name);
            o.put("mime", f.mime == null ? "" : f.mime);
            o.put("size", f.size);
            if (zipEntryById != null) {
                String e = zipEntryById.get(f.id);
                if (e != null) o.put("zipEntry", e);
            }
            files.put(o);
        }
        root.put("count", all.size());
        root.put("files", files);
        return root;
    }

    // ---------------------------------------------------------------- writers
    // Each writer returns the number of items written. Writers never close
    // the stream they are given - the caller owns and closes it.

    /** Writes the JSON export of one screen. Returns the item count. */
    public static int writeJson(Context c, String type, OutputStream out) throws Exception {
        JSONObject root;
        if (TYPE_SKILLS.equals(type)) root = skillsJson(c);
        else if (TYPE_MEMORY.equals(type)) root = memoryJson(c);
        else if (TYPE_LIBRARY.equals(type)) root = libraryJson(c);
        else if (TYPE_FILES.equals(type)) root = filesJson(c, null);
        else throw new Exception("Unknown export type.");
        out.write(root.toString(2).getBytes("UTF-8"));
        out.flush();
        return root.optInt("count", 0);
    }

    /** Writes the PDF export of one screen. Returns the item count. */
    public static int writePdf(Context c, String type, OutputStream out) throws Exception {
        String title;
        List<String[]> sections = new ArrayList<>();
        int count;
        if (TYPE_SKILLS.equals(type)) {
            title = "Skills";
            AppDb db = new AppDb(c);
            List<AppDb.Skill> skills = db.listSkills();
            count = skills.size();
            for (AppDb.Skill s : skills) {
                sections.add(new String[]{
                        s.name + (s.builtin ? "  (Built-in)" : ""), s.prompt});
            }
        } else if (TYPE_MEMORY.equals(type)) {
            title = "Memory";
            AppDb db = new AppDb(c);
            List<AppDb.Fact> facts = db.listMemory();
            count = facts.size();
            for (AppDb.Fact f : facts) {
                sections.add(new String[]{"Fact - " + fmtDate(f.created), f.fact});
            }
        } else if (TYPE_LIBRARY.equals(type)) {
            title = "Library";
            AiDb db = new AiDb(c);
            List<AiDb.Item> items = db.listItems();
            count = items.size();
            for (AiDb.Item it : items) {
                String head = "[" + AiDb.kindLabel(it.kind) + "] " + it.title;
                if (it.folder != null && !it.folder.isEmpty()) head += "  •  " + it.folder;
                String body = it.content == null || it.content.isEmpty()
                        ? "(empty)" : it.content;
                sections.add(new String[]{head, body});
            }
        } else if (TYPE_FILES.equals(type)) {
            title = "My Files";
            UserDb db = new UserDb(c);
            List<UserDb.UserFile> all = db.listAllFiles();
            count = all.size();
            Map<String, List<UserDb.UserFile>> byFolder = new LinkedHashMap<>();
            for (UserDb.UserFile f : all) {
                String path = db.folderPath(f.folderId);
                List<UserDb.UserFile> list = byFolder.get(path);
                if (list == null) {
                    list = new ArrayList<>();
                    byFolder.put(path, list);
                }
                list.add(f);
            }
            for (Map.Entry<String, List<UserDb.UserFile>> e : byFolder.entrySet()) {
                StringBuilder b = new StringBuilder();
                for (UserDb.UserFile f : e.getValue()) {
                    b.append("- ").append(f.name).append("  (")
                            .append(FileStore.prettySize(f.size))
                            .append(", ").append(fmtDate(f.created)).append(")\n");
                }
                sections.add(new String[]{e.getKey(), b.toString().trim()});
            }
        } else {
            throw new Exception("Unknown export type.");
        }
        if (sections.isEmpty()) {
            sections.add(new String[]{"Nothing here yet", "No items to show."});
        }
        buildPdf(out, APP_NAME + " - " + title,
                "Exported " + fmtDateTime(System.currentTimeMillis()), sections);
        return count;
    }

    /**
     * Writes the ZIP export of one screen. For Files the ZIP holds the
     * JSON plus the actual file bytes; for the other screens it holds
     * just the JSON (they have no attachments). Returns the item count.
     */
    public static int writeZip(Context c, String type, OutputStream out) throws Exception {
        ZipOutputStream zip = new ZipOutputStream(out);
        if (TYPE_FILES.equals(type)) {
            UserDb db = new UserDb(c);
            List<UserDb.UserFile> all = db.listAllFiles();
            Map<Long, String> entries = new HashMap<>();
            int i = 0;
            for (UserDb.UserFile f : all) {
                File file = new File(f.path);
                if (file.exists() && file.isFile()) {
                    entries.put(f.id, "files/" + i + "_" + sanitize(f.name));
                    i++;
                }
            }
            JSONObject root = filesJson(c, entries);
            putZipEntry(zip, "golu-files.json", root.toString(2).getBytes("UTF-8"));
            for (UserDb.UserFile f : all) {
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
            return all.size();
        }
        JSONObject root;
        String entryName;
        if (TYPE_SKILLS.equals(type)) {
            root = skillsJson(c);
            entryName = "golu-skills.json";
        } else if (TYPE_MEMORY.equals(type)) {
            root = memoryJson(c);
            entryName = "golu-memory.json";
        } else if (TYPE_LIBRARY.equals(type)) {
            root = libraryJson(c);
            entryName = "golu-library.json";
        } else {
            throw new Exception("Unknown export type.");
        }
        putZipEntry(zip, entryName, root.toString(2).getBytes("UTF-8"));
        zip.finish();
        return root.optInt("count", 0);
    }

    private static void putZipEntry(ZipOutputStream zip, String name, byte[] data)
            throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(data);
        zip.closeEntry();
    }

    // -------------------------------------------------------------------- PDF

    /**
     * Builds a paginated PDF: title + subtitle on the first page, then one
     * heading + wrapped body per section. Sections are {heading, body}.
     */
    public static void buildPdf(OutputStream out, String title, String subtitle,
                                List<String[]> sections) throws Exception {
        PdfDocument doc = new PdfDocument();
        PdfWriter w = new PdfWriter(doc);
        w.title(title, subtitle);
        for (String[] s : sections) {
            w.heading(s[0]);
            w.body(s[1] == null ? "" : s[1]);
            w.gap(10);
        }
        w.finish(out);
        doc.close();
    }

    /** Tiny paginated PDF writer (A4 pages, word-wrapped text). */
    private static class PdfWriter {
        private static final int PAGE_W = 595;
        private static final int PAGE_H = 842;
        private static final int MARGIN = 40;
        private static final int TOP = 48;
        private static final int BOTTOM = PAGE_H - 44;

        private final PdfDocument doc;
        private final Paint titlePaint = new Paint();
        private final Paint subPaint = new Paint();
        private final Paint headPaint = new Paint();
        private final Paint bodyPaint = new Paint();
        private final Paint footPaint = new Paint();
        private PdfDocument.Page page;
        private Canvas canvas;
        private int pageNo = 0;
        private int y;

        PdfWriter(PdfDocument doc) {
            this.doc = doc;
            titlePaint.setTypeface(Typeface.DEFAULT_BOLD);
            titlePaint.setTextSize(20);
            titlePaint.setColor(0xFF4E342E);
            subPaint.setTextSize(11);
            subPaint.setColor(0xFF8A7360);
            headPaint.setTypeface(Typeface.DEFAULT_BOLD);
            headPaint.setTextSize(14);
            headPaint.setColor(0xFFE65100);
            bodyPaint.setTextSize(11);
            bodyPaint.setColor(0xFF4E342E);
            footPaint.setTextSize(9);
            footPaint.setColor(0xFF8A7360);
            newPage();
        }

        private void newPage() {
            if (page != null) {
                String foot = "Page " + pageNo;
                float w = footPaint.measureText(foot);
                canvas.drawText(foot, (PAGE_W - w) / 2, PAGE_H - 24, footPaint);
                doc.finishPage(page);
            }
            pageNo++;
            PdfDocument.PageInfo info =
                    new PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create();
            page = doc.startPage(info);
            canvas = page.getCanvas();
            y = TOP;
        }

        private void ensureSpace(int h) {
            if (y + h > BOTTOM) newPage();
        }

        void title(String t, String sub) {
            drawWrapped(t, titlePaint, 26);
            y += 2;
            drawWrapped(sub, subPaint, 15);
            y += 14;
        }

        void heading(String h) {
            ensureSpace(22);
            drawWrapped(h, headPaint, 18);
            y += 2;
        }

        void body(String b) {
            if (b == null || b.isEmpty()) {
                drawWrapped("(empty)", bodyPaint, 15);
                return;
            }
            for (String para : b.split("\n", -1)) {
                if (para.trim().isEmpty()) {
                    y += 8;
                    continue;
                }
                drawWrapped(para, bodyPaint, 15);
            }
        }

        void gap(int px) {
            y += px;
        }

        private void drawWrapped(String text, Paint paint, int lineHeight) {
            int usable = PAGE_W - 2 * MARGIN;
            String rest = text;
            while (rest.length() > 0) {
                int n = paint.breakText(rest, true, usable, null);
                if (n <= 0) n = 1;
                if (n < rest.length()) {
                    // prefer breaking at a space
                    int sp = rest.lastIndexOf(' ', n);
                    if (sp > 0) n = sp;
                }
                String line = rest.substring(0, n).trim();
                rest = rest.substring(n).trim();
                if (!line.isEmpty()) {
                    ensureSpace(lineHeight);
                    canvas.drawText(line, MARGIN, y, paint);
                    y += lineHeight;
                }
            }
        }

        void finish(OutputStream out) throws Exception {
            String foot = "Page " + pageNo;
            float w = footPaint.measureText(foot);
            canvas.drawText(foot, (PAGE_W - w) / 2, PAGE_H - 24, footPaint);
            doc.finishPage(page);
            page = null;
            doc.writeTo(out);
            out.flush();
        }
    }

    // ------------------------------------------------------------------ import

    /**
     * Imports a file the user picked (JSON or ZIP) into one screen.
     * Detects the file kind by its first bytes (PK = ZIP, else JSON),
     * validates it, and merges the data. Never throws - every problem
     * comes back as Result.error in plain words.
     */
    public static Result importUri(Context c, String expectedType, Uri uri) {
        Result r = new Result();
        InputStream raw = null;
        try {
            raw = c.getContentResolver().openInputStream(uri);
            if (raw == null) {
                r.error = "Could not open that file.";
                return r;
            }
            PushbackInputStream in = new PushbackInputStream(raw, 2);
            byte[] head = new byte[2];
            int n0 = 0;
            int b;
            while (n0 < 2 && (b = in.read()) != -1) head[n0++] = (byte) b;
            if (n0 > 0) in.unread(head, 0, n0);
            boolean isZip = n0 == 2 && head[0] == 'P' && head[1] == 'K';
            if (isZip) {
                return importZip(c, expectedType, uri);
            }
            byte[] bytes = readAllCapped(in, MAX_JSON_BYTES);
            JSONObject root;
            try {
                root = new JSONObject(new String(bytes, "UTF-8"));
            } catch (Exception e) {
                r.error = "This file is not a Golu export. "
                        + "Pick a JSON or ZIP file exported from Golu.";
                return r;
            }
            return importJson(c, expectedType, root);
        } catch (Exception e) {
            r.error = plainError(e);
            return r;
        } finally {
            if (raw != null) {
                try {
                    raw.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    /** First ZIP pass: finds the JSON entry, then hands it to the importer. */
    private static Result importZip(Context c, String expectedType, Uri uri) {
        Result r = new Result();
        JSONObject root;
        Set<String> entries = new HashSet<>();
        InputStream raw = null;
        try {
            raw = c.getContentResolver().openInputStream(uri);
            if (raw == null) {
                r.error = "Could not open that file.";
                return r;
            }
            ZipInputStream zip = new ZipInputStream(raw);
            ZipEntry e;
            byte[] jsonBytes = null;
            while ((e = zip.getNextEntry()) != null) {
                entries.add(e.getName());
                String name = e.getName();
                if (name.endsWith(".json")
                        && !"manifest.json".equals(name) && !"data.json".equals(name)) {
                    if (jsonBytes == null) {
                        jsonBytes = readAllCapped(zip, MAX_JSON_BYTES);
                    }
                }
            }
            if (jsonBytes == null) {
                if (entries.contains("manifest.json") || entries.contains("data.json")) {
                    r.error = "This is a full Golu backup, not a single export. "
                            + "Restore it from Settings > Backup & Restore.";
                } else {
                    r.error = "This ZIP has no Golu data file inside.";
                }
                return r;
            }
            try {
                root = new JSONObject(new String(jsonBytes, "UTF-8"));
            } catch (Exception ex) {
                r.error = "The data file inside this ZIP is damaged (bad JSON).";
                return r;
            }
        } catch (Exception ex) {
            r.error = plainError(ex);
            return r;
        } finally {
            if (raw != null) {
                try {
                    raw.close();
                } catch (Exception ignored) {
                }
            }
        }
        if (TYPE_FILES.equals(expectedType)) {
            return importFilesZip(c, root, uri, entries);
        }
        return importJson(c, expectedType, root);
    }

    /** Validates a parsed export JSON (type + version) and merges it. */
    private static Result importJson(Context c, String expectedType, JSONObject root) {
        Result r = new Result();
        // a full backup sneaking into a single-screen import?
        if (root.has("userDb") || root.has("appDb") || root.has("aiDb")) {
            r.error = "This is a full Golu backup, not a single export. "
                    + "Restore it from Settings > Backup & Restore.";
            return r;
        }
        String type = root.optString("type", "");
        if (!expectedType.equals(type)) {
            if (type.isEmpty()) {
                r.error = "This file is not a Golu export (no type inside).";
            } else {
                r.error = "This is a " + typeLabel(type) + " export, not a "
                        + typeLabel(expectedType) + " export.";
            }
            return r;
        }
        int version = root.optInt("version", 1);
        if (version > EXPORT_VERSION) {
            r.error = "This export was made by a newer version of Golu "
                    + "and cannot be read here.";
            return r;
        }
        try {
            if (TYPE_SKILLS.equals(expectedType)) return importSkills(c, root);
            if (TYPE_MEMORY.equals(expectedType)) return importMemory(c, root);
            if (TYPE_LIBRARY.equals(expectedType)) return importLibrary(c, root);
            if (TYPE_FILES.equals(expectedType)) return importFilesJsonOnly(c, root);
        } catch (Exception e) {
            r.error = plainError(e);
            return r;
        }
        r.error = "Unknown import type.";
        return r;
    }

    private static String typeLabel(String type) {
        if (TYPE_SKILLS.equals(type)) return "skills";
        if (TYPE_MEMORY.equals(type)) return "memory";
        if (TYPE_LIBRARY.equals(type)) return "library";
        if (TYPE_FILES.equals(type)) return "files";
        return "unknown";
    }

    private static String summary(String one, String many, int imported, int skipped,
                                  String skipReason) {
        if (imported == 0 && skipped == 0) {
            return "Nothing to import - the file was empty.";
        }
        StringBuilder b = new StringBuilder();
        b.append("Imported ").append(imported).append(" ")
                .append(imported == 1 ? one : many);
        if (skipped > 0) {
            b.append(", skipped ").append(skipped)
                    .append(" (").append(skipReason).append(")");
        }
        b.append(".");
        return b.toString();
    }

    private static boolean isBuiltinName(String name) {
        return Skills.NAME_DEFAULT.equals(name) || Skills.NAME_QUIZ_MAKER.equals(name)
                || Skills.NAME_ONELINER.equals(name) || Skills.NAME_ASKER.equals(name);
    }

    private static Result importSkills(Context c, JSONObject root) {
        Result r = new Result();
        AppDb db = new AppDb(c);
        JSONArray items = root.optJSONArray("items");
        if (items == null) {
            r.error = "This export has no skills in it.";
            return r;
        }
        int imported = 0, skipped = 0;
        for (int i = 0; i < items.length(); i++) {
            JSONObject o = items.optJSONObject(i);
            if (o == null) {
                skipped++;
                continue;
            }
            String name = o.optString("name", "").trim();
            String prompt = o.optString("prompt", "");
            if (name.isEmpty()) {
                skipped++;
                continue;
            }
            if (db.getSkillByName(name) != null) {
                skipped++;
                continue;
            }
            // built-in names are protected - the built-ins are seeded already
            if (isBuiltinName(name)) {
                skipped++;
                continue;
            }
            db.addSkill(name, prompt, false);
            imported++;
        }
        r.ok = true;
        r.imported = imported;
        r.skipped = skipped;
        r.message = summary("skill", "skills", imported, skipped, "already exist");
        return r;
    }

    private static Result importMemory(Context c, JSONObject root) {
        Result r = new Result();
        AppDb db = new AppDb(c);
        JSONArray items = root.optJSONArray("items");
        if (items == null) {
            r.error = "This export has no memories in it.";
            return r;
        }
        Set<String> existing = new HashSet<>();
        for (AppDb.Fact f : db.listMemory()) existing.add(f.fact.trim());
        int imported = 0, skipped = 0;
        for (int i = 0; i < items.length(); i++) {
            JSONObject o = items.optJSONObject(i);
            if (o == null) {
                skipped++;
                continue;
            }
            String fact = o.optString("fact", "").trim();
            if (fact.isEmpty()) {
                skipped++;
                continue;
            }
            if (existing.contains(fact)) {
                skipped++;
                continue;
            }
            db.addMemory(fact);
            existing.add(fact);
            imported++;
        }
        r.ok = true;
        r.imported = imported;
        r.skipped = skipped;
        r.message = summary("memory", "memories", imported, skipped, "already exist");
        return r;
    }

    private static Result importLibrary(Context c, JSONObject root) {
        Result r = new Result();
        AiDb db = new AiDb(c);
        JSONArray items = root.optJSONArray("items");
        if (items == null) {
            r.error = "This export has no library items in it.";
            return r;
        }
        List<AiDb.Item> existing = db.listItems();
        int imported = 0, skipped = 0;
        for (int i = 0; i < items.length(); i++) {
            JSONObject o = items.optJSONObject(i);
            if (o == null) {
                skipped++;
                continue;
            }
            String title = o.optString("title", "Untitled");
            String kind = o.optString("kind", AiDb.KIND_OTHER);
            if (!isKnownKind(kind)) kind = AiDb.KIND_OTHER;
            String content = o.optString("content", "");
            String source = o.optString("source", "");
            String folder = o.optString("folder", "");
            boolean dup = false;
            for (AiDb.Item e : existing) {
                if (e.title.equals(title) && e.kind.equals(kind)
                        && e.content.equals(content)) {
                    dup = true;
                    break;
                }
            }
            if (dup) {
                skipped++;
                continue;
            }
            long id = db.addItem(title, kind, content, source);
            if (!folder.isEmpty()) db.setItemFolder(id, folder);
            imported++;
        }
        r.ok = true;
        r.imported = imported;
        r.skipped = skipped;
        r.message = summary("item", "items", imported, skipped, "already in the Library");
        return r;
    }

    private static boolean isKnownKind(String kind) {
        return AiDb.KIND_QUIZ.equals(kind) || AiDb.KIND_NOTES.equals(kind)
                || AiDb.KIND_FLASHCARDS.equals(kind) || AiDb.KIND_ONELINER.equals(kind)
                || AiDb.KIND_QUESTIONS.equals(kind) || AiDb.KIND_OTHER.equals(kind);
    }

    // ------------------------------------------------------------- files import

    /** Folder import plan: full path -> folder id, plus how many are new. */
    private static class FolderPlan {
        Map<String, Long> ids = new HashMap<>();
        int created = 0;
    }

    /** Creates any missing folders from the export (by full path). */
    private static FolderPlan importFolders(UserDb db, JSONArray foldersJson) {
        FolderPlan plan = new FolderPlan();
        for (UserDb.Folder f : db.listFolders()) {
            plan.ids.put(db.folderPath(f.id), f.id);
        }
        if (foldersJson == null) return plan;
        List<String> paths = new ArrayList<>();
        for (int i = 0; i < foldersJson.length(); i++) {
            JSONObject o = foldersJson.optJSONObject(i);
            if (o == null) continue;
            String path = o.optString("path", "").trim();
            if (!path.isEmpty() && !paths.contains(path)) paths.add(path);
        }
        // shallowest first, so parents exist before their children
        for (int i = 0; i < paths.size(); i++) {
            for (int j = i + 1; j < paths.size(); j++) {
                if (depth(paths.get(j)) < depth(paths.get(i))) {
                    String t = paths.get(i);
                    paths.set(i, paths.get(j));
                    paths.set(j, t);
                }
            }
        }
        for (String path : paths) ensureFolderPath(db, plan, path);
        return plan;
    }

    private static int depth(String path) {
        int d = 0;
        for (int i = 0; i < path.length(); i++) if (path.charAt(i) == '/') d++;
        return d;
    }

    /** Ensures the full folder path exists (creating parents first). */
    private static long ensureFolderPath(UserDb db, FolderPlan plan, String path) {
        Long known = plan.ids.get(path);
        if (known != null) return known;
        int slash = path.lastIndexOf('/');
        String parentPath = slash < 0 ? "" : path.substring(0, slash).trim();
        String leaf = slash < 0 ? path.trim() : path.substring(slash + 1).trim();
        if (leaf.isEmpty()) return 0;
        long parentId = 0;
        if (!parentPath.isEmpty()) parentId = ensureFolderPath(db, plan, parentPath);
        for (UserDb.Folder kid : db.listChildFolders(parentId)) {
            if (kid.name.equals(leaf)) {
                plan.ids.put(path, kid.id);
                return kid.id;
            }
        }
        long id = db.createFolder(leaf, parentId);
        plan.ids.put(path, id);
        plan.created++;
        return id;
    }

    private static long defaultFolderId(UserDb db, FolderPlan plan) {
        List<UserDb.Folder> top = db.listChildFolders(0);
        if (!top.isEmpty()) return top.get(0).id;
        long id = db.createFolder("My PDFs", 0);
        plan.ids.put("My PDFs", id);
        plan.created++;
        return id;
    }

    /** JSON-only files import: folders are imported, files are skipped (no bytes). */
    private static Result importFilesJsonOnly(Context c, JSONObject root) {
        Result r = new Result();
        UserDb db = new UserDb(c);
        FolderPlan plan = importFolders(db, root.optJSONArray("folders"));
        JSONArray files = root.optJSONArray("files");
        int fileCount = files == null ? 0 : files.length();
        r.ok = true;
        r.imported = plan.created;
        r.skipped = fileCount;
        StringBuilder b = new StringBuilder();
        if (plan.created > 0) {
            b.append("Imported ").append(plan.created)
                    .append(plan.created == 1 ? " folder" : " folders").append(". ");
        } else {
            b.append("All folders already exist. ");
        }
        if (fileCount > 0) {
            b.append(fileCount).append(fileCount == 1 ? " file" : " files")
                    .append(" skipped: a JSON export has no file contents - "
                            + "import a ZIP export to bring the files too.");
        }
        r.message = b.toString().trim();
        return r;
    }

    /** One file to pull out of a files ZIP. */
    private static class FilePlan {
        String entry;
        String name;
        String mime;
        long size;
        long folderId;
    }

    /** Files ZIP import: folders plus the actual file bytes (second ZIP pass). */
    private static Result importFilesZip(Context c, JSONObject root, Uri uri,
                                         Set<String> zipEntries) {
        Result r = new Result();
        // the JSON must be a files export
        String type = root.optString("type", "");
        if (!TYPE_FILES.equals(type)) {
            if (type.isEmpty()) {
                r.error = "This file is not a Golu export (no type inside).";
            } else {
                r.error = "This is a " + typeLabel(type)
                        + " export, not a files export.";
            }
            return r;
        }
        int version = root.optInt("version", 1);
        if (version > EXPORT_VERSION) {
            r.error = "This export was made by a newer version of Golu "
                    + "and cannot be read here.";
            return r;
        }
        UserDb db = new UserDb(c);
        FolderPlan plan = importFolders(db, root.optJSONArray("folders"));
        JSONArray files = root.optJSONArray("files");
        if (files == null) {
            r.ok = true;
            r.message = "Imported " + plan.created + " folder(s). No files in this export.";
            return r;
        }
        Map<String, FilePlan> wanted = new HashMap<>();
        int skipped = 0;
        for (int i = 0; i < files.length(); i++) {
            JSONObject o = files.optJSONObject(i);
            if (o == null) {
                skipped++;
                continue;
            }
            String name = o.optString("name", "").trim();
            String folderPath = o.optString("folder", "").trim();
            String entry = o.optString("zipEntry", "");
            if (name.isEmpty() || entry.isEmpty() || !zipEntries.contains(entry)) {
                skipped++;
                continue;
            }
            long folderId = folderPath.isEmpty()
                    ? defaultFolderId(db, plan)
                    : ensureFolderPath(db, plan, folderPath);
            boolean dup = false;
            for (UserDb.UserFile ex : db.listFiles(folderId)) {
                if (ex.name.equals(name) && ex.size == o.optLong("size", -1)) {
                    dup = true;
                    break;
                }
            }
            if (dup) {
                skipped++;
                continue;
            }
            FilePlan fp = new FilePlan();
            fp.entry = entry;
            fp.name = name;
            fp.mime = o.optString("mime", "");
            fp.size = o.optLong("size", 0);
            fp.folderId = folderId;
            wanted.put(entry, fp);
        }
        int imported = 0;
        if (!wanted.isEmpty()) {
            InputStream raw = null;
            try {
                raw = c.getContentResolver().openInputStream(uri);
                if (raw == null) {
                    r.error = "Could not open that file.";
                    return r;
                }
                ZipInputStream zip = new ZipInputStream(raw);
                ZipEntry e;
                Set<String> done = new HashSet<>();
                while ((e = zip.getNextEntry()) != null) {
                    FilePlan fp = wanted.get(e.getName());
                    if (fp == null) continue;
                    done.add(e.getName());
                    try {
                        FileStore.Stored stored = FileStore.copyFromStream(
                                c, zip, fp.name, fp.mime, fp.folderId);
                        db.addFile(fp.folderId, stored.name, stored.mime,
                                stored.size, stored.path);
                        imported++;
                    } catch (Exception ex) {
                        skipped++;
                    }
                }
                for (String entry : wanted.keySet()) {
                    if (!done.contains(entry)) skipped++;
                }
            } catch (Exception ex) {
                r.error = plainError(ex);
                return r;
            } finally {
                if (raw != null) {
                    try {
                        raw.close();
                    } catch (Exception ignored) {
                    }
                }
            }
        }
        r.ok = true;
        r.imported = imported;
        r.skipped = skipped;
        StringBuilder b = new StringBuilder();
        b.append("Imported ").append(imported)
                .append(imported == 1 ? " file" : " files");
        if (plan.created > 0) {
            b.append(" and ").append(plan.created)
                    .append(plan.created == 1 ? " new folder" : " new folders");
        }
        if (skipped > 0) {
            b.append(", skipped ").append(skipped)
                    .append(" (already exist or missing from the ZIP)");
        }
        b.append(".");
        r.message = b.toString();
        return r;
    }

    // ------------------------------------------- background export / import runs

    /** Callback for runImport: the activity shows the result and refreshes. */
    public interface ImportDone {
        void onImported(Result res);
    }

    /**
     * Runs an export on a background thread and toasts the result.
     * extraNote (may be null) is appended to the success toast - used to
     * tell the user a Files JSON export lists files without their bytes.
     */
    public static void runExport(final Activity a, final String type,
                                 final String singular, final String plural,
                                 final int format, final Uri uri,
                                 final String extraNote) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                String msg;
                OutputStream out = null;
                try {
                    out = a.getContentResolver().openOutputStream(uri);
                    if (out == null) {
                        throw new Exception("Could not write to the selected location.");
                    }
                    int n;
                    if (format == FORMAT_PDF) n = writePdf(a, type, out);
                    else if (format == FORMAT_ZIP) n = writeZip(a, type, out);
                    else n = writeJson(a, type, out);
                    msg = "Exported " + n + " " + (n == 1 ? singular : plural);
                    if (extraNote != null && !extraNote.isEmpty()) msg += extraNote;
                } catch (Exception e) {
                    String m = e.getMessage();
                    msg = "Export failed: " + (m == null || m.isEmpty() ? "unknown error" : m);
                } finally {
                    if (out != null) {
                        try {
                            out.close();
                        } catch (Exception ignored) {
                        }
                    }
                }
                final String toast = msg;
                a.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (a.isFinishing() || a.isDestroyed()) return;
                        Ui.toast(a, toast);
                    }
                });
            }
        }).start();
    }

    /** Runs an import on a background thread, then calls back on the UI thread. */
    public static void runImport(final Activity a, final String type, final Uri uri,
                                 final ImportDone done) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                final Result res = importUri(a, type, uri);
                a.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (a.isFinishing() || a.isDestroyed()) return;
                        done.onImported(res);
                    }
                });
            }
        }).start();
    }

    private DataPort() {
    }
}
