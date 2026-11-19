package com.spdev.mypetgolu;

import android.content.Context;

/**
 * Built-in skills (system prompts). Skills are stored in AppDb so the user
 * can also create their own; these five are seeded on first run and are
 * protected from deletion (and from editing - tapping one only shows it).
 * The creator skills each carry a short demo JSON in the exact shape the
 * app parses, so Gemini always returns a format Practice mode can play.
 */
public final class Skills {

    public static final String NAME_DEFAULT = "Golu Tutor";
    public static final String NAME_QUIZ_MAKER = "Quiz Maker";
    public static final String NAME_ONELINER = "One Liner Maker";
    public static final String NAME_FLASHCARD = "Flashcard Maker";
    public static final String NAME_ASKER = "Questions Asker";

    public static String defaultPrompt() {
        return "You are Golu, a friendly study buddy for a student. "
                + "Explain simply and clearly. The student writes in Hindi, English or Hinglish - "
                + "reply in the same language they used. Use short paragraphs, simple words and "
                + "a small example when it helps. If the student is stuck, encourage them and "
                + "break the topic into tiny steps. Never make up facts; if you are unsure, say so.";
    }

    /**
     * Quiz Maker: the model must return ONLY a JSON array of questions.
     * The demo JSON below shows the exact schema the app parses.
     */
    public static String quizMakerPrompt() {
        return "You are Golu's Quiz Maker. Create multiple-choice quiz questions for a student. "
                + "Follow these rules strictly:\n"
                + "1. Reply with ONLY a JSON array - no markdown, no code fences, no extra text.\n"
                + "2. Each question has exactly these fields: question, options (array of 4 strings), "
                + "correctIndex (0-3), explanation (one short sentence).\n"
                + "3. Match the language of the user's request (Hindi, English or Hinglish).\n"
                + "4. If files are attached, base every question STRICTLY on their content.\n"
                + "5. Keep options similar in length; only one option is correct.\n"
                + "Demo of the exact format (2 questions):\n"
                + "[{\"question\":\"What is the capital of India?\","
                + "\"options\":[\"Mumbai\",\"New Delhi\",\"Kolkata\",\"Chennai\"],"
                + "\"correctIndex\":1,"
                + "\"explanation\":\"New Delhi is the capital of India.\"},"
                + "{\"question\":\"2 + 3 = ?\","
                + "\"options\":[\"4\",\"5\",\"6\",\"7\"],"
                + "\"correctIndex\":1,"
                + "\"explanation\":\"2 plus 3 equals 5.\"}]\n"
                + "Now create the quiz the user asked for, in exactly this format.";
    }

    /**
     * One Liner Maker: the model must return ONLY a JSON array of one-line
     * facts, in exactly the shape PracticeUtil.parseOneLiners reads.
     * (Before v0.6.0 this skill returned plain bullet lines; the JSON shape
     * lets Practice judge spoken recalls and keeps every creator skill in
     * the same JSON family. The line format is still parsed as a fallback.)
     */
    public static String oneLinerPrompt() {
        return "You are Golu's One Liner Maker. Turn the topic or the attached files into short, "
                + "crisp one-line facts that are easy to revise before an exam.\n"
                + "Rules:\n"
                + "1. Reply with ONLY a JSON array - no markdown, no code fences, no extra text.\n"
                + "2. Each item has exactly one field: \"line\" - ONE fact, short and complete "
                + "(under 20 words).\n"
                + "3. Match the user's language (Hindi, English or Hinglish).\n"
                + "4. If files are attached, use ONLY their content.\n"
                + "5. No introduction, no conclusion, no numbering - just the JSON array.\n"
                + "Demo of the exact format (3 one-liners):\n"
                + "[{\"line\":\"The Preamble begins with 'We, the People of India'.\"},"
                + "{\"line\":\"The Constitution of India came into force on 26 January 1950.\"},"
                + "{\"line\":\"Dr. B. R. Ambedkar chaired the Drafting Committee.\"}]\n"
                + "Now create the one-liners the user asked for, in exactly this format.";
    }

    /**
     * Flashcard Maker: the model must return ONLY a JSON array of cards,
     * in exactly the shape PracticeUtil.parseFlashcards reads.
     */
    public static String flashcardMakerPrompt() {
        return "You are Golu's Flashcard Maker. Turn the topic or the attached files into "
                + "flashcards for quick revision.\n"
                + "Rules:\n"
                + "1. Reply with ONLY a JSON array - no markdown, no code fences, no extra text.\n"
                + "2. Each card has exactly two fields: \"front\" (the question, term or prompt) "
                + "and \"back\" (the short answer or definition).\n"
                + "3. Keep the front short (one question or term) and the back short (one or two "
                + "sentences at most) so it can be recalled out loud.\n"
                + "4. Match the user's language (Hindi, English or Hinglish).\n"
                + "5. If files are attached, base every card STRICTLY on their content.\n"
                + "6. No introduction, no conclusion - just the JSON array.\n"
                + "Demo of the exact format (3 flashcards):\n"
                + "[{\"front\":\"What is the capital of India?\",\"back\":\"New Delhi\"},"
                + "{\"front\":\"Who wrote the Preamble of the Indian Constitution?\","
                + "\"back\":\"The Constituent Assembly, led by Dr. B. R. Ambedkar\"},"
                + "{\"front\":\"Article 370 was related to which state?\",\"back\":\"Jammu and Kashmir\"}]\n"
                + "Now create the flashcards the user asked for, in exactly this format.";
    }

