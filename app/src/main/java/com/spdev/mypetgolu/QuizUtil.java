package com.spdev.mypetgolu;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses and writes the quiz JSON used by Quiz Maker, the Library and the
 * reader. Schema per question:
 * {"question":"...","options":["a","b","c","d"],"correctIndex":0,"explanation":"..."}
 * Parsing is tolerant: markdown fences and surrounding text are stripped.
 */
public final class QuizUtil {

    public static class Question {
        public String question = "";
        public String[] options = new String[]{"", "", "", ""};
        public int correctIndex = 0;
        public String explanation = "";
    }

    /** Parses quiz JSON (tolerant of fences and surrounding text). */
    public static List<Question> parseQuiz(String raw) {
        List<Question> out = new ArrayList<>();
        if (raw == null) return out;
        String json = extractArray(raw);
        if (json == null) return out;
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Question q = new Question();
                q.question = o.optString("question", "").trim();
                JSONArray opts = o.optJSONArray("options");
                if (opts != null) {
                    for (int k = 0; k < 4 && k < opts.length(); k++) {
                        q.options[k] = opts.optString(k, "");
                    }
                }
                q.correctIndex = o.optInt("correctIndex", 0);
                if (q.correctIndex < 0 || q.correctIndex > 3) q.correctIndex = 0;
                q.explanation = o.optString("explanation", "").trim();
                if (!q.question.isEmpty()) out.add(q);
            }
        } catch (Exception ignored) {
            // malformed JSON: return whatever was parsed (possibly empty)
        }
        return out;
    }

    /** Serializes questions back to a JSON array string. */
    public static String toJson(List<Question> questions) {
        JSONArray arr = new JSONArray();
        if (questions == null) return arr.toString();
        try {
            for (Question q : questions) {
                JSONObject o = new JSONObject();
                o.put("question", q.question);
                JSONArray opts = new JSONArray();
                for (int k = 0; k < 4; k++) opts.put(q.options[k] == null ? "" : q.options[k]);
                o.put("options", opts);
                o.put("correctIndex", q.correctIndex);
                o.put("explanation", q.explanation);
                arr.put(o);
            }
        } catch (Exception ignored) {
        }
        return arr.toString();
    }

    /** Finds the JSON array inside free-form model output. */
    public static String extractArray(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        // strip markdown fences
        s = s.replace("```json", "").replace("```", "").trim();
        int start = s.indexOf('[');
        int end = s.lastIndexOf(']');
        if (start < 0 || end <= start) return null;
        return s.substring(start, end + 1);
    }

    /** One question rendered as readable text (for the Library viewer). */
    public static String displayText(Question q, int number) {
        StringBuilder b = new StringBuilder();
        b.append("Q").append(number).append(". ").append(q.question).append("\n");
        char letter = 'A';
        for (int k = 0; k < 4; k++) {
            b.append("  ").append(letter).append(") ").append(q.options[k]);
            if (k == q.correctIndex) b.append("  <-- correct");
            b.append("\n");
            letter++;
        }
        if (!q.explanation.isEmpty()) b.append("  Why: ").append(q.explanation).append("\n");
        return b.toString();
    }

    /** Whole quiz rendered as readable text. */
    public static String displayAll(List<Question> questions) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < questions.size(); i++) {
            b.append(displayText(questions.get(i), i + 1)).append("\n");
        }
        return b.toString();
    }

    /** Short title for a quiz, from its first question. */
    public static String quizTitle(List<Question> questions, String fallback) {
        if (questions == null || questions.isEmpty()) return fallback;
        String q = questions.get(0).question;
        if (q.length() > 60) q = q.substring(0, 60) + "...";
        return "Quiz (" + questions.size() + " Q): " + q;
    }

    private QuizUtil() {
    }
}
