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
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * File manager: the user's own folders and uploaded files (PDFs, images,
 * text). Files can be attached to chats or used by Quiz Maker.
 */
public class FilesActivity extends Activity {

    private static final int REQ_PICK_FILE = 1001;

    private UserDb db;
    private List<UserDb.Folder> folders;
    private List<UserDb.UserFile> files;
    private long selectedFolderId = -1;
    private LinearLayout folderRow;
    private ListView listView;
    private FileAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = new UserDb(this);

        LinearLayout root = Ui.column(this);

        LinearLayout bar = Ui.topBar(this, "My Files");
        TextView newBtn = new TextView(this);
        newBtn.setText("+ New folder");
        newBtn.setTextSize(13f);
        newBtn.setTypeface(Typeface.DEFAULT_BOLD);
        newBtn.setTextColor(0xFFFFFFFF);
        newBtn.setPadding(Ui.dp(this, 12), Ui.dp(this, 7), Ui.dp(this, 12), Ui.dp(this, 7));
        newBtn.setBackground(Ui.pill(this, Ui.ACCENT, 0, 12));
        newBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Ui.input(FilesActivity.this, "New folder", "Folder name, e.g. Class 10 Science",
                        "", new Ui.InputCallback() {
                            @Override
                            public void onInput(String text) {
                                if (text == null || text.isEmpty()) return;
                                long id = db.createFolder(text);
                                selectedFolderId = id;
                                refresh();
                            }
                        });
            }
        });
        bar.addView(newBtn, Ui.wrapWrap());
        root.addView(bar, Ui.matchWrap());

        // folder chips
        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        folderRow = new LinearLayout(this);
        folderRow.setOrientation(LinearLayout.HORIZONTAL);
        folderRow.setPadding(Ui.dp(this, 10), Ui.dp(this, 8), Ui.dp(this, 10), Ui.dp(this, 4));
        hsv.addView(folderRow, Ui.wrapWrap());
        root.addView(hsv, Ui.matchWrap());

        // upload button
        TextView uploadBtn = Ui.button(this, "Upload file", Ui.GREEN);
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

        listView = new ListView(this);
        listView.setDivider(null);
        listView.setDividerHeight(0);
        listView.setPadding(Ui.dp(this, 10), Ui.dp(this, 4), Ui.dp(this, 10), Ui.dp(this, 8));
        root.addView(listView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView emptyView = Ui.empty(this,
                "No files in this folder yet.\nTap Upload file to add a PDF, image or text file.");
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

    private void refresh() {
        folders = db.listFolders();
        if (folders.isEmpty()) {
            // the default folder is created on first DB run; recreate if all were deleted
            db.createFolder("My PDFs");
            folders = db.listFolders();
        }
        boolean found = false;
        for (UserDb.Folder f : folders) {
            if (f.id == selectedFolderId) {
                found = true;
                break;
            }
        }
        if (!found) selectedFolderId = folders.get(0).id;

        buildFolderChips();
        files = db.listFiles(selectedFolderId);
        adapter.notifyDataSetChanged();
    }

    private void buildFolderChips() {
        folderRow.removeAllViews();
        for (final UserDb.Folder folder : folders) {
            TextView chip = Ui.chip(this, folder.name + " (" + db.countFiles(folder.id) + ")",
                    folder.id == selectedFolderId);
            LinearLayout.LayoutParams lp = Ui.wrapWrap();
            lp.rightMargin = Ui.dp(this, 6);
            chip.setLayoutParams(lp);
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    selectedFolderId = folder.id;
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
                            Ui.confirm(FilesActivity.this, "Delete folder?",
                                    "Delete \"" + folder.name + "\" and all "
                                            + db.countFiles(folder.id)
                                            + " file(s) inside it? This cannot be undone.",
                                    new Ui.ConfirmCallback() {
                                        @Override
                                        public void onYes() {
                                            FileStore.deleteFolderDir(FilesActivity.this, folder.id);
                                            db.deleteFolder(folder.id);
                                            if (selectedFolderId == folder.id) selectedFolderId = -1;
                                            refresh();
                                        }
                                    });
                        }
                    }
                })
                .show();
    }

    private void onUpload() {
        if (selectedFolderId < 0) {
            Ui.toast(this, "Pick a folder first");
            return;
        }
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
        if (requestCode != REQ_PICK_FILE || resultCode != RESULT_OK || data == null) return;
        if (data.getData() == null) return;
        try {
            FileStore.Stored stored = FileStore.copyFromUri(this, data.getData(), selectedFolderId);
            db.addFile(selectedFolderId, stored.name, stored.mime, stored.size, stored.path);
            refresh();
            Ui.toast(this, "Uploaded: " + stored.name);
        } catch (Exception e) {
            String msg = e.getMessage() == null ? "Could not upload the file." : e.getMessage();
            Ui.toast(this, msg);
        }
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
            sub.setText(FileStore.prettySize(file.size) + "  •  " + fmt(file.created));
            sub.setTextSize(12f);
            sub.setTextColor(Ui.MUTED);
            card.addView(sub, Ui.matchWrap());

            outer.addView(card, Ui.matchWrap());
            return outer;
        }
    }
}
