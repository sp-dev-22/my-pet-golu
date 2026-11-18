package com.spdev.mypetgolu;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * All user settings, stored in SharedPreferences ("golu_study").
 * The Gemini API key lives here only - it is never written into code,
 * resources or the repository.
 */
public final class Prefs {

    private static final String NAME = "golu_study";

    /** Current preset models (v0.4.0). Old presets were removed (see OLD_PRESETS). */
    public static final String[] DEFAULT_MODELS = {
            "gemini-3.5-flash-lite",
            "gemini-3.6-flash",
            "gemini-3.8-flash"
    };

    /** Presets from v3.0, dropped in v3.1 - migrated away on existing installs. */
    private static final String[] OLD_PRESETS = {
            "gemini-2.5-flash",
            "gemini-2.5-flash-lite",
            "gemini-3.5-flash"
    };

    private static final String K_MODELS_MIGRATED = "models_migrated_v31";
    private static final String K_SKILLS_SEEDED = "skills_seeded";
    private static final String K_PRACTICE_MIC_TAP = "practice_mic_tap";

    private static final String K_API_KEY = "api_key";
    private static final String K_MODEL_AGENT = "model_agent";
    private static final String K_MODEL_ASKING = "model_asking";
    private static final String K_MODEL_CONVO = "model_convo";
    private static final String K_MODEL_BG = "model_bg";
    private static final String K_MODELS_SAVED = "models_saved";
    private static final String K_TTS_LANG = "tts_lang";
    private static final String K_STT_LANG = "stt_lang";
    private static final String K_QUIZ_TIME = "quiz_time_sec";
    private static final String K_MEMORY_ON = "memory_enabled";
    private static final String K_CONV_MIC = "conv_mic";
    private static final String K_CONV_TTS = "conv_tts";
    private static final String K_CONV_STT = "conv_stt";
    private static final String K_REMINDER_ON = "reminder_on";
    private static final String K_REMINDER_HOUR = "reminder_hour";
    private static final String K_REMINDER_MIN = "reminder_min";
    private static final String K_LOOK_PALETTE = "palette";
    private static final String K_LOOK_GENDER = "gender";
    private static final String K_LOOK_OUTFIT = "outfit";
    private static final String K_LOOK_ACCESSORY = "accessory";

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    // -------------------------------------------------------------- API key

    public static String getApiKey(Context c) {
        return sp(c).getString(K_API_KEY, "");
    }

    public static void setApiKey(Context c, String key) {
        sp(c).edit().putString(K_API_KEY, key == null ? "" : key.trim()).apply();
    }

    public static boolean hasApiKey(Context c) {
        String k = getApiKey(c);
        return k != null && !k.trim().isEmpty();
    }

    // ----------------------------------------------------------------- models

    /**
     * One-time migration (v3.1): drops the old v3.0 presets from the saved
     * list, makes sure the three new presets are present, keeps any other
     * user-added models, and remaps the four model slots off old presets.
     * Fresh installs are seeded with DEFAULT_MODELS once.
     */
    private static void migrateModelsIfNeeded(Context c) {
        SharedPreferences p = sp(c);
        if (p.getBoolean(K_MODELS_MIGRATED, false)) return;
        LinkedHashSet<String> saved = new LinkedHashSet<>();
        String raw = p.getString(K_MODELS_SAVED, null);
        if (raw != null) {
            for (String s : raw.split("\n")) {
                s = s.trim();
                if (!s.isEmpty()) saved.add(s);
            }
        }
        for (String old : OLD_PRESETS) saved.remove(old);
        for (String def : DEFAULT_MODELS) saved.add(def);
        if (saved.isEmpty()) saved.addAll(Arrays.asList(DEFAULT_MODELS));
        saveModels(c, saved);
        remapSlot(c, K_MODEL_AGENT);
        remapSlot(c, K_MODEL_ASKING);
        remapSlot(c, K_MODEL_CONVO);
        remapSlot(c, K_MODEL_BG);
        p.edit().putBoolean(K_MODELS_MIGRATED, true).apply();
    }

