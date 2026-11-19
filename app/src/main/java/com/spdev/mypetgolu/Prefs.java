package com.spdev.mypetgolu;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

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

    /** Current preset models (v0.6.0). Old presets were removed (see OLD_PRESETS). */
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
    private static final String K_SKILLS_SEEDED_V60 = "skills_seeded_v60";
    private static final String K_SKILLS_SEEDED_V70 = "skills_seeded_v70";
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
    private static final String K_LOOK_CLOTHES_COLOR = "clothes_color";

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
     * v0.6.0 re-seed (adds the Flashcard Maker skill and refreshes the
     * built-in prompts, e.g. the one-liner JSON format). Existing installs
     * already have skills_seeded set, so this separate flag makes the
     * one-time v0.6.0 seed run exactly once.
     */
    public static boolean isSkillsSeededV60(Context c) {
        return sp(c).getBoolean(K_SKILLS_SEEDED_V60, false);
    }

    public static void setSkillsSeededV60(Context c) {
        sp(c).edit().putBoolean(K_SKILLS_SEEDED_V60, true).apply();
    }

    /**
     * v0.7.0 re-seed (adds the Web App Maker skill and refreshes the
     * built-in prompts). Same pattern as the v0.6.0 flag: existing installs
     * already have the older flags set, so this separate flag makes the
     * one-time v0.7.0 seed run exactly once.
     */
    public static boolean isSkillsSeededV70(Context c) {
        return sp(c).getBoolean(K_SKILLS_SEEDED_V70, false);
    }

    public static void setSkillsSeededV70(Context c) {
        sp(c).edit().putBoolean(K_SKILLS_SEEDED_V70, true).apply();
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

    /** Clothes colour index into PetView.CLOTHES_COLORS; -1 = the outfit's own colour. */
    public static int getClothesColor(Context c) {
        return sp(c).getInt(K_LOOK_CLOTHES_COLOR, -1);
    }

    public static void saveLook(Context c, int palette, int gender, int outfit, int accessory) {
        saveLook(c, palette, gender, outfit, accessory, getClothesColor(c));
    }

    public static void saveLook(Context c, int palette, int gender, int outfit, int accessory,
                                int clothesColor) {
        sp(c).edit()
                .putInt(K_LOOK_PALETTE, palette)
                .putInt(K_LOOK_GENDER, gender)
                .putInt(K_LOOK_OUTFIT, outfit)
                .putInt(K_LOOK_ACCESSORY, accessory)
                .putInt(K_LOOK_CLOTHES_COLOR, clothesColor)
                .apply();
    }

    /** Clears everything except the API key choice is left to the caller. */
    public static void clearAll(Context c) {
        sp(c).edit().clear().apply();
    }

    // ------------------------------------------------------- backup / restore

    /**
     * All settings for a backup, as JSON. The Gemini API key is NEVER
     * included - it stays on the device and the user re-enters it after
     * a restore. If a new setting is added to this file, add it here too.
     */
    public static JSONObject exportSettings(Context c) {
        JSONObject o = new JSONObject();
        try {
            SharedPreferences p = sp(c);
            o.put("models_saved", p.getString(K_MODELS_SAVED, ""));
            o.put("model_agent", p.getString(K_MODEL_AGENT, "gemini-3.6-flash"));
            o.put("model_asking", p.getString(K_MODEL_ASKING, "gemini-3.6-flash"));
            o.put("model_convo", p.getString(K_MODEL_CONVO, "gemini-3.5-flash-lite"));
            o.put("model_bg", p.getString(K_MODEL_BG, "gemini-3.5-flash-lite"));
            o.put("tts_lang", p.getString(K_TTS_LANG, "hi"));
            o.put("stt_lang", p.getString(K_STT_LANG, "hi"));
            o.put("quiz_time_sec", p.getInt(K_QUIZ_TIME, 30));
            o.put("memory_enabled", p.getBoolean(K_MEMORY_ON, true));
            o.put("conv_mic", p.getBoolean(K_CONV_MIC, true));
            o.put("conv_tts", p.getBoolean(K_CONV_TTS, true));
            o.put("conv_stt", p.getBoolean(K_CONV_STT, true));
            o.put("reminder_on", p.getBoolean(K_REMINDER_ON, false));
            o.put("reminder_hour", p.getInt(K_REMINDER_HOUR, 19));
            o.put("reminder_min", p.getInt(K_REMINDER_MIN, 0));
            o.put("palette", p.getInt(K_LOOK_PALETTE, 0));
            o.put("gender", p.getInt(K_LOOK_GENDER, 0));
            o.put("outfit", p.getInt(K_LOOK_OUTFIT, 0));
            o.put("accessory", p.getInt(K_LOOK_ACCESSORY, 0));
            o.put("clothes_color", p.getInt(K_LOOK_CLOTHES_COLOR, -1));
            o.put("practice_mic_tap", p.getBoolean(K_PRACTICE_MIC_TAP, false));
            o.put("models_migrated_v31", p.getBoolean(K_MODELS_MIGRATED, false));
            o.put("skills_seeded", p.getBoolean(K_SKILLS_SEEDED, false));
            o.put("skills_seeded_v60", p.getBoolean(K_SKILLS_SEEDED_V60, false));
            o.put("skills_seeded_v70", p.getBoolean(K_SKILLS_SEEDED_V70, false));
        } catch (Exception ignored) {
            // org.json only throws for invalid values; these are all plain types
        }
        return o;
    }

    /**
     * Applies settings from a backup. Only keys present in the JSON are
     * touched. The API key is deliberately never read from a backup file -
     * even if a (forged) backup contained one, it is ignored here.
     */
    public static void restoreSettings(Context c, JSONObject o) {
        if (o == null) return;
        SharedPreferences.Editor e = sp(c).edit();
        if (o.has("models_saved")) e.putString(K_MODELS_SAVED, o.optString("models_saved", ""));
        if (o.has("model_agent")) e.putString(K_MODEL_AGENT, o.optString("model_agent", "gemini-3.6-flash"));
        if (o.has("model_asking")) e.putString(K_MODEL_ASKING, o.optString("model_asking", "gemini-3.6-flash"));
        if (o.has("model_convo")) e.putString(K_MODEL_CONVO, o.optString("model_convo", "gemini-3.5-flash-lite"));
        if (o.has("model_bg")) e.putString(K_MODEL_BG, o.optString("model_bg", "gemini-3.5-flash-lite"));
        if (o.has("tts_lang")) e.putString(K_TTS_LANG, o.optString("tts_lang", "hi"));
        if (o.has("stt_lang")) e.putString(K_STT_LANG, o.optString("stt_lang", "hi"));
        if (o.has("quiz_time_sec")) e.putInt(K_QUIZ_TIME, o.optInt("quiz_time_sec", 30));
        if (o.has("memory_enabled")) e.putBoolean(K_MEMORY_ON, o.optBoolean("memory_enabled", true));
        if (o.has("conv_mic")) e.putBoolean(K_CONV_MIC, o.optBoolean("conv_mic", true));
        if (o.has("conv_tts")) e.putBoolean(K_CONV_TTS, o.optBoolean("conv_tts", true));
        if (o.has("conv_stt")) e.putBoolean(K_CONV_STT, o.optBoolean("conv_stt", true));
        if (o.has("reminder_on")) e.putBoolean(K_REMINDER_ON, o.optBoolean("reminder_on", false));
        if (o.has("reminder_hour")) e.putInt(K_REMINDER_HOUR, o.optInt("reminder_hour", 19));
        if (o.has("reminder_min")) e.putInt(K_REMINDER_MIN, o.optInt("reminder_min", 0));
        if (o.has("palette")) e.putInt(K_LOOK_PALETTE, o.optInt("palette", 0));
        if (o.has("gender")) e.putInt(K_LOOK_GENDER, o.optInt("gender", 0));
        if (o.has("outfit")) e.putInt(K_LOOK_OUTFIT, o.optInt("outfit", 0));
        if (o.has("accessory")) e.putInt(K_LOOK_ACCESSORY, o.optInt("accessory", 0));
        if (o.has("clothes_color")) e.putInt(K_LOOK_CLOTHES_COLOR, o.optInt("clothes_color", -1));
        if (o.has("practice_mic_tap")) e.putBoolean(K_PRACTICE_MIC_TAP, o.optBoolean("practice_mic_tap", false));
        if (o.has("models_migrated_v31")) e.putBoolean(K_MODELS_MIGRATED, o.optBoolean("models_migrated_v31", false));
        if (o.has("skills_seeded")) e.putBoolean(K_SKILLS_SEEDED, o.optBoolean("skills_seeded", false));
        if (o.has("skills_seeded_v60")) e.putBoolean(K_SKILLS_SEEDED_V60, o.optBoolean("skills_seeded_v60", false));
        if (o.has("skills_seeded_v70")) e.putBoolean(K_SKILLS_SEEDED_V70, o.optBoolean("skills_seeded_v70", false));
        // NOTE: "api_key" is never read here, on purpose.
        e.apply();
    }

    private Prefs() {
    }
}
