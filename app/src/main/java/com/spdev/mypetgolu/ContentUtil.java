package com.spdev.mypetgolu;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Content parsing for the reader (v0.8.0): turns Library items back into
 * readable items. One Card is one readable item, whatever its kind:
 * - flashcards: prompt = front, reveal = back
 * - one-liners: prompt = the line (nothing to reveal)
 * - questions:  prompt = question, reveal = answer, explanation = why
 * - quiz:       parsed by QuizUtil (it keeps the 4 options); see
 *               ReaderActivity, which renders quiz questions directly.
 *
 * Parsing is tolerant: Library content comes from the model or from the
 * user's own saved messages, so every parser accepts several shapes and
 * falls back to "one line = one item" instead of failing.
 *
 * This file also owns the "read:" message-kind tag: when an agent-chat
 * reply is auto-saved to the Library, the message is tagged so the chat
 * shows a compact "[Quiz] Title - tap to read" card instead of the raw
 * JSON, and tapping it opens the reader. (Web apps use their own
 * "webapp:" tag in WebAppUtil.)
 */
public final class ContentUtil {

    public static class Card {
        public String prompt = "";
        /** flashcard back / open-question answer; "" when there is none */
        public String reveal = "";
        /** open-question "Why"; "" when there is none */
        public String explanation = "";
    }

    // ------------------------------------------------- Library item -> cards

    /**
     * Parses a Library item into readable cards, or null when the kind has
     * no card shape (notes render as text, quizzes use QuizUtil, web apps
     * open in the player).
     */
    public static List<Card> cardsFor(AiDb.Item item) {
        if (item == null || item.content == null) return null;
        List<Card> out;
        if (AiDb.KIND_FLASHCARDS.equals(item.kind)) {
            out = parseFlashcards(item.content);
        } else if (AiDb.KIND_ONELINER.equals(item.kind)) {
            out = parseOneLiners(item.content);
        } else if (AiDb.KIND_QUESTIONS.equals(item.kind)) {
            out = parseQuestions(item.content);
        } else {
            return null;
        }
        return out.isEmpty() ? null : out;
    }

    /** True when the Library item opens in the reader (everything but web apps). */
    public static boolean opensInReader(String kind) {
        return !AiDb.KIND_WEBAPP.equals(kind);
    }

    // -------------------------------------------------------------- flashcards

    /**
     * Flashcards, in any of these shapes:
     * - JSON array of {"front","back"} / {"question","answer"} /
     *   {"q","a"} / {"term","definition"}
     * - lines "front :: back", "front | back", "front -> back", "front => back"
     * - "Q: ..." / "A: ..." line pairs
     * - anything else: one line = one card (front only)
     */
    public static List<Card> parseFlashcards(String raw) {
        List<Card> out = new ArrayList<>();
        if (raw == null) return out;

        // 1. JSON array
        String json = QuizUtil.extractArray(raw);
        if (json != null) {
            try {
                JSONArray arr = new JSONArray(json);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    String front = firstNonEmpty(o, "front", "question", "q", "term");
                    String back = firstNonEmpty(o, "back", "answer", "a", "definition");
                    if (!front.isEmpty()) {
                        Card c = new Card();
                        c.prompt = front;
                        c.reveal = back;
                        out.add(c);
                    }
                }
                if (!out.isEmpty()) return out;
            } catch (Exception ignored) {
                // not JSON objects: fall through to line parsing
            }
        }

