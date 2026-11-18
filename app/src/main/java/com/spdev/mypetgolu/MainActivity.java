package com.spdev.mypetgolu;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayDeque;

/**
 * Home screen of Golu Study: Golu the pet companion on top, a bottom
 * navigation panel for the study screens, and a Play sheet (book button)
 * with Golu's look and a trimmed set of fun actions.
 */
public class MainActivity extends Activity implements PetView.Listener {

    /** Trimmed set of fun actions shown in the Play sheet. */
    private static final int[] PLAY_ACTIONS = {
            Acts.SMILE, Acts.LAUGH, Acts.DANCE, Acts.SING, Acts.JUMP,
            Acts.TICKLE, Acts.SLEEP, Acts.WAKEUP, Acts.NAMASTE, Acts.LOVE,
            Acts.CRY, Acts.ANGRY, Acts.SHY, Acts.SURPRISED, Acts.BALL,
            Acts.KITE, Acts.TEDDY, Acts.BOOK, Acts.GUITAR, Acts.YOGA
    };

    private PetView pet;
    private TextView status;
    private TextView soundButton;

    private FrameLayout menuOverlay;
    private LinearLayout menuPanel;

    private GoluVoice voice;
    private boolean ttsOn = true;

    private final ArrayDeque<Long> pokes = new ArrayDeque<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable sleepRunnable;

    // ------------------------------------------------------------- lifecycle

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        Skills.seed(new AppDb(this));

        setContentView(buildUi());

        pet.setListener(this);
        pet.setLook(Prefs.getPalette(this), Prefs.getGender(this),
                Prefs.getOutfit(this), Prefs.getAccessory(this));

        voice = new GoluVoice(this);
        voice.initTts(Prefs.getTtsLocale(this), null);
        voice.setTtsListener(new GoluVoice.TtsListener() {
            @Override
            public void onStart(String id) {
                pet.setSpeaking(true);
            }

            @Override
            public void onDone(String id) {
                pet.setSpeaking(false);
            }

            @Override
            public void onError(String id) {
                pet.setSpeaking(false);
            }
        });

