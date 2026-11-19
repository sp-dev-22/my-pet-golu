package com.spdev.mypetgolu;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeMap;

/**
 * The Library: everything the AI created (quizzes, notes, flashcards,
 * one-liners, questions). Items live in folders (or unfiled). Filter by
 * kind and folder, search by title, tap to view, long-press to move or
 * delete. Quizzes, flashcards, one-liners and questions can be opened
 * straight into Practice mode ("Practice").
 * "Manage" asks the helper model to organise unfiled items into folders.
 */
public class LibraryActivity extends Activity {

    private static final int REQ_EXPORT = 2201;
    private static final int REQ_IMPORT = 2202;

    private static final String[] FILTER_LABELS = {
            "All", "Quiz", "Notes", "Flashcards", "One-liners", "Questions", "Web Apps"
    };
    private static final String[] FILTER_KINDS = {
            null, AiDb.KIND_QUIZ, AiDb.KIND_NOTES, AiDb.KIND_FLASHCARDS,
            AiDb.KIND_ONELINER, AiDb.KIND_QUESTIONS, AiDb.KIND_WEBAPP
    };

    private AiDb db;
    private List<AiDb.Item> items;
    private List<String> folderNames = new ArrayList<>();
    private ListView listView;
    private ItemAdapter adapter;
    private LinearLayout chipRow;
    private LinearLayout folderRow;
    private EditText searchBox;
    private int selectedFilter;
    /** null = all folders, "" = unfiled only, else a folder name */
    private String selectedFolder;
    private boolean managing = false;
    private int pendingExportFormat = DataPort.FORMAT_JSON;
    private boolean destroyed = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = new AiDb(this);

        LinearLayout root = Ui.column(this);
        root.addView(Ui.topBar(this, "Library"), Ui.matchWrap());

