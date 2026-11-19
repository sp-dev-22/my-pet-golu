package com.spdev.mypetgolu;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/**
 * The reader (v0.8.0): opens a Library item from the Library or from a
 * card in an agent chat and shows it for READING - notes as clean text,
 * quizzes as questions with a per-question "Show answer" reveal,
 * flashcards as tap-to-flip cards, one-liners as a clean list, and
 * questions with their answers and explanations.
 *
 * Deliberately voice-free: no TTS, no STT, no mic and no speaker button
 * anywhere here. Speech stays in Talk mode only.
 */
public class ReaderActivity extends Activity {

    private AiDb db;
    private long itemId;
    private AiDb.Item item;
    private boolean destroyed = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = new AiDb(this);
        itemId = getIntent().getLongExtra("library_item_id", -1);
        if (itemId <= 0) {
            Ui.toast(this, "Nothing to read");
            finish();
            return;
        }

        // loading screen while the item is read off the UI thread
        LinearLayout loading = Ui.column(this);
        loading.addView(Ui.empty(this, "Loading..."), Ui.matchWrap());
        setContentView(loading);

        new Thread(new Runnable() {
            @Override
            public void run() {
                final AiDb.Item loaded = db.getItem(itemId);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (destroyed) return;
                        if (loaded == null) {
                            new AlertDialog.Builder(ReaderActivity.this)
                                    .setTitle("Can't open this item")
                                    .setMessage("It is no longer in the Library.")
                                    .setPositiveButton("OK",
                                            new DialogInterface.OnClickListener() {
                                                @Override
                                                public void onClick(DialogInterface d, int w) {
                                                    finish();
                                                }
                                            })
                                    .setCancelable(false)
                                    .show();
                            return;
                        }
                        item = loaded;
                        buildUi();
                    }
                });
            }
        }).start();
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        super.onDestroy();
    }

    private void buildUi() {
        LinearLayout root = Ui.column(this);
        root.addView(Ui.topBar(this, item.title), Ui.matchWrap());

        // kind subtitle
        TextView sub = new TextView(this);
        sub.setText(AiDb.kindLabel(item.kind));
        sub.setTextSize(12f);
        sub.setTypeface(Typeface.DEFAULT_BOLD);
        sub.setTextColor(Ui.MUTED);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 2));
        root.addView(sub, Ui.matchWrap());

        ScrollView sv = new ScrollView(this);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(Ui.dp(this, 12), Ui.dp(this, 4), Ui.dp(this, 12), Ui.dp(this, 16));
        sv.addView(body, Ui.matchWrap());
        root.addView(sv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);

        String kind = item.kind == null ? AiDb.KIND_OTHER : item.kind;
        if (AiDb.KIND_WEBAPP.equals(kind)) {
            // a web app is played, not read: hand over to the player
            TextView note = new TextView(this);
            note.setText("This is a web app made by Golu. Tap Open to play it.");
            note.setTextSize(15f);
            note.setTextColor(Ui.INK);
            body.addView(note, Ui.matchWrap());
            TextView openBtn = Ui.button(this, "Open web app", Ui.ACCENT);
            openBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent i = new Intent(ReaderActivity.this, WebAppActivity.class);
                    i.putExtra("library_item_id", item.id);
                    startActivity(i);
                }
            });
            LinearLayout.LayoutParams lp = Ui.matchWrap();
            lp.topMargin = Ui.dp(this, 12);
            body.addView(openBtn, lp);
        } else if (AiDb.KIND_QUIZ.equals(kind)) {
            showQuiz(body);
        } else if (AiDb.KIND_FLASHCARDS.equals(kind)) {
            showFlashcards(body);
        } else if (AiDb.KIND_ONELINER.equals(kind)) {
            showOneLiners(body);
        } else if (AiDb.KIND_QUESTIONS.equals(kind)) {
            showQuestions(body);
        } else {
            showNotes(body);
        }
    }

    // -------------------------------------------------------------------- quiz

    /** Questions with options; each answer hides behind a "Show answer" tap. */
    private void showQuiz(LinearLayout body) {
        List<QuizUtil.Question> qs = QuizUtil.parseQuiz(item.content);
        if (qs.isEmpty()) {
            showNotes(body);
            return;
        }
        for (int i = 0; i < qs.size(); i++) {
            final QuizUtil.Question q = qs.get(i);
            LinearLayout card = Ui.card(this);

            TextView qv = new TextView(this);
            qv.setText("Q" + (i + 1) + ". " + q.question);
            qv.setTextSize(16f);
            qv.setTypeface(Typeface.DEFAULT_BOLD);
            qv.setTextColor(Ui.INK);
            card.addView(qv, Ui.matchWrap());

            TextView opts = new TextView(this);
            StringBuilder ob = new StringBuilder();
            char letter = 'A';
            for (int k = 0; k < 4; k++) {
                ob.append(letter).append(") ").append(q.options[k] == null ? "" : q.options[k]);
                if (k < 3) ob.append("\n");
                letter++;
            }
            opts.setText(ob.toString());
            opts.setTextSize(15f);
            opts.setTextColor(Ui.INK);
            opts.setPadding(0, Ui.dp(this, 6), 0, 0);
            card.addView(opts, Ui.matchWrap());

            final TextView answer = new TextView(this);
            StringBuilder ab = new StringBuilder();
            int correct = q.correctIndex < 0 || q.correctIndex > 3 ? 0 : q.correctIndex;
            ab.append("\u2713 Correct: ").append((char) ('A' + correct)).append(") ")
                    .append(q.options[correct] == null ? "" : q.options[correct]);
            if (q.explanation != null && !q.explanation.isEmpty()) {
                ab.append("\nWhy: ").append(q.explanation);
            }
            answer.setText(ab.toString());
            answer.setTextSize(15f);
            answer.setTypeface(Typeface.DEFAULT_BOLD);
            answer.setTextColor(Ui.GREEN);
            answer.setPadding(0, Ui.dp(this, 8), 0, 0);
            answer.setVisibility(View.GONE);
            card.addView(answer, Ui.matchWrap());

            final TextView revealBtn = Ui.button(this, "Show answer", Ui.BLUE);
            revealBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    boolean showing = answer.getVisibility() == View.VISIBLE;
                    answer.setVisibility(showing ? View.GONE : View.VISIBLE);
                    revealBtn.setText(showing ? "Show answer" : "Hide answer");
                }
            });
            LinearLayout.LayoutParams blp = Ui.wrapWrap();
            blp.topMargin = Ui.dp(this, 8);
            card.addView(revealBtn, blp);

            body.addView(card, Ui.matchWrap());
            body.addView(spacer(), Ui.matchWrap());
        }
    }

    // -------------------------------------------------------------- flashcards

    /** Front/back cards: tap a card to flip it. */
    private void showFlashcards(LinearLayout body) {
        List<ContentUtil.Card> cards = ContentUtil.parseFlashcards(item.content);
        if (cards.isEmpty()) {
            showNotes(body);
            return;
        }
        TextView hint = new TextView(this);
        hint.setText("Tap a card to flip it.");
        hint.setTextSize(12f);
        hint.setTextColor(Ui.MUTED);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, 0, 0, Ui.dp(this, 8));
        body.addView(hint, Ui.matchWrap());
        for (int i = 0; i < cards.size(); i++) {
            final ContentUtil.Card c = cards.get(i);
            final LinearLayout card = Ui.card(this);
            card.setPadding(Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 14));

            TextView count = new TextView(this);
            count.setText("Card " + (i + 1) + " of " + cards.size());
            count.setTextSize(11f);
            count.setTypeface(Typeface.DEFAULT_BOLD);
            count.setTextColor(Ui.MUTED);
            card.addView(count, Ui.matchWrap());

            final TextView face = new TextView(this);
            face.setText(c.prompt);
            face.setTextSize(17f);
            face.setTypeface(Typeface.DEFAULT_BOLD);
            face.setTextColor(Ui.INK);
            face.setGravity(Gravity.CENTER);
            face.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 10));
            card.addView(face, Ui.matchWrap());

            final boolean[] flipped = {false};
            final String front = c.prompt;
            final String back = c.reveal == null || c.reveal.isEmpty()
                    ? "(no answer saved)" : c.reveal;
            card.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    flipped[0] = !flipped[0];
                    face.setText(flipped[0] ? back : front);
                    face.setTypeface(flipped[0] ? Typeface.DEFAULT : Typeface.DEFAULT_BOLD);
                }
            });

            body.addView(card, Ui.matchWrap());
            body.addView(spacer(), Ui.matchWrap());
        }
    }

    // -------------------------------------------------------------- one-liners

    /** One fact per row, numbered. */
    private void showOneLiners(LinearLayout body) {
        List<ContentUtil.Card> lines = ContentUtil.parseOneLiners(item.content);
        if (lines.isEmpty()) {
            showNotes(body);
            return;
        }
        for (int i = 0; i < lines.size(); i++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, Ui.dp(this, 5), 0, Ui.dp(this, 5));

            TextView num = new TextView(this);
            num.setText((i + 1) + ".");
            num.setTextSize(15f);
            num.setTypeface(Typeface.DEFAULT_BOLD);
            num.setTextColor(Ui.ACCENT_DARK);
            num.setPadding(0, 0, Ui.dp(this, 8), 0);
            row.addView(num, Ui.wrapWrap());

            TextView line = new TextView(this);
            line.setText(lines.get(i).prompt);
            line.setTextSize(15f);
            line.setTextColor(Ui.INK);
            row.addView(line, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            body.addView(row, Ui.matchWrap());
        }
    }

    // -------------------------------------------------------------- questions

    /** Each question with its answer and explanation underneath. */
    private void showQuestions(LinearLayout body) {
        List<ContentUtil.Card> qs = ContentUtil.parseQuestions(item.content);
        if (qs.isEmpty()) {
            showNotes(body);
            return;
        }
        for (int i = 0; i < qs.size(); i++) {
            ContentUtil.Card c = qs.get(i);
            LinearLayout card = Ui.card(this);

            TextView qv = new TextView(this);
            qv.setText("Q" + (i + 1) + ". " + c.prompt);
            qv.setTextSize(16f);
            qv.setTypeface(Typeface.DEFAULT_BOLD);
            qv.setTextColor(Ui.INK);
            card.addView(qv, Ui.matchWrap());

            if (c.reveal != null && !c.reveal.isEmpty()) {
                TextView av = new TextView(this);
                av.setText("Answer: " + c.reveal);
                av.setTextSize(15f);
                av.setTextColor(Ui.INK);
                av.setPadding(0, Ui.dp(this, 6), 0, 0);
                card.addView(av, Ui.matchWrap());
            }
            if (c.explanation != null && !c.explanation.isEmpty()) {
                TextView wv = new TextView(this);
                wv.setText("Why: " + c.explanation);
                wv.setTextSize(14f);
                wv.setTextColor(Ui.MUTED);
                wv.setPadding(0, Ui.dp(this, 4), 0, 0);
                card.addView(wv, Ui.matchWrap());
            }

            body.addView(card, Ui.matchWrap());
            body.addView(spacer(), Ui.matchWrap());
        }
    }

    // ------------------------------------------------------------------- notes

    /** Notes and anything else: clean readable text, markdown stripped. */
    private void showNotes(LinearLayout body) {
        TextView tv = new TextView(this);
        String text = ContentUtil.cleanMarkdown(item.content);
        if (text.isEmpty()) text = "(empty)";
        tv.setText(text);
        tv.setTextSize(15f);
        tv.setTextColor(Ui.INK);
        body.addView(tv, Ui.matchWrap());
    }

    private View spacer() {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 4)));
        return v;
    }
}
