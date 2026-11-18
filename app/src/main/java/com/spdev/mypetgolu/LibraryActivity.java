package com.spdev.mypetgolu;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * The Library: everything the AI created (quizzes, notes, flashcards,
 * one-liners, questions). Filter by kind, tap to view, long-press to delete.
 * Quizzes can be opened straight into asking mode ("Ask me").
 */
public class LibraryActivity extends Activity {

    private static final String[] FILTER_LABELS = {
            "All", "Quiz", "Notes", "Flashcards", "One-liners", "Questions"
    };
    private static final String[] FILTER_KINDS = {
            null, AiDb.KIND_QUIZ, AiDb.KIND_NOTES, AiDb.KIND_FLASHCARDS,
            AiDb.KIND_ONELINER, AiDb.KIND_QUESTIONS
    };

    private AiDb db;
    private List<AiDb.Item> items;
    private ListView listView;
    private ItemAdapter adapter;
    private LinearLayout chipRow;
    private int selectedFilter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = new AiDb(this);

        LinearLayout root = Ui.column(this);
        root.addView(Ui.topBar(this, "Library"), Ui.matchWrap());

        // filter chips
        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        chipRow = new LinearLayout(this);
        chipRow.setOrientation(LinearLayout.HORIZONTAL);
        chipRow.setPadding(Ui.dp(this, 10), Ui.dp(this, 8), Ui.dp(this, 10), Ui.dp(this, 4));
        hsv.addView(chipRow, Ui.wrapWrap());
        root.addView(hsv, Ui.matchWrap());

        listView = new ListView(this);
        listView.setDivider(null);
        listView.setDividerHeight(0);
        listView.setPadding(Ui.dp(this, 10), Ui.dp(this, 4), Ui.dp(this, 10), Ui.dp(this, 8));
        root.addView(listView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView emptyView = Ui.empty(this,
                "Library is empty.\nAsk Golu (Quiz Maker, One Liner Maker\nor Questions Asker) to create something!");
        root.addView(emptyView, Ui.matchWrap());
        listView.setEmptyView(emptyView);

        setContentView(root);

        adapter = new ItemAdapter();
        listView.setAdapter(adapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                onItemTap(items.get(position));
            }
        });
        listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                onItemDelete(items.get(position));
                return true;
            }
        });

        buildChips();
        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void buildChips() {
        chipRow.removeAllViews();
        for (int i = 0; i < FILTER_LABELS.length; i++) {
            final int index = i;
            TextView chip = Ui.chip(this, FILTER_LABELS[i], i == selectedFilter);
            LinearLayout.LayoutParams lp = Ui.wrapWrap();
            lp.rightMargin = Ui.dp(this, 6);
            chip.setLayoutParams(lp);
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    selectedFilter = index;
                    buildChips();
                    refresh();
                }
            });
            chipRow.addView(chip);
        }
    }

    private void refresh() {
        String kind = FILTER_KINDS[selectedFilter];
        items = kind == null ? db.listItems() : db.listItemsByKind(kind);
        adapter.notifyDataSetChanged();
    }

    private void onItemTap(final AiDb.Item item) {
        if (AiDb.KIND_QUIZ.equals(item.kind)) {
            List<QuizUtil.Question> qs = QuizUtil.parseQuiz(item.content);
            String text = qs.isEmpty() ? item.content : QuizUtil.displayAll(qs);
            TextView tv = new TextView(this);
            tv.setText(text);
            tv.setTextSize(14f);
            tv.setTextColor(Ui.INK);
            tv.setPadding(Ui.dp(this, 16), Ui.dp(this, 12), Ui.dp(this, 16), Ui.dp(this, 12));
            ScrollView sv = new ScrollView(this);
            sv.addView(tv);
            new AlertDialog.Builder(this)
                    .setTitle(item.title)
                    .setView(sv)
                    .setPositiveButton("Ask me", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface d, int w) {
                            Intent i = new Intent(LibraryActivity.this, AskingActivity.class);
                            i.putExtra("library_quiz_id", item.id);
                            startActivity(i);
                        }
                    })
                    .setNeutralButton("Delete", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface d, int w) {
                            onItemDelete(item);
                        }
                    })
                    .setNegativeButton("Close", null)
                    .show();
        } else {
            Ui.showText(this, item.title, item.content);
        }
    }

    private void onItemDelete(final AiDb.Item item) {
        Ui.confirm(this, "Delete from Library?",
                "Delete \"" + item.title + "\"? This cannot be undone.",
                new Ui.ConfirmCallback() {
                    @Override
                    public void onYes() {
                        db.deleteItem(item.id);
                        refresh();
                    }
                });
    }

    private static String fmt(long t) {
        return new SimpleDateFormat("dd MMM yyyy", Locale.US).format(new Date(t));
    }

    private class ItemAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return items == null ? 0 : items.size();
        }

        @Override
        public Object getItem(int position) {
            return items.get(position);
        }

        @Override
        public long getItemId(int position) {
            return items.get(position).id;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            AiDb.Item item = items.get(position);
            LinearLayout outer = new LinearLayout(LibraryActivity.this);
            outer.setOrientation(LinearLayout.VERTICAL);
            outer.setPadding(0, 0, 0, Ui.dp(LibraryActivity.this, 8));

            LinearLayout card = Ui.card(LibraryActivity.this);
            TextView title = new TextView(LibraryActivity.this);
            title.setText("[" + AiDb.kindMark(item.kind) + "] "
                    + AiDb.kindLabel(item.kind) + " - " + item.title);
            title.setTextSize(15f);
            title.setTypeface(Typeface.DEFAULT_BOLD);
            title.setTextColor(Ui.INK);
            card.addView(title, Ui.matchWrap());

            TextView sub = new TextView(LibraryActivity.this);
            String source = item.source == null ? "" : item.source;
            String subText = fmt(item.created);
            if (!source.isEmpty()) subText += "  •  from " + source;
            sub.setText(subText);
            sub.setTextSize(12f);
            sub.setTextColor(Ui.MUTED);
            card.addView(sub, Ui.matchWrap());

            outer.addView(card, Ui.matchWrap());
            return outer;
        }
    }
}
