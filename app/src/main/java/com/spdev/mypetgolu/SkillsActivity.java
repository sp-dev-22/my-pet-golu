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
import android.widget.TextView;

import java.util.List;

/**
 * Skills: the system prompts behind agent chats. Built-in skills can only
 * be viewed; custom skills can be created, edited and deleted.
 * Export / Import (JSON, PDF, ZIP) lives in the button row below the bar.
 */
public class SkillsActivity extends Activity {

    private static final int REQ_EXPORT = 2001;
    private static final int REQ_IMPORT = 2002;

    private AppDb db;
    private List<AppDb.Skill> skills;
    private ListView listView;
    private SkillAdapter adapter;
    private int pendingExportFormat = DataPort.FORMAT_JSON;
    private boolean destroyed = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = new AppDb(this);
        // seed the built-in skills once, off the UI thread (the v0.6.0/v0.7.0/
        // v0.8.0 flags re-run seeding once on existing installs for the new
        // skills and prompt updates)
        if (!Prefs.isSkillsSeeded(this) || !Prefs.isSkillsSeededV60(this)
                || !Prefs.isSkillsSeededV70(this) || !Prefs.isSkillsSeededV80(this)) {
            final AppDb seedDb = db;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    Skills.seed(seedDb);
                    Prefs.setSkillsSeeded(SkillsActivity.this);
                    Prefs.setSkillsSeededV60(SkillsActivity.this);
                    Prefs.setSkillsSeededV70(SkillsActivity.this);
                    Prefs.setSkillsSeededV80(SkillsActivity.this);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (!destroyed) refresh();
                        }
                    });
                }
            }).start();
        }

        LinearLayout root = Ui.column(this);

        LinearLayout bar = Ui.topBar(this, "Skills");
        TextView newBtn = new TextView(this);
        newBtn.setText("+ New skill");
        newBtn.setTextSize(13f);
        newBtn.setTypeface(Typeface.DEFAULT_BOLD);
        newBtn.setTextColor(0xFFFFFFFF);
        newBtn.setPadding(Ui.dp(this, 12), Ui.dp(this, 7), Ui.dp(this, 12), Ui.dp(this, 7));
        newBtn.setBackground(Ui.pill(this, Ui.ACCENT, 0, 12));
        newBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onNewSkill();
            }
        });
        bar.addView(newBtn, Ui.wrapWrap());
        root.addView(bar, Ui.matchWrap());

        // export / import row
        LinearLayout portRow = new LinearLayout(this);
        portRow.setOrientation(LinearLayout.HORIZONTAL);
        portRow.setPadding(Ui.dp(this, 10), Ui.dp(this, 8), Ui.dp(this, 10), Ui.dp(this, 4));
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
                DataPort.launchOpen(SkillsActivity.this, REQ_IMPORT);
            }
        });
        portRow.addView(importBtn, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(portRow, Ui.matchWrap());

        listView = new ListView(this);
        listView.setDivider(null);
        listView.setDividerHeight(0);
        listView.setPadding(Ui.dp(this, 10), Ui.dp(this, 8), Ui.dp(this, 10), Ui.dp(this, 8));
        root.addView(listView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView emptyView = Ui.empty(this, "No skills yet.\nTap + New skill to create one.");
        root.addView(emptyView, Ui.matchWrap());
        listView.setEmptyView(emptyView);

        setContentView(root);

        adapter = new SkillAdapter();
        listView.setAdapter(adapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                onSkillTap(skills.get(position));
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
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        final Uri uri = data.getData();
        if (requestCode == REQ_EXPORT) {
            DataPort.runExport(this, DataPort.TYPE_SKILLS, "skill", "skills",
                    pendingExportFormat, uri, null);
        } else if (requestCode == REQ_IMPORT) {
            DataPort.runImport(this, DataPort.TYPE_SKILLS, uri,
                    new DataPort.ImportDone() {
                        @Override
                        public void onImported(DataPort.Result res) {
                            if (res.ok) {
                                Ui.showText(SkillsActivity.this, "Import finished", res.message);
                                refresh();
                            } else {
                                Ui.showErrorDialog(SkillsActivity.this, "Import failed", res.error);
                            }
                        }
                    });
        }
    }

    private void onExport() {
        if (skills == null || skills.isEmpty()) {
            Ui.toast(this, "No skills to export yet");
            return;
        }
        Ui.singleChoice(this, "Export skills as", new String[]{"JSON", "PDF", "ZIP"}, 0,
                new Ui.ChoiceCallback() {
                    @Override
                    public void onChoice(int index) {
                        pendingExportFormat = index;
                        DataPort.launchCreate(SkillsActivity.this, REQ_EXPORT,
                                DataPort.exportFileName("skills", DataPort.formatExt(index)),
                                DataPort.formatMime(index));
                    }
                });
    }

    private void refresh() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<AppDb.Skill> loaded = db.listSkills();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (destroyed) return;
                        skills = loaded;
                        adapter.notifyDataSetChanged();
                    }
                });
            }
        }).start();
    }

    private void onNewSkill() {
        Ui.input(this, "New skill", "Skill name, e.g. Maths Helper", "", new Ui.InputCallback() {
            @Override
            public void onInput(final String name) {
                if (name == null || name.isEmpty()) {
                    Ui.toast(SkillsActivity.this, "Please give the skill a name");
                    return;
                }
                Ui.longInput(SkillsActivity.this, "Skill prompt",
                        "Write what Golu should do with this skill...",
                        "", new Ui.InputCallback() {
                            @Override
                            public void onInput(String prompt) {
                                if (prompt == null || prompt.isEmpty()) {
                                    Ui.toast(SkillsActivity.this, "Please write a prompt");
                                    return;
                                }
                                db.addSkill(name, prompt, false);
                                refresh();
                            }
                        });
            }
        });
    }

    private void onSkillTap(final AppDb.Skill skill) {
        if (skill.builtin) {
            Ui.showText(this, skill.name, skill.prompt);
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(skill.name)
                .setItems(new String[]{"View", "Edit", "Delete"}, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        if (which == 0) {
                            Ui.showText(SkillsActivity.this, skill.name, skill.prompt);
                        } else if (which == 1) {
                            Ui.input(SkillsActivity.this, "Edit skill name", "Skill name",
                                    skill.name, new Ui.InputCallback() {
                                        @Override
                                        public void onInput(final String name) {
                                            if (name == null || name.isEmpty()) return;
                                            Ui.longInput(SkillsActivity.this, "Edit prompt",
                                                    "Skill prompt", skill.prompt,
                                                    new Ui.InputCallback() {
                                                        @Override
                                                        public void onInput(String prompt) {
                                                            if (prompt == null || prompt.isEmpty()) {
                                                                return;
                                                            }
                                                            db.updateSkill(skill.id, name, prompt);
                                                            refresh();
                                                        }
                                                    });
                                        }
                                    });
                        } else {
                            Ui.confirm(SkillsActivity.this, "Delete skill?",
                                    "Delete \"" + skill.name + "\"? Chats using it will fall back to Golu Tutor.",
                                    new Ui.ConfirmCallback() {
                                        @Override
                                        public void onYes() {
                                            db.deleteSkill(skill.id);
                                            refresh();
                                        }
                                    });
                        }
                    }
                })
                .show();
    }

    private static String firstLine(String s) {
        if (s == null) return "";
        String line = s.trim();
        int nl = line.indexOf('\n');
        if (nl >= 0) line = line.substring(0, nl).trim();
        if (line.length() > 80) line = line.substring(0, 77) + "...";
        return line;
    }

    private class SkillAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return skills == null ? 0 : skills.size();
        }

        @Override
        public Object getItem(int position) {
            return skills.get(position);
        }

        @Override
        public long getItemId(int position) {
            return skills.get(position).id;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            AppDb.Skill skill = skills.get(position);
            LinearLayout outer = new LinearLayout(SkillsActivity.this);
            outer.setOrientation(LinearLayout.VERTICAL);
            outer.setPadding(0, 0, 0, Ui.dp(SkillsActivity.this, 8));

            LinearLayout card = Ui.card(SkillsActivity.this);
            TextView name = new TextView(SkillsActivity.this);
            name.setText(skill.name + (skill.builtin ? "  (Built-in)" : ""));
            name.setTextSize(15f);
            name.setTypeface(Typeface.DEFAULT_BOLD);
            name.setTextColor(Ui.INK);
            card.addView(name, Ui.matchWrap());

            TextView sub = new TextView(SkillsActivity.this);
            sub.setText(firstLine(skill.prompt));
            sub.setTextSize(12f);
            sub.setTextColor(Ui.MUTED);
            card.addView(sub, Ui.matchWrap());

            outer.addView(card, Ui.matchWrap());
            return outer;
        }
    }
}
