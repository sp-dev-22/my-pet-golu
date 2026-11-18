package com.spdev.mypetgolu;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Offline practice mode (was Asking/Quiz). Golu practises with the user
 * using items already saved in the Library (made in agent chats) -
 * nothing is created with AI here, and no API calls happen during
 * practice. Four kinds, each with its own flow:
 * - quiz: Golu reads the question + options aloud, then listens for the
 *   answer by voice or tap until the countdown runs out.
 * - flashcards: Golu reads the front; the user recalls, then reveals the
 *   back (tap, voice or timeout) and marks Knew/Missed.
 * - one-liners: Golu reads each line; the user marks Knew/Missed.
 * - questions: Golu asks the question; the user answers aloud (or taps
 *   to reveal), then sees the answer and marks Knew/Missed.
 * TTS and STT are always on here. The mic has two modes: AUTO (listens
 * on its own when a question is asked) and TAP (the user taps the mic
 * button to speak the answer).
 */
public class AskingActivity extends Activity {

    private static final int REQ_AUDIO = 101;

    private AppDb db;
    private AiDb aiDb;
    private AppDb.Chat chat;
    private GoluVoice voice;

    private PetView pet;
    private TextView status;
    private ListView list;
    private MsgAdapter adapter;
    private List<AppDb.Msg> messages = new ArrayList<>();

    private TextView cardView;
    private LinearLayout revealRow;
    private TextView revealButton;
    private LinearLayout optionsArea;
    private TextView[] optionButtons = new TextView[4];
    private LinearLayout markRow;
    private LinearLayout finishRow;
    private TextView nextButton;
    private TextView micIndicator;
    private TextView micModeChip;
    private TextView micButton;

    // practice state
    private PracticeUtil.PracticeSet set;
    private String setTitle = "Practice";
    private int index = 0;
    private int score = 0;
    private boolean waiting = false;
    private boolean answered = false;
    private boolean finished = false;
    private boolean revealed = false;
    private boolean destroyed = false;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private int secondsLeft = 0;
    private long sessionId = -1;
    // per-item TTS id: a stale onDone/onError from a previous item
    // (e.g. after switching sets mid-speech) must not start listening
    private int questionSeq = 0;
    private String currentQid = "";

    private final Runnable tickRunnable = new Runnable() {
        @Override
        public void run() {
            if (destroyed || !waiting || answered) return;
            secondsLeft--;
            if (secondsLeft <= 0) {
                onTickTimeout();
            } else {
                updateStatus();
                handler.postDelayed(tickRunnable, 1000);
            }
        }
    };

