package com.spdev.mypetgolu;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Shared file-attach picker (agent chats and Talk): files from the user's
 * folders (with sub-folder paths) and items from the Library (with Library
 * folders), grouped under folder headers, with a search field on top.
 * The caller caps how many can be picked (10 in agent chats, 3 in Talk).
 */
public final class AttachPicker {

    public interface Listener {
        void onPicked(List<UserDb.UserFile> files, List<AiDb.Item> items);
    }

    /** One row in the picker list: either a folder header or a checkable file. */
    private static class Row {
        boolean header;
        String headerText;
        UserDb.UserFile file;
        AiDb.Item item;
        String label;
        String sub;
    }

    public static void show(final Activity act, final UserDb userDb, final AiDb aiDb,
                            final int max,
                            List<UserDb.UserFile> preFiles, List<AiDb.Item> preItems,
                            final Listener listener) {
        final List<UserDb.UserFile> allFiles = userDb.listAllFiles();
        final List<AiDb.Item> allItems = aiDb.listItemSummaries(null, null, null);
        if (allFiles.isEmpty() && allItems.isEmpty()) {
            Ui.toast(act, "Nothing to attach yet - upload a file or save something to the Library first");
            return;
        }

        final Set<Long> checkedFiles = new HashSet<>();
        final Set<Long> checkedItems = new HashSet<>();
        if (preFiles != null) {
            for (UserDb.UserFile f : preFiles) checkedFiles.add(f.id);
        }
        if (preItems != null) {
            for (AiDb.Item it : preItems) checkedItems.add(it.id);
        }

        // ---- build the full row list (grouped by folder) ----
        final List<Row> allRows = new ArrayList<>();
        List<UserDb.UserFile> sortedFiles = new ArrayList<>(allFiles);
        Collections.sort(sortedFiles, new Comparator<UserDb.UserFile>() {
            @Override
            public int compare(UserDb.UserFile a, UserDb.UserFile b) {
                String pa = userDb.folderPath(a.folderId);
                String pb = userDb.folderPath(b.folderId);
                int c = pa.compareToIgnoreCase(pb);
                if (c != 0) return c;
                return a.name.compareToIgnoreCase(b.name);
            }
        });
        String lastFolder = null;
        for (UserDb.UserFile f : sortedFiles) {
            String path = userDb.folderPath(f.folderId);
            if (!path.equals(lastFolder)) {
                lastFolder = path;
                Row h = new Row();
                h.header = true;
                h.headerText = path;
                allRows.add(h);
            }
            Row r = new Row();
            r.file = f;
            r.label = f.name;
            r.sub = FileStore.prettySize(f.size);
            allRows.add(r);
        }

        List<AiDb.Item> sortedItems = new ArrayList<>(allItems);
        Collections.sort(sortedItems, new Comparator<AiDb.Item>() {
            @Override
            public int compare(AiDb.Item a, AiDb.Item b) {
                String fa = a.folder == null ? "" : a.folder;
                String fb = b.folder == null ? "" : b.folder;
                int c = fa.compareToIgnoreCase(fb);
                if (c != 0) return c;
                return a.title.compareToIgnoreCase(b.title);
            }
        });
        String lastLibFolder = null;
        for (AiDb.Item it : sortedItems) {
            String f = it.folder == null ? "" : it.folder;
            if (!f.equals(lastLibFolder)) {
                lastLibFolder = f;
                Row h = new Row();
                h.header = true;
                h.headerText = f.isEmpty() ? "Library" : "Library / " + f;
                allRows.add(h);
            }
            Row r = new Row();
            r.item = it;
            r.label = it.title;
            r.sub = AiDb.kindLabel(it.kind);
            allRows.add(r);
        }

        // ---- dialog layout: search field + list ----
        LinearLayout layout = new LinearLayout(act);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(Ui.dp(act, 12), Ui.dp(act, 8), Ui.dp(act, 12), Ui.dp(act, 4));

        final EditText search = Ui.searchBox(act);
        layout.addView(search, Ui.matchWrap());

        final List<Row> visible = new ArrayList<>(allRows);
        final ListView listView = new ListView(act);
        listView.setDivider(null);
        listView.setDividerHeight(0);
        LinearLayout.LayoutParams listLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(act, 380));
        listLp.topMargin = Ui.dp(act, 8);
        layout.addView(listView, listLp);

