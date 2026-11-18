package com.spdev.mypetgolu;

import android.app.Activity;
import android.app.TimePickerDialog;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.TimePicker;

import java.io.File;
import java.util.List;
import java.util.Locale;

/**
 * Settings: API key, models, voice languages, quiz timer, Talk
 * toggles, study reminder and data wipe. All rows rebuild on resume.
 */
public class SettingsActivity extends Activity {

    private LinearLayout body;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = Ui.column(this);
        root.addView(Ui.topBar(this, "Settings"), Ui.matchWrap());

        ScrollView sv = new ScrollView(this);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(Ui.dp(this, 12), Ui.dp(this, 4), Ui.dp(this, 12), Ui.dp(this, 16));
        sv.addView(body, Ui.matchWrap());
        root.addView(sv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    // ------------------------------------------------------------------ build

    private void refresh() {
        body.removeAllViews();

        // ------------------------------------------------------- Gemini API key
        body.addView(Ui.section(this, "Gemini API key"));
        body.addView(row("API key", Prefs.hasApiKey(this) ? "Set" : "Not set",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        onEnterKey();
                    }
                }));
        TextView enterKeyBtn = Ui.button(this, "Enter key", Ui.ACCENT);
        enterKeyBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onEnterKey();
            }
        });
        body.addView(withBottomMargin(enterKeyBtn));
        body.addView(note("Your key stays on this device only. Get one at Google AI Studio."));

        // ---------------------------------------------------------------- models
        body.addView(Ui.section(this, "Models"));
        body.addView(row("Agent model", Prefs.getAgentModel(this), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickModel(0);
            }
        }));
        body.addView(row("Quiz model", Prefs.getAskingModel(this), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickModel(1);
            }
        }));
        body.addView(row("Talk model", Prefs.getConversationModel(this), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickModel(2);
            }
        }));
        body.addView(row("Helper model", Prefs.getBackgroundModel(this), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickModel(3);
            }
        }));

        body.addView(Ui.section(this, "Saved models"));
        final List<String> saved = Prefs.getSavedModels(this);
        for (final String model : saved) {
            body.addView(modelRow(model, saved.size()));
        }
        TextView addModelBtn = Ui.button(this, "+ Add model", Ui.GREEN);
        addModelBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Ui.input(SettingsActivity.this, "Add model", "Model name, e.g. gemini-3.6-flash",
                        "", new Ui.InputCallback() {
                            @Override
                            public void onInput(String text) {
                                if (text == null || text.isEmpty()) return;
                                Prefs.addSavedModel(SettingsActivity.this, text);
                                refresh();
                            }
                        });
            }
        });
        body.addView(withBottomMargin(addModelBtn));

        // ----------------------------------------------------------------- voice
        body.addView(Ui.section(this, "Voice"));
        body.addView(row("TTS language", langLabel(Prefs.getTtsLang(this)), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickLanguage(true);
            }
        }));
        body.addView(row("STT language", langLabel(Prefs.getSttLang(this)), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickLanguage(false);
            }
        }));

        // ------------------------------------------------------------------ quiz
        body.addView(Ui.section(this, "Quiz"));
        body.addView(row("Answer time", String.valueOf(Prefs.getQuizTimeSec(this)),
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Ui.input(SettingsActivity.this, "Answer time (seconds)",
                                "5 - 300 seconds",
                                String.valueOf(Prefs.getQuizTimeSec(SettingsActivity.this)),
                                new Ui.InputCallback() {
                                    @Override
                                    public void onInput(String text) {
                                        try {
                                            int sec = Integer.parseInt(text.trim());
                                            if (sec < 5) sec = 5;
                                            if (sec > 300) sec = 300;
                                            Prefs.setQuizTimeSec(SettingsActivity.this, sec);
                                            refresh();
                                        } catch (NumberFormatException e) {
                                            Ui.toast(SettingsActivity.this,
                                                    "Please enter a number");
                                        }
                                    }
                                });
                    }
                }));

        // ------------------------------------------------------------------ talk
        body.addView(Ui.section(this, "Talk"));
        body.addView(row("Mic", onOff(Prefs.isConvMicOn(this)), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Prefs.setConvMicOn(SettingsActivity.this, !Prefs.isConvMicOn(SettingsActivity.this));
                refresh();
            }
        }));
        body.addView(row("Voice replies (TTS)", onOff(Prefs.isConvTtsOn(this)), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Prefs.setConvTtsOn(SettingsActivity.this, !Prefs.isConvTtsOn(SettingsActivity.this));
                refresh();
            }
        }));
        body.addView(row("Speech input (STT)", onOff(Prefs.isConvSttOn(this)), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Prefs.setConvSttOn(SettingsActivity.this, !Prefs.isConvSttOn(SettingsActivity.this));
                refresh();
            }
        }));

        // -------------------------------------------------------- study reminder
        body.addView(Ui.section(this, "Study reminder"));
        body.addView(row("Reminder", onOff(Prefs.isReminderOn(this)), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean on = !Prefs.isReminderOn(SettingsActivity.this);
                Prefs.setReminderOn(SettingsActivity.this, on);
                if (on) {
                    ReminderReceiver.schedule(SettingsActivity.this);
                } else {
                    ReminderReceiver.cancel(SettingsActivity.this);
                }
                refresh();
            }
        }));
        String timeLabel = String.format(Locale.US, "%02d:%02d",
                Prefs.getReminderHour(this), Prefs.getReminderMinute(this));
        body.addView(row("Reminder time", timeLabel, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new TimePickerDialog(SettingsActivity.this,
                        new TimePickerDialog.OnTimeSetListener() {
                            @Override
                            public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
                                Prefs.setReminderTime(SettingsActivity.this, hourOfDay, minute);
                                if (Prefs.isReminderOn(SettingsActivity.this)) {
                                    ReminderReceiver.schedule(SettingsActivity.this);
                                }
                                refresh();
                            }
                        },
                        Prefs.getReminderHour(SettingsActivity.this),
                        Prefs.getReminderMinute(SettingsActivity.this), true).show();
            }
        }));

        // ------------------------------------------------------------------ data
        body.addView(Ui.section(this, "Data"));
        TextView clearBtn = Ui.button(this, "Clear all data", Ui.RED);
        clearBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Ui.confirm(SettingsActivity.this, "Clear all data?",
                        "Deletes all chats, library, files and settings. This cannot be undone.",
                        new Ui.ConfirmCallback() {
                            @Override
                            public void onYes() {
                                ReminderReceiver.cancel(SettingsActivity.this);
                                deleteDatabase("golu_user.db");
                                deleteDatabase("golu_ai.db");
                                deleteDatabase("golu_app.db");
                                deleteRecursive(new File(getFilesDir(), "user_files"));
                                Prefs.clearAll(SettingsActivity.this);
                                Ui.toast(SettingsActivity.this, "All data cleared");
                                refresh();
                            }
                        });
            }
        });
        body.addView(withBottomMargin(clearBtn));

        // ----------------------------------------------------------------- about
        body.addView(Ui.section(this, "About"));
        body.addView(note("Golu Study 3.1 - your study buddy. Chats, library, files and memory "
                + "stay on your device; only your messages and attached files go to Gemini."));
    }

    // ----------------------------------------------------------------- helpers

    private void onEnterKey() {
        Ui.passwordInput(this, "Gemini API key", "Paste your Gemini API key",
                Prefs.getApiKey(this), new Ui.InputCallback() {
                    @Override
                    public void onInput(String text) {
                        Prefs.setApiKey(SettingsActivity.this, text);
                        Ui.toast(SettingsActivity.this,
                                text == null || text.isEmpty() ? "Key removed" : "Key saved");
                        refresh();
                    }
                });
    }

    /** which: 0 agent, 1 asking, 2 conversation, 3 background */
    private void pickModel(final int which) {
        final List<String> models = Prefs.getSavedModels(this);
        String[] items = models.toArray(new String[models.size()]);
        String current;
        if (which == 1) current = Prefs.getAskingModel(this);
        else if (which == 2) current = Prefs.getConversationModel(this);
        else if (which == 3) current = Prefs.getBackgroundModel(this);
        else current = Prefs.getAgentModel(this);
        int checked = models.indexOf(current);
        if (checked < 0) checked = 0;
        Ui.singleChoice(this, "Pick a model", items, checked, new Ui.ChoiceCallback() {
            @Override
            public void onChoice(int index) {
                String model = models.get(index);
                if (which == 1) Prefs.setAskingModel(SettingsActivity.this, model);
                else if (which == 2) Prefs.setConversationModel(SettingsActivity.this, model);
                else if (which == 3) Prefs.setBackgroundModel(SettingsActivity.this, model);
                else Prefs.setAgentModel(SettingsActivity.this, model);
                refresh();
            }
        });
    }

    private void pickLanguage(final boolean tts) {
        String current = tts ? Prefs.getTtsLang(this) : Prefs.getSttLang(this);
        Ui.singleChoice(this, tts ? "TTS language" : "STT language",
                new String[]{"Hindi", "English"}, "en".equals(current) ? 1 : 0,
                new Ui.ChoiceCallback() {
                    @Override
                    public void onChoice(int index) {
                        String lang = index == 1 ? "en" : "hi";
                        if (tts) Prefs.setTtsLang(SettingsActivity.this, lang);
                        else Prefs.setSttLang(SettingsActivity.this, lang);
                        refresh();
                    }
                });
    }

    private static String langLabel(String lang) {
        return "en".equals(lang) ? "English" : "Hindi";
    }

    private static String onOff(boolean on) {
        return on ? "ON" : "OFF";
    }

    /** Tappable card row: label on the left, value on the right. */
    private LinearLayout row(String label, String value, View.OnClickListener click) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setBackground(Ui.pill(this, Ui.CARD, Ui.LINE, 12));
        r.setPadding(Ui.dp(this, 12), Ui.dp(this, 13), Ui.dp(this, 12), Ui.dp(this, 13));
        LinearLayout.LayoutParams lp = Ui.matchWrap();
        lp.bottomMargin = Ui.dp(this, 8);
        r.setLayoutParams(lp);

        TextView l = new TextView(this);
        l.setText(label);
        l.setTextSize(15f);
        l.setTextColor(Ui.INK);
        r.addView(l, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(14f);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setTextColor(Ui.MUTED);
        v.setGravity(Gravity.RIGHT);
        r.addView(v, Ui.wrapWrap());

        if (click != null) r.setOnClickListener(click);
        return r;
    }

    /** Saved-model row with a red remove "x" on the right. */
    private LinearLayout modelRow(final String model, final int totalCount) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setBackground(Ui.pill(this, Ui.CARD, Ui.LINE, 12));
        r.setPadding(Ui.dp(this, 12), Ui.dp(this, 11), Ui.dp(this, 12), Ui.dp(this, 11));
        LinearLayout.LayoutParams lp = Ui.matchWrap();
        lp.bottomMargin = Ui.dp(this, 8);
        r.setLayoutParams(lp);

        TextView l = new TextView(this);
        l.setText(model);
        l.setTextSize(14f);
        l.setTextColor(Ui.INK);
        r.addView(l, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView x = new TextView(this);
        x.setText("✕");
        x.setTextSize(16f);
        x.setTypeface(Typeface.DEFAULT_BOLD);
        x.setTextColor(Ui.RED);
        x.setPadding(Ui.dp(this, 10), Ui.dp(this, 2), Ui.dp(this, 4), Ui.dp(this, 2));
        x.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (totalCount <= 1) {
                    Ui.toast(SettingsActivity.this, "Keep at least one saved model");
                    return;
                }
                Ui.confirm(SettingsActivity.this, "Remove model?",
                        "Remove \"" + model + "\" from the saved models?",
                        new Ui.ConfirmCallback() {
                            @Override
                            public void onYes() {
                                Prefs.removeSavedModel(SettingsActivity.this, model);
                                refresh();
                            }
                        });
            }
        });
        r.addView(x, Ui.wrapWrap());
        return r;
    }

    private TextView note(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(12f);
        tv.setTextColor(Ui.MUTED);
        tv.setPadding(Ui.dp(this, 4), Ui.dp(this, 2), Ui.dp(this, 4), Ui.dp(this, 4));
        return tv;
    }

    private static View withBottomMargin(View v) {
        LinearLayout.LayoutParams lp = Ui.matchWrap();
        lp.bottomMargin = Ui.dp(v.getContext(), 8);
        v.setLayoutParams(lp);
        return v;
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
}