        // search field + Manage button
        LinearLayout searchRow = new LinearLayout(this);
        searchRow.setOrientation(LinearLayout.HORIZONTAL);
        searchRow.setPadding(Ui.dp(this, 10), Ui.dp(this, 8), Ui.dp(this, 10), Ui.dp(this, 2));
        searchBox = Ui.searchBox(this);
        searchRow.addView(searchBox, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView manageBtn = Ui.button(this, "Manage", Ui.ACCENT);
        LinearLayout.LayoutParams mbLp = Ui.wrapWrap();
        mbLp.leftMargin = Ui.dp(this, 8);
        manageBtn.setLayoutParams(mbLp);
        manageBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onManage();
            }
        });
        searchRow.addView(manageBtn);
        root.addView(searchRow, Ui.matchWrap());
        searchBox.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                refresh();
            }
        });

        // export / import row (exports the whole Library, not the filtered view)
        LinearLayout portRow = new LinearLayout(this);
        portRow.setOrientation(LinearLayout.HORIZONTAL);
        portRow.setPadding(Ui.dp(this, 10), Ui.dp(this, 4), Ui.dp(this, 10), Ui.dp(this, 2));
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
                DataPort.launchOpen(LibraryActivity.this, REQ_IMPORT);
            }
        });
        portRow.addView(importBtn, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(portRow, Ui.matchWrap());

        // kind filter chips
        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        chipRow = new LinearLayout(this);
        chipRow.setOrientation(LinearLayout.HORIZONTAL);
        chipRow.setPadding(Ui.dp(this, 10), Ui.dp(this, 6), Ui.dp(this, 10), Ui.dp(this, 2));
        hsv.addView(chipRow, Ui.wrapWrap());
        root.addView(hsv, Ui.matchWrap());

        // folder filter chips
        HorizontalScrollView fsv = new HorizontalScrollView(this);
        fsv.setHorizontalScrollBarEnabled(false);
        folderRow = new LinearLayout(this);
        folderRow.setOrientation(LinearLayout.HORIZONTAL);
        folderRow.setPadding(Ui.dp(this, 10), Ui.dp(this, 2), Ui.dp(this, 10), Ui.dp(this, 4));
        fsv.addView(folderRow, Ui.wrapWrap());
        root.addView(fsv, Ui.matchWrap());

        listView = new ListView(this);
        listView.setDivider(null);
        listView.setDividerHeight(0);
        listView.setPadding(Ui.dp(this, 10), Ui.dp(this, 4), Ui.dp(this, 10), Ui.dp(this, 8));
        root.addView(listView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView emptyView = Ui.empty(this,
                "Library is empty.\nAsk Golu (Quiz Maker, Flashcard Maker,\nOne Liner Maker, Questions Asker\nor Web App Maker) to create something!");
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
                onItemLongPress(items.get(position));
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
            DataPort.runExport(this, DataPort.TYPE_LIBRARY, "item", "items",
                    pendingExportFormat, uri, null);
        } else if (requestCode == REQ_IMPORT) {
            DataPort.runImport(this, DataPort.TYPE_LIBRARY, uri,
                    new DataPort.ImportDone() {
                        @Override
                        public void onImported(DataPort.Result res) {
                            if (res.ok) {
                                Ui.showText(LibraryActivity.this, "Import finished", res.message);
                                refresh();
                            } else {
                                Ui.showErrorDialog(LibraryActivity.this, "Import failed", res.error);
                            }
                        }
                    });
        }
    }

    private void onExport() {
        if (db.countItems() == 0) {
            Ui.toast(this, "Library is empty - nothing to export yet");
            return;
        }
        Ui.singleChoice(this, "Export library as", new String[]{"JSON", "PDF", "ZIP"}, 0,
                new Ui.ChoiceCallback() {
                    @Override
                    public void onChoice(int index) {
                        pendingExportFormat = index;
                        DataPort.launchCreate(LibraryActivity.this, REQ_EXPORT,
                                DataPort.exportFileName("library", DataPort.formatExt(index)),
                                DataPort.formatMime(index));
                    }
                });
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

    private void buildFolderChips() {
        folderRow.removeAllViews();
        List<String> options = new ArrayList<>();
        options.add(null); // all folders
        options.add(""); // unfiled
        options.addAll(folderNames);
        for (final String folder : options) {
            String label;
            boolean selected;
            if (folder == null) {
                label = "All folders";
                selected = selectedFolder == null;
            } else if (folder.isEmpty()) {
                label = "Unfiled";
                selected = "".equals(selectedFolder);
            } else {
                label = folder;
                selected = folder.equals(selectedFolder);
            }
            TextView chip = Ui.chip(this, label, selected);
            LinearLayout.LayoutParams lp = Ui.wrapWrap();
            lp.rightMargin = Ui.dp(this, 6);
            chip.setLayoutParams(lp);
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    selectedFolder = folder;
                    buildFolderChips();
                    refresh();
                }
            });
            folderRow.addView(chip);
        }
    }

    /** Loads summaries (no content) off the UI thread. */
    private void refresh() {
        final String kind = FILTER_KINDS[selectedFilter];
        final String folder = selectedFolder;
        final String query = searchBox == null ? null : searchBox.getText().toString().trim();
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<AiDb.Item> loaded = db.listItemSummaries(kind, folder, query);
                final List<String> folders = db.listFolders();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (destroyed) return;
                        items = loaded;
                        folderNames = folders;
                        buildFolderChips();
                        adapter.notifyDataSetChanged();
                    }
                });
            }
        }).start();
    }

    /** Summaries carry no content, so the full item is loaded on tap. */
    private void onItemTap(final AiDb.Item summary) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                final AiDb.Item full = db.getItem(summary.id);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (destroyed || full == null) return;
                        showItem(full);
                    }
                });
            }
        }).start();
    }

    private void showItem(final AiDb.Item item) {
        if (AiDb.KIND_WEBAPP.equals(item.kind)) {
            // never dump the raw HTML: a short card with an Open button
            TextView tv = new TextView(this);
            tv.setText("A web app made by Golu. Tap Open to play it - when you "
                    + "finish, your result is sent back to Golu for feedback.");
            tv.setTextSize(14f);
            tv.setTextColor(Ui.INK);
            tv.setPadding(Ui.dp(this, 16), Ui.dp(this, 12), Ui.dp(this, 16), Ui.dp(this, 12));
            new AlertDialog.Builder(this)
                    .setTitle(item.title)
                    .setView(tv)
                    .setPositiveButton("Open", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface d, int w) {
                            openWebApp(item);
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
            return;
        }
        if (PracticeUtil.isPracticableKind(item.kind)) {
            String text = item.content;
            if (AiDb.KIND_QUIZ.equals(item.kind)) {
                List<QuizUtil.Question> qs = QuizUtil.parseQuiz(item.content);
                if (!qs.isEmpty()) text = QuizUtil.displayAll(qs);
            }
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
                    .setPositiveButton("Practice", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface d, int w) {
                            Intent i = new Intent(LibraryActivity.this, AskingActivity.class);
                            i.putExtra("library_item_id", item.id);
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

    /** Launches the WebView player for a web app item. */
    private void openWebApp(AiDb.Item item) {
        Intent i = new Intent(this, WebAppActivity.class);
        i.putExtra("library_item_id", item.id);
        startActivity(i);
    }

    private void onItemLongPress(final AiDb.Item item) {
        final boolean isWebApp = AiDb.KIND_WEBAPP.equals(item.kind);
        String[] options = isWebApp
                ? new String[]{"Open", "Move to folder", "Delete"}
                : new String[]{"Move to folder", "Delete"};
        new AlertDialog.Builder(this)
                .setTitle(item.title)
                .setItems(options,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int which) {
                                if (isWebApp && which == 0) {
                                    openWebApp(item);
                                } else if (which == (isWebApp ? 1 : 0)) {
                                    onMoveToFolder(item);
                                } else {
                                    onItemDelete(item);
                                }
                            }
                        })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void onMoveToFolder(final AiDb.Item item) {
        final List<String> folders = new ArrayList<>(folderNames);
        final List<String> labels = new ArrayList<>(folders);
        labels.add("New folder...");
        labels.add("No folder");
        final String[] itemsArr = labels.toArray(new String[0]);
        new AlertDialog.Builder(this)
                .setTitle("Move to folder")
                .setItems(itemsArr, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        if (which < folders.size()) {
                            db.setItemFolder(item.id, folders.get(which));
                            refresh();
                        } else if (which == folders.size()) {
                            Ui.input(LibraryActivity.this, "New folder", "Folder name", "",
                                    new Ui.InputCallback() {
                                        @Override
                                        public void onInput(String name) {
                                            if (name == null || name.trim().isEmpty()) return;
                                            db.setItemFolder(item.id, name.trim());
                                            refresh();
                                        }
                                    });
                        } else {
                            db.setItemFolder(item.id, "");
                            refresh();
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
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

    // ------------------------------------------------------------------ Manage

    /** Asks the helper (background) model to organise unfiled items. */
    private void onManage() {
        if (managing) return;
        if (!Prefs.hasApiKey(this)) {
            Ui.toast(this, "Add your Gemini API key in Settings first");
            startActivity(new Intent(this, SettingsActivity.class));
            return;
        }
        managing = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<AiDb.Item> unfiled = db.listUnfiledItems();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (destroyed) {
                            managing = false;
                            return;
                        }
                        if (unfiled.isEmpty()) {
                            managing = false;
                            Ui.toast(LibraryActivity.this,
                                    "Everything is already in folders");
                            return;
                        }
                        requestManage(unfiled);
                    }
                });
            }
        }).start();
    }

    private void requestManage(final List<AiDb.Item> unfiled) {
        StringBuilder sb = new StringBuilder();
        sb.append("You organise a student's study library into folders.\n");
        sb.append("For each item below, suggest a short folder name (2-4 words, ");
        sb.append("e.g. \"Indian Polity\", \"Hindi Grammar\"). Group related items ");
        sb.append("in the same folder. Reply with ONLY a JSON array like ");
        sb.append("[{\"id\":1,\"folder\":\"Indian Polity\"}, ...] using the exact ids given. ");
        sb.append("No markdown, no extra text.\n\n");
        for (AiDb.Item it : unfiled) {
            sb.append(it.id).append(". [").append(AiDb.kindLabel(it.kind))
                    .append("] ").append(it.title).append("\n");
        }
        GeminiClient.generateSimple(Prefs.getApiKey(this), Prefs.getBackgroundModel(this),
                "You organise study material into folders. Reply with JSON only, no extra text.",
                sb.toString(), true, new GeminiClient.Callback() {
                    @Override
                    public void onOk(String reply) {
                        managing = false;
                        if (destroyed) return;
                        List<Proposal> proposals = parseManageReply(reply, unfiled);
                        if (proposals.isEmpty()) {
                            Ui.toast(LibraryActivity.this,
                                    "Couldn't organise - please try again");
                            return;
                        }
                        showManageConfirm(proposals);
                    }

                    @Override
                    public void onError(String message) {
                        managing = false;
                        if (destroyed) return;
                        Ui.showErrorDialog(LibraryActivity.this, "Gemini error", message);
                    }
                });
    }

    private static class Proposal {
        long id;
        String title;
        String folder;
    }

    private List<Proposal> parseManageReply(String reply, List<AiDb.Item> unfiled) {
        List<Proposal> out = new ArrayList<>();
        if (reply == null) return out;
        // strip any markdown fence, keep just the JSON array
        int start = reply.indexOf('[');
        int end = reply.lastIndexOf(']');
        if (start < 0 || end <= start) return out;
        Set<Long> known = new HashSet<>();
        for (AiDb.Item it : unfiled) known.add(it.id);
        try {
            JSONArray arr = new JSONArray(reply.substring(start, end + 1));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                long id = obj.optLong("id", -1);
                String folder = obj.optString("folder", "").trim();
                if (!known.contains(id) || folder.isEmpty()) continue;
                Proposal p = new Proposal();
                p.id = id;
                p.folder = folder;
                for (AiDb.Item it : unfiled) {
                    if (it.id == id) {
                        p.title = it.title;
                        break;
                    }
                }
                out.add(p);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private void showManageConfirm(final List<Proposal> proposals) {
        TreeMap<String, List<String>> byFolder = new TreeMap<>();
        for (Proposal p : proposals) {
            List<String> titles = byFolder.get(p.folder);
            if (titles == null) {
                titles = new ArrayList<>();
                byFolder.put(p.folder, titles);
            }
            titles.add(p.title);
        }
        StringBuilder sb = new StringBuilder();
        for (String folder : byFolder.keySet()) {
            sb.append(folder).append(":\n");
            for (String title : byFolder.get(folder)) {
                sb.append("  - ").append(title).append("\n");
            }
            sb.append("\n");
        }
        TextView tv = new TextView(this);
        tv.setText(sb.toString().trim());
        tv.setTextSize(14f);
        tv.setTextColor(Ui.INK);
        tv.setPadding(Ui.dp(this, 16), Ui.dp(this, 12), Ui.dp(this, 16), Ui.dp(this, 12));
        ScrollView sv = new ScrollView(this);
        sv.addView(tv);
        new AlertDialog.Builder(this)
                .setTitle("Organise into " + byFolder.size() + " folders?")
                .setView(sv)
                .setPositiveButton("Apply", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        for (Proposal p : proposals) {
                            db.setItemFolder(p.id, p.folder);
                        }
                        Ui.toast(LibraryActivity.this,
                                "Organised " + proposals.size() + " items");
                        refresh();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
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
            if (item.folder != null && !item.folder.isEmpty()) {
                subText += "  •  " + item.folder;
            }
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