        final RowAdapter adapter = new RowAdapter(act, visible, checkedFiles, checkedItems, max);
        listView.setAdapter(adapter);

        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                String q = s.toString().trim().toLowerCase();
                visible.clear();
                if (q.isEmpty()) {
                    visible.addAll(allRows);
                } else {
                    Row pendingHeader = null;
                    for (Row r : allRows) {
                        if (r.header) {
                            pendingHeader = r;
                        } else if (r.label.toLowerCase().contains(q)) {
                            if (pendingHeader != null) {
                                visible.add(pendingHeader);
                                pendingHeader = null;
                            }
                            visible.add(r);
                        }
                    }
                }
                adapter.notifyDataSetChanged();
            }
        });

        new AlertDialog.Builder(act)
                .setTitle("Attach (max " + max + ")")
                .setView(layout)
                .setPositiveButton("Attach", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        List<UserDb.UserFile> files = new ArrayList<>();
                        for (UserDb.UserFile f : allFiles) {
                            if (checkedFiles.contains(f.id)) files.add(f);
                        }
                        // fetch the full items (summaries carry no content)
                        List<AiDb.Item> items = new ArrayList<>();
                        for (AiDb.Item it : allItems) {
                            if (checkedItems.contains(it.id)) {
                                AiDb.Item full = aiDb.getItem(it.id);
                                if (full != null) items.add(full);
                            }
                        }
                        listener.onPicked(files, items);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private static class RowAdapter extends BaseAdapter {
        private final Activity act;
        private final List<Row> rows;
        private final Set<Long> checkedFiles;
        private final Set<Long> checkedItems;
        private final int max;

        RowAdapter(Activity act, List<Row> rows, Set<Long> checkedFiles,
                   Set<Long> checkedItems, int max) {
            this.act = act;
            this.rows = rows;
            this.checkedFiles = checkedFiles;
            this.checkedItems = checkedItems;
            this.max = max;
        }

        @Override
        public int getCount() {
            return rows.size();
        }

        @Override
        public Object getItem(int position) {
            return rows.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public int getViewTypeCount() {
            return 2;
        }

        @Override
        public int getItemViewType(int position) {
            return rows.get(position).header ? 0 : 1;
        }

        private int selectedCount() {
            return checkedFiles.size() + checkedItems.size();
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final Row row = rows.get(position);
            if (row.header) {
                TextView h = new TextView(act);
                h.setText(row.headerText);
                h.setTextSize(12f);
                h.setTypeface(Typeface.DEFAULT_BOLD);
                h.setTextColor(Ui.MUTED);
                h.setPadding(Ui.dp(act, 4), Ui.dp(act, 10), Ui.dp(act, 4), Ui.dp(act, 2));
                return h;
            }

            final boolean isFile = row.file != null;
            final long id = isFile ? row.file.id : row.item.id;
            final Set<Long> set = isFile ? checkedFiles : checkedItems;

            LinearLayout line = new LinearLayout(act);
            line.setOrientation(LinearLayout.HORIZONTAL);
            line.setGravity(Gravity.CENTER_VERTICAL);
            line.setPadding(Ui.dp(act, 2), Ui.dp(act, 4), Ui.dp(act, 2), Ui.dp(act, 4));

            final CheckBox box = new CheckBox(act);
            box.setChecked(set.contains(id));
            box.setClickable(false);
            line.addView(box, Ui.wrapWrap());

            LinearLayout texts = new LinearLayout(act);
            texts.setOrientation(LinearLayout.VERTICAL);
            TextView name = new TextView(act);
            name.setText(row.label);
            name.setTextSize(14f);
            name.setTextColor(Ui.INK);
            texts.addView(name, Ui.matchWrap());
            TextView sub = new TextView(act);
            sub.setText(row.sub);
            sub.setTextSize(11f);
            sub.setTextColor(Ui.MUTED);
            texts.addView(sub, Ui.matchWrap());
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            line.addView(texts, tp);

            line.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (set.contains(id)) {
                        set.remove(id);
                        box.setChecked(false);
                    } else {
                        if (selectedCount() >= max) {
                            Ui.toast(act, "Max " + max + " attachments at a time");
                            return;
                        }
                        set.add(id);
                        box.setChecked(true);
                    }
                }
            });
            return line;
        }
    }

    private AttachPicker() {
    }
}