        // 2. separator lines / Q-A pairs / plain lines
        String[] lines = raw.split("\n");
        Card pending = null;
        for (String rawLine : lines) {
            String line = stripBullet(rawLine);
            if (line.isEmpty()) continue;
            String lower = line.toLowerCase();
            if (lower.startsWith("q:") || lower.startsWith("q.")) {
                pending = new Card();
                pending.prompt = line.substring(2).trim();
                out.add(pending);
                continue;
            }
            if ((lower.startsWith("a:") || lower.startsWith("a.")) && pending != null) {
                pending.reveal = line.substring(2).trim();
                pending = null;
                continue;
            }
            String[] pair = splitPair(line);
            Card c = new Card();
            if (pair != null) {
                c.prompt = pair[0];
                c.reveal = pair[1];
            } else {
                c.prompt = line;
            }
            out.add(c);
            pending = null;
        }
        return out;
    }

    private static String firstNonEmpty(JSONObject o, String... keys) {
        for (String k : keys) {
            String v = o.optString(k, "").trim();
            if (!v.isEmpty()) return v;
        }
        return "";
    }

    /** Splits "front :: back" style lines; null when there is no separator. */
    private static String[] splitPair(String line) {
        String[] seps = {"::", " | ", " -> ", " => ", "\t"};
        for (String sep : seps) {
            int at = line.indexOf(sep);
            if (at > 0 && at + sep.length() < line.length()) {
                String a = line.substring(0, at).trim();
                String b = line.substring(at + sep.length()).trim();
                if (!a.isEmpty() && !b.isEmpty()) return new String[]{a, b};
            }
        }
        return null;
    }

    // -------------------------------------------------------------- one-liners

    /**
     * One-liners, in either shape:
     * - JSON array of {"line":"..."} / {"text":"..."} / {"fact":"..."} objects,
     *   or a plain JSON array of strings (the v0.6.0 One Liner Maker format)
     * - one fact per line, bullets / numbering stripped (the old format,
     *   kept so Library items made before v0.6.0 still read)
     */
    public static List<Card> parseOneLiners(String raw) {
        List<Card> out = new ArrayList<>();
        if (raw == null) return out;

        // 1. JSON array
        String json = QuizUtil.extractArray(raw);
        if (json != null) {
            try {
                JSONArray arr = new JSONArray(json);
                for (int i = 0; i < arr.length(); i++) {
                    String line = "";
                    Object entry = arr.get(i);
                    if (entry instanceof JSONObject) {
                        line = firstNonEmpty((JSONObject) entry,
                                "line", "text", "fact", "oneliner");
                    } else if (entry instanceof String) {
                        line = ((String) entry).trim();
                    }
                    if (!line.isEmpty()) {
                        Card c = new Card();
                        c.prompt = line;
                        out.add(c);
                    }
                }
                if (!out.isEmpty()) return out;
            } catch (Exception ignored) {
                // not a JSON array of lines: fall through to line parsing
            }
        }

        // 2. one fact per line (bullets / numbering stripped)
        for (String rawLine : raw.split("\n")) {
            String line = stripBullet(rawLine);
            line = line.replaceAll("^\\d+[.)]\\s+", "");
            if (line.isEmpty()) continue;
            Card c = new Card();
            c.prompt = line;
            out.add(c);
        }
        return out;
    }

    // -------------------------------------------------------------- questions

    /**
     * Questions Asker format: numbered questions, each followed by an
     * "Answer: ..." line and a "Why: ..." line. Plain question lists
     * (numbered or one per line) work too; cards without an answer are
     * shown as questions only.
     */
    public static List<Card> parseQuestions(String raw) {
        List<Card> out = new ArrayList<>();
        if (raw == null) return out;
        Card cur = null;
        for (String rawLine : raw.split("\n")) {
            String line = rawLine.trim();
            if (line.isEmpty()) continue;
            String lower = line.toLowerCase();
            if (lower.startsWith("answer:")) {
                if (cur != null) cur.reveal = line.substring(7).trim();
                continue;
            }
            if (lower.startsWith("why:")) {
                if (cur != null) cur.explanation = line.substring(4).trim();
                continue;
            }
            String numbered = stripNumber(line);
            if (numbered != null) {
                cur = new Card();
                cur.prompt = numbered;
                out.add(cur);
                continue;
            }
            if (cur == null) {
                cur = new Card();
                cur.prompt = line;
                out.add(cur);
            } else if (cur.reveal.isEmpty()) {
                cur.prompt = cur.prompt + " " + line;
            } else {
                cur.explanation = cur.explanation.isEmpty()
                        ? line : cur.explanation + " " + line;
            }
        }
        List<Card> kept = new ArrayList<>();
        for (Card c : out) if (!c.prompt.isEmpty()) kept.add(c);
        return kept;
    }

    /** "1. text" / "1) text" -> "text"; null when the line is not numbered. */
    private static String stripNumber(String line) {
        if (!line.matches("^\\d+[.)]\\s+.*")) return null;
        return line.replaceFirst("^\\d+[.)]\\s+", "").trim();
    }

    /** Strips a leading bullet ("- ", "* ", "• ") from a line. */
    private static String stripBullet(String line) {
        String s = line.trim();
        if (s.startsWith("- ") || s.startsWith("* ") || s.startsWith("\u2022 ")) {
            return s.substring(2).trim();
        }
        return s;
    }

    // ----------------------------------------------------------- markdown text

    /**
     * Cleans basic markdown out of notes text so it reads well in the
     * reader: code fences are dropped, "# " headers lose their hashes,
     * and "**" / "__" emphasis markers are removed. The words stay.
     */
    public static String cleanMarkdown(String raw) {
        if (raw == null) return "";
        StringBuilder out = new StringBuilder();
        for (String line : raw.split("\n")) {
            String s = line.trim();
            if (s.startsWith("```")) continue; // fence line (with or without a language)
            while (s.startsWith("#")) s = s.substring(1).trim();
            s = s.replace("**", "").replace("__", "");
            out.append(s).append('\n');
        }
        return out.toString().trim();
    }

    // -------------------------------------------------- "read:" message tag
    // Tag shape: "read:<kind>:<library item id>:<title>". The title may
    // itself contain ':' - only the first two separators are split on.
    // Titles are single-line by construction (firstLine60 / quizTitle /
    // extractTitle), so the tag stays one line.

    /** Message kind prefix: "read:<kind>:<id>:<title>". */
    public static final String MSG_KIND_PREFIX = "read:";

    /** Builds the tag for a message whose reply was saved to the Library. */
    public static String readMessageKind(String kind, long itemId, String title) {
        String t = title == null ? "" : title.replace('\n', ' ').trim();
        return MSG_KIND_PREFIX + (kind == null ? AiDb.KIND_OTHER : kind)
                + ":" + itemId + ":" + t;
    }

    /** True when a message kind tags the message as a saved Library item. */
    public static boolean isReadMessageKind(String kind) {
        return kind != null && kind.startsWith(MSG_KIND_PREFIX);
    }

    /** The Library kind from a "read:" tag, or KIND_OTHER. */
    public static String readKind(String kind) {
        if (!isReadMessageKind(kind)) return AiDb.KIND_OTHER;
        String rest = kind.substring(MSG_KIND_PREFIX.length());
        int at = rest.indexOf(':');
        if (at <= 0) return AiDb.KIND_OTHER;
        return rest.substring(0, at);
    }

    /** The library item id from a "read:" tag, or -1. */
    public static long readItemId(String kind) {
        if (!isReadMessageKind(kind)) return -1;
        String rest = kind.substring(MSG_KIND_PREFIX.length());
        int first = rest.indexOf(':');
        if (first < 0) return -1;
        int second = rest.indexOf(':', first + 1);
        String idPart = second < 0
                ? rest.substring(first + 1) : rest.substring(first + 1, second);
        try {
            return Long.parseLong(idPart.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** The title from a "read:" tag, or "" when there is none. */
    public static String readTitle(String kind) {
        if (!isReadMessageKind(kind)) return "";
        String rest = kind.substring(MSG_KIND_PREFIX.length());
        int first = rest.indexOf(':');
        if (first < 0) return "";
        int second = rest.indexOf(':', first + 1);
        if (second < 0) return "";
        return rest.substring(second + 1).trim();
    }

    /**
     * The compact card text shown in the chat for a tagged message, e.g.
     * "[Quiz] Quiz (10 Q): What is... - tap to read".
     */
    public static String readCardText(String kind) {
        String label = AiDb.kindLabel(readKind(kind));
        String title = readTitle(kind);
        if (title.isEmpty()) title = "Saved item";
        return "[" + label + "] " + title + " - tap to read";
    }

    private ContentUtil() {
    }
}
