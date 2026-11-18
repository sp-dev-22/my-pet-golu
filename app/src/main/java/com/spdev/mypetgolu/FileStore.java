package com.spdev.mypetgolu;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * Stores user files in internal storage (getFilesDir/user_files/<folderId>/)
 * so uploads survive after the picker is closed. Also reads files back as
 * base64 for Gemini inlineData parts.
 */
public final class FileStore {

    /** Files bigger than this are not sent inline to Gemini (20 MB API cap). */
    public static final long MAX_INLINE_BYTES = 15L * 1024L * 1024L;

    /** Result of copying a picked document into internal storage. */
    public static class Stored {
        public String name;
        public String mime;
        public long size;
        public String path;
    }

    /** Copies the document at uri into the folder's internal directory. */
    public static Stored copyFromUri(Context c, Uri uri, long folderId) throws Exception {
        String name = queryName(c, uri);
        if (name == null || name.isEmpty()) name = "file_" + System.currentTimeMillis();
        name = sanitize(name);
        String mime = c.getContentResolver().getType(uri);
        if (mime == null || mime.isEmpty()) mime = mimeFromName(name);

        File dir = dirFor(c, folderId);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new Exception("Could not create storage folder.");
        }
        File out = unique(dir, name);
        InputStream in = c.getContentResolver().openInputStream(uri);
        if (in == null) throw new Exception("Could not open the selected file.");
        FileOutputStream fos = null;
        long size = 0;
        try {
            fos = new FileOutputStream(out);
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                fos.write(buf, 0, n);
                size += n;
            }
            fos.flush();
        } finally {
            try {
                in.close();
            } catch (Exception ignored) {
            }
            if (fos != null) {
                try {
                    fos.close();
                } catch (Exception ignored) {
                }
            }
        }
        Stored s = new Stored();
        s.name = out.getName();
        s.mime = mime;
        s.size = size;
        s.path = out.getAbsolutePath();
        return s;
    }

    public static File dirFor(Context c, long folderId) {
        return new File(new File(c.getFilesDir(), "user_files"), String.valueOf(folderId));
    }

    /** Deletes one stored file's bytes (the DB row is deleted separately). */
    public static void deletePath(String path) {
        if (path == null) return;
        File f = new File(path);
        if (f.exists()) f.delete();
    }

    /** Deletes a whole folder directory and everything in it. */
    public static void deleteFolderDir(Context c, long folderId) {
        deleteRecursive(dirFor(c, folderId));
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

    public static byte[] readBytes(String path) throws Exception {
        File f = new File(path);
        FileInputStream in = new FileInputStream(f);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        } finally {
            try {
                in.close();
            } catch (Exception ignored) {
            }
        }
        return out.toByteArray();
    }

    /**
     * Base64 of a file for a Gemini inlineData part, or null when the file
     * is missing or too big to send inline.
     */
    public static String base64OfFile(String path) {
        try {
            File f = new File(path);
            if (!f.exists() || f.length() > MAX_INLINE_BYTES) return null;
            return Base64.encodeToString(readBytes(path), Base64.NO_WRAP);
        } catch (Exception e) {
            return null;
        }
    }

    public static String prettySize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return (bytes / 1024) + " KB";
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }

    private static String queryName(Context c, Uri uri) {
        Cursor cur = null;
        try {
            cur = c.getContentResolver().query(uri,
                    new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (cur != null && cur.moveToFirst()) {
                return cur.getString(0);
            }
        } catch (Exception ignored) {
        } finally {
            if (cur != null) cur.close();
        }
        String last = uri.getLastPathSegment();
        return last == null ? null : last;
    }

    private static String sanitize(String name) {
        String s = name.replaceAll("[^A-Za-z0-9._\\- ]", "_").trim();
        if (s.isEmpty()) s = "file";
        if (s.length() > 80) {
            int dot = s.lastIndexOf('.');
            String ext = dot > 0 ? s.substring(dot) : "";
            s = s.substring(0, Math.min(80 - ext.length(), s.length())) + ext;
        }
        return s;
    }

    private static File unique(File dir, String name) {
        File f = new File(dir, name);
        if (!f.exists()) return f;
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String ext = dot > 0 ? name.substring(dot) : "";
        int i = 2;
        while (true) {
            File g = new File(dir, base + " (" + i + ")" + ext);
            if (!g.exists()) return g;
            i++;
        }
    }

    private static String mimeFromName(String name) {
        String n = name.toLowerCase();
        if (n.endsWith(".pdf")) return "application/pdf";
        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg";
        if (n.endsWith(".webp")) return "image/webp";
        if (n.endsWith(".txt") || n.endsWith(".md")) return "text/plain";
        return "application/octet-stream";
    }

    private FileStore() {
    }
}
