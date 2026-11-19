package com.spdev.mypetgolu;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Home screen of Golu Study Buddy: Golu the companion on top, fully
 * visible above the bottom panel, and a bottom panel with the study
 * screens. Tapping Buddy swaps the navigation buttons in place for
 * Golu's look (decoration only: boy/girl, colours, clothes, glasses) -
 * no separate screen to open and close again and again.
 * The Play actions (Sleep, Laugh, ...) were removed in v0.4.0: Golu is
 * a calm study buddy now, not a toy.
 */
public class MainActivity extends Activity implements PetView.Listener {

    private PetView pet;
    private TextView status;

    private LinearLayout navPanel;
    private ScrollView buddyScroll;
    private LinearLayout buddyContent;
    private boolean buddyOpen = false;

    // ------------------------------------------------------------- lifecycle

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        // seed the built-in skills once, off the UI thread (the v0.6.0 flag
        // re-runs seeding once on existing installs for the new skill)
        if (!Prefs.isSkillsSeeded(this) || !Prefs.isSkillsSeededV60(this)) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    Skills.seed(new AppDb(MainActivity.this));
                    Prefs.setSkillsSeeded(MainActivity.this);
                    Prefs.setSkillsSeededV60(MainActivity.this);
                }
            }).start();
        }

        setContentView(buildUi());

        pet.setListener(this);
        pet.setLook(Prefs.getPalette(this), Prefs.getGender(this),
                Prefs.getOutfit(this), Prefs.getAccessory(this), Prefs.getClothesColor(this));
        status.setText("Golu is happy");
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (pet != null) {
            pet.setLook(Prefs.getPalette(this), Prefs.getGender(this),
                    Prefs.getOutfit(this), Prefs.getAccessory(this), Prefs.getClothesColor(this));
        }
    }

    @Override
    public void onBackPressed() {
        if (buddyOpen) {
            showNav();
        } else {
            super.onBackPressed();
        }
    }

    // -------------------------------------------------------------------- UI

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private View buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Ui.BG);

        // Column: the pet gets all the space ABOVE the bottom panel, so he
        // is never hidden under the menu or buttons (he used to be drawn
        // full-screen with the panel overlapping his lower body).
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);

        pet = new PetView(this);
        column.addView(pet, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        column.addView(buildBottomArea(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(column, new FrameLayout.LayoutParams(
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

        return root;
    }

    /** Bottom area holding the nav grid and, hidden, the Buddy panel. */
    private LinearLayout buildBottomArea() {
        LinearLayout area = new LinearLayout(this);
        area.setOrientation(LinearLayout.VERTICAL);
        area.setPadding(dp(10), dp(10), dp(10), dp(12));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadii(new float[]{dp(22), dp(22), dp(22), dp(22), 0, 0, 0, 0});
        bg.setColor(Ui.PANEL);
        area.setBackground(bg);

        navPanel = buildNavPanel();
        area.addView(navPanel);

        buddyScroll = new ScrollView(this);
        buddyScroll.setVerticalScrollBarEnabled(true);
        buddyContent = new LinearLayout(this);
        buddyContent.setOrientation(LinearLayout.VERTICAL);
        buddyContent.setPadding(dp(4), 0, dp(4), dp(8));
        buddyScroll.addView(buddyContent, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        int buddyHeight = (int) (getResources().getDisplayMetrics().heightPixels * 0.55);
        buddyScroll.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, buddyHeight));
        buddyScroll.setVisibility(View.GONE);
        area.addView(buddyScroll);
        return area;
    }

    private LinearLayout buildNavPanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.addView(navButton("Chats", new Runnable() {
            @Override
            public void run() {
                startActivity(new Intent(MainActivity.this, ChatListActivity.class));
            }
        }));
        row1.addView(navButton("Talk", new Runnable() {
            @Override
            public void run() {
                startActivity(new Intent(MainActivity.this, ConversationActivity.class));
            }
        }));
        row1.addView(navButton("Practice", new Runnable() {
            @Override
            public void run() {
                startActivity(new Intent(MainActivity.this, AskingActivity.class));
            }
        }));
        panel.addView(row1);

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.addView(navButton("Library", new Runnable() {
            @Override
            public void run() {
                startActivity(new Intent(MainActivity.this, LibraryActivity.class));
            }
        }));
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
        panel.addView(row2);

        LinearLayout row3 = new LinearLayout(this);
        row3.setOrientation(LinearLayout.HORIZONTAL);
        row3.addView(navButton("Memory", new Runnable() {
            @Override
            public void run() {
                startActivity(new Intent(MainActivity.this, MemoryActivity.class));
            }
        }));
        row3.addView(navButton("Buddy", new Runnable() {
            @Override
            public void run() {
                showBuddy();
            }
        }));
        row3.addView(navButton("Settings", new Runnable() {
            @Override
            public void run() {
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
            }
        }));
        panel.addView(row3);
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

    // ------------------------------------------------------------ buddy panel

    /** Swaps the nav grid for the Buddy panel, in place. */
    private void showBuddy() {
        fillBuddy();
        navPanel.setVisibility(View.GONE);
        buddyScroll.setVisibility(View.VISIBLE);
        buddyOpen = true;
    }

    /** Swaps the Buddy panel back for the nav grid. */
    private void showNav() {
        buddyScroll.setVisibility(View.GONE);
        navPanel.setVisibility(View.VISIBLE);
        buddyOpen = false;
    }

    /** Decoration only: boy/girl, colours, clothes, accessories. */
    private void fillBuddy() {
        buddyContent.removeAllViews();

        // header
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(this);
        title.setText("Buddy");
        title.setTextSize(18f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(0xFF5A3E2B);
        header.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView back = Ui.button(this, "Back", Ui.ACCENT);
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showNav();
            }
        });
        header.addView(back, Ui.wrapWrap());
        buddyContent.addView(header);

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
                fillBuddy();
            }
        }));
        genderRow.addView(chip("Girl", pet.getGender() == 1, new Runnable() {
            @Override
            public void run() {
                pet.setLook(pet.getPalette(), 1, Math.min(pet.getOutfit(), PetView.OUTFIT_GIRL.length - 1),
                        pet.getAccessory());
                saveLook();
                fillBuddy();
            }
        }));
        buddyContent.addView(genderRow);

        // colours
        buddyContent.addView(smallLabel("Colour"));
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
                    fillBuddy();
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(30), dp(30));
            lp.rightMargin = dp(6);
            lp.bottomMargin = dp(4);
            swatchRow.addView(dot, lp);
        }
        buddyContent.addView(swatchRow);

        // outfits
        buddyContent.addView(smallLabel("Clothes"));
        LinearLayout outfitRow = null;
        for (int i = 0; i < pet.outfitCount(); i++) {
            if (i % 3 == 0) {
                outfitRow = new LinearLayout(this);
                outfitRow.setOrientation(LinearLayout.HORIZONTAL);
                buddyContent.addView(outfitRow);
            }
            final int idx = i;
            outfitRow.addView(chip(pet.outfitName(i), pet.getOutfit() == i, new Runnable() {
                @Override
                public void run() {
                    pet.setLook(pet.getPalette(), pet.getGender(), idx, pet.getAccessory());
                    saveLook();
                    fillBuddy();
                }
            }));
        }

        // clothes colour (like the body colour row above)
        buddyContent.addView(smallLabel("Clothes colour"));
        LinearLayout clothesRow = new LinearLayout(this);
        clothesRow.setOrientation(LinearLayout.HORIZONTAL);
        TextView defaultClothes = chip("Default", pet.getClothesColor() < 0, new Runnable() {
            @Override
            public void run() {
                pet.setLook(pet.getPalette(), pet.getGender(), pet.getOutfit(),
                        pet.getAccessory(), -1);
                saveLook();
                fillBuddy();
            }
        });
        // chip() sets weight-1 params for multi-chip rows; alone here it
        // should wrap its text instead of stretching full width
        defaultClothes.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        clothesRow.addView(defaultClothes);
        buddyContent.addView(clothesRow);
        LinearLayout clothesSwatchRow = new LinearLayout(this);
        clothesSwatchRow.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < PetView.CLOTHES_COLORS.length; i++) {
            final int idx = i;
            View dot = new View(this);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(PetView.CLOTHES_COLORS[i]);
            g.setStroke(dp(pet.getClothesColor() == i ? 3 : 1.5f),
                    pet.getClothesColor() == i ? 0xFF3E2723 : 0xFFB0A090);
            dot.setBackground(g);
            dot.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    pet.setLook(pet.getPalette(), pet.getGender(), pet.getOutfit(),
                            pet.getAccessory(), idx);
                    saveLook();
                    fillBuddy();
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(30), dp(30));
            lp.rightMargin = dp(6);
            lp.bottomMargin = dp(4);
            clothesSwatchRow.addView(dot, lp);
        }
        buddyContent.addView(clothesSwatchRow);

        // accessories
        buddyContent.addView(smallLabel("Accessory"));
        LinearLayout accRow = null;
        for (int i = 0; i < PetView.ACCESSORY_NAME.length; i++) {
            if (i % 3 == 0) {
                accRow = new LinearLayout(this);
                accRow.setOrientation(LinearLayout.HORIZONTAL);
                buddyContent.addView(accRow);
            }
            final int idx = i;
            accRow.addView(chip(PetView.ACCESSORY_NAME[i], pet.getAccessory() == i, new Runnable() {
                @Override
                public void run() {
                    pet.setLook(pet.getPalette(), pet.getGender(), pet.getOutfit(), idx);
                    saveLook();
                    fillBuddy();
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
        buddyContent.addView(tv);
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

    private void saveLook() {
        Prefs.saveLook(this, pet.getPalette(), pet.getGender(),
                pet.getOutfit(), pet.getAccessory(), pet.getClothesColor());
    }

    // ------------------------------------------------------- pet interaction

    // The Play actions were removed in v0.4.0: long-presses and swipes on
    // Golu no longer trigger actions. He stays a calm companion (idle
    // breathing, blinking, looking around) and speaks with lip-sync in
    // Talk and Practice. A tap just makes him smile for a moment - a small
    // hello, not a toy action. The listener methods stay because PetView
    // still calls them.

    @Override
    public void onPetTapped() {
        pet.setActivity(Acts.SMILE);
    }

    @Override
    public void onPetLongPressed() {
        // no action
    }

    @Override
    public void onSwipeUp() {
        // no action
    }

    @Override
    public void onSwipeDown() {
        // no action
    }

    @Override
    public void onActivityChanged(int act) {
        status.setText("Golu is happy");
    }
}
