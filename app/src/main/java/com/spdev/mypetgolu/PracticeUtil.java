package com.spdev.mypetgolu;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Practice sets for Practice mode (was Asking/Quiz). One Card is one
 * practice item, whatever its kind:
 * - quiz:       prompt = question, options (4), correctIndex, explanation
 * - flashcards: prompt = front, reveal = back
 * - one-liners: prompt = the line (nothing to reveal)
 * - questions:  prompt = question, reveal = answer, explanation = why
 *
 * Parsing is tolerant: Library content comes from the model or from the
 * user's own saved messages, so every parser accepts several shapes and
 * falls back to "one line = one item" instead of failing.
 */
public final class PracticeUtil {

    public static final String MODE_QUIZ = "quiz";
    public static final String MODE_CARDS = "flashcards";
    public static final String MODE_ONELINER = "oneliner";
    public static final String MODE_QUESTIONS = "questions";

    public static class Card {
        public String prompt = "";
        /** quiz only (4 options); null for every other kind */
        public String[] options;
        /** quiz only (0-3); -1 otherwise */
        public int correctIndex = -1;
        /** flashcard back / open-question answer; "" when there is none */
        public String reveal = "";
        /** quiz explanation / open-question "Why"; "" when there is none */
        public String explanation = "";

        public boolean isQuiz() {
            return options != null;
        }
    }

    public static class PracticeSet {
        public String mode = MODE_QUIZ;
        public List<Card> items = new ArrayList<>();
    }

    /** Is this Library kind practicable? (Notes and other text are not.) */
    public static boolean isPracticableKind(String kind) {
        return AiDb.KIND_QUIZ.equals(kind) || AiDb.KIND_FLASHCARDS.equals(kind)
                || AiDb.KIND_ONELINER.equals(kind) || AiDb.KIND_QUESTIONS.equals(kind);
    }

    /** Short kind label shown in the picker, e.g. "[Cards] My set". */
    public static String pickerLabel(String kind) {
        if (AiDb.KIND_QUIZ.equals(kind)) return "Quiz";
        if (AiDb.KIND_FLASHCARDS.equals(kind)) return "Cards";
        if (AiDb.KIND_ONELINER.equals(kind)) return "Lines";
        if (AiDb.KIND_QUESTIONS.equals(kind)) return "Questions";
        return "Saved";
    }

    /** Word used in the status line: "Q 1 of 5", "Card 1 of 8", ... */
    public static String modeWord(String mode) {
        if (MODE_CARDS.equals(mode)) return "Card";
        if (MODE_ONELINER.equals(mode)) return "Line";
        return "Q";
    }

    /** Parses a Library item into a practice set, or null if it has no items. */
    public static PracticeSet fromLibraryItem(AiDb.Item item) {
        if (item == null || item.content == null) return null;
        PracticeSet set = new PracticeSet();
        if (AiDb.KIND_QUIZ.equals(item.kind)) {
            set.mode = MODE_QUIZ;
            set.items = fromQuizQuestions(QuizUtil.parseQuiz(item.content));
        } else if (AiDb.KIND_FLASHCARDS.equals(item.kind)) {
            set.mode = MODE_CARDS;
            set.items = parseFlashcards(item.content);
        } else if (AiDb.KIND_ONELINER.equals(item.kind)) {
            set.mode = MODE_ONELINER;
            set.items = parseOneLiners(item.content);
        } else if (AiDb.KIND_QUESTIONS.equals(item.kind)) {
            set.mode = MODE_QUESTIONS;
            set.items = parseQuestions(item.content);
        } else {
            return null;
        }
        return set.items.isEmpty() ? null : set;
    }

    /** Quiz questions as Cards (options kept). */
    public static List<Card> fromQuizQuestions(List<QuizUtil.Question> qs) {
        List<Card> out = new ArrayList<>();
        if (qs == null) return out;
        for (QuizUtil.Question q : qs) {
            Card c = new Card();
            c.prompt = q.question == null ? "" : q.question;
            c.options = q.options;
            c.correctIndex = q.correctIndex;
            c.explanation = q.explanation == null ? "" : q.explanation;
            if (!c.prompt.isEmpty()) out.add(c);
        }
        return out;
    }

    // -------------------------------------------------------------- flashcards

    /**
     * Flashcards, in any of these shapes:
     * - JSON array of {"front","back"} / {"question","answer"} /
     *   {"q","a"} / {"term","definition"}
     * - lines "front :: back", "front | back", "front -> back", "front => back"
     * - "Q: ..." / "A: ..." line pairs
     * - anything else: one line = one card (front only, self-marked)
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

    /** One-liners: one fact per line (bullets / numbering stripped). */
    public static List<Card> parseOneLiners(String raw) {
        List<Card> out = new ArrayList<>();
        if (raw == null) return out;
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
     * practised as recall-and-mark items.
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

    // ------------------------------------------------------------ session JSON

    /** Serializes a set for the asking_sessions table (quiz_json column). */
    public static String toSessionJson(String mode, List<Card> items) {
        JSONObject root = new JSONObject();
        try {
            root.put("mode", mode == null ? MODE_QUIZ : mode);
            JSONArray arr = new JSONArray();
            if (items != null) {
                for (Card c : items) {
                    JSONObject o = new JSONObject();
                    o.put("prompt", c.prompt == null ? "" : c.prompt);
                    if (c.options != null) {
                        JSONArray opts = new JSONArray();
                        for (int k = 0; k < 4; k++) {
                            opts.put(k < c.options.length && c.options[k] != null
                                    ? c.options[k] : "");
                        }
                        o.put("options", opts);
                        o.put("correctIndex", c.correctIndex);
                    }
                    o.put("reveal", c.reveal == null ? "" : c.reveal);
                    o.put("explanation", c.explanation == null ? "" : c.explanation);
                    arr.put(o);
                }
            }
            root.put("items", arr);
        } catch (Exception ignored) {
        }
        return root.toString();
    }

    /**
     * Parses a session back. Old v3.1 sessions stored a plain quiz array
     * ("[...]") - those still parse as quiz mode.
     */
    public static PracticeSet parseSession(String json) {
        PracticeSet set = new PracticeSet();
        if (json == null) return set;
        String s = json.trim();
        if (s.startsWith("[")) {
            set.mode = MODE_QUIZ;
            set.items = fromQuizQuestions(QuizUtil.parseQuiz(s));
            return set;
        }
        try {
            JSONObject root = new JSONObject(s);
            set.mode = root.optString("mode", MODE_QUIZ);
            JSONArray arr = root.optJSONArray("items");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    Card c = new Card();
                    c.prompt = o.optString("prompt", "").trim();
                    JSONArray opts = o.optJSONArray("options");
                    if (opts != null) {
                        c.options = new String[]{"", "", "", ""};
                        for (int k = 0; k < 4 && k < opts.length(); k++) {
                            c.options[k] = opts.optString(k, "");
                        }
                        c.correctIndex = o.optInt("correctIndex", -1);
                    }
                    c.reveal = o.optString("reveal", "").trim();
                    c.explanation = o.optString("explanation", "").trim();
                    if (!c.prompt.isEmpty()) set.items.add(c);
                }
            }
        } catch (Exception ignored) {
            // malformed session: empty set
        }
        return set;
    }

    private PracticeUtil() {
    }
}