        scheduleAutoSleep();
        pet.setActivity(Acts.NAMASTE);
        status.setText("Golu");
    }

    @Override
    protected void onResume() {
        super.onResume();
        // the TTS language may have changed in Settings
        if (voice != null) voice.setTtsLanguage(Prefs.getTtsLocale(this));
        if (pet != null) {
            pet.setLook(Prefs.getPalette(this), Prefs.getGender(this),
                    Prefs.getOutfit(this), Prefs.getAccessory(this));
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (voice != null) voice.shutdownAll();
        super.onDestroy();
    }

    // -------------------------------------------------------------------- UI

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private View buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Ui.BG);

        pet = new PetView(this);
        root.addView(pet, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // status pill
        status = new TextView(this);
        status.setText("Golu");
        status.setTextSize(14f);
        status.setTextColor(0xFF5A3E2B);
        status.setTypeface(Typeface.DEFAULT_BOLD);
        status.setGravity(Gravity.CENTER);
        status.setPadding(dp(16), dp(8), dp(16), dp(8));
        status.setBackground(Ui.pill(this, 0xF2FFFFFF, 0xFFE0A800, 20));
        FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        slp.topMargin = dp(14);
        root.addView(status, slp);

        // sound toggle (not persisted - just for this session)
        soundButton = circleButton("\u266B", 38);
        soundButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ttsOn = !ttsOn;
                soundButton.setText(ttsOn ? "\u266B" : "x");
                soundButton.setAlpha(ttsOn ? 1f : 0.55f);
                if (!ttsOn && voice != null) voice.stopTts();
            }
        });
        FrameLayout.LayoutParams soundLp = new FrameLayout.LayoutParams(dp(38), dp(38),
                Gravity.TOP | Gravity.END);
        soundLp.topMargin = dp(10);
        soundLp.rightMargin = dp(10);
        root.addView(soundButton, soundLp);

        // bottom navigation panel
        root.addView(buildNavPanel(), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM));

        buildMenu(root);
        return root;
    }

    private LinearLayout buildNavPanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(10), dp(10), dp(10), dp(12));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadii(new float[]{dp(22), dp(22), dp(22), dp(22), 0, 0, 0, 0});
        bg.setColor(Ui.PANEL);
        panel.setBackground(bg);

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.addView(navButton("Chat", new Runnable() {
            @Override
            public void run() {
                startActivity(new Intent(MainActivity.this, ChatListActivity.class));
            }
        }));
        row1.addView(navButton("Conversation", new Runnable() {
            @Override
            public void run() {
                startActivity(new Intent(MainActivity.this, ConversationActivity.class));
            }
        }));
        row1.addView(navButton("Asking", new Runnable() {
            @Override
            public void run() {
                startActivity(new Intent(MainActivity.this, AskingActivity.class));
            }
        }));
        row1.addView(navButton("Library", new Runnable() {
            @Override
            public void run() {
                startActivity(new Intent(MainActivity.this, LibraryActivity.class));
            }
        }));
        panel.addView(row1);

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.addView(navButton("Files", new Runnable() {
            @Override
            public void run() {
                startActivity(new Intent(MainActivity.this, FilesActivity.class));
            }
        }));
        row2.addView(navButton("Skills", new Runnable() {
            @Override
            public void run() {
                startActivity(new Intent(MainActivity.this, SkillsActivity.class));
            }
        }));
        row2.addView(navButton("Settings", new Runnable() {
            @Override
            public void run() {
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
            }
        }));
        row2.addView(navButton("\uD83D\uDCD4 Play", new Runnable() {
            @Override
            public void run() {
                showMenu();
            }
        }));
        panel.addView(row2);
        return panel;
    }

    private TextView navButton(String label, final Runnable action) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(12f);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setTextColor(0xFF6B4A22);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dp(4), dp(11), dp(4), dp(11));
        tv.setBackground(Ui.pill(this, Ui.CHIP_BG, Ui.LINE, 14));
        tv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                action.run();
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.rightMargin = dp(5);
        lp.leftMargin = dp(5);
        lp.topMargin = dp(4);
        lp.bottomMargin = dp(4);
        tv.setLayoutParams(lp);
        return tv;
    }

    private TextView circleButton(String label, int sizeDp) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(13f);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setTextColor(0xFF7A4B00);
        tv.setGravity(Gravity.CENTER);
        tv.setBackground(Ui.pill(this, 0xF2FFFFFF, 0xFFE0A800, sizeDp / 2));
        return tv;
    }

    // -------------------------------------------------------------- play sheet

    private void buildMenu(FrameLayout root) {
        menuOverlay = new FrameLayout(this);
        menuOverlay.setBackgroundColor(0xAA2B1B00);
        menuOverlay.setVisibility(View.GONE);
        menuOverlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideMenu();
            }
        });

        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(true);
        menuPanel = new LinearLayout(this);
        menuPanel.setOrientation(LinearLayout.VERTICAL);
        menuPanel.setPadding(dp(14), dp(10), dp(14), dp(20));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadii(new float[]{dp(22), dp(22), dp(22), dp(22), 0, 0, 0, 0});
        bg.setColor(Ui.PANEL);
        menuPanel.setBackground(bg);
        menuPanel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // swallow taps so the sheet does not close
            }
        });
        sv.addView(menuPanel, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout.LayoutParams svLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.BOTTOM);
        svLp.topMargin = dp(70);
        menuOverlay.addView(sv, svLp);
        root.addView(menuOverlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        fillMenu();
    }

    private void fillMenu() {
        menuPanel.removeAllViews();

        // header
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(this);
        title.setText("Golu's Playbook");
        title.setTextSize(18f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(0xFF5A3E2B);
        header.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView close = circleButton("X", 34);
        close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideMenu();
            }
        });
        header.addView(close, new LinearLayout.LayoutParams(dp(34), dp(34)));
        menuPanel.addView(header);

        // -------- look section
        sectionHeader("Look");
        LinearLayout genderRow = new LinearLayout(this);
        genderRow.setOrientation(LinearLayout.HORIZONTAL);
        genderRow.addView(chip("Boy", pet.getGender() == 0, new Runnable() {
            @Override
            public void run() {
                pet.setLook(pet.getPalette(), 0, Math.min(pet.getOutfit(), PetView.OUTFIT_BOY.length - 1),
                        pet.getAccessory());
                saveLook();
                fillMenu();
            }
        }));
        genderRow.addView(chip("Girl", pet.getGender() == 1, new Runnable() {
            @Override
            public void run() {
                pet.setLook(pet.getPalette(), 1, Math.min(pet.getOutfit(), PetView.OUTFIT_GIRL.length - 1),
                        pet.getAccessory());
                saveLook();
                fillMenu();
            }
        }));
        menuPanel.addView(genderRow);

        // colours
        menuPanel.addView(smallLabel("Colour"));
        LinearLayout swatchRow = new LinearLayout(this);
        swatchRow.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < PetView.PALETTE.length; i++) {
            final int idx = i;
            View dot = new View(this);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(PetView.PALETTE[i][0]);
            g.setStroke(dp(pet.getPalette() == i ? 3 : 1.5f),
                    pet.getPalette() == i ? 0xFF3E2723 : 0xFFB0A090);
            dot.setBackground(g);
            dot.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    pet.setLook(idx, pet.getGender(), pet.getOutfit(), pet.getAccessory());
                    saveLook();
                    fillMenu();
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(30), dp(30));
            lp.rightMargin = dp(6);
            lp.bottomMargin = dp(4);
            swatchRow.addView(dot, lp);
        }
        menuPanel.addView(swatchRow);

        // outfits
        menuPanel.addView(smallLabel("Clothes"));
        LinearLayout outfitRow = null;
        for (int i = 0; i < pet.outfitCount(); i++) {
            if (i % 3 == 0) {
                outfitRow = new LinearLayout(this);
                outfitRow.setOrientation(LinearLayout.HORIZONTAL);
                menuPanel.addView(outfitRow);
            }
            final int idx = i;
            outfitRow.addView(chip(pet.outfitName(i), pet.getOutfit() == i, new Runnable() {
                @Override
                public void run() {
                    pet.setLook(pet.getPalette(), pet.getGender(), idx, pet.getAccessory());
                    saveLook();
                    fillMenu();
                }
            }));
        }

        // accessories
        menuPanel.addView(smallLabel("Accessory"));
        LinearLayout accRow = null;
        for (int i = 0; i < PetView.ACCESSORY_NAME.length; i++) {
            if (i % 3 == 0) {
                accRow = new LinearLayout(this);
                accRow.setOrientation(LinearLayout.HORIZONTAL);
                menuPanel.addView(accRow);
            }
            final int idx = i;
            accRow.addView(chip(PetView.ACCESSORY_NAME[i], pet.getAccessory() == i, new Runnable() {
                @Override
                public void run() {
                    pet.setLook(pet.getPalette(), pet.getGender(), pet.getOutfit(), idx);
                    saveLook();
                    fillMenu();
                }
            }));
        }

        // -------- trimmed fun actions
        sectionHeader("Play with Golu");
        LinearLayout row = null;
        for (int i = 0; i < PLAY_ACTIONS.length; i++) {
            if (i % 3 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                menuPanel.addView(row);
            }
            final int a = PLAY_ACTIONS[i];
            row.addView(chip(Acts.LABEL[a], pet.getActivity() == a, new Runnable() {
                @Override
                public void run() {
                    hideMenu();
                    doActivity(a);
                }
            }));
        }
    }

    private void sectionHeader(String name) {
        TextView tv = new TextView(this);
        tv.setText(name);
        tv.setTextSize(15f);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setTextColor(0xFFB26A00);
        tv.setPadding(0, dp(12), 0, dp(4));
        menuPanel.addView(tv);
    }

    private TextView smallLabel(String s) {
        TextView tv = new TextView(this);
        tv.setText(s);
        tv.setTextSize(11f);
        tv.setTextColor(0xFF8A7360);
        tv.setPadding(0, dp(6), 0, dp(2));
        return tv;
    }

    private TextView chip(String label, boolean selected, final Runnable action) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(12f);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setTextColor(selected ? Color.WHITE : 0xFF6B4A22);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dp(6), dp(9), dp(6), dp(9));
        tv.setBackground(Ui.pill(this, selected ? Ui.ACCENT : Ui.CHIP_BG,
                selected ? 0 : Ui.LINE, 14));
        tv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                action.run();
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.rightMargin = dp(6);
        lp.bottomMargin = dp(6);
        tv.setLayoutParams(lp);
        return tv;
    }

    private void showMenu() {
        fillMenu();
        menuOverlay.setVisibility(View.VISIBLE);
        menuOverlay.setAlpha(0f);
        menuOverlay.animate().alpha(1f).setDuration(160).start();
    }

    private void hideMenu() {
        menuOverlay.animate().alpha(0f).setDuration(140).withEndAction(new Runnable() {
            @Override
            public void run() {
                menuOverlay.setVisibility(View.GONE);
            }
        }).start();
    }

    private void saveLook() {
        Prefs.saveLook(this, pet.getPalette(), pet.getGender(),
                pet.getOutfit(), pet.getAccessory());
    }

    // --------------------------------------------------------------- actions

    private void doActivity(int a) {
        if (a == Acts.SLEEP) {
            if (sleepRunnable != null) handler.removeCallbacks(sleepRunnable);
            pet.setActivity(Acts.SLEEP);
            speak(Acts.SLEEP);
            return;
        }
        if (pet.isSleeping() && a != Acts.WAKEUP) {
            pet.setActivity(Acts.WAKEUP);
            speak(Acts.WAKEUP);
            scheduleAutoSleep();
            return;
        }
        pet.setActivity(a);
        speak(a);
        scheduleAutoSleep();
    }

    private void speak(int a) {
        if (voice == null || !voice.isTtsReady() || !ttsOn) return;
        if (a < 0 || a >= Acts.SAY_EN.length) return;
        String line = "hi".equals(Prefs.getTtsLang(this)) ? Acts.SAY_HI[a] : Acts.SAY_EN[a];
        voice.speak(line, "golu");
    }

    @Override
    public void onActivityChanged(int act) {
        if (act == Acts.IDLE) status.setText("Golu is happy");
        else status.setText("Golu \u00B7 " + Acts.LABEL[act]);
    }

    // ------------------------------------------------------------- gestures

    @Override
    public void onPetTapped() {
        long now = System.currentTimeMillis();
        pokes.addLast(now);
        while (!pokes.isEmpty() && now - pokes.peekFirst() > 4500) pokes.pollFirst();

        if (pet.isSleeping()) {
            pet.setActivity(Acts.ANGRY);
            speak(Acts.ANGRY);
            scheduleAutoSleep();
            return;
        }

        int n = pokes.size();
        int a = n <= 1 ? Acts.SMILE : (n == 2 ? Acts.LAUGH : Acts.ANGRY);
        pet.setActivity(a);
        speak(a);
        scheduleAutoSleep();
    }

    @Override
    public void onPetLongPressed() {
        pet.setActivity(Acts.TICKLE);
        speak(Acts.TICKLE);
        scheduleAutoSleep();
    }

    @Override
    public void onSwipeUp() {
        pet.setActivity(Acts.JUMP);
        speak(Acts.JUMP);
        scheduleAutoSleep();
    }

    @Override
    public void onSwipeDown() {
        doActivity(Acts.SLEEP);
    }

    private void scheduleAutoSleep() {
        if (sleepRunnable == null) {
            sleepRunnable = new Runnable() {
                @Override
                public void run() {
                    if (!pet.isSleeping()) pet.setActivity(Acts.SLEEP);
                }
            };
        }
        handler.removeCallbacks(sleepRunnable);
        handler.postDelayed(sleepRunnable, 120000);
    }
}