    // ------------------------------------------------------------- lifecycle

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = new AppDb(this);
        aiDb = new AiDb(this);
        chat = db.getOrCreateSingleChat(AppDb.CHAT_ASKING, "Practice",
                Prefs.getAskingModel(this));
        if ("Quiz".equals(chat.title)) {
            // renamed in v0.4.0 (Asking/Quiz -> Practice)
            db.updateChatTitle(chat.id, "Practice");
            chat.title = "Practice";
        }
        setContentView(buildUi());

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
                if (currentQid.equals(id) && !destroyed && !answered && !finished) {
                    onAskSpeechDone();
                }
            }

            @Override
            public void onError(String id) {
                pet.setSpeaking(false);
                if (currentQid.equals(id) && !destroyed && !answered && !finished) {
                    onAskSpeechDone();
                }
            }
        });

        refreshTranscript();

        long itemId = getIntent().getLongExtra("library_item_id", -1);
        if (itemId < 0) itemId = getIntent().getLongExtra("library_quiz_id", -1);
        long sessionExtra = getIntent().getLongExtra("session_id", -1);
        if (itemId >= 0) {
            AiDb.Item item = aiDb.getItem(itemId);
            PracticeUtil.PracticeSet s = PracticeUtil.fromLibraryItem(item);
            if (s == null) {
                Ui.toast(this, "Nothing to practise in that item - pick another one");
                showPicker();
            } else {
                beginPractice(s, item.title);
            }
        } else if (sessionExtra >= 0) {
            AppDb.AskingSession s = db.getAskingSession(sessionExtra);
            if (s == null) {
                showPicker();
            } else {
                resumeSession(s);
            }
        } else {
            showPicker();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveProgress();
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        handler.removeCallbacksAndMessages(null);
        if (voice != null) voice.shutdownAll();
        super.onDestroy();
    }

    // -------------------------------------------------------------------- UI

    private View buildUi() {
        LinearLayout root = Ui.column(this);
        root.addView(Ui.topBar(this, "Practice"));

        // compact header: the pet fills the short header instead of
        // leaving a large empty sky area below him
        pet = new PetView(this);
        pet.setCompact(true);
        root.addView(pet, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 140)));

        status = new TextView(this);
        status.setText("Pick something to practise");
        status.setTextSize(13f);
        status.setTextColor(Ui.MUTED);
        status.setGravity(Gravity.CENTER);
        status.setPadding(Ui.dp(this, 12), Ui.dp(this, 6), Ui.dp(this, 12), Ui.dp(this, 6));
        root.addView(status, Ui.matchWrap());

        adapter = new MsgAdapter();
        list = new ListView(this);
        list.setAdapter(adapter);
        list.setDivider(null);
        list.setDividerHeight(0);
        list.setPadding(Ui.dp(this, 8), Ui.dp(this, 4), Ui.dp(this, 8), Ui.dp(this, 4));
        list.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                if (position < 0 || position >= messages.size()) return true;
                showMessageMenu(messages.get(position));
                return true;
            }
        });
        root.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // card view: the flashcard front / one-liner / open question
        cardView = new TextView(this);
        cardView.setTextSize(16f);
        cardView.setTextColor(Ui.INK);
        cardView.setGravity(Gravity.CENTER);
        cardView.setMinHeight(Ui.dp(this, 110));
        cardView.setPadding(Ui.dp(this, 16), Ui.dp(this, 14), Ui.dp(this, 16), Ui.dp(this, 14));
        cardView.setBackground(Ui.pill(this, Ui.CARD, Ui.LINE, 14));
        cardView.setVisibility(View.GONE);
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.leftMargin = Ui.dp(this, 8);
        cardLp.rightMargin = Ui.dp(this, 8);
        cardLp.topMargin = Ui.dp(this, 4);
        cardLp.bottomMargin = Ui.dp(this, 4);
        root.addView(cardView, cardLp);

        // reveal row: "Show answer" for flashcards and open questions
        revealRow = new LinearLayout(this);
        revealRow.setOrientation(LinearLayout.HORIZONTAL);
        revealRow.setGravity(Gravity.CENTER);
        revealRow.setPadding(Ui.dp(this, 8), Ui.dp(this, 4), Ui.dp(this, 8), Ui.dp(this, 4));
        revealRow.setVisibility(View.GONE);
        revealButton = Ui.button(this, "Show answer", Ui.ACCENT);
        revealButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (waiting && !answered) revealItem();
            }
        });
        revealRow.addView(revealButton, buttonLp());
        root.addView(revealRow, Ui.matchWrap());

        // options area: 4 buttons in a 2x2 grid (quiz only)
        optionsArea = new LinearLayout(this);
        optionsArea.setOrientation(LinearLayout.VERTICAL);
        optionsArea.setPadding(Ui.dp(this, 8), Ui.dp(this, 4), Ui.dp(this, 8), Ui.dp(this, 4));
        optionsArea.setVisibility(View.GONE);
        for (int r = 0; r < 2; r++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int c = 0; c < 2; c++) {
                final int idx = r * 2 + c;
                TextView b = new TextView(this);
                b.setTextSize(13f);
                b.setTextColor(Ui.INK);
                b.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
                b.setMinHeight(Ui.dp(this, 44));
                b.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
                b.setBackground(Ui.pill(this, Ui.CHIP_BG, Ui.LINE, 14));
                b.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (waiting && !answered) onAnswer(idx, false);
                    }
                });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                lp.leftMargin = Ui.dp(this, 4);
                lp.rightMargin = Ui.dp(this, 4);
                lp.topMargin = Ui.dp(this, 3);
                lp.bottomMargin = Ui.dp(this, 3);
                row.addView(b, lp);
                optionButtons[idx] = b;
            }
            optionsArea.addView(row, Ui.matchWrap());
        }
        root.addView(optionsArea, Ui.matchWrap());

        // mark row: "Knew it" / "Missed it" (self-marking)
        markRow = new LinearLayout(this);
        markRow.setOrientation(LinearLayout.HORIZONTAL);
        markRow.setGravity(Gravity.CENTER);
        markRow.setPadding(Ui.dp(this, 8), Ui.dp(this, 4), Ui.dp(this, 8), Ui.dp(this, 4));
        markRow.setVisibility(View.GONE);
        TextView knew = Ui.button(this, "Knew it", Ui.GREEN);
        knew.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onMark(true);
            }
        });
        TextView missed = Ui.button(this, "Missed it", Ui.RED);
        missed.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onMark(false);
            }
        });
        markRow.addView(knew, buttonLp());
        markRow.addView(missed, buttonLp());
        root.addView(markRow, Ui.matchWrap());

        // finish row: "Again" / "Pick practice"
        finishRow = new LinearLayout(this);
        finishRow.setOrientation(LinearLayout.HORIZONTAL);
        finishRow.setGravity(Gravity.CENTER);
        finishRow.setPadding(Ui.dp(this, 8), Ui.dp(this, 4), Ui.dp(this, 8), Ui.dp(this, 4));
        finishRow.setVisibility(View.GONE);
        TextView playAgain = Ui.button(this, "Again", Ui.ACCENT);
        playAgain.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (set != null && !set.items.isEmpty()) beginPractice(set, setTitle);
            }
        });
        TextView finishNew = Ui.button(this, "Pick practice", Ui.BLUE);
        finishNew.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showPicker();
            }
        });
        finishRow.addView(playAgain, buttonLp());
        finishRow.addView(finishNew, buttonLp());
        root.addView(finishRow, Ui.matchWrap());

        // control row: Next + Pick practice + mic mode + mic button + indicator
        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        controls.setBackgroundColor(Ui.PANEL);
        controls.setPadding(Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8));

        nextButton = Ui.button(this, "Next", Ui.GREEN);
        nextButton.setVisibility(View.GONE);
        nextButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onNext();
            }
        });
        controls.addView(nextButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView pickSet = Ui.button(this, "Pick practice", Ui.BLUE);
        pickSet.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // the running set keeps going behind the picker; it is
                // stopped only once the user actually picks something
                showPicker();
            }
        });
        LinearLayout.LayoutParams nqLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nqLp.leftMargin = Ui.dp(this, 8);
        controls.addView(pickSet, nqLp);

        micModeChip = Ui.chip(this, "", true);
        refreshMicModeChip();
        micModeChip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean tap = !Prefs.isPracticeMicTap(AskingActivity.this);
                Prefs.setPracticeMicTap(AskingActivity.this, tap);
                refreshMicModeChip();
                if (waiting && !answered) {
                    // apply the new mode to the current wait
                    if (tap) {
                        if (voice != null) voice.cancelListening();
                        micButton.setText("Speak");
                        micButton.setVisibility(View.VISIBLE);
                        micIndicator.setText("Tap Speak to answer");
                    } else {
                        micButton.setVisibility(View.GONE);
                        rearmMic();
                    }
                }
            }
        });
        LinearLayout.LayoutParams mmLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mmLp.leftMargin = Ui.dp(this, 8);
        controls.addView(micModeChip, mmLp);

        // mic button: TAP mode only, while waiting for an answer
        micButton = Ui.button(this, "Speak", Ui.ACCENT);
        micButton.setVisibility(View.GONE);
        micButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (waiting && !answered) {
                    micButton.setText("...");
                    rearmMic();
                }
            }
        });
        LinearLayout.LayoutParams mbLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mbLp.leftMargin = Ui.dp(this, 8);
        controls.addView(micButton, mbLp);

        micIndicator = new TextView(this);
        micIndicator.setText("");
        micIndicator.setTextSize(12f);
        micIndicator.setTextColor(Ui.MUTED);
        micIndicator.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        micIndicator.setPadding(Ui.dp(this, 8), 0, 0, 0);
        controls.addView(micIndicator, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        root.addView(controls, Ui.matchWrap());
        return root;
    }

    private LinearLayout.LayoutParams buttonLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = Ui.dp(this, 6);
        lp.rightMargin = Ui.dp(this, 6);
        return lp;
    }

    private void refreshMicModeChip() {
        boolean tap = Prefs.isPracticeMicTap(this);
        micModeChip.setText(tap ? "Mic: Tap" : "Mic: Auto");
        micModeChip.setTextColor(tap ? 0xFFFFFFFF : 0xFF6B4A22);
        micModeChip.setBackground(Ui.pill(this, tap ? Ui.ACCENT : Ui.CHIP_BG,
                tap ? 0 : Ui.LINE, 14));
    }

    /** TTS and STT are always on in Practice - no voice toggle. */
    private void speak(String text, String id) {
        if (voice == null || !voice.isTtsReady()) return;
        voice.speak(text, id);
    }

    // ------------------------------------------------------------- transcript

    private void refreshTranscript() {
        messages = db.listMessages(chat.id);
        adapter.notifyDataSetChanged();
        if (!messages.isEmpty()) list.setSelection(messages.size() - 1);
    }

    private void showMessageMenu(final AppDb.Msg m) {
        final String[] items = {"Copy", "Delete pair"};
        new AlertDialog.Builder(this)
                .setTitle("Message")
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        if (which == 0) {
                            ClipboardManager cm = (ClipboardManager)
                                    getSystemService(Context.CLIPBOARD_SERVICE);
                            if (cm != null) {
                                cm.setPrimaryClip(ClipData.newPlainText("Golu message", m.text));
                                Ui.toast(AskingActivity.this, "Copied");
                            }
                        } else {
                            db.deleteMessagePair(chat.id, m.id);
                            refreshTranscript();
                        }
                    }
                })
                .show();
    }

    private class MsgAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return messages.size();
        }

        @Override
        public Object getItem(int position) {
            return messages.get(position);
        }

        @Override
        public long getItemId(int position) {
            return messages.get(position).id;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            AppDb.Msg m = messages.get(position);
            boolean mine = AppDb.ROLE_USER.equals(m.role);

            LinearLayout row = new LinearLayout(AskingActivity.this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, Ui.dp(AskingActivity.this, 3),
                    0, Ui.dp(AskingActivity.this, 3));

            TextView bubble = new TextView(AskingActivity.this);
            bubble.setText(m.text);
            bubble.setTextSize(14f);
            bubble.setTextColor(Ui.INK);
            bubble.setPadding(Ui.dp(AskingActivity.this, 12),
                    Ui.dp(AskingActivity.this, 9),
                    Ui.dp(AskingActivity.this, 12),
                    Ui.dp(AskingActivity.this, 9));
            bubble.setBackground(Ui.pill(AskingActivity.this,
                    mine ? Ui.BUBBLE_USER : Ui.BUBBLE_AI, Ui.LINE, 14));

            View spacer = new View(AskingActivity.this);
            LinearLayout.LayoutParams spacerLp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);

            if (mine) {
                row.addView(spacer, spacerLp);
                row.addView(bubble, Ui.wrapWrap());
            } else {
                row.addView(bubble, Ui.wrapWrap());
                row.addView(spacer, spacerLp);
            }
            return row;
        }
    }

    // ------------------------------------------------------------ set picker

    /** Library items only - Practice never creates anything with AI. */
    private void showPicker() {
        final List<AiDb.Item> items = new ArrayList<>();
        items.addAll(aiDb.listItemsByKind(AiDb.KIND_QUIZ));
        items.addAll(aiDb.listItemsByKind(AiDb.KIND_FLASHCARDS));
        items.addAll(aiDb.listItemsByKind(AiDb.KIND_ONELINER));
        items.addAll(aiDb.listItemsByKind(AiDb.KIND_QUESTIONS));
        final AppDb.AskingSession lastUnfinished = findUnfinishedSession();

        if (items.isEmpty() && lastUnfinished == null) {
            new AlertDialog.Builder(this)
                    .setTitle("Nothing to practise yet")
                    .setMessage("Make something first in Chats (Quiz Maker, One Liner Maker "
                            + "or Questions Asker) - it will be saved to the Library and you "
                            + "can practise it here.")
                    .setPositiveButton("Open Chats", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface d, int w) {
                            startActivity(new Intent(AskingActivity.this, ChatListActivity.class));
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            return;
        }

        final List<String> labels = new ArrayList<>();
        for (AiDb.Item it : items) {
            labels.add("[" + PracticeUtil.pickerLabel(it.kind) + "] " + it.title);
        }
        if (lastUnfinished != null) labels.add("Resume last session");

        final String[] dialogItems = labels.toArray(new String[0]);
        new AlertDialog.Builder(this)
                .setTitle("Pick practice")
                .setItems(dialogItems, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        if (which < items.size()) {
                            AiDb.Item item = items.get(which);
                            PracticeUtil.PracticeSet s = PracticeUtil.fromLibraryItem(item);
                            if (s == null) {
                                Ui.toast(AskingActivity.this,
                                        "Nothing to practise in that item - pick another one");
                                showPicker();
                            } else {
                                beginPractice(s, item.title);
                            }
                        } else {
                            resumeSession(lastUnfinished);
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /** Most recently updated session that still has items left, or null. */
    private AppDb.AskingSession findUnfinishedSession() {
        List<AppDb.AskingSession> sessions = db.listAskingSessions();
        for (AppDb.AskingSession s : sessions) {
            PracticeUtil.PracticeSet set = PracticeUtil.parseSession(s.quizJson);
            if (!set.items.isEmpty() && s.current < set.items.size()) return s;
        }
        return null;
    }

    // --------------------------------------------------------- practice start

    private void beginPractice(PracticeUtil.PracticeSet s, String title) {
        stopPractice();
        set = s;
        setTitle = title == null ? "Practice" : title;
        index = 0;
        score = 0;
        finished = false;
        sessionId = db.saveAskingSession(setTitle,
                PracticeUtil.toSessionJson(set.mode, set.items), 0, 0);
        finishRow.setVisibility(View.GONE);
        markRow.setVisibility(View.GONE);
        askItem();
    }

    private void resumeSession(AppDb.AskingSession s) {
        PracticeUtil.PracticeSet parsed = PracticeUtil.parseSession(s.quizJson);
        if (parsed.items.isEmpty()) {
            Ui.toast(this, "That session is empty - pick another one");
            showPicker();
            return;
        }
        stopPractice();
        set = parsed;
        setTitle = s.quizTitle == null ? "Practice" : s.quizTitle;
        sessionId = s.id;
        index = Math.max(0, Math.min(s.current, set.items.size()));
        score = s.score;
        finished = false;
        finishRow.setVisibility(View.GONE);
        markRow.setVisibility(View.GONE);
        if (index >= set.items.size()) {
            finishPractice();
        } else {
            askItem();
        }
    }

    /** Stops the current set (countdown, STT, speech) without finishing it. */
    private void stopPractice() {
        waiting = false;
        answered = true;
        handler.removeCallbacks(tickRunnable);
        if (voice != null) {
            voice.cancelListening();
            voice.stopTts();
        }
        pet.setSpeaking(false);
        micButton.setVisibility(View.GONE);
        saveProgress();
    }

    private void saveProgress() {
        if (sessionId >= 0 && set != null && !set.items.isEmpty()) {
            int current = finished ? set.items.size() : (answered ? index + 1 : index);
            db.updateAskingSession(sessionId, current, score);
        }
    }

    // ---------------------------------------------------------- asking loop

    private boolean isQuiz() {
        return set != null && PracticeUtil.MODE_QUIZ.equals(set.mode);
    }

    private boolean isOneLiner() {
        return set != null && PracticeUtil.MODE_ONELINER.equals(set.mode);
    }

    private PracticeUtil.Card currentCard() {
        if (set == null || index < 0 || index >= set.items.size()) return null;
        return set.items.get(index);
    }

    private void askItem() {
        if (set == null || index >= set.items.size()) {
            finishPractice();
            return;
        }
        answered = false;
        waiting = false;
        revealed = false;
        finished = false;
        nextButton.setVisibility(View.GONE);
        markRow.setVisibility(View.GONE);
        finishRow.setVisibility(View.GONE);
        micButton.setVisibility(View.GONE);
        revealRow.setVisibility(View.GONE);

        PracticeUtil.Card card = currentCard();
        String word = PracticeUtil.modeWord(set.mode);
        updateStatus();
        micIndicator.setText("Golu is asking...");

        String speech;
        if (isQuiz()) {
            optionsArea.setVisibility(View.VISIBLE);
            cardView.setVisibility(View.GONE);
            for (int i = 0; i < 4; i++) {
                char letter = (char) ('A' + i);
                optionButtons[i].setText(letter + ") " + card.options[i]);
                optionButtons[i].setTextColor(Ui.INK);
                optionButtons[i].setBackground(Ui.pill(this, Ui.CHIP_BG, Ui.LINE, 14));
            }
            db.addMessage(chat.id, AppDb.ROLE_AI, questionText(card, index + 1), "text");
            StringBuilder sb = new StringBuilder();
            sb.append("Question ").append(index + 1).append(". ").append(card.prompt);
            for (int i = 0; i < 4; i++) {
                sb.append(" Option ").append((char) ('A' + i)).append(": ")
                        .append(card.options[i]).append(".");
            }
            speech = sb.toString();
        } else {
            optionsArea.setVisibility(View.GONE);
            cardView.setVisibility(View.VISIBLE);
            cardView.setText(card.prompt);
            db.addMessage(chat.id, AppDb.ROLE_AI,
                    word + " " + (index + 1) + ". " + card.prompt, "text");
            speech = card.prompt;
        }
        refreshTranscript();

        if (voice != null && voice.isTtsReady()) {
            questionSeq++;
            currentQid = "q" + questionSeq;
            voice.speak(speech, currentQid);
        } else {
            // no TTS: go straight to the waiting phase
            onAskSpeechDone();
        }
    }

    /** Question + options as plain text (without revealing the answer). */
    private String questionText(PracticeUtil.Card card, int number) {
        StringBuilder b = new StringBuilder();
        b.append("Q").append(number).append(". ").append(card.prompt).append("\n");
        for (int i = 0; i < 4; i++) {
            b.append((char) ('A' + i)).append(") ").append(card.options[i]).append("\n");
        }
        return b.toString().trim();
    }

    /** Golu finished asking out loud: move to the waiting / marking phase. */
    private void onAskSpeechDone() {
        if (destroyed || answered || finished || currentCard() == null) return;
        if (isQuiz()) {
            startWaiting();
        } else if (isOneLiner() || currentCard().reveal.isEmpty()) {
            // one-liners and items with no answer: nothing to wait for,
            // the user just marks how well they knew it
            showMarkRow();
        } else {
            startCardWait();
        }
    }

    /** Shows the Knew/Missed row (one-liners, answer-less items). */
    private void showMarkRow() {
        waiting = false;
        micIndicator.setText("How did you do?");
        markRow.setVisibility(View.VISIBLE);
        updateStatus();
    }

    private void startWaiting() {
        if (destroyed || answered || finished || currentCard() == null) {
            return;
        }
        waiting = true;
        secondsLeft = Prefs.getQuizTimeSec(this);
        updateStatus();
        handler.removeCallbacks(tickRunnable);
        handler.postDelayed(tickRunnable, 1000);
        if (Prefs.isPracticeMicTap(this)) {
            micIndicator.setText("Tap Speak to answer, or tap an option");
            micButton.setText("Speak");
            micButton.setVisibility(View.VISIBLE);
        } else {
            micIndicator.setText("Listening... say A, B, C or D, or tap an option");
            startListeningForAnswer();
        }
    }

    /** Waiting phase for flashcards / open questions (reveal, not answer). */
    private void startCardWait() {
        if (destroyed || answered || finished || currentCard() == null) {
            return;
        }
        waiting = true;
        secondsLeft = Prefs.getQuizTimeSec(this);
        updateStatus();
        revealRow.setVisibility(View.VISIBLE);
        handler.removeCallbacks(tickRunnable);
        handler.postDelayed(tickRunnable, 1000);
        if (Prefs.isPracticeMicTap(this)) {
            micIndicator.setText("Recall it, then tap Speak or Show answer");
            micButton.setText("Speak");
            micButton.setVisibility(View.VISIBLE);
        } else {
            micIndicator.setText("Recall it out loud, or tap Show answer");
            startListeningForCard();
        }
    }

    private void stopWaiting() {
        waiting = false;
        handler.removeCallbacks(tickRunnable);
        micButton.setVisibility(View.GONE);
        if (voice != null) voice.cancelListening();
    }

    /** The countdown ran out: quiz -> timeout verdict; cards -> auto-reveal. */
    private void onTickTimeout() {
        if (isQuiz()) {
            onTimeout();
        } else if (!revealed) {
            revealItem();
        }
    }

    // -------------------------------------------------------------------- mic

    /** Starts one listening pass if the mic is usable; false otherwise. */
    private boolean startListeningPass(GoluVoice.SttListener listener) {
        if (!waiting || answered || destroyed || voice == null) return false;
        if (Build.VERSION.SDK_INT >= 23
                && checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_AUDIO);
            micIndicator.setText("Mic permission needed - tap to answer");
            return false;
        }
        if (!GoluVoice.isSttAvailable(this)) {
            micIndicator.setText("Speech recognition not available - tap to answer");
            return false;
        }
        // mic time = answer time: the recognizer restarts itself on silence
        // until the countdown runs out, instead of stopping after a few seconds
        long micMs = Math.max(1000L, (long) secondsLeft * 1000L);
        voice.startListeningUntil(Prefs.getSttTag(this), micMs, listener);
        return true;
    }

    /** (Re)arms the mic for the current wait, per the item kind. */
    private void rearmMic() {
        if (!waiting || answered || destroyed) return;
        if (isQuiz()) {
            startListeningForAnswer();
        } else {
            startListeningForCard();
        }
    }

    private void startListeningForAnswer() {
        startListeningPass(new GoluVoice.SttListener() {
            @Override
            public void onResult(String text) {
                micButton.setText("Speak");
                if (!waiting || answered || destroyed) return;
                int a = parseAnswer(text);
                if (a >= 0) {
                    onAnswer(a, true);
                } else {
                    // could not parse - listen again (AUTO) or wait for a tap
                    if (Prefs.isPracticeMicTap(AskingActivity.this)) {
                        micIndicator.setText("Didn't catch that - tap Speak again");
                    } else {
                        micIndicator.setText("Didn't catch that - say A, B, C or D");
                        startListeningForAnswer();
                    }
                }
            }

            @Override
            public void onError(String message) {
                micButton.setText("Speak");
                if (!waiting || answered || destroyed) return;
                if ("NOMATCH".equals(message)) {
                    // no speech heard: listen again (AUTO) or wait for a tap
                    if (Prefs.isPracticeMicTap(AskingActivity.this)) {
                        micIndicator.setText("Didn't catch that - tap Speak again");
                    } else {
                        startListeningForAnswer();
                    }
                }
                // "TIMEOUT" needs nothing: the countdown ends the question
            }
        });
    }

    /** Flashcards / open questions: any spoken words count as an answer. */
    private void startListeningForCard() {
        startListeningPass(new GoluVoice.SttListener() {
            @Override
            public void onResult(String text) {
                micButton.setText("Speak");
                if (!waiting || answered || destroyed || revealed) return;
                db.addMessage(chat.id, AppDb.ROLE_USER, "My answer: " + text, "text");
                refreshTranscript();
                revealItem();
            }

            @Override
            public void onError(String message) {
                micButton.setText("Speak");
                if (!waiting || answered || destroyed) return;
                if ("NOMATCH".equals(message)) {
                    if (Prefs.isPracticeMicTap(AskingActivity.this)) {
                        micIndicator.setText("Didn't catch that - tap Speak again");
                    } else {
                        startListeningForCard();
                    }
                }
                // "TIMEOUT" needs nothing: the countdown reveals the card
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (waiting && !answered && !Prefs.isPracticeMicTap(this)) rearmMic();
            } else {
                Ui.toast(this, "Mic denied - tap to answer");
                micIndicator.setText("Tap to answer");
            }
        }
    }

    /**
     * Parses a spoken answer: "option 1..4", "1..4", "a..d", "option a..d",
     * or the option text contained in the speech. Returns 0..3, or -1.
     */
    private int parseAnswer(String speech) {
        PracticeUtil.Card card = currentCard();
        if (speech == null || card == null || card.options == null) return -1;
        String s = speech.trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty()) return -1;
        String cleaned = s.replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").trim();

        // "option a".."option d" and "option 1".."option 4"
        for (int i = 0; i < 4; i++) {
            char letter = (char) ('a' + i);
            if (cleaned.contains("option " + letter) || cleaned.contains("option" + letter)) {
                return i;
            }
        }
        for (int i = 0; i < 4; i++) {
            if (cleaned.contains("option " + (i + 1)) || cleaned.contains("option" + (i + 1))) {
                return i;
            }
        }
        // bare "a".."d" / "1".."4" as the whole answer
        if (cleaned.length() == 1) {
            char c = cleaned.charAt(0);
            if (c >= 'a' && c <= 'd') return c - 'a';
            if (c >= '1' && c <= '4') return c - '1';
        }
        // a lone letter/number token ("the answer is b"); skip "a" - it is
        // also the article, so only accept it as the whole answer above
        String[] tokens = cleaned.split(" ");
        for (String tok : tokens) {
            if (tok.length() == 1) {
                char c = tok.charAt(0);
                if (c >= 'b' && c <= 'd') return c - 'a';
                if (c >= '1' && c <= '4') return c - '1';
            }
        }
        // the option text contained in the speech
        for (int i = 0; i < 4; i++) {
            String opt = card.options[i] == null ? "" : card.options[i].trim().toLowerCase(Locale.ROOT);
            if (opt.length() >= 3 && s.contains(opt)) return i;
        }
        return -1;
    }

    // -------------------------------------------------------------- answering

    private void onAnswer(int choice, boolean byVoice) {
        PracticeUtil.Card card = currentCard();
        if (answered || card == null || card.options == null) return;
        answered = true;
        stopWaiting();

        boolean correct = choice == card.correctIndex;
        if (correct) score++;

        // mark the options: green = correct, red = wrong pick
        for (int i = 0; i < 4; i++) {
            if (i == card.correctIndex) {
                optionButtons[i].setBackground(Ui.pill(this, 0xFFC8E6C9, Ui.GREEN, 14));
            } else if (i == choice) {
                optionButtons[i].setBackground(Ui.pill(this, 0xFFFFCDD2, Ui.RED, 14));
            }
        }

        char letter = (char) ('A' + choice);
        char correctLetter = (char) ('A' + card.correctIndex);
        db.addMessage(chat.id, AppDb.ROLE_USER, "My answer: " + letter, "text");

        boolean hindi = "hi".equals(Prefs.getTtsLang(this));
        String verdict;
        if (correct) {
            verdict = (hindi ? "Sahi jawab! " : "Correct! ") + card.explanation;
        } else {
            verdict = hindi
                    ? "Galat jawab. Sahi jawab " + correctLetter + " tha. " + card.explanation
                    : "Wrong answer. The correct answer was " + correctLetter + ". " + card.explanation;
        }
        db.addMessage(chat.id, AppDb.ROLE_AI, verdict.trim(), "text");
        refreshTranscript();
        speak(verdict, "verdict");

        micIndicator.setText(byVoice ? "Heard: " + letter : "You picked " + letter);
        updateStatus();
        nextButton.setVisibility(View.VISIBLE);
        saveProgress();
    }

    private void onTimeout() {
        PracticeUtil.Card card = currentCard();
        if (answered || card == null || card.options == null) return;
        answered = true;
        stopWaiting();

        char correctLetter = (char) ('A' + card.correctIndex);
        optionButtons[card.correctIndex].setBackground(Ui.pill(this, 0xFFC8E6C9, Ui.GREEN, 14));

        boolean hindi = "hi".equals(Prefs.getTtsLang(this));
        String verdict = hindi
                ? "Time up! Sahi jawab " + correctLetter + " tha. " + card.explanation
                : "Time up! The correct answer was " + correctLetter + ". " + card.explanation;
        db.addMessage(chat.id, AppDb.ROLE_AI, verdict.trim(), "text");
        refreshTranscript();
        speak(verdict, "verdict");

        micIndicator.setText("Time's up!");
        updateStatus();
        markRow.setVisibility(View.VISIBLE);
    }

    /** Reveals the back of a flashcard / the answer to an open question. */
    private void revealItem() {
        PracticeUtil.Card card = currentCard();
        if (card == null || revealed) return;
        revealed = true;
        stopWaiting();
        revealRow.setVisibility(View.GONE);

        StringBuilder shown = new StringBuilder(card.prompt);
        StringBuilder spoken = new StringBuilder();
        if (!card.reveal.isEmpty()) {
            shown.append("\n\n").append(card.reveal);
            spoken.append(card.reveal);
        }
        if (!card.explanation.isEmpty()) {
            shown.append("\n\n").append(card.explanation);
            if (spoken.length() > 0) spoken.append(" ");
            spoken.append(card.explanation);
        }
        cardView.setText(shown.toString().trim());
        db.addMessage(chat.id, AppDb.ROLE_AI, shown.toString().trim(), "text");
        refreshTranscript();
        if (spoken.length() > 0) speak(spoken.toString(), "reveal");

        micIndicator.setText("How did you do?");
        updateStatus();
        markRow.setVisibility(View.VISIBLE);
    }

    private void onMark(boolean knewIt) {
        if (answered && !isQuiz()) {
            // already marked (double tap guard for card modes)
            return;
        }
        answered = true;
        stopWaiting();
        if (knewIt) {
            score++;
            db.addMessage(chat.id, AppDb.ROLE_USER, "Knew it", "text");
        } else {
            db.addMessage(chat.id, AppDb.ROLE_USER, "Missed it", "text");
        }
        refreshTranscript();
        markRow.setVisibility(View.GONE);
        updateStatus();
        nextButton.setVisibility(View.VISIBLE);
        saveProgress();
    }

    private void onNext() {
        index++;
        if (sessionId >= 0) db.updateAskingSession(sessionId, index, score);
        if (set == null || index >= set.items.size()) {
            finishPractice();
        } else {
            askItem();
        }
    }

    private void finishPractice() {
        waiting = false;
        answered = true;
        finished = true;
        stopWaiting();
        if (sessionId >= 0 && set != null) {
            db.updateAskingSession(sessionId, set.items.size(), score);
        }
        int total = set == null ? 0 : set.items.size();
        boolean hindi = "hi".equals(Prefs.getTtsLang(this));
        String summary = hindi
                ? "Practice khatam! Your score: " + score + " out of " + total + "."
                : "Practice finished! Your score: " + score + " out of " + total + ".";
        db.addMessage(chat.id, AppDb.ROLE_AI, summary, "text");
        refreshTranscript();
        speak(summary, "finish");
        status.setText("Practice finished \u00B7 Score: " + score + " / " + total);
        micIndicator.setText("");
        optionsArea.setVisibility(View.GONE);
        cardView.setVisibility(View.GONE);
        revealRow.setVisibility(View.GONE);
        nextButton.setVisibility(View.GONE);
        markRow.setVisibility(View.GONE);
        finishRow.setVisibility(View.VISIBLE);
    }

    private void updateStatus() {
        if (set == null || set.items.isEmpty()) {
            status.setText("Pick something to practise");
            return;
        }
        if (finished) {
            status.setText("Practice finished \u00B7 Score: " + score + " / " + set.items.size());
            return;
        }
        StringBuilder b = new StringBuilder();
        b.append(PracticeUtil.modeWord(set.mode)).append(" ").append(index + 1)
                .append(" of ").append(set.items.size());
        b.append(" \u00B7 Score: ").append(score);
        if (waiting) b.append(" \u00B7 ").append(secondsLeft).append("s");
        status.setText(b.toString());
    }

}
