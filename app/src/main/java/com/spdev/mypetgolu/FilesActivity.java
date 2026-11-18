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
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * File manager: the user's own folders and uploaded files (PDFs, images,
 * text). Folders can hold sub-folders; files can be attached to chats or
 * used by Quiz Maker. Search finds files across all folders.
 */
public class FilesActivity extends Activity {

    private static final int REQ_PICK_FILE = 1001;
    private static final int REQ_EXPORT = 1002;
    private static final int REQ_IMPORT = 1003;

    private UserDb db;
    private List<UserDb.Folder> folders = new ArrayList<>();
    private List<UserDb.UserFile> files = new ArrayList<>();
    /** folder paths for search results, filled off the UI thread */
    private java.util.Map<Long, String> searchPaths = new java.util.HashMap<>();
    /** 0 = top level (all folders), else the open folder */
    private long currentFolderId = 0;
    private long pendingUploadFolderId = -1;
    private int pendingExportFormat = DataPort.FORMAT_JSON;
    private LinearLayout folderRow;
    private TextView breadcrumb;
    private TextView upButton;
    private EditText searchBox;
    private ListView listView;
    private FileAdapter adapter;
    private boolean destroyed = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = new UserDb(this);

        LinearLayout root = Ui.column(this);

        LinearLayout bar = Ui.topBar(this, "Files");
        TextView newBtn = new TextView(this);
        newBtn.setText("+ Folder");
        newBtn.setTextSize(13f);
        newBtn.setTypeface(Typeface.DEFAULT_BOLD);
        newBtn.setTextColor(0xFFFFFFFF);
        newBtn.setPadding(Ui.dp(this, 12), Ui.dp(this, 7), Ui.dp(this, 12), Ui.dp(this, 7));
        newBtn.setBackground(Ui.pill(this, Ui.ACCENT, 0, 12));
        newBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onNewFolder();
            }
        });
        bar.addView(newBtn, Ui.wrapWrap());
        root.addView(bar, Ui.matchWrap());

        // breadcrumb + Up
        LinearLayout crumbRow = new LinearLayout(this);
        crumbRow.setOrientation(LinearLayout.HORIZONTAL);
        crumbRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        crumbRow.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 10), Ui.dp(this, 2));
        upButton = new TextView(this);
        upButton.setText("< Up");
        upButton.setTextSize(13f);
        upButton.setTypeface(Typeface.DEFAULT_BOLD);
        upButton.setTextColor(Ui.ACCENT_DARK);
        upButton.setPadding(Ui.dp(this, 8), Ui.dp(this, 4), Ui.dp(this, 8), Ui.dp(this, 4));
        upButton.setVisibility(View.GONE);
        upButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                goUp();
            }
        });
        crumbRow.addView(upButton, Ui.wrapWrap());
        breadcrumb = new TextView(this);
        breadcrumb.setText("Files");
        breadcrumb.setTextSize(14f);
        breadcrumb.setTypeface(Typeface.DEFAULT_BOLD);
        breadcrumb.setTextColor(Ui.INK);
        LinearLayout.LayoutParams bcLp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        bcLp.leftMargin = Ui.dp(this, 6);
        crumbRow.addView(breadcrumb, bcLp);
        root.addView(crumbRow, Ui.matchWrap());

        // search field
        searchBox = Ui.searchBox(this);
        LinearLayout.LayoutParams sbLp = Ui.matchWrap();
        sbLp.leftMargin = Ui.dp(this, 10);
        sbLp.rightMargin = Ui.dp(this, 10);
        sbLp.topMargin = Ui.dp(this, 4);
        sbLp.bottomMargin = Ui.dp(this, 2);
        searchBox.setLayoutParams(sbLp);
        root.addView(searchBox);
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

        // sub-folder chips (children of the current folder)
        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        folderRow = new LinearLayout(this);
        folderRow.setOrientation(LinearLayout.HORIZONTAL);
        folderRow.setPadding(Ui.dp(this, 10), Ui.dp(this, 6), Ui.dp(this, 10), Ui.dp(this, 4));
        hsv.addView(folderRow, Ui.wrapWrap());
        root.addView(hsv, Ui.matchWrap());

        // upload button
        TextView uploadBtn = Ui.button(this, "Upload", Ui.GREEN);
        LinearLayout.LayoutParams upLp = Ui.matchWrap();
        upLp.leftMargin = Ui.dp(this, 10);
        upLp.rightMargin = Ui.dp(this, 10);
        upLp.topMargin = Ui.dp(this, 4);
        upLp.bottomMargin = Ui.dp(this, 4);
        uploadBtn.setLayoutParams(upLp);
        uploadBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onUpload();
            }
        });
        root.addView(uploadBtn);

        // export / import row (exports ALL folders and files, not just this folder)
        LinearLayout portRow = new LinearLayout(this);
        portRow.setOrientation(LinearLayout.HORIZONTAL);
        portRow.setPadding(Ui.dp(this, 10), Ui.dp(this, 2), Ui.dp(this, 10), Ui.dp(this, 4));
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
                DataPort.launchOpen(FilesActivity.this, REQ_IMPORT);
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
                "No files here yet.\nTap Upload to add a PDF, image or text file.");
        root.addView(emptyView, Ui.matchWrap());
        listView.setEmptyView(emptyView);

        setContentView(root);

        adapter = new FileAdapter();
        listView.setAdapter(adapter);
        listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                final UserDb.UserFile file = files.get(position);
                Ui.confirm(FilesActivity.this, "Delete file?",
                        "Delete \"" + file.name + "\"? The file will be removed from this device.",
                        new Ui.ConfirmCallback() {
                            @Override
                            public void onYes() {
                                FileStore.deletePath(file.path);
                                db.deleteFile(file.id);
                                refresh();
                            }
                        });
                return true;
            }
        });

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
    public void onBackPressed() {
        if (currentFolderId != 0) {
            goUp();
        } else {
            super.onBackPressed();
        }
    }

    private void goUp() {
        if (currentFolderId == 0) return;
        UserDb.Folder cur = db.getFolder(currentFolderId);
        currentFolderId = cur == null ? 0 : cur.parentId;
        refresh();
    }

    private void onNewFolder() {
        String title = currentFolderId == 0 ? "New folder" : "New sub-folder";
        Ui.input(this, title, "Folder name, e.g. Class 10 Science", "",
                new Ui.InputCallback() {
                    @Override
                    public void onInput(String text) {
                        if (text == null || text.isEmpty()) return;
                        long id = db.createFolder(text, currentFolderId);
                        currentFolderId = id;
                        refresh();
                    }
                });
    }

    /** Loads the current folder's children + files off the UI thread. */
    private void refresh() {
        final long folderId = currentFolderId;
        final String query = searchBox == null ? null : searchBox.getText().toString().trim();
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (db.listChildFolders(0).isEmpty()) {
                    // the default folder is created on first DB run;
                    // recreate if all folders were deleted
                    db.createFolder("My PDFs");
                }
                final String path = folderId == 0 ? "Files" : db.folderPath(folderId);
                final List<UserDb.Folder> children = db.listChildFolders(folderId);
                final List<UserDb.UserFile> loaded;
                final java.util.Map<Long, String> paths = new java.util.HashMap<>();
                if (query != null && !query.isEmpty()) {
                    // search across all folders
                    loaded = new ArrayList<>();
                    String q = query.toLowerCase();
                    for (UserDb.UserFile f : db.listAllFiles()) {
                        if (f.name.toLowerCase().contains(q)) {
                            loaded.add(f);
                            if (!paths.containsKey(f.folderId)) {
                                paths.put(f.folderId, db.folderPath(f.folderId));
                            }
                        }
                    }
                } else {
                    loaded = db.listFiles(folderId);
                }
                final List<Integer> counts = new ArrayList<>();
                for (UserDb.Folder f : children) {
                    counts.add(db.countFilesRecursive(f.id));
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (destroyed) return;
                        breadcrumb.setText(path);
                        upButton.setVisibility(folderId == 0 ? View.GONE : View.VISIBLE);
                        folders = children;
                        files = loaded;
                        searchPaths = paths;
                        buildFolderChips(counts);
                        adapter.notifyDataSetChanged();
                    }
                });
            }
        }).start();
    }

    private void buildFolderChips(List<Integer> counts) {
        folderRow.removeAllViews();
        for (int i = 0; i < folders.size(); i++) {
            final UserDb.Folder folder = folders.get(i);
            int count = i < counts.size() ? counts.get(i) : 0;
            TextView chip = Ui.chip(this, folder.name + " (" + count + ")", false);
            LinearLayout.LayoutParams lp = Ui.wrapWrap();
            lp.rightMargin = Ui.dp(this, 6);
            chip.setLayoutParams(lp);
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    currentFolderId = folder.id;
                    refresh();
                }
            });
            chip.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    onFolderLongPress(folder);
                    return true;
                }
            });
            folderRow.addView(chip);
        }
    }

    private void onFolderLongPress(final UserDb.Folder folder) {
        new AlertDialog.Builder(this)
                .setTitle(folder.name)
                .setItems(new String[]{"Rename", "Delete"}, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        if (which == 0) {
                            Ui.input(FilesActivity.this, "Rename folder", "New name",
                                    folder.name, new Ui.InputCallback() {
                                        @Override
                                        public void onInput(String text) {
                                            if (text == null || text.isEmpty()) return;
                                            db.renameFolder(folder.id, text);
                                            refresh();
                                        }
                                    });
                        } else {
                            int total = db.countFilesRecursive(folder.id);
                            Ui.confirm(FilesActivity.this, "Delete folder?",
                                    "Delete \"" + folder.name + "\" with its sub-folders and all "
                                            + total
                                            + " file(s) inside? This cannot be undone.",
                                    new Ui.ConfirmCallback() {
                                        @Override
                                        public void onYes() {
                                            for (long id : db.collectFolderTreeIds(folder.id)) {
                                                FileStore.deleteFolderDir(FilesActivity.this, id);
                                            }
                                            db.deleteFolderTree(folder.id);
                                            if (currentFolderId == folder.id) {
                                                currentFolderId = folder.parentId;
                                            }
                                            refresh();
                                        }
                                    });
                        }
                    }
                })
                .show();
    }

    private void onUpload() {
        if (currentFolderId != 0) {
            launchPicker(currentFolderId);
            return;
        }
        // at the top level, let the user pick the destination folder
        final List<UserDb.Folder> all = db.listFolders();
        if (all.isEmpty()) {
            long id = db.createFolder("My PDFs");
            launchPicker(id);
            return;
        }
        final String[] labels = new String[all.size()];
        for (int i = 0; i < all.size(); i++) {
            labels[i] = db.folderPath(all.get(i).id);
        }
        Ui.singleChoice(this, "Upload to which folder?", labels, 0,
                new Ui.ChoiceCallback() {
                    @Override
                    public void onChoice(int index) {
                        launchPicker(all.get(index).id);
                    }
                });
    }

    private void launchPicker(long folderId) {
        pendingUploadFolderId = folderId;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "application/pdf", "image/png", "image/jpeg", "text/plain"});
        startActivityForResult(intent, REQ_PICK_FILE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        final Uri uri = data.getData();
        if (requestCode == REQ_EXPORT) {
            String note = pendingExportFormat == DataPort.FORMAT_JSON
                    ? " (list only - use ZIP to include the files themselves)" : null;
            DataPort.runExport(this, DataPort.TYPE_FILES, "file", "files",
                    pendingExportFormat, uri, note);
            return;
        }
        if (requestCode == REQ_IMPORT) {
            DataPort.runImport(this, DataPort.TYPE_FILES, uri,
                    new DataPort.ImportDone() {
                        @Override
                        public void onImported(DataPort.Result res) {
                            if (res.ok) {
                                Ui.showText(FilesActivity.this, "Import finished", res.message);
                                refresh();
                            } else {
                                Ui.showErrorDialog(FilesActivity.this, "Import failed", res.error);
                            }
                        }
                    });
            return;
        }
        if (requestCode != REQ_PICK_FILE) return;
        if (pendingUploadFolderId < 0) pendingUploadFolderId = currentFolderId;
        try {
            FileStore.Stored stored = FileStore.copyFromUri(this, data.getData(),
                    pendingUploadFolderId);
            db.addFile(pendingUploadFolderId, stored.name, stored.mime, stored.size, stored.path);
            currentFolderId = pendingUploadFolderId;
            refresh();
            Ui.toast(this, "Uploaded: " + stored.name);
        } catch (Exception e) {
            String msg = e.getMessage() == null ? "Could not upload the file." : e.getMessage();
            Ui.toast(this, msg);
        }
    }

    private void onExport() {
        if (db.countAllFiles() == 0 && db.listFolders().isEmpty()) {
            Ui.toast(this, "No files or folders to export yet");
            return;
        }
        Ui.singleChoice(this, "Export files as", new String[]{"JSON", "PDF", "ZIP"}, 0,
                new Ui.ChoiceCallback() {
                    @Override
                    public void onChoice(int index) {
                        pendingExportFormat = index;
                        DataPort.launchCreate(FilesActivity.this, REQ_EXPORT,
                                DataPort.exportFileName("files", DataPort.formatExt(index)),
                                DataPort.formatMime(index));
                    }
                });
    }

    private static String fmt(long t) {
        return new SimpleDateFormat("dd MMM yyyy", Locale.US).format(new Date(t));
    }

    private class FileAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return files == null ? 0 : files.size();
        }

        @Override
        public Object getItem(int position) {
            return files.get(position);
        }

        @Override
        public long getItemId(int position) {
            return files.get(position).id;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            UserDb.UserFile file = files.get(position);
            LinearLayout outer = new LinearLayout(FilesActivity.this);
            outer.setOrientation(LinearLayout.VERTICAL);
            outer.setPadding(0, 0, 0, Ui.dp(FilesActivity.this, 8));

            LinearLayout card = Ui.card(FilesActivity.this);
            TextView name = new TextView(FilesActivity.this);
            name.setText(file.name);
            name.setTextSize(15f);
            name.setTypeface(Typeface.DEFAULT_BOLD);
            name.setTextColor(Ui.INK);
            card.addView(name, Ui.matchWrap());

            TextView sub = new TextView(FilesActivity.this);
            String subText = FileStore.prettySize(file.size) + "  •  " + fmt(file.created);
            String path = searchPaths.get(file.folderId);
            if (path != null) {
                subText += "  •  " + path;
            }
            sub.setText(subText);
            sub.setTextSize(12f);
            sub.setTextColor(Ui.MUTED);
            card.addView(sub, Ui.matchWrap());

            outer.addView(card, Ui.matchWrap());
            return outer;
        }
    }
}
