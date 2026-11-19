package com.spdev.mypetgolu;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;

/**
 * Helpers for AI-made web apps (v0.7.0): pulling a clean HTML document out
 * of a Gemini reply, reading its title, pretty-printing result JSON, and
 * the message-kind tag that links a chat message to its saved web app.
 */
public final class WebAppUtil {

    /** Message kind prefix: "webapp:<library item id>". */
    public static final String MSG_KIND_PREFIX = "webapp:";

    /**
     * Extracts the HTML document from an AI reply. The model is asked to
     * reply with ONLY the HTML, but it often wraps it in ```html fences or
     * adds a line of prose - so find the document itself: from <!DOCTYPE
     * html> (or <html> when there is no doctype) to the last </html>.
     * Returns null when the reply holds no HTML document at all.
     */
    public static String extractHtml(String reply) {
        if (reply == null) return null;
        String lower = reply.toLowerCase(Locale.US);
        int start = lower.indexOf("<!doctype html");
        if (start < 0) start = lower.indexOf("<html");
        if (start < 0) return null;
        int end = lower.lastIndexOf("</html>");
        String html;
        if (end >= start) {
            html = reply.substring(start, end + "</html>".length());
        } else {
            html = reply.substring(start);
        }
        html = html.trim();
        if (html.length() < 50) return null;
        return html;
    }

    /**
     * The <title> of an HTML document (a raw reply or clean HTML), or the
     * fallback when there is no usable title.
     */
    public static String extractTitle(String html, String fallback) {
        if (html != null) {
            String lower = html.toLowerCase(Locale.US);
            int s = lower.indexOf("<title>");
            int e = s >= 0 ? lower.indexOf("</title>", s) : -1;
            if (s >= 0 && e > s) {
                String t = html.substring(s + "<title>".length(), e).trim();
                t = t.replaceAll("<[^>]*>", " ").replaceAll("\\s+", " ").trim();
                if (!t.isEmpty()) {
                    if (t.length() > 60) t = t.substring(0, 57) + "...";
                    return t;
                }
            }
        }
        return fallback == null || fallback.isEmpty() ? "Web app" : fallback;
    }

    /** Pretty-prints a JSON string (2-space indent), truncated to maxLen chars. */
    public static String prettyJson(String json, int maxLen) {
        String out = json == null ? "" : json;
        try {
            out = new JSONObject(json).toString(2);
        } catch (Exception e1) {
            try {
                out = new JSONArray(json).toString(2);
            } catch (Exception ignored) {
                // not JSON at all: keep the raw text
            }
        }
        if (maxLen > 0 && out.length() > maxLen) {
            out = out.substring(0, maxLen) + "\n... (truncated)";
        }
        return out;
    }

    /** True when a message kind tags the message as a saved web app. */
    public static boolean isWebAppMessageKind(String kind) {
        return kind != null && kind.startsWith(MSG_KIND_PREFIX);
    }

    /** The library item id from a "webapp:<id>" message kind, or -1. */
    public static long webAppItemId(String kind) {
        if (!isWebAppMessageKind(kind)) return -1;
        try {
            return Long.parseLong(kind.substring(MSG_KIND_PREFIX.length()).trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private WebAppUtil() {
    }
}
