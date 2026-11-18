package com.spdev.mypetgolu;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * AI memory: short facts about the student that Golu remembers and uses in
 * every chat (when memory is ON). Facts can be added by hand or suggested
 * from past chats by the background model.
 */
public class MemoryActivity extends Activity {

    private static final int REQ_EXPORT = 2101;
    private static final int REQ_IMPORT = 2102;

    private static final String SUGGEST_SYSTEM =
            "You are a memory assistant. Read the chat and extract up to 5 short facts "
                    + "about the student (name, subjects, exam, goals, likes). Reply ONLY with "
                    + "one fact per line, each starting with '- '. If nothing worth remembering, "
                    + "reply with nothing.";

    private AppDb db;
    private List<AppDb.Fact> facts;
    private ListView listView;
    private FactAdapter adapter;
    private TextView toggleBtn;
    private TextView suggestBtn;
    private int pendingExportFormat = DataPort.FORMAT_JSON;
    private boolean destroyed = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = new AppDb(this);

        LinearLayout root = Ui.column(this);
        root.addView(Ui.topBar(this, "Memory"), Ui.matchWrap());

        // toggle + add fact row
        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setPadding(Ui.dp(this, 10), Ui.dp(this, 8), Ui.dp(this, 10), Ui.dp(this, 4));