    /** Maps an old v3.0 preset to its v3.1 replacement (or returns it unchanged). */
    public static String mapOldModel(String model) {
        if ("gemini-2.5-flash".equals(model) || "gemini-3.5-flash".equals(model)) {
            return "gemini-3.6-flash";
        }
        if ("gemini-2.5-flash-lite".equals(model)) return "gemini-3.5-flash-lite";
        return model;
    }

    private static void remapSlot(Context c, String key) {
        String v = sp(c).getString(key, null);
        if (v != null && !mapOldModel(v).equals(v)) {
            sp(c).edit().putString(key, mapOldModel(v)).apply();
        }
    }

    /**
     * Saved model names. The defaults are seeded ONCE (first run / migration);
     * after that the stored list is the truth, so deleting a model in
     * Settings really deletes it.
     */
    public static List<String> getSavedModels(Context c) {
        migrateModelsIfNeeded(c);
        String raw = sp(c).getString(K_MODELS_SAVED, null);
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (raw != null) {
            for (String s : raw.split("\n")) {
                s = s.trim();
                if (!s.isEmpty()) out.add(s);
            }
        }
        if (out.isEmpty()) out.addAll(Arrays.asList(DEFAULT_MODELS));
        return new ArrayList<>(out);
    }

    public static void addSavedModel(Context c, String model) {
        if (model == null) return;
        model = model.trim();
        if (model.isEmpty()) return;
        LinkedHashSet<String> all = new LinkedHashSet<>(getSavedModels(c));
        all.add(model);
        saveModels(c, all);
    }

    public static void removeSavedModel(Context c, String model) {
        LinkedHashSet<String> all = new LinkedHashSet<>(getSavedModels(c));
        all.remove(model);
        if (all.isEmpty()) all.add(DEFAULT_MODELS[0]);
        saveModels(c, all);
    }

    private static void saveModels(Context c, java.util.Collection<String> models) {
        StringBuilder b = new StringBuilder();
        for (String m : models) {
            if (b.length() > 0) b.append('\n');
            b.append(m);
        }
        sp(c).edit().putString(K_MODELS_SAVED, b.toString()).apply();
    }

    public static String getAgentModel(Context c) {
        migrateModelsIfNeeded(c);
        return mapOldModel(sp(c).getString(K_MODEL_AGENT, "gemini-3.6-flash"));
    }

    public static void setAgentModel(Context c, String m) {
        sp(c).edit().putString(K_MODEL_AGENT, m).apply();
    }

    public static String getAskingModel(Context c) {
        migrateModelsIfNeeded(c);
        return mapOldModel(sp(c).getString(K_MODEL_ASKING, "gemini-3.6-flash"));
    }

    public static void setAskingModel(Context c, String m) {
        sp(c).edit().putString(K_MODEL_ASKING, m).apply();
    }

    public static String getConversationModel(Context c) {
        migrateModelsIfNeeded(c);
        return mapOldModel(sp(c).getString(K_MODEL_CONVO, "gemini-3.5-flash-lite"));
    }

    public static void setConversationModel(Context c, String m) {
        sp(c).edit().putString(K_MODEL_CONVO, m).apply();
    }

    public static String getBackgroundModel(Context c) {
        migrateModelsIfNeeded(c);
        return mapOldModel(sp(c).getString(K_MODEL_BG, "gemini-3.5-flash-lite"));
    }

    public static void setBackgroundModel(Context c, String m) {
        sp(c).edit().putString(K_MODEL_BG, m).apply();
    }

    // ------------------------------------------------------------- languages

    /** "hi" or "en" */
    public static String getTtsLang(Context c) {
        return sp(c).getString(K_TTS_LANG, "hi");
    }

    public static void setTtsLang(Context c, String lang) {
        sp(c).edit().putString(K_TTS_LANG, lang).apply();
    }

    /** "hi" or "en" */
    public static String getSttLang(Context c) {
        return sp(c).getString(K_STT_LANG, "hi");
    }

    public static void setSttLang(Context c, String lang) {
        sp(c).edit().putString(K_STT_LANG, lang).apply();
    }

    public static Locale getTtsLocale(Context c) {
        return "en".equals(getTtsLang(c)) ? Locale.US : new Locale("hi", "IN");
    }

    public static String getSttTag(Context c) {
        return "en".equals(getSttLang(c)) ? "en-IN" : "hi-IN";
    }

