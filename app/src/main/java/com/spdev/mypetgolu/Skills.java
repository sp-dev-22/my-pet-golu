package com.spdev.mypetgolu;

import android.content.Context;

/**
 * Built-in skills (system prompts). Skills are stored in AppDb so the user
 * can also create their own; these four are seeded on first run and are
 * protected from deletion.
 */
public final class Skills {

    public static final String NAME_DEFAULT = "Golu Tutor";
    public static final String NAME_QUIZ_MAKER = "Quiz Maker";
    public static final String NAME_ONELINER = "One Liner Maker";
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

    public static String oneLinerPrompt() {
        return "You are Golu's One Liner Maker. Turn the topic or the attached files into short, "
                + "crisp one-line facts that are easy to revise before an exam.\n"
                + "Rules:\n"
                + "1. Reply ONLY with one-liners, one per line, each starting with a bullet (-).\n"
                + "2. Each line is ONE fact, short and complete (under 20 words).\n"
                + "3. Match the user's language (Hindi, English or Hinglish).\n"
                + "4. If files are attached, use ONLY their content.\n"
                + "5. No introduction, no conclusion, no numbering - just the lines.";
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

    /** Inserts the built-ins if they are not already in the DB. */
    public static void seed(AppDb db) {
        if (db.getSkillByName(NAME_DEFAULT) == null) {
            db.addSkill(NAME_DEFAULT, defaultPrompt(), true);
        }
        if (db.getSkillByName(NAME_QUIZ_MAKER) == null) {
            db.addSkill(NAME_QUIZ_MAKER, quizMakerPrompt(), true);
        }
        if (db.getSkillByName(NAME_ONELINER) == null) {
            db.addSkill(NAME_ONELINER, oneLinerPrompt(), true);
        }
        if (db.getSkillByName(NAME_ASKER) == null) {
            db.addSkill(NAME_ASKER, askerPrompt(), true);
        }
    }

    /** Which library kind an AI reply belongs to, based on the active skill. */
    public static String libraryKindForSkill(String skillName) {
        if (NAME_QUIZ_MAKER.equals(skillName)) return AiDb.KIND_QUIZ;
        if (NAME_ONELINER.equals(skillName)) return AiDb.KIND_ONELINER;
        if (NAME_ASKER.equals(skillName)) return AiDb.KIND_QUESTIONS;
        return null; // not a creator skill: no auto-save
    }

    /**
     * The system prompt actually sent to Gemini: the skill prompt plus the
     * AI memory facts (when memory is enabled).
     */
    public static String effectivePrompt(Context c, AppDb db, AppDb.Skill skill) {
        String base = skill == null ? defaultPrompt() : skill.prompt;
        if (Prefs.isMemoryEnabled(c)) {
            String mem = db.memoryBlock();
            if (!mem.isEmpty()) base = base + mem;
        }
        return base;
    }

    private Skills() {
    }
}