        toggleBtn = Ui.button(this, "", Ui.ACCENT);
        toggleBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Prefs.setMemoryEnabled(MemoryActivity.this,
                        !Prefs.isMemoryEnabled(MemoryActivity.this));
                refreshToggle();
            }
        });
        LinearLayout.LayoutParams toggleLp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        toggleLp.rightMargin = Ui.dp(this, 6);
        btnRow.addView(toggleBtn, toggleLp);

        TextView addBtn = Ui.button(this, "+ Add fact", Ui.GREEN);
        addBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Ui.longInput(MemoryActivity.this, "Add a fact",
                        "e.g. Preparing for Class 10 board exams, weak in Maths",
                        "", new Ui.InputCallback() {
                            @Override
                            public void onInput(String text) {
                                if (text == null || text.isEmpty()) return;
                                db.addMemory(text);
                                refresh();
                            }
                        });
            }
        });
        btnRow.addView(addBtn, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(btnRow, Ui.matchWrap());

        // suggest button
        suggestBtn = Ui.button(this, "Suggest from chats", Ui.BLUE);
        LinearLayout.LayoutParams sgLp = Ui.matchWrap();
        sgLp.leftMargin = Ui.dp(this, 10);
        sgLp.rightMargin = Ui.dp(this, 10);
        sgLp.topMargin = Ui.dp(this, 4);
        sgLp.bottomMargin = Ui.dp(this, 4);
        suggestBtn.setLayoutParams(sgLp);
        suggestBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onSuggest();
            }
        });
        root.addView(suggestBtn);

        // export / import row
        LinearLayout portRow = new LinearLayout(this);
        portRow.setOrientation(LinearLayout.HORIZONTAL);
        portRow.setPadding(Ui.dp(this, 10), Ui.dp(this, 4), Ui.dp(this, 10), Ui.dp(this, 4));
        TextView exportBtn = Ui.button(this, "Export", Ui.BLUE);
        exportBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onExport();
            }
        });
        LinearLayout.LayoutParams expLp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        expLp.rightMargin = Ui.dp(this, 6);
        portRow.addView(exportBtn, expLp);
        TextView importBtn = Ui.button(this, "Import", Ui.GREEN);
        importBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                DataPort.launchOpen(MemoryActivity.this, REQ_IMPORT);
            }
        });
        portRow.addView(importBtn, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(portRow, Ui.matchWrap());

        listView = new ListView(this);
        listView.setDivider(null);
        listView.setDividerHeight(0);
        listView.setPadding(Ui.dp(this, 10), Ui.dp(this, 4), Ui.dp(this, 10), Ui.dp(this, 8));
        root.addView(listView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView emptyView = Ui.empty(this,
                "Golu remembers nothing yet.\nAdd a fact, or let Golu suggest facts from your chats.");
        root.addView(emptyView, Ui.matchWrap());
        listView.setEmptyView(emptyView);

        setContentView(root);

        adapter = new FactAdapter();
        listView.setAdapter(adapter);
        listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                final AppDb.Fact fact = facts.get(position);
                Ui.confirm(MemoryActivity.this, "Forget this?",
                        "Delete this memory? Golu will no longer use it in chats.",
                        new Ui.ConfirmCallback() {
                            @Override
                            public void onYes() {
                                db.deleteMemory(fact.id);
                                refresh();
                            }
                        });
                return true;
            }
        });

        refreshToggle();
        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshToggle();
        refresh();
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        final Uri uri = data.getData();
        if (requestCode == REQ_EXPORT) {
            DataPort.runExport(this, DataPort.TYPE_MEMORY, "memory", "memories",
                    pendingExportFormat, uri, null);
        } else if (requestCode == REQ_IMPORT) {
            DataPort.runImport(this, DataPort.TYPE_MEMORY, uri,
                    new DataPort.ImportDone() {
                        @Override
                        public void onImported(DataPort.Result res) {
                            if (res.ok) {
                                Ui.showText(MemoryActivity.this, "Import finished", res.message);
                                refresh();
                            } else {
                                Ui.showErrorDialog(MemoryActivity.this, "Import failed", res.error);
                            }
                        }
                    });
        }
    }

    private void onExport() {
        if (facts == null || facts.isEmpty()) {
            Ui.toast(this, "No memories to export yet");
            return;
        }
        Ui.singleChoice(this, "Export memory as", new String[]{"JSON", "PDF", "ZIP"}, 0,
                new Ui.ChoiceCallback() {
                    @Override
                    public void onChoice(int index) {
                        pendingExportFormat = index;
                        DataPort.launchCreate(MemoryActivity.this, REQ_EXPORT,
                                DataPort.exportFileName("memory", DataPort.formatExt(index)),
                                DataPort.formatMime(index));
                    }
                });
    }

    private void refreshToggle() {
        toggleBtn.setText("Memory: " + (Prefs.isMemoryEnabled(this) ? "ON" : "OFF"));
    }

    private void refresh() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<AppDb.Fact> loaded = db.listMemory();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (destroyed) return;
                        facts = loaded;
                        adapter.notifyDataSetChanged();
                    }
                });
            }
        }).start();
    }

    private void onSuggest() {
        if (!Prefs.hasApiKey(this)) {
            Ui.toast(this, "Add your Gemini API key in Settings");
            startActivity(new Intent(this, SettingsActivity.class));
            return;
        }
        suggestBtn.setEnabled(false);
        // gather the chat history off the UI thread; every callback below
        // checks "destroyed" so leaving this screen early cannot crash
        new Thread(new Runnable() {
            @Override
            public void run() {
                // last 20 messages across agent chats + the conversation chat
                List<AppDb.Msg> all = new ArrayList<>();
                List<AppDb.Chat> chats = db.listChats(AppDb.CHAT_AGENT);
                AppDb.Chat convo = db.getChatByKind(AppDb.CHAT_CONVERSATION);
                if (convo != null) chats.add(convo);
                for (AppDb.Chat c : chats) {
                    all.addAll(db.lastMessages(c.id, 20));
                }
                Collections.sort(all, new Comparator<AppDb.Msg>() {
                    @Override
                    public int compare(AppDb.Msg a, AppDb.Msg b) {
                        if (a.created < b.created) return -1;
                        if (a.created > b.created) return 1;
                        return 0;
                    }
                });
                if (all.size() > 20) {
                    all = new ArrayList<>(all.subList(all.size() - 20, all.size()));
                }
                StringBuilder b = new StringBuilder();
                for (AppDb.Msg m : all) {
                    b.append(AppDb.ROLE_USER.equals(m.role) ? "user: " : "Golu: ");
                    b.append(m.text).append('\n');
                }
                String chatText = b.toString();
                if (chatText.length() > 6000) {
                    chatText = chatText.substring(chatText.length() - 6000);
                }
                final String text = chatText;
                final boolean empty = all.isEmpty();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (destroyed) return;
                        if (empty) {
                            suggestBtn.setEnabled(true);
                            Ui.toast(MemoryActivity.this,
                                    "No chats yet to learn from - chat with Golu first!");
                            return;
                        }
                        GeminiClient.generateSimple(Prefs.getApiKey(MemoryActivity.this),
                                Prefs.getBackgroundModel(MemoryActivity.this),
                                SUGGEST_SYSTEM, text, false, new GeminiClient.Callback() {
                                    @Override
                                    public void onOk(String reply) {
                                        if (destroyed) return;
                                        suggestBtn.setEnabled(true);
                                        showSuggestedFacts(reply);
                                    }

                                    @Override
                                    public void onError(String message) {
                                        if (destroyed) return;
                                        suggestBtn.setEnabled(true);
                                        Ui.showErrorDialog(MemoryActivity.this,
                                                "Gemini error", message);
                                    }
                                });
                    }
                });
            }
        }).start();
    }

    private void showSuggestedFacts(String reply) {
        final List<String> lines = new ArrayList<>();
        if (reply != null) {
            for (String raw : reply.split("\n")) {
                String line = raw.trim();
                if (line.startsWith("- ")) {
                    String fact = line.substring(2).trim();
                    if (!fact.isEmpty()) lines.add(fact);
                }
            }
        }
        if (lines.isEmpty()) {
            Ui.toast(this, "Nothing worth remembering found");
            return;
        }
        StringBuilder b = new StringBuilder();
        for (String fact : lines) b.append("- ").append(fact).append('\n');
        TextView tv = new TextView(this);
        tv.setText(b.toString());
        tv.setTextSize(14f);
        tv.setTextColor(Ui.INK);
        tv.setPadding(Ui.dp(this, 16), Ui.dp(this, 12), Ui.dp(this, 16), Ui.dp(this, 12));
        ScrollView sv = new ScrollView(this);
        sv.addView(tv);
        new AlertDialog.Builder(this)
                .setTitle("Suggested facts")
                .setView(sv)
                .setPositiveButton("Add all", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        for (String fact : lines) db.addMemory(fact);
                        refresh();
                        Ui.toast(MemoryActivity.this, "Facts saved to memory");
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private static String fmt(long t) {
        return new SimpleDateFormat("dd MMM yyyy", Locale.US).format(new Date(t));
    }

    private class FactAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return facts == null ? 0 : facts.size();
        }

        @Override
        public Object getItem(int position) {
            return facts.get(position);
        }

        @Override
        public long getItemId(int position) {
            return facts.get(position).id;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            AppDb.Fact fact = facts.get(position);
            LinearLayout outer = new LinearLayout(MemoryActivity.this);
            outer.setOrientation(LinearLayout.VERTICAL);
            outer.setPadding(0, 0, 0, Ui.dp(MemoryActivity.this, 8));

            LinearLayout card = Ui.card(MemoryActivity.this);
            TextView text = new TextView(MemoryActivity.this);
            text.setText(fact.fact);
            text.setTextSize(15f);
            text.setTextColor(Ui.INK);
            card.addView(text, Ui.matchWrap());

            TextView sub = new TextView(MemoryActivity.this);
            sub.setText(fmt(fact.created));
            sub.setTextSize(12f);
            sub.setTextColor(Ui.MUTED);
            card.addView(sub, Ui.matchWrap());

            outer.addView(card, Ui.matchWrap());
            return outer;
        }
    }
}