    // ------------------------------------------------------------ quiz timer

    public static int getQuizTimeSec(Context c) {
        int s = sp(c).getInt(K_QUIZ_TIME, 30);
        if (s < 5) s = 5;
        if (s > 300) s = 300;
        return s;
    }

    public static void setQuizTimeSec(Context c, int sec) {
        sp(c).edit().putInt(K_QUIZ_TIME, sec).apply();
    }

    // ---------------------------------------------------------------- memory

    public static boolean isMemoryEnabled(Context c) {
        return sp(c).getBoolean(K_MEMORY_ON, true);
    }

    public static void setMemoryEnabled(Context c, boolean on) {
        sp(c).edit().putBoolean(K_MEMORY_ON, on).apply();
    }

    // ------------------------------------------------- conversation toggles

    /** Built-in skills were seeded once (avoids re-checking the DB every launch). */
    public static boolean isSkillsSeeded(Context c) {
        return sp(c).getBoolean(K_SKILLS_SEEDED, false);
    }

    public static void setSkillsSeeded(Context c) {
        sp(c).edit().putBoolean(K_SKILLS_SEEDED, true).apply();
    }

    /**
     * Practice mic mode: false = AUTO (listens on its own when a question is
     * asked), true = TAP (the user taps the mic button to speak the answer).
     */
    public static boolean isPracticeMicTap(Context c) {
        return sp(c).getBoolean(K_PRACTICE_MIC_TAP, false);
    }

    public static void setPracticeMicTap(Context c, boolean tap) {
        sp(c).edit().putBoolean(K_PRACTICE_MIC_TAP, tap).apply();
    }

    public static boolean isConvMicOn(Context c) {
        return sp(c).getBoolean(K_CONV_MIC, true);
    }

    public static void setConvMicOn(Context c, boolean on) {
        sp(c).edit().putBoolean(K_CONV_MIC, on).apply();
    }

    public static boolean isConvTtsOn(Context c) {
        return sp(c).getBoolean(K_CONV_TTS, true);
    }

    public static void setConvTtsOn(Context c, boolean on) {
        sp(c).edit().putBoolean(K_CONV_TTS, on).apply();
    }

    public static boolean isConvSttOn(Context c) {
        return sp(c).getBoolean(K_CONV_STT, true);
    }

    public static void setConvSttOn(Context c, boolean on) {
        sp(c).edit().putBoolean(K_CONV_STT, on).apply();
    }

    // --------------------------------------------------------------- reminder

    public static boolean isReminderOn(Context c) {
        return sp(c).getBoolean(K_REMINDER_ON, false);
    }

    public static void setReminderOn(Context c, boolean on) {
        sp(c).edit().putBoolean(K_REMINDER_ON, on).apply();
    }

    public static int getReminderHour(Context c) {
        return sp(c).getInt(K_REMINDER_HOUR, 19);
    }

    public static int getReminderMinute(Context c) {
        return sp(c).getInt(K_REMINDER_MIN, 0);
    }

    public static void setReminderTime(Context c, int hour, int minute) {
        sp(c).edit().putInt(K_REMINDER_HOUR, hour).putInt(K_REMINDER_MIN, minute).apply();
    }

    // ------------------------------------------------------------- Golu look

    public static int getPalette(Context c) {
        return sp(c).getInt(K_LOOK_PALETTE, 0);
    }

    public static int getGender(Context c) {
        return sp(c).getInt(K_LOOK_GENDER, 0);
    }

    public static int getOutfit(Context c) {
        return sp(c).getInt(K_LOOK_OUTFIT, 0);
    }

    public static int getAccessory(Context c) {
        return sp(c).getInt(K_LOOK_ACCESSORY, 0);
    }

    public static void saveLook(Context c, int palette, int gender, int outfit, int accessory) {
        sp(c).edit()
                .putInt(K_LOOK_PALETTE, palette)
                .putInt(K_LOOK_GENDER, gender)
                .putInt(K_LOOK_OUTFIT, outfit)
                .putInt(K_LOOK_ACCESSORY, accessory)
                .apply();
    }

    /** Clears everything except the API key choice is left to the caller. */
    public static void clearAll(Context c) {
        sp(c).edit().clear().apply();
    }

    private Prefs() {
    }
}
