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
 * Offline question-asking (quiz) mode. Golu reads each question aloud
 * (TTS with lip-sync, can be switched off), then listens for the answer
 * by voice or tap until the countdown runs out. Questions come only from
 * quizzes already saved in the Library (made in agent chats) - nothing
 * is created with AI here, and no API calls happen during asking.
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

    private LinearLayout optionsArea;
    private TextView[] optionButtons = new TextView[4];
    private LinearLayout markRow;
    private LinearLayout finishRow;
    private TextView nextButton;
    private TextView micIndicator;
    private TextView voiceChip;

    // quiz state
    private List<QuizUtil.Question> questions;
    private String quizTitle = "Quiz";
    private int index = 0;
    private int score = 0;
    private boolean waiting = false;
    private boolean answered = false;
    private boolean finished = false;
    private boolean destroyed = false;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private int secondsLeft = 0;
    private long sessionId = -1;
    // per-question TTS id: a stale onDone/onError from a previous question
    // (e.g. after switching quizzes mid-speech) must not start listening
    private int questionSeq = 0;
    private String currentQid = "";

    private final Runnable tickRunnable = new Runnable() {
        @Override
        public void run() {
            if (destroyed || !waiting || answered) return;
            secondsLeft--;
            if (secondsLeft <= 0) {
                onTimeout();
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
        chat = db.getOrCreateSingleChat(AppDb.CHAT_ASKING, "Quiz",
                Prefs.getAskingModel(this));
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
                if (currentQid.equals(id) && !destroyed && !answered && !finished) startWaiting();
            }

            @Override
            public void onError(String id) {
                pet.setSpeaking(false);
                if (currentQid.equals(id) && !destroyed && !answered && !finished) startWaiting();
            }
        });

        refreshTranscript();

        long libraryQuizId = getIntent().getLongExtra("library_quiz_id", -1);
        long sessionExtra = getIntent().getLongExtra("session_id", -1);
        if (libraryQuizId >= 0) {
            AiDb.Item item = aiDb.getItem(libraryQuizId);
            List<QuizUtil.Question> qs = item == null
                    ? new ArrayList<QuizUtil.Question>()
                    : QuizUtil.parseQuiz(item.content);
            if (qs.isEmpty()) {
                Ui.toast(this, "That quiz is empty - pick another one");
                showPicker();
            } else {
                beginQuiz(qs, item.title);
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
        root.addView(Ui.topBar(this, "Quiz"));

        pet = new PetView(this);
        root.addView(pet, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 190)));

        status = new TextView(this);
        status.setText("Pick a quiz to start");
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

        // options area: 4 buttons in a 2x2 grid
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

        // timeout mark row: "Knew it" / "Missed it"
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

        // finish row: "Again" / "Pick quiz"
        finishRow = new LinearLayout(this);
        finishRow.setOrientation(LinearLayout.HORIZONTAL);
        finishRow.setGravity(Gravity.CENTER);
        finishRow.setPadding(Ui.dp(this, 8), Ui.dp(this, 4), Ui.dp(this, 8), Ui.dp(this, 4));
        finishRow.setVisibility(View.GONE);
        TextView playAgain = Ui.button(this, "Again", Ui.ACCENT);
        playAgain.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (questions != null && !questions.isEmpty()) beginQuiz(questions, quizTitle);
            }
        });
        TextView finishNew = Ui.button(this, "Pick quiz", Ui.BLUE);
        finishNew.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showPicker();
            }
        });
        finishRow.addView(playAgain, buttonLp());
        finishRow.addView(finishNew, buttonLp());
        root.addView(finishRow, Ui.matchWrap());

        // control row: Next + Pick quiz + Voice toggle + mic indicator
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

        TextView pickQuiz = Ui.button(this, "Pick quiz", Ui.BLUE);
        pickQuiz.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // the running quiz keeps going behind the picker; it is
                // stopped only once the user actually picks something
                showPicker();
            }
        });
        LinearLayout.LayoutParams nqLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nqLp.leftMargin = Ui.dp(this, 8);
        controls.addView(pickQuiz, nqLp);

        voiceChip = Ui.chip(this, "", true);
        refreshVoiceChip();
        voiceChip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean on = !Prefs.isAskingTtsOn(AskingActivity.this);
                Prefs.setAskingTtsOn(AskingActivity.this, on);
                if (!on && voice != null) {
                    voice.stopTts();
                    pet.setSpeaking(false);
                }
                refreshVoiceChip();
            }
        });
        LinearLayout.LayoutParams vcLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        vcLp.leftMargin = Ui.dp(this, 8);
        controls.addView(voiceChip, vcLp);

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

    private void refreshVoiceChip() {
        boolean on = Prefs.isAskingTtsOn(this);
        voiceChip.setText(on ? "Voice ON" : "Voice OFF");
        voiceChip.setTextColor(on ? 0xFFFFFFFF : 0xFF6B4A22);
        voiceChip.setBackground(Ui.pill(this, on ? Ui.ACCENT : Ui.CHIP_BG,
                on ? 0 : Ui.LINE, 14));
    }

    /** Speaks only when the Asking voice toggle is ON and TTS is ready. */
    private void speak(String text, String id) {
        if (!Prefs.isAskingTtsOn(this) || voice == null || !voice.isTtsReady()) return;
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

    // ------------------------------------------------------------ quiz picker

    /** Library quizzes only - Asking never creates anything with AI. */
    private void showPicker() {
        final List<AiDb.Item> quizzes = aiDb.listItemsByKind(AiDb.KIND_QUIZ);
        final AppDb.AskingSession lastUnfinished = findUnfinishedSession();

        if (quizzes.isEmpty() && lastUnfinished == null) {
            new AlertDialog.Builder(this)
                    .setTitle("No quizzes yet")
                    .setMessage("Make a quiz first in Chats (Quiz Maker skill) - "
                            + "it will be saved to the Library and you can practise it here.")
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
        for (AiDb.Item it : quizzes) labels.add(it.title);
        if (lastUnfinished != null) labels.add("Resume last session");

        final String[] items = labels.toArray(new String[0]);
        new AlertDialog.Builder(this)
                .setTitle("Pick a quiz")
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        if (which < quizzes.size()) {
                            AiDb.Item item = quizzes.get(which);
                            List<QuizUtil.Question> qs = QuizUtil.parseQuiz(item.content);
                            if (qs.isEmpty()) {
                                Ui.toast(AskingActivity.this, "That quiz is empty - pick another one");
                                showPicker();
                            } else {
                                beginQuiz(qs, item.title);
                            }
                        } else {
                            resumeSession(lastUnfinished);
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /** Most recently updated session that still has questions left, or null. */
    private AppDb.AskingSession findUnfinishedSession() {
        List<AppDb.AskingSession> sessions = db.listAskingSessions();
        for (AppDb.AskingSession s : sessions) {
            List<QuizUtil.Question> qs = QuizUtil.parseQuiz(s.quizJson);
            if (!qs.isEmpty() && s.current < qs.size()) return s;
        }
        return null;
    }

    // ----------------------------------------------------------- quiz start

    private void beginQuiz(List<QuizUtil.Question> qs, String title) {
        stopQuiz();
        questions = qs;
        quizTitle = title == null ? "Quiz" : title;
        index = 0;
        score = 0;
        finished = false;
        sessionId = db.saveAskingSession(quizTitle, QuizUtil.toJson(questions), 0, 0);
        optionsArea.setVisibility(View.VISIBLE);
        finishRow.setVisibility(View.GONE);
        markRow.setVisibility(View.GONE);
        askQuestion();
    }

    private void resumeSession(AppDb.AskingSession s) {
        List<QuizUtil.Question> qs = QuizUtil.parseQuiz(s.quizJson);
        if (qs.isEmpty()) {
            Ui.toast(this, "That session is empty - pick another quiz");
            showPicker();
            return;
        }
        stopQuiz();
        questions = qs;
        quizTitle = s.quizTitle == null ? "Quiz" : s.quizTitle;
        sessionId = s.id;
        index = Math.max(0, Math.min(s.current, qs.size()));
        score = s.score;
        finished = false;
        optionsArea.setVisibility(View.VISIBLE);
        finishRow.setVisibility(View.GONE);
        markRow.setVisibility(View.GONE);
        if (index >= questions.size()) {
            finishQuiz();
        } else {
            askQuestion();
        }
    }

    /** Stops the current quiz (countdown, STT, speech) without finishing it. */
    private void stopQuiz() {
        waiting = false;
        answered = true;
        handler.removeCallbacks(tickRunnable);
        if (voice != null) {
            voice.cancelListening();
            voice.stopTts();
        }
        pet.setSpeaking(false);
        saveProgress();
    }

    private void saveProgress() {
        if (sessionId >= 0 && questions != null && !questions.isEmpty()) {
            int current = finished ? questions.size() : (answered ? index + 1 : index);
            db.updateAskingSession(sessionId, current, score);
        }
    }

    // ---------------------------------------------------------- asking loop

    private void askQuestion() {
        if (questions == null || index >= questions.size()) {
            finishQuiz();
            return;
        }
        answered = false;
        waiting = false;
        finished = false;
        nextButton.setVisibility(View.GONE);
        markRow.setVisibility(View.GONE);
        finishRow.setVisibility(View.GONE);
        optionsArea.setVisibility(View.VISIBLE);

        QuizUtil.Question q = questions.get(index);
        for (int i = 0; i < 4; i++) {
            char letter = (char) ('A' + i);
            optionButtons[i].setText(letter + ") " + q.options[i]);
            optionButtons[i].setTextColor(Ui.INK);
            optionButtons[i].setBackground(Ui.pill(this, Ui.CHIP_BG, Ui.LINE, 14));
        }
        updateStatus();
        micIndicator.setText("Golu is asking...");

        db.addMessage(chat.id, AppDb.ROLE_AI, questionText(q, index + 1), "text");
        refreshTranscript();

        StringBuilder speech = new StringBuilder();
        speech.append("Question ").append(index + 1).append(". ").append(q.question);
        for (int i = 0; i < 4; i++) {
            speech.append(" Option ").append((char) ('A' + i)).append(": ").append(q.options[i]).append(".");
        }

        if (Prefs.isAskingTtsOn(this) && voice != null && voice.isTtsReady()) {
            questionSeq++;
            currentQid = "q" + questionSeq;
            voice.speak(speech.toString(), currentQid);
        } else {
            // voice off or no TTS: go straight to waiting for the answer
            startWaiting();
        }
    }

    /** Question + options as plain text (without revealing the answer). */
    private String questionText(QuizUtil.Question q, int number) {
        StringBuilder b = new StringBuilder();
        b.append("Q").append(number).append(". ").append(q.question).append("\n");
        for (int i = 0; i < 4; i++) {
            b.append((char) ('A' + i)).append(") ").append(q.options[i]).append("\n");
        }
        return b.toString().trim();
    }

    private void startWaiting() {
        if (destroyed || answered || finished || questions == null || index >= questions.size()) {
            return;
        }
        waiting = true;
        secondsLeft = Prefs.getQuizTimeSec(this);
        updateStatus();
        micIndicator.setText("Listening... say A, B, C or D, or tap an option");
        handler.removeCallbacks(tickRunnable);
        handler.postDelayed(tickRunnable, 1000);
        startListeningForAnswer();
    }

    private void stopWaiting() {
        waiting = false;
        handler.removeCallbacks(tickRunnable);
        if (voice != null) voice.cancelListening();
    }

    private void startListeningForAnswer() {
        if (!waiting || answered || destroyed || voice == null) return;
        if (Build.VERSION.SDK_INT >= 23
                && checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_AUDIO);
            micIndicator.setText("Mic permission needed - tap an option to answer");
            return;
        }
        if (!GoluVoice.isSttAvailable(this)) {
            micIndicator.setText("Speech recognition not available - tap an option");
            return;
        }
        // mic time = answer time: the recognizer restarts itself on silence
        // until the countdown runs out, instead of stopping after a few seconds
        long micMs = Math.max(1000L, (long) secondsLeft * 1000L);
        voice.startListeningUntil(Prefs.getSttTag(this), micMs, new GoluVoice.SttListener() {
            @Override
            public void onResult(String text) {
                if (!waiting || answered || destroyed) return;
                int a = parseAnswer(text);
                if (a >= 0) {
                    onAnswer(a, true);
                } else {
                    // could not parse - keep listening while time remains
                    micIndicator.setText("Didn't catch that - say A, B, C or D");
                    startListeningForAnswer();
                }
            }

            @Override
            public void onError(String message) {
                if (!waiting || answered || destroyed) return;
                if ("NOMATCH".equals(message)) {
                    // no speech heard: listen again while time remains
                    startListeningForAnswer();
                }
                // "TIMEOUT" needs nothing: the countdown ends the question
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (waiting && !answered) startListeningForAnswer();
            } else {
                Ui.toast(this, "Mic denied - tap an option to answer");
                micIndicator.setText("Tap an option to answer");
            }
        }
    }

    /**
     * Parses a spoken answer: "option 1..4", "1..4", "a..d", "option a..d",
     * or the option text contained in the speech. Returns 0..3, or -1.
     */
    private int parseAnswer(String speech) {
        if (speech == null || questions == null || index >= questions.size()) return -1;
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
        QuizUtil.Question q = questions.get(index);
        for (int i = 0; i < 4; i++) {
            String opt = q.options[i] == null ? "" : q.options[i].trim().toLowerCase(Locale.ROOT);
            if (opt.length() >= 3 && s.contains(opt)) return i;
        }
        return -1;
    }

    // -------------------------------------------------------------- answering

    private void onAnswer(int choice, boolean byVoice) {
        if (answered || questions == null || index >= questions.size()) return;
        answered = true;
        stopWaiting();

        QuizUtil.Question q = questions.get(index);
        boolean correct = choice == q.correctIndex;
        if (correct) score++;

        // mark the options: green = correct, red = wrong pick
        for (int i = 0; i < 4; i++) {
            if (i == q.correctIndex) {
                optionButtons[i].setBackground(Ui.pill(this, 0xFFC8E6C9, Ui.GREEN, 14));
            } else if (i == choice) {
                optionButtons[i].setBackground(Ui.pill(this, 0xFFFFCDD2, Ui.RED, 14));
            }
        }

        char letter = (char) ('A' + choice);
        char correctLetter = (char) ('A' + q.correctIndex);
        db.addMessage(chat.id, AppDb.ROLE_USER, "My answer: " + letter, "text");

        boolean hindi = "hi".equals(Prefs.getTtsLang(this));
        String verdict;
        if (correct) {
            verdict = (hindi ? "Sahi jawab! " : "Correct! ") + q.explanation;
        } else {
            verdict = hindi
                    ? "Galat jawab. Sahi jawab " + correctLetter + " tha. " + q.explanation
                    : "Wrong answer. The correct answer was " + correctLetter + ". " + q.explanation;
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
        if (answered || questions == null || index >= questions.size()) return;
        answered = true;
        stopWaiting();

        QuizUtil.Question q = questions.get(index);
        char correctLetter = (char) ('A' + q.correctIndex);
        optionButtons[q.correctIndex].setBackground(Ui.pill(this, 0xFFC8E6C9, Ui.GREEN, 14));

        boolean hindi = "hi".equals(Prefs.getTtsLang(this));
        String verdict = hindi
                ? "Time up! Sahi jawab " + correctLetter + " tha. " + q.explanation
                : "Time up! The correct answer was " + correctLetter + ". " + q.explanation;
        db.addMessage(chat.id, AppDb.ROLE_AI, verdict.trim(), "text");
        refreshTranscript();
        speak(verdict, "verdict");

        micIndicator.setText("Time's up!");
        updateStatus();
        markRow.setVisibility(View.VISIBLE);
    }

    private void onMark(boolean knewIt) {
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
        if (questions == null || index >= questions.size()) {
            finishQuiz();
        } else {
            askQuestion();
        }
    }

    private void finishQuiz() {
        waiting = false;
        answered = true;
        finished = true;
        stopWaiting();
        if (sessionId >= 0 && questions != null) {
            db.updateAskingSession(sessionId, questions.size(), score);
        }
        int total = questions == null ? 0 : questions.size();
        boolean hindi = "hi".equals(Prefs.getTtsLang(this));
        String summary = hindi
                ? "Quiz khatam! Your score: " + score + " out of " + total + "."
                : "Quiz finished! Your score: " + score + " out of " + total + ".";
        db.addMessage(chat.id, AppDb.ROLE_AI, summary, "text");
        refreshTranscript();
        speak(summary, "finish");
        status.setText("Quiz finished \u00B7 Score: " + score + " / " + total);
        micIndicator.setText("");
        optionsArea.setVisibility(View.GONE);
        nextButton.setVisibility(View.GONE);
        markRow.setVisibility(View.GONE);
        finishRow.setVisibility(View.VISIBLE);
    }

    private void updateStatus() {
        if (questions == null || questions.isEmpty()) {
            status.setText("Pick a quiz to start");
            return;
        }
        if (finished) {
            status.setText("Quiz finished \u00B7 Score: " + score + " / " + questions.size());
            return;
        }
        StringBuilder b = new StringBuilder();
        b.append("Q ").append(index + 1).append(" of ").append(questions.size());
        b.append(" \u00B7 Score: ").append(score);
        if (waiting) b.append(" \u00B7 ").append(secondsLeft).append("s");
        status.setText(b.toString());
    }

}