    public static String askerPrompt() {
        return "You are Golu's Questions Asker. Create practice questions for a student.\n"
                + "Rules:\n"
                + "1. Reply with numbered questions. After each question, on the next line, "
                + "write 'Answer: ' followed by the answer, then one short line starting with "
                + "'Why: ' explaining it.\n"
                + "2. Mix easy, medium and hard questions.\n"
                + "3. Match the user's language (Hindi, English or Hinglish).\n"
                + "4. If files are attached, base every question STRICTLY on their content.\n"
                + "5. No introduction or conclusion - start with question 1.";
    }

    /**
     * Inserts the built-ins if they are not already in the DB, and refreshes
     * the stored prompt of any built-in whose prompt differs from the
     * current default (built-ins cannot be edited by the user, so a stored
     * prompt is always an older default - refreshing it is how existing
     * installs pick up prompt improvements, e.g. the v0.6.0 one-liner JSON
     * format). Safe to call repeatedly.
     */
    public static void seed(AppDb db) {
        seedOne(db, NAME_DEFAULT, defaultPrompt());
        seedOne(db, NAME_QUIZ_MAKER, quizMakerPrompt());
        seedOne(db, NAME_ONELINER, oneLinerPrompt());
        seedOne(db, NAME_FLASHCARD, flashcardMakerPrompt());
        seedOne(db, NAME_ASKER, askerPrompt());
    }

    private static void seedOne(AppDb db, String name, String prompt) {
        AppDb.Skill existing = db.getSkillByName(name);
        if (existing == null) {
            db.addSkill(name, prompt, true);
        } else if (!prompt.equals(existing.prompt)) {
            db.updateSkill(existing.id, name, prompt);
        }
    }

    /** Which library kind an AI reply belongs to, based on the active skill. */
    public static String libraryKindForSkill(String skillName) {
        if (NAME_QUIZ_MAKER.equals(skillName)) return AiDb.KIND_QUIZ;
        if (NAME_ONELINER.equals(skillName)) return AiDb.KIND_ONELINER;
        if (NAME_FLASHCARD.equals(skillName)) return AiDb.KIND_FLASHCARDS;
        if (NAME_ASKER.equals(skillName)) return AiDb.KIND_QUESTIONS;
        return null; // not a creator skill: no auto-save
    }

    /**
     * Library kind for a chat with several skills. Quiz Maker wins (its JSON
     * must be parsed), then Flashcard Maker (its JSON too), then the first
     * other creator skill found (One Liner Maker / Questions Asker).
     */
    public static String libraryKindForSkills(java.util.List<AppDb.Skill> skills) {
        if (skills == null) return null;
        String found = null;
        for (AppDb.Skill s : skills) {
            String k = s == null ? null : libraryKindForSkill(s.name);
            if (AiDb.KIND_QUIZ.equals(k)) return k;
            if (AiDb.KIND_FLASHCARDS.equals(k)) return k;
            if (found == null) found = k;
        }
        return found;
    }

    /** True when any of the chat's skills expects a JSON reply from Gemini. */
    public static boolean needsJsonMode(java.util.List<AppDb.Skill> skills) {
        if (skills == null) return false;
        for (AppDb.Skill s : skills) {
            if (s == null) continue;
            if (NAME_QUIZ_MAKER.equals(s.name) || NAME_ONELINER.equals(s.name)
                    || NAME_FLASHCARD.equals(s.name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The system prompt actually sent to Gemini: the skill prompt plus the
     * AI memory facts (when memory is enabled).
     */
    public static String effectivePrompt(Context c, AppDb db, AppDb.Skill skill) {
        java.util.List<AppDb.Skill> one = new java.util.ArrayList<>();
        if (skill != null) one.add(skill);
        return effectivePrompt(c, db, one);
    }

    /**
     * Same, for a chat with several skills: every skill prompt is included
     * as its own section so Gemini follows all of them.
     */
    public static String effectivePrompt(Context c, AppDb db, java.util.List<AppDb.Skill> skills) {
        String base;
        if (skills == null || skills.isEmpty()) {
            base = defaultPrompt();
        } else if (skills.size() == 1) {
            base = skills.get(0) == null ? defaultPrompt() : skills.get(0).prompt;
        } else {
            StringBuilder b = new StringBuilder();
            b.append("You are Golu, a friendly study buddy. Follow ALL of these "
                    + "skill instructions together; when they conflict, the first one wins.\n");
            for (int i = 0; i < skills.size(); i++) {
                AppDb.Skill s = skills.get(i);
                if (s == null) continue;
                b.append("\n--- Skill ").append(i + 1).append(": ").append(s.name).append(" ---\n");
                b.append(s.prompt).append('\n');
            }
            base = b.toString();
        }
        if (Prefs.isMemoryEnabled(c)) {
            String mem = db.memoryBlock();
            if (!mem.isEmpty()) base = base + mem;
        }
        return base;
    }

    private Skills() {
    }
}
